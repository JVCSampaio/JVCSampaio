//! The engine Requiem drives: yt-dlp (downloads), Deno (solves YouTube's JavaScript checks for
//! yt-dlp) and FFmpeg (joins video and audio, converts). Downloaded on first run into the app's
//! folder, so nothing has to be installed by hand; copies already on PATH are used as a fallback.

use anyhow::{anyhow, bail, Context, Result};
use futures_util::StreamExt;
use serde::Serialize;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use tokio::io::AsyncWriteExt;

use crate::process::{exe_name, first_line, which};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Tool {
    Ytdlp,
    Deno,
    Ffmpeg,
    Ffprobe,
}

impl Tool {
    pub const ALL: [Tool; 4] = [Tool::Ytdlp, Tool::Deno, Tool::Ffmpeg, Tool::Ffprobe];

    pub fn base(self) -> &'static str {
        match self {
            Tool::Ytdlp => "yt-dlp",
            Tool::Deno => "deno",
            Tool::Ffmpeg => "ffmpeg",
            Tool::Ffprobe => "ffprobe",
        }
    }

    /// What the waiting screen says while this tool downloads.
    pub fn label(self) -> &'static str {
        match self {
            Tool::Ytdlp => "Baixando o yt-dlp, que tira o áudio e o vídeo do YouTube",
            Tool::Deno => "Baixando o Deno, que o yt-dlp usa para abrir o YouTube",
            Tool::Ffmpeg => "Baixando o FFmpeg, que junta imagem e som",
            Tool::Ffprobe => "Baixando o FFprobe, que confere os arquivos",
        }
    }
}

enum Packing {
    Raw,
    Gzip,
    Zip(&'static str),
}

struct Asset {
    url: String,
    packing: Packing,
}

const FFMPEG_RELEASE: &str = "b6.1.1";

fn asset(tool: Tool) -> Result<Asset> {
    let os = std::env::consts::OS;
    let arch = std::env::consts::ARCH;
    let arm = arch == "aarch64";
    Ok(match tool {
        Tool::Ytdlp => {
            let file = match (os, arm) {
                ("windows", false) => "yt-dlp.exe",
                ("windows", true) => "yt-dlp_arm64.exe",
                ("macos", _) => "yt-dlp_macos",
                ("linux", false) => "yt-dlp_linux",
                ("linux", true) => "yt-dlp_linux_aarch64",
                _ => bail!("sistema sem yt-dlp oficial: {os} {arch}"),
            };
            Asset {
                url: format!("https://github.com/yt-dlp/yt-dlp/releases/latest/download/{file}"),
                packing: Packing::Raw,
            }
        }
        Tool::Deno => {
            let target = match (os, arm) {
                // Windows on ARM runs the x64 build through emulation.
                ("windows", _) => "x86_64-pc-windows-msvc",
                ("macos", true) => "aarch64-apple-darwin",
                ("macos", false) => "x86_64-apple-darwin",
                ("linux", true) => "aarch64-unknown-linux-gnu",
                ("linux", false) => "x86_64-unknown-linux-gnu",
                _ => bail!("sistema sem Deno oficial: {os} {arch}"),
            };
            Asset {
                url: format!("https://github.com/denoland/deno/releases/latest/download/deno-{target}.zip"),
                packing: Packing::Zip(if os == "windows" { "deno.exe" } else { "deno" }),
            }
        }
        Tool::Ffmpeg | Tool::Ffprobe => {
            let platform = match os {
                "windows" => "win32",
                "macos" => "darwin",
                "linux" => "linux",
                _ => bail!("sistema sem FFmpeg estático: {os}"),
            };
            let arch = if arm && os != "windows" { "arm64" } else { "x64" };
            Asset {
                url: format!(
                    "https://github.com/eugeneware/ffmpeg-static/releases/download/{FFMPEG_RELEASE}/{}-{platform}-{arch}.gz",
                    tool.base()
                ),
                packing: Packing::Gzip,
            }
        }
    })
}

#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ToolsStatus {
    pub ytdlp: Option<String>,
    pub deno: Option<String>,
    pub ffmpeg: Option<String>,
    /// yt-dlp runs; the rest improves quality but is not required.
    pub ready: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InstallStep {
    pub tool: Tool,
    pub label: String,
    pub index: usize,
    pub total: usize,
    pub fraction: Option<f64>,
}

/// Where each program is, after looking in the app's folder and then on PATH.
#[derive(Debug, Clone, Default)]
pub struct ToolPaths {
    pub ytdlp: Option<PathBuf>,
    pub deno: Option<PathBuf>,
    pub ffmpeg: Option<PathBuf>,
}

impl ToolPaths {
    pub fn ffmpeg_dir(&self) -> Option<PathBuf> {
        self.ffmpeg.as_ref().and_then(|p| p.parent().map(Path::to_path_buf))
    }
}

pub struct Tools {
    dir: PathBuf,
    http: reqwest::Client,
}

impl Tools {
    pub fn new(dir: PathBuf, http: reqwest::Client) -> Tools {
        Tools { dir, http }
    }

    fn managed(&self, tool: Tool) -> PathBuf {
        self.dir.join(exe_name(tool.base()))
    }

    pub fn paths(&self) -> ToolPaths {
        let find = |tool: Tool| {
            let own = self.managed(tool);
            if own.is_file() {
                Some(own)
            } else {
                which(tool.base())
            }
        };
        ToolPaths { ytdlp: find(Tool::Ytdlp), deno: find(Tool::Deno), ffmpeg: find(Tool::Ffmpeg) }
    }

    pub async fn status(&self) -> ToolsStatus {
        let paths = self.paths();
        let version = |p: Option<PathBuf>, args: &'static [&'static str]| async move {
            match p {
                Some(p) => first_line(&p, args).await,
                None => None,
            }
        };
        let ytdlp = version(paths.ytdlp.clone(), &["--version"]).await;
        let deno = version(paths.deno.clone(), &["--version"]).await.map(|l| short_version(&l));
        let ffmpeg = version(paths.ffmpeg.clone(), &["-version"]).await.map(|l| short_version(&l));
        ToolsStatus { ready: ytdlp.is_some(), ytdlp, deno, ffmpeg }
    }

    /// Which tools still have to be downloaded into the app's folder.
    pub fn missing(&self) -> Vec<Tool> {
        Tool::ALL.into_iter().filter(|t| !self.managed(*t).is_file()).collect()
    }

    /// Downloads every missing tool, reporting each step. A tool that fails to download is
    /// skipped when a copy exists on PATH; yt-dlp is the only one the app cannot run without.
    pub async fn install(&self, mut on_step: impl FnMut(InstallStep)) -> Result<ToolsStatus> {
        tokio::fs::create_dir_all(&self.dir).await?;
        let missing = self.missing();
        let total = missing.len();
        let mut first_error = None;
        for (index, tool) in missing.into_iter().enumerate() {
            let step = |fraction| InstallStep { tool, label: tool.label().into(), index, total, fraction };
            on_step(step(Some(0.0)));
            let result = self.download(tool, |f| on_step(step(f))).await;
            if let Err(e) = result {
                log::warn!("download of {} failed: {e:#}", tool.base());
                if first_error.is_none() && (tool == Tool::Ytdlp || which(tool.base()).is_none()) {
                    first_error = Some(e);
                }
            }
        }
        let status = self.status().await;
        match first_error {
            Some(e) if !status.ready => Err(e),
            _ => Ok(status),
        }
    }

    async fn download(&self, tool: Tool, mut on_fraction: impl FnMut(Option<f64>)) -> Result<()> {
        let asset = asset(tool)?;
        let target = self.managed(tool);
        let part = self.dir.join(format!("{}.part", tool.base()));
        let response = self
            .http
            .get(&asset.url)
            .send()
            .await
            .and_then(|r| r.error_for_status())
            .with_context(|| format!("baixando {}", asset.url))?;
        let total = response.content_length();
        let mut file = tokio::fs::File::create(&part).await?;
        let mut got: u64 = 0;
        let mut last_report = 0.0;
        let mut stream = response.bytes_stream();
        while let Some(chunk) = stream.next().await {
            let chunk = chunk?;
            got += chunk.len() as u64;
            file.write_all(&chunk).await?;
            if let Some(total) = total.filter(|t| *t > 0) {
                let fraction = got as f64 / total as f64;
                if fraction - last_report >= 0.01 {
                    last_report = fraction;
                    on_fraction(Some(fraction.min(1.0)));
                }
            }
        }
        file.flush().await?;
        drop(file);
        on_fraction(None);

        let staged = self.dir.join(format!("{}.new", tool.base()));
        let (part_c, staged_c) = (part.clone(), staged.clone());
        let packing = asset.packing;
        tokio::task::spawn_blocking(move || unpack(&part_c, &staged_c, &packing)).await??;
        let _ = tokio::fs::remove_file(&part).await;
        make_executable(&staged)?;
        tokio::fs::rename(&staged, &target).await?;
        Ok(())
    }

    /// yt-dlp follows YouTube's changes in its own releases; this asks it to update itself.
    pub async fn update_ytdlp(&self) -> Result<String> {
        let path = self.managed(Tool::Ytdlp);
        if !path.is_file() {
            self.download(Tool::Ytdlp, |_| {}).await?;
            return first_line(&path, &["--version"]).await.ok_or_else(|| anyhow!("yt-dlp não abriu"));
        }
        let out = crate::process::command(&path).arg("-U").output().await?;
        let text = String::from_utf8_lossy(&out.stdout).to_string() + &String::from_utf8_lossy(&out.stderr);
        log::info!("yt-dlp -U: {}", text.trim());
        first_line(&path, &["--version"]).await.ok_or_else(|| anyhow!("yt-dlp não abriu depois de atualizar"))
    }
}

fn unpack(part: &Path, staged: &Path, packing: &Packing) -> Result<()> {
    match packing {
        Packing::Raw => {
            std::fs::copy(part, staged)?;
        }
        Packing::Gzip => {
            let mut decoder = flate2::read::GzDecoder::new(std::fs::File::open(part)?);
            let mut out = std::fs::File::create(staged)?;
            std::io::copy(&mut decoder, &mut out)?;
            out.flush()?;
        }
        Packing::Zip(entry) => {
            let mut archive = zip::ZipArchive::new(std::fs::File::open(part)?)?;
            let mut file = archive.by_name(entry).with_context(|| format!("{entry} não está no zip"))?;
            let mut bytes = Vec::with_capacity(file.size() as usize);
            file.read_to_end(&mut bytes)?;
            std::fs::write(staged, bytes)?;
        }
    }
    Ok(())
}

fn make_executable(path: &Path) -> Result<()> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o755))?;
    }
    let _ = path;
    Ok(())
}

/// "deno 2.4.1 (stable, release, x86_64-unknown-linux-gnu)" becomes "2.4.1";
/// "ffmpeg version 6.1.1 Copyright ..." becomes "6.1.1".
fn short_version(line: &str) -> String {
    line.split_whitespace()
        .find(|w| w.chars().next().is_some_and(|c| c.is_ascii_digit()))
        .unwrap_or(line)
        .trim_end_matches(',')
        .to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn versions_are_shortened() {
        assert_eq!(short_version("deno 2.4.1 (stable, release, x86_64-unknown-linux-gnu)"), "2.4.1");
        assert_eq!(short_version("ffmpeg version 6.1.1 Copyright (c) 2000-2023"), "6.1.1");
        assert_eq!(short_version("2025.10.22"), "2025.10.22");
    }

    #[test]
    fn every_tool_has_an_asset_here() {
        for tool in Tool::ALL {
            let a = asset(tool).unwrap();
            assert!(a.url.starts_with("https://github.com/"), "{}", a.url);
        }
    }

    #[test]
    fn gzip_and_zip_unpack() {
        let dir = tempfile::tempdir().unwrap();
        let gz = dir.path().join("a.gz");
        let mut enc = flate2::write::GzEncoder::new(std::fs::File::create(&gz).unwrap(), flate2::Compression::fast());
        enc.write_all(b"binary").unwrap();
        enc.finish().unwrap();
        let out = dir.path().join("a");
        unpack(&gz, &out, &Packing::Gzip).unwrap();
        assert_eq!(std::fs::read(&out).unwrap(), b"binary");

        let zp = dir.path().join("d.zip");
        let mut zw = zip::ZipWriter::new(std::fs::File::create(&zp).unwrap());
        zw.start_file("deno", zip::write::SimpleFileOptions::default()).unwrap();
        zw.write_all(b"deno-binary").unwrap();
        zw.finish().unwrap();
        let out2 = dir.path().join("deno");
        unpack(&zp, &out2, &Packing::Zip("deno")).unwrap();
        assert_eq!(std::fs::read(&out2).unwrap(), b"deno-binary");
    }

    /// Downloads the real tools from GitHub. Run with `cargo test -- --ignored online_`.
    #[tokio::test]
    #[ignore]
    async fn online_install_tools() {
        let dir = tempfile::tempdir().unwrap();
        let tools = Tools::new(dir.path().to_path_buf(), reqwest::Client::new());
        let status = tools.install(|s| eprintln!("{} {:?}", s.label, s.fraction)).await.unwrap();
        eprintln!("{status:?}");
        assert!(status.ytdlp.is_some(), "yt-dlp did not run");
        assert!(status.deno.is_some(), "deno did not run");
        assert!(status.ffmpeg.is_some(), "ffmpeg did not run");
        assert!(tools.missing().is_empty());
    }
}
