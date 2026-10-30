//! Writes a YouTube session in the Netscape cookies.txt format yt-dlp reads.

use anyhow::Result;
use serde::Deserialize;
use std::fmt::Write as _;
use std::path::Path;

#[derive(Debug, Clone, Deserialize, PartialEq)]
pub struct CookieRecord {
    pub domain: String,
    pub path: String,
    pub secure: bool,
    pub http_only: bool,
    /// Unix seconds; 0 for a session cookie.
    pub expires: i64,
    pub name: String,
    pub value: String,
}

/// Only Google and YouTube cookies leave the web view.
fn relevant(domain: &str) -> bool {
    let d = domain.trim_start_matches('.');
    d == "youtube.com" || d.ends_with(".youtube.com") || d == "google.com" || d.ends_with(".google.com")
}

pub fn has_session(cookies: &[CookieRecord]) -> bool {
    let youtube = |c: &&CookieRecord| c.domain.trim_start_matches('.').ends_with("youtube.com");
    cookies.iter().filter(youtube).any(|c| c.name == "SAPISID" || c.name == "__Secure-3PAPISID")
        && cookies.iter().filter(youtube).any(|c| c.name == "LOGIN_INFO" || c.name.ends_with("PSID"))
}

pub fn netscape(cookies: &[CookieRecord], now_secs: i64) -> String {
    let mut out = String::from(
        "# Netscape HTTP Cookie File\n# Written by Requiem. Do not share: this file is your YouTube login.\n\n",
    );
    for c in cookies.iter().filter(|c| relevant(&c.domain)) {
        let host = c.domain.trim_start_matches('.');
        let domain = if c.http_only { format!("#HttpOnly_.{host}") } else { format!(".{host}") };
        // yt-dlp may drop session cookies; a year keeps them for the sync.
        let expires = if c.expires > 0 { c.expires } else { now_secs + 365 * 24 * 3600 };
        let path = if c.path.is_empty() { "/" } else { &c.path };
        let secure = if c.secure { "TRUE" } else { "FALSE" };
        let _ = writeln!(out, "{domain}\tTRUE\t{path}\t{secure}\t{expires}\t{}\t{}", c.name, c.value);
    }
    out
}

pub fn write(path: &Path, cookies: &[CookieRecord], now_secs: i64) -> Result<()> {
    if let Some(dir) = path.parent() {
        std::fs::create_dir_all(dir)?;
    }
    std::fs::write(path, netscape(cookies, now_secs))?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600))?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn c(domain: &str, name: &str, http_only: bool, expires: i64) -> CookieRecord {
        CookieRecord {
            domain: domain.into(),
            path: "/".into(),
            secure: true,
            http_only,
            expires,
            name: name.into(),
            value: "v".into(),
        }
    }

    #[test]
    fn writes_only_google_and_youtube() {
        let text = netscape(
            &[
                c(".youtube.com", "SAPISID", false, 100),
                c("example.com", "x", false, 0),
                c("accounts.google.com", "SID", true, 0),
            ],
            1000,
        );
        assert!(text.contains(".youtube.com\tTRUE\t/\tTRUE\t100\tSAPISID\tv"));
        assert!(text.contains("#HttpOnly_.accounts.google.com\tTRUE\t/\tTRUE\t31537000\tSID\tv"));
        assert!(!text.contains("example.com"));
    }

    #[test]
    fn session_detection() {
        assert!(!has_session(&[c(".youtube.com", "VISITOR_INFO1_LIVE", false, 0)]));
        assert!(has_session(&[c(".youtube.com", "SAPISID", false, 0), c(".youtube.com", "LOGIN_INFO", true, 0)]));
    }
}
