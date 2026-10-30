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

/// A command that yields the processor to everything else: below normal priority on Windows,
/// through `nice` elsewhere when it exists. For background work such as measuring loudness.
pub fn low_priority(program: impl AsRef<OsStr>) -> Command {
    #[cfg(windows)]
    {
        const BELOW_NORMAL_PRIORITY_CLASS: u32 = 0x0000_4000;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        let mut cmd = command(program);
        cmd.creation_flags(CREATE_NO_WINDOW | BELOW_NORMAL_PRIORITY_CLASS);
        cmd
    }
    #[cfg(not(windows))]
    match which("nice") {
        Some(nice) => {
            let mut cmd = command(nice);
            cmd.args(["-n", "10"]).arg(program);
            cmd
        }
        None => command(program),
    }
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
