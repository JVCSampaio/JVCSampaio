//! Everything Requiem asks yt-dlp: search, read a video or a feed, and download with progress.

use anyhow::{anyhow, Context, Result};
use serde_json::Value;
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::sync::Notify;

use crate::media::{self, Entry, Info, Kind};
use crate::process::command;
use crate::settings::{Account, AudioFormat};
use crate::tools::ToolPaths;

/// How yt-dlp proves who the person is, when it needs to (history, likes).
#[derive(Debug, Clone, PartialEq)]
pub enum Auth {
    None,
    CookieFile(PathBuf),
    Browser(String),
}

impl Auth {
    pub fn from_account(account: &Account, cookie_file: &Path) -> Auth {
        match account {
            Account::None => Auth::None,
            Account::App | Account::File => Auth::CookieFile(cookie_file.to_path_buf()),
            Account::Browser { browser } => Auth::Browser(browser.clone()),
        }
    }

    fn args(&self) -> Vec<String> {
        match self {
            Auth::None => vec![],
            Auth::CookieFile(path) => vec!["--cookies".into(), path.to_string_lossy().into_owned()],
            Auth::Browser(name) => vec!["--cookies-from-browser".into(), name.clone()],
        }
    }
}

#[derive(Clone)]
pub struct YtDlp {
    pub tools: ToolPaths,
    pub auth: Auth,
}

/// Progress of one download, already reduced to what the interface shows.
#[derive(Debug, Clone, PartialEq)]
pub enum Progress {
    Bytes { done: u64, total: Option<u64>, speed: Option<f64>, eta: Option<f64>, part: u32 },
    Processing(String),
}

/// What to download and where.
pub struct DownloadPlan {
    pub kind: Kind,
    pub video_height: u32,
    pub audio_format: AudioFormat,
    /// Path without extension; yt-dlp adds it.
    pub output_stem: PathBuf,
    pub thumbnail_stem: PathBuf,
}

impl YtDlp {
    fn base(&self) -> Result<tokio::process::Command> {
        let bin = self.tools.ytdlp.as_ref().ok_or_else(|| anyhow!("yt-dlp ainda não foi instalado"))?;
        let mut cmd = command(bin);
        cmd.args(["--ignore-config", "--no-color", "--no-cache-dir"]);
        if let Some(deno) = &self.tools.deno {
            cmd.arg("--js-runtimes").arg(format!("deno:{}", deno.to_string_lossy()));
        }
        if let Some(dir) = self.tools.ffmpeg_dir() {
            cmd.arg("--ffmpeg-location").arg(dir);
        }
        cmd.args(self.auth.args());
        Ok(cmd)
    }

    /// Runs yt-dlp and parses its JSON output.
    async fn json(&self, args: &[&str]) -> Result<Value> {
        let mut cmd = self.base()?;
        cmd.args(args).stdin(Stdio::null()).stdout(Stdio::piped()).stderr(Stdio::piped());
        let out = cmd.output().await.context("yt-dlp não abriu")?;
        let stdout = String::from_utf8_lossy(&out.stdout);
        let stderr = String::from_utf8_lossy(&out.stderr);
        // A failed read still prints JSON: `null` for the video it could not read.
        let value: Option<Value> = serde_json::from_str(stdout.trim()).ok();
        match value {
            Some(v) if v.is_object() => Ok(v),
            _ => {
                log::warn!("yt-dlp gave no answer: {}", stderr.trim());
                Err(anyhow!(friendly_error(&stderr)))
            }
        }
    }

    /// Songs search the same index as videos: YouTube Music's flat results carry no artist and
    /// no length, which leaves a list of identical titles to choose from.
    pub async fn search(&self, query: &str, kind: Kind, limit: u32) -> Result<Vec<Entry>> {
        let listing = self.json(&["--flat-playlist", "-J", &search_target(query, limit)]).await?;
        let mut found = media::entries(&listing);
        if kind == Kind::Music {
            rank_for_music(&mut found);
        }
        Ok(found)
    }

    /// Lists a feed or playlist without reading each video (fast).
    pub async fn listing(&self, url: &str, limit: u32) -> Result<(String, Vec<Entry>)> {
        let end = limit.to_string();
        let listing = self.json(&["--flat-playlist", "-J", "--playlist-end", &end, url]).await?;
        let title = listing.get("title").and_then(Value::as_str).unwrap_or("").to_string();
        Ok((title, media::entries(&listing)))
    }

    /// Reads one video in full and keeps the raw JSON, so the download does not read it again.
    pub async fn info(&self, url: &str) -> Result<(Info, Value)> {
        let raw = self.json(&["-J", "--no-playlist", url]).await?;
        let info: Info = serde_json::from_value(raw.clone()).context("resposta do yt-dlp sem formato esperado")?;
        Ok((info, raw))
    }

    /// Downloads from an info JSON written by [`info`]. Returns the final file path.
    pub async fn download(
        &self,
        info_json: &Path,
        plan: &DownloadPlan,
        cancel: Arc<Notify>,
        mut on_progress: impl FnMut(Progress),
    ) -> Result<PathBuf> {
        let mut cmd = self.base()?;
        let has_ffmpeg = self.tools.ffmpeg.is_some();
        cmd.arg("--load-info-json").arg(info_json);
        cmd.args(download_args(plan, has_ffmpeg));
        cmd.stdin(Stdio::null()).stdout(Stdio::piped()).stderr(Stdio::piped());
        let mut child = cmd.spawn().context("yt-dlp não abriu")?;
        let stdout = child.stdout.take().expect("piped stdout");
        let stderr = child.stderr.take().expect("piped stderr");

        let err_task = tokio::spawn(async move {
            let mut lines = BufReader::new(stderr).lines();
            let mut text = String::new();
            while let Ok(Some(line)) = lines.next_line().await {
                text.push_str(&line);
                text.push('\n');
            }
            text
        });

        let mut lines = BufReader::new(stdout).lines();
        let mut final_path: Option<PathBuf> = None;
        let mut part: u32 = 0;
        let mut last_format = String::new();
        loop {
            tokio::select! {
                line = lines.next_line() => {
                    let Some(line) = line? else { break };
                    match parse_line(&line) {
                        Some(Line::Bytes { done, total, speed, eta, format }) => {
                            if format != last_format {
                                if !last_format.is_empty() { part += 1; }
                                last_format = format;
                            }
                            on_progress(Progress::Bytes { done, total, speed, eta, part });
                        }
                        Some(Line::Post(name)) => on_progress(Progress::Processing(name)),
                        Some(Line::Path(p)) => final_path = Some(p),
                        None => {}
                    }
                }
                _ = cancel.notified() => {
                    let _ = child.kill().await;
                    return Err(anyhow!("Cancelado"));
                }
            }
        }
        let status = child.wait().await?;
        let stderr = err_task.await.unwrap_or_default();
        if !status.success() {
            return Err(anyhow!(friendly_error(&stderr)));
        }
        let path = final_path.ok_or_else(|| anyhow!("O yt-dlp terminou sem dizer onde salvou o arquivo"))?;
        if !path.is_file() {
            return Err(anyhow!("O arquivo baixado não apareceu em {}", path.display()));
        }
        Ok(path)
    }

    pub async fn version(&self) -> Option<String> {
        let bin = self.tools.ytdlp.as_ref()?;
        crate::process::first_line(bin, &["--version"]).await
    }
}

fn search_target(query: &str, limit: u32) -> String {
    format!("ytsearch{limit}:{query}")
}

/// Looking for a song, Shorts and hour-long mixes are rarely the answer: they go to the end,
/// the rest keeps YouTube's order.
fn rank_for_music(entries: &mut [Entry]) {
    entries.sort_by_key(|e| e.is_short || e.duration.is_some_and(|d| d > 20.0 * 60.0));
}

/// Formats chosen so every platform's web view plays the file: AAC audio, H.264 video in MP4.
pub fn format_selector(plan: &DownloadPlan, has_ffmpeg: bool) -> String {
    match plan.kind {
        Kind::Video => {
            let h = plan.video_height;
            if has_ffmpeg {
                format!(
                    "bv*[height<={h}][ext=mp4][vcodec^=avc1]+ba[ext=m4a]/bv*[height<={h}][ext=mp4]+ba[ext=m4a]/b[height<={h}][ext=mp4]/bv*[height<={h}]+ba/b[height<={h}]/b"
                )
            } else {
                // Without FFmpeg nothing can be joined: only files that already have both.
                format!("b[height<={h}][ext=mp4]/b[ext=mp4]/b")
            }
        }
        _ => "ba[ext=m4a]/ba[acodec^=mp4a]/ba/b".to_string(),
    }
}

pub fn download_args(plan: &DownloadPlan, has_ffmpeg: bool) -> Vec<String> {
    let mut args: Vec<String> = vec![
        "-f".into(),
        format_selector(plan, has_ffmpeg),
        "-o".into(),
        format!("{}.%(ext)s", plan.output_stem.to_string_lossy()),
        "-o".into(),
        format!("thumbnail:{}.%(ext)s", plan.thumbnail_stem.to_string_lossy()),
        "--write-thumbnail".into(),
        "--no-playlist".into(),
        "--no-part".into(),
        "--no-mtime".into(),
        "--windows-filenames".into(),
        "--no-simulate".into(),
        "--print".into(),
        "after_move:RQPATH %(filepath)s".into(),
        "--progress".into(),
        "--newline".into(),
        "--progress-template".into(),
        "download:RQDL %(progress.downloaded_bytes)s %(progress.total_bytes)s %(progress.total_bytes_estimate)s %(progress.speed)s %(progress.eta)s %(info.format_id)s".into(),
        "--progress-template".into(),
        "postprocess:RQPP %(progress.postprocessor)s".into(),
    ];
    if has_ffmpeg {
        args.extend(["--embed-metadata".into(), "--embed-thumbnail".into()]);
        match plan.kind {
            Kind::Video => args.extend(["--merge-output-format".into(), "mp4".into()]),
            _ => {
                if plan.audio_format == AudioFormat::Mp3 {
                    args.extend([
                        "-x".into(),
                        "--audio-format".into(),
                        "mp3".into(),
                        "--audio-quality".into(),
                        "0".into(),
                    ]);
                }
            }
        }
    }
    args
}

#[derive(Debug, PartialEq)]
enum Line {
    Bytes { done: u64, total: Option<u64>, speed: Option<f64>, eta: Option<f64>, format: String },
    Post(String),
    Path(PathBuf),
}

fn parse_line(line: &str) -> Option<Line> {
    if let Some(rest) = line.strip_prefix("RQPATH ") {
        return Some(Line::Path(PathBuf::from(rest.trim())));
    }
    if let Some(rest) = line.strip_prefix("RQPP ") {
        return Some(Line::Post(rest.trim().to_string()));
    }
    let rest = line.strip_prefix("RQDL ")?;
    let parts: Vec<&str> = rest.split_whitespace().collect();
    let num = |i: usize| parts.get(i).and_then(|s| s.parse::<f64>().ok());
    let done = num(0)? as u64;
    let total = num(1).or_else(|| num(2)).map(|t| t as u64);
    Some(Line::Bytes { done, total, speed: num(3), eta: num(4), format: parts.get(5).unwrap_or(&"").to_string() })
}

/// What the person reads when YouTube asks the connection to sign in.
pub const BOT_CHECK: &str =
    "O YouTube pediu login para confirmar que você não é um robô. Conecte sua conta em Ajustes.";

/// The request failed only because YouTube wants a signed-in session.
pub fn is_bot_check(error: &anyhow::Error) -> bool {
    error.to_string() == BOT_CHECK
}

/// The last ERROR line of yt-dlp, turned into something the person can act on.
pub fn friendly_error(stderr: &str) -> String {
    let error = stderr
        .lines()
        .rev()
        .find(|l| l.starts_with("ERROR:"))
        .map(|l| l.trim_start_matches("ERROR:").trim().to_string())
        .unwrap_or_else(|| stderr.lines().rev().find(|l| !l.trim().is_empty()).unwrap_or("").trim().to_string());
    let lower = error.to_lowercase();
    let message = if lower.contains("sign in to confirm") || lower.contains("not a bot") {
        BOT_CHECK
    } else if lower.contains("cookies are no longer valid") || lower.contains("cookies") && lower.contains("expired") {
        "Sua sessão do YouTube expirou. Entre de novo em Ajustes."
    } else if lower.contains("could not copy") && lower.contains("cookie") || lower.contains("failed to decrypt") {
        "Não deu para ler o login desse navegador. Feche o navegador ou use outra opção em Ajustes."
    } else if lower.contains("private video") {
        "O vídeo é privado."
    } else if lower.contains("members-only") || lower.contains("join this channel") {
        "O vídeo é só para membros do canal."
    } else if lower.contains("premium") {
        "O vídeo é exclusivo do YouTube Premium."
    } else if lower.contains("age") && lower.contains("confirm your age") {
        "O vídeo tem restrição de idade. Conecte sua conta em Ajustes."
    } else if lower.contains("video unavailable")
        || lower.contains("this video is unavailable")
        || lower.contains("removed")
    {
        "O vídeo não está mais disponível."
    } else if lower.contains("requested format is not available") {
        "O YouTube não ofereceu esse formato agora. Tente de novo mais tarde."
    } else if lower.contains("unable to download")
        || lower.contains("timed out")
        || lower.contains("network")
        || lower.contains("getaddrinfo")
    {
        "Sem conexão com o YouTube. Confira a internet e tente de novo."
    } else if lower.contains("login required") || lower.contains("need to log in") || lower.contains("authentication") {
        "Essa lista precisa da sua conta. Conecte o YouTube em Ajustes."
    } else if error.is_empty() {
        "O yt-dlp parou sem explicar o motivo."
    } else {
        return error;
    };
    message.to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn plan(kind: Kind) -> DownloadPlan {
        DownloadPlan {
            kind,
            video_height: 720,
            audio_format: AudioFormat::M4a,
            output_stem: PathBuf::from("/lib/Músicas/A - T [id]"),
            thumbnail_stem: PathBuf::from("/data/covers/id"),
        }
    }

    #[test]
    fn music_keeps_aac_without_conversion() {
        let args = download_args(&plan(Kind::Music), true);
        assert!(args.contains(&"ba[ext=m4a]/ba[acodec^=mp4a]/ba/b".to_string()));
        assert!(!args.contains(&"-x".to_string()));
        assert!(args.contains(&"/lib/Músicas/A - T [id].%(ext)s".to_string()));
    }

    #[test]
    fn mp3_converts() {
        let p = DownloadPlan { audio_format: AudioFormat::Mp3, ..plan(Kind::Music) };
        let args = download_args(&p, true);
        assert!(args.windows(2).any(|w| w[0] == "--audio-format" && w[1] == "mp3"));
    }

    #[test]
    fn video_prefers_h264_mp4_under_the_height() {
        let selector = format_selector(&plan(Kind::Video), true);
        assert!(selector.starts_with("bv*[height<=720][ext=mp4][vcodec^=avc1]+ba[ext=m4a]"));
        let args = download_args(&plan(Kind::Video), true);
        assert!(args.windows(2).any(|w| w[0] == "--merge-output-format" && w[1] == "mp4"));
    }

    #[test]
    fn video_without_ffmpeg_avoids_merging() {
        let selector = format_selector(&plan(Kind::Video), false);
        assert!(!selector.contains('+'));
        let args = download_args(&plan(Kind::Video), false);
        assert!(!args.contains(&"--embed-metadata".to_string()));
    }

    #[test]
    fn progress_lines_parse() {
        assert_eq!(
            parse_line("RQDL 1024 4096 NA 512.5 6 137"),
            Some(Line::Bytes {
                done: 1024,
                total: Some(4096),
                speed: Some(512.5),
                eta: Some(6.0),
                format: "137".into()
            })
        );
        assert_eq!(
            parse_line("RQDL 10 NA 2000.0 NA NA 140"),
            Some(Line::Bytes { done: 10, total: Some(2000), speed: None, eta: None, format: "140".into() })
        );
        assert_eq!(parse_line("RQPP Merger"), Some(Line::Post("Merger".into())));
        assert_eq!(parse_line("RQPATH /lib/a b.m4a"), Some(Line::Path(PathBuf::from("/lib/a b.m4a"))));
        assert_eq!(parse_line("[youtube] abc: Downloading webpage"), None);
    }

    #[test]
    fn errors_become_actions() {
        let bot = "WARNING: x\nERROR: [youtube] abc: Sign in to confirm you’re not a bot. Use --cookies";
        assert!(friendly_error(bot).contains("Conecte sua conta"));
        assert_eq!(friendly_error("ERROR: [youtube] abc: Private video. Sign in"), "O vídeo é privado.");
        assert_eq!(friendly_error("ERROR: something odd"), "something odd");
        assert!(friendly_error("").contains("sem explicar"));
    }

    #[test]
    fn songs_come_before_shorts_and_mixes() {
        assert_eq!(search_target("ação & fé", 20), "ytsearch20:ação & fé");
        let entry = |id: &str, duration: f64, is_short: bool| Entry {
            id: id.into(),
            title: id.into(),
            artist: String::new(),
            channel: String::new(),
            duration: Some(duration),
            thumbnail: String::new(),
            is_short,
        };
        let mut found = vec![
            entry("mix", 3600.0, false),
            entry("short", 30.0, true),
            entry("song", 240.0, false),
            entry("live", 600.0, false),
        ];
        rank_for_music(&mut found);
        let order: Vec<&str> = found.iter().map(|e| e.id.as_str()).collect();
        assert_eq!(order, ["song", "live", "mix", "short"]);
    }

    #[test]
    fn auth_arguments() {
        assert!(Auth::None.args().is_empty());
        assert_eq!(Auth::Browser("firefox".into()).args(), vec!["--cookies-from-browser", "firefox"]);
        assert_eq!(Auth::CookieFile(PathBuf::from("/c.txt")).args(), vec!["--cookies", "/c.txt"]);
    }

    /// The whole path against the real YouTube: install the tools, search, read and download a
    /// song and a video. Run with `cargo test -- --ignored online_`.
    #[tokio::test]
    #[ignore]
    async fn online_search_and_download() {
        let dir = tempfile::tempdir().unwrap();
        let tools = crate::tools::Tools::new(dir.path().join("bin"), reqwest::Client::new());
        tools.install(|_| {}).await.unwrap();
        let ytdlp = YtDlp { tools: tools.paths(), auth: Auth::None };

        for (kind, query) in [(Kind::Music, "Saint-Saens Danse Macabre"), (Kind::Video, "Danse Macabre orchestra")] {
            let found = ytdlp.search(query, kind, 10).await.unwrap();
            assert!(!found.is_empty(), "nothing found for {query}");
            let pick = found
                .iter()
                .filter(|e| !e.is_short && e.duration.is_some_and(|d| d > 60.0 && d < 900.0))
                .min_by(|a, b| a.duration.partial_cmp(&b.duration).unwrap())
                .unwrap_or(&found[0]);
            eprintln!("{kind:?}: {} ({})", pick.title, pick.id);

            let (_, raw) = ytdlp.info(&crate::links::watch_url(&pick.id)).await.unwrap();
            let info_path = dir.path().join(format!("{}.info.json", pick.id));
            std::fs::write(&info_path, serde_json::to_vec(&raw).unwrap()).unwrap();
            let plan = DownloadPlan {
                video_height: 360,
                output_stem: dir.path().join(&pick.id),
                thumbnail_stem: dir.path().join(format!("{}-cover", pick.id)),
                ..plan(kind)
            };
            let file = ytdlp.download(&info_path, &plan, Arc::new(Notify::new()), |_| {}).await.unwrap();
            let size = std::fs::metadata(&file).unwrap().len();
            eprintln!("  {} ({size} bytes)", file.display());
            assert!(size > 100_000, "{} is too small", file.display());
        }
    }

    /// The radio against the real YouTube: the mix of a song lists other songs.
    #[tokio::test]
    #[ignore]
    async fn online_radio_mix() {
        let dir = tempfile::tempdir().unwrap();
        let tools = crate::tools::Tools::new(dir.path().join("bin"), reqwest::Client::new());
        tools.install(|_| {}).await.unwrap();
        let ytdlp = YtDlp { tools: tools.paths(), auth: Auth::None };
        let seed = ytdlp.search("Saint-Saens Danse Macabre", Kind::Music, 5).await.unwrap().remove(0);
        let (_, mix) = ytdlp.listing(&crate::radio::mix_url(&seed.id), 60).await.unwrap();
        let played = [seed.id.clone()].into();
        let picks = crate::radio::picks(mix, &played, &Default::default(), &Default::default(), 20.0 * 60.0);
        for e in picks.iter().take(10) {
            eprintln!("{} - {} ({:?})", e.artist, e.title, e.duration);
        }
        assert!(picks.len() >= 5, "only {} songs in the mix of {}", picks.len(), seed.id);
    }
}
