//! Child processes that never flash a console window on Windows and always speak UTF-8.

use std::ffi::OsStr;
use std::path::{Path, PathBuf};
use tokio::process::Command;

pub fn command(program: impl AsRef<OsStr>) -> Command {
    let mut cmd = Command::new(program);
    cmd.env("PYTHONUTF8", "1").env("PYTHONIOENCODING", "utf-8").kill_on_drop(true);
    #[cfg(windows)]
    {
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    cmd
}

pub fn exe_name(base: &str) -> String {
    if cfg!(windows) {
        format!("{base}.exe")
    } else {
        base.to_string()
    }
}

/// Looks a program up on PATH, the way a shell would.
pub fn which(base: &str) -> Option<PathBuf> {
    let name = exe_name(base);
    std::env::var_os("PATH")
        .and_then(|paths| std::env::split_paths(&paths).map(|dir| dir.join(&name)).find(|p| p.is_file()))
}

/// The first line a program prints for its version flag, or None when it does not run.
pub async fn first_line(program: &Path, args: &[&str]) -> Option<String> {
    let out = command(program).args(args).output().await.ok()?;
    if !out.status.success() {
        return None;
    }
    String::from_utf8_lossy(&out.stdout).lines().next().map(|l| l.trim().to_string()).filter(|l| !l.is_empty())
}
