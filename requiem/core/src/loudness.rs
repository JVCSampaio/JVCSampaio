//! How loud each song is, measured once, so the player can even out the volume between songs
//! the way streaming services do (EBU R128). Gains other players already wrote in the tags
//! (ReplayGain) are trusted first; otherwise FFmpeg's ebur128 filter reads the whole file.

use anyhow::{anyhow, Result};
use lofty::file::TaggedFileExt;
use lofty::prelude::ItemKey;
use regex::Regex;
use std::path::Path;
use std::process::Stdio;
use std::sync::LazyLock;

use crate::process;

/// Integrated loudness in LUFS and sample peak in dBFS.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Measured {
    pub loudness: f64,
    pub peak: Option<f64>,
}

/// Quieter than this is silence or hiss: it gets no correction.
pub const SILENCE: f64 = -40.0;

/// ReplayGain 2.0 sets its gains against this loudness.
const REPLAYGAIN_REFERENCE: f64 = -18.0;

static INTEGRATED: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"I:\s+(-?(?:\d+(?:\.\d+)?|inf))\s+LUFS").unwrap());
static PEAK: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"Peak:\s+(-?(?:\d+(?:\.\d+)?|inf))\s+dBFS").unwrap());
static GAIN: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"^\s*([+-]?\d+(?:\.\d+)?)\s*(?:dB)?\s*$").unwrap());

/// Reads the summary FFmpeg's ebur128 filter prints at the end. A silent file reports `-inf`.
pub fn parse_ebur128(stderr: &str) -> Option<Measured> {
    let summary = &stderr[stderr.rfind("Summary:")?..];
    let loudness: f64 = INTEGRATED.captures(summary)?[1].parse().ok()?;
    let peak = PEAK.captures(summary).and_then(|c| c[1].parse::<f64>().ok()).filter(|p| p.is_finite());
    Some(Measured { loudness, peak })
}

/// "-6.20 dB" as written by ReplayGain taggers.
fn parse_gain(text: &str) -> Option<f64> {
    GAIN.captures(text)?[1].parse().ok()
}

/// A track gain another player wrote in the file, turned into loudness.
pub fn from_tags(path: &Path) -> Option<Measured> {
    let file = lofty::read_from_path(path).ok()?;
    file.tags().iter().find_map(|tag| {
        let gain = parse_gain(tag.get_string(ItemKey::ReplayGainTrackGain)?)?;
        let peak = tag
            .get_string(ItemKey::ReplayGainTrackPeak)
            .and_then(|p| p.trim().parse::<f64>().ok())
            .filter(|p| *p > 0.0)
            .map(|p| 20.0 * p.log10());
        Some(Measured { loudness: REPLAYGAIN_REFERENCE - gain, peak })
    })
}

/// Measures the first audio stream of a file with FFmpeg at low priority, one thread, so a
/// library being read never competes with what plays. Ok(None) for silence.
pub async fn measure(ffmpeg: &Path, file: &Path) -> Result<Option<Measured>> {
    let mut cmd = process::low_priority(ffmpeg);
    cmd.args(["-nostdin", "-hide_banner", "-nostats", "-threads", "1", "-i"])
        .arg(file)
        .args(["-map", "0:a:0", "-vn", "-sn", "-dn", "-af", "ebur128=peak=sample:framelog=verbose", "-f", "null", "-"])
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::piped());
    let out = cmd.output().await.map_err(|e| anyhow!("FFmpeg não abriu: {e}"))?;
    let stderr = String::from_utf8_lossy(&out.stderr);
    if !out.status.success() {
        let last = stderr.lines().rev().find(|l| !l.trim().is_empty()).unwrap_or("").trim();
        return Err(anyhow!("FFmpeg não leu o áudio: {last}"));
    }
    let measured = parse_ebur128(&stderr).ok_or_else(|| anyhow!("FFmpeg não mediu o volume"))?;
    Ok((measured.loudness >= SILENCE).then_some(measured))
}

/// The tags when they carry a gain, FFmpeg otherwise. The second value says which.
pub async fn read(ffmpeg: &Path, file: &Path) -> Result<(Option<Measured>, &'static str)> {
    let path = file.to_path_buf();
    if let Ok(Some(tagged)) = tokio::task::spawn_blocking(move || from_tags(&path)).await {
        return Ok((Some(tagged), "replaygain"));
    }
    Ok((measure(ffmpeg, file).await?, "ebur128"))
}

#[cfg(test)]
mod tests {
    use super::*;

    const SUMMARY: &str = "[Parsed_ebur128_0 @ 0x5581] Summary:

  Integrated loudness:
    I:         -9.4 LUFS
    Threshold: -19.6 LUFS

  Loudness range:
    LRA:         4.1 LU
    Threshold: -29.6 LUFS
    LRA low:   -12.0 LUFS
    LRA high:   -7.9 LUFS

  Sample peak:
    Peak:        0.4 dBFS
";

    #[test]
    fn reads_the_summary() {
        let log = format!("[Parsed_ebur128_0 @ 0x5581] t: 0.1 I: -70.0 LUFS\n{SUMMARY}");
        assert_eq!(parse_ebur128(&log), Some(Measured { loudness: -9.4, peak: Some(0.4) }));
        let silent =
            "Summary:\n  Integrated loudness:\n    I:         -inf LUFS\n  Sample peak:\n    Peak:       -inf dBFS\n";
        let m = parse_ebur128(silent).unwrap();
        assert!(m.loudness < SILENCE && m.peak.is_none());
        assert_eq!(parse_ebur128("no summary here"), None);
    }

    #[test]
    fn replaygain_gains() {
        assert_eq!(parse_gain("-6.20 dB"), Some(-6.2));
        assert_eq!(parse_gain("+1.5 dB"), Some(1.5));
        assert_eq!(parse_gain(" 3 "), Some(3.0));
        assert_eq!(parse_gain("loud"), None);
    }

    /// A tone made by FFmpeg (when it is on this machine) reads near its known loudness, and
    /// digital silence reads as none.
    #[tokio::test]
    async fn measures_a_tone_with_ffmpeg() {
        let Some(ffmpeg) = process::which("ffmpeg") else {
            eprintln!("ffmpeg not installed here; skipped");
            return;
        };
        let dir = tempfile::tempdir().unwrap();
        let tone = dir.path().join("tone.wav");
        let silence = dir.path().join("silence.wav");
        for (file, source) in [(&tone, "sine=frequency=1000:duration=5"), (&silence, "anullsrc=duration=5")] {
            let ok = process::command(&ffmpeg)
                .args(["-nostdin", "-loglevel", "error", "-f", "lavfi", "-i", source, "-ac", "1", "-ar", "48000"])
                .arg(file)
                .status()
                .await
                .unwrap()
                .success();
            assert!(ok);
        }
        // FFmpeg's sine has 1/8 of full scale: a peak of -18.06 dBFS, -21.1 LUFS in mono.
        let m = measure(&ffmpeg, &tone).await.unwrap().unwrap();
        assert!((m.loudness - -21.1).abs() < 0.3, "{m:?}");
        assert!((m.peak.unwrap() - -18.06).abs() < 0.3, "{m:?}");
        assert_eq!(measure(&ffmpeg, &silence).await.unwrap(), None);
    }
}
