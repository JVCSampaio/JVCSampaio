//! Lyrics from LRCLIB (lrclib.net, an open lyrics database), synced line by line when
//! available. Cached in the library so they work offline after the first time.

use anyhow::Result;
use regex::Regex;
use serde::{Deserialize, Serialize};
use std::sync::LazyLock;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Line {
    pub ms: u64,
    pub text: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Lyrics {
    /// Timed lines, empty when only plain text exists.
    pub lines: Vec<Line>,
    pub plain: Option<String>,
    pub instrumental: bool,
}

/// What is stored in the cache column: lyrics, or the date of a search that found nothing.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Cached {
    pub lyrics: Option<Lyrics>,
    pub fetched_at: i64,
}

static TIMESTAMP: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]").unwrap());

/// Parses LRC text. A line may carry several timestamps ("[00:12.00][01:30.50]chorus").
pub fn parse_lrc(text: &str) -> Vec<Line> {
    let mut lines = Vec::new();
    for raw in text.lines() {
        let stamps: Vec<u64> = TIMESTAMP
            .captures_iter(raw)
            .map(|c| {
                let min: u64 = c[1].parse().unwrap_or(0);
                let sec: u64 = c[2].parse().unwrap_or(0);
                let frac = c.get(3).map(|m| {
                    let digits = m.as_str();
                    let value: u64 = digits.parse().unwrap_or(0);
                    match digits.len() {
                        1 => value * 100,
                        2 => value * 10,
                        _ => value,
                    }
                });
                min * 60_000 + sec * 1000 + frac.unwrap_or(0)
            })
            .collect();
        if stamps.is_empty() {
            continue;
        }
        let words = TIMESTAMP.replace_all(raw, "").trim().to_string();
        for ms in stamps {
            lines.push(Line { ms, text: words.clone() });
        }
    }
    lines.sort_by_key(|l| l.ms);
    lines
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct LrcLibRecord {
    synced_lyrics: Option<String>,
    plain_lyrics: Option<String>,
    #[serde(default)]
    instrumental: bool,
}

impl LrcLibRecord {
    fn into_lyrics(self) -> Option<Lyrics> {
        let lines = self.synced_lyrics.as_deref().map(parse_lrc).unwrap_or_default();
        let plain = self.plain_lyrics.filter(|p| !p.trim().is_empty());
        if lines.is_empty() && plain.is_none() && !self.instrumental {
            return None;
        }
        Some(Lyrics { lines, plain, instrumental: self.instrumental })
    }
}

const API: &str = "https://lrclib.net/api";

/// Exact match first (title, artist, album, duration), then a search by title and artist.
pub async fn fetch(
    http: &reqwest::Client,
    title: &str,
    artist: &str,
    album: Option<&str>,
    duration: Option<f64>,
) -> Result<Option<Lyrics>> {
    let mut query: Vec<(&str, String)> = vec![("track_name", title.to_string()), ("artist_name", artist.to_string())];
    if let Some(album) = album.filter(|a| !a.is_empty()) {
        query.push(("album_name", album.to_string()));
    }
    if let Some(d) = duration.filter(|d| *d > 0.0) {
        query.push(("duration", format!("{}", d.round() as i64)));
    }
    let exact = http.get(format!("{API}/get")).query(&query).send().await?;
    if exact.status().is_success() {
        if let Some(found) = exact.json::<LrcLibRecord>().await?.into_lyrics() {
            return Ok(Some(found));
        }
    }
    let search = http
        .get(format!("{API}/search"))
        .query(&[("track_name", title), ("artist_name", artist)])
        .send()
        .await?
        .error_for_status()?;
    let records: Vec<LrcLibRecord> = search.json().await?;
    let mut best: Option<Lyrics> = None;
    for record in records {
        if let Some(lyrics) = record.into_lyrics() {
            let synced = !lyrics.lines.is_empty();
            if synced {
                return Ok(Some(lyrics));
            }
            best.get_or_insert(lyrics);
        }
    }
    Ok(best)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lrc_parses_and_sorts() {
        let lines =
            parse_lrc("[ar: Someone]\n[00:12.50]Primeira\n[00:05.1]Abertura\n[01:00.123][00:30.00]Refrão\nsem tempo");
        assert_eq!(
            lines,
            vec![
                Line { ms: 5_100, text: "Abertura".into() },
                Line { ms: 12_500, text: "Primeira".into() },
                Line { ms: 30_000, text: "Refrão".into() },
                Line { ms: 60_123, text: "Refrão".into() },
            ]
        );
    }

    #[test]
    fn empty_record_is_none() {
        let r =
            LrcLibRecord { synced_lyrics: Some(String::new()), plain_lyrics: Some("  ".into()), instrumental: false };
        assert!(r.into_lyrics().is_none());
        let i = LrcLibRecord { synced_lyrics: None, plain_lyrics: None, instrumental: true };
        assert!(i.into_lyrics().unwrap().instrumental);
    }
}
