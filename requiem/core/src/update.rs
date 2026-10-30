//! Tells the person when a newer Requiem is out. CI publishes every approved version to the
//! same release, `requiem-latest`, and writes its version in the notes; the app compares that
//! with its own.

use anyhow::Result;
use regex::Regex;
use serde::Serialize;
use serde_json::Value;
use std::sync::LazyLock;

pub const RELEASE_API: &str = "https://api.github.com/repos/JVCSampaio/JVCSampaio/releases/tags/requiem-latest";
pub const RELEASE_PAGE: &str = "https://github.com/JVCSampaio/JVCSampaio/releases/tag/requiem-latest";

static NOTES_VERSION: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"requiem-version:\s*(\d+\.\d+\.\d+)").unwrap());
static TITLE_VERSION: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\((\d+\.\d+\.\d+)\)").unwrap());

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Update {
    pub version: String,
    pub url: String,
}

/// The version a release announces: the marker in its notes, or the one in its title.
pub fn parse_release(release: &Value) -> Option<String> {
    let from = |field: &str, pattern: &Regex| {
        let text = release.get(field)?.as_str()?;
        Some(pattern.captures(text)?[1].to_string())
    };
    from("body", &NOTES_VERSION).or_else(|| from("name", &TITLE_VERSION))
}

fn numbers(version: &str) -> Option<(u64, u64, u64)> {
    let mut parts = version.trim().split('.').map(|p| p.parse::<u64>().ok());
    Some((parts.next()??, parts.next()??, parts.next()??))
}

pub fn is_newer(candidate: &str, current: &str) -> bool {
    matches!((numbers(candidate), numbers(current)), (Some(a), Some(b)) if a > b)
}

/// Builds made outside the release workflow keep the placeholder version and never ask.
pub fn is_release_build(current: &str) -> bool {
    !cfg!(debug_assertions) && current != "0.1.0"
}

/// The newest published version, when it is newer than `current`. No release yet (the
/// workflow deletes and recreates it) means nothing new.
pub async fn check(http: &reqwest::Client, current: &str) -> Result<Option<Update>> {
    let response = http.get(RELEASE_API).header("Accept", "application/vnd.github+json").send().await?;
    if response.status() == reqwest::StatusCode::NOT_FOUND {
        return Ok(None);
    }
    let release: Value = response.error_for_status()?.json().await?;
    Ok(newer(&release, current))
}

pub fn newer(release: &Value, current: &str) -> Option<Update> {
    parse_release(release)
        .filter(|version| is_newer(version, current))
        .map(|version| Update { version, url: RELEASE_PAGE.into() })
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn reads_the_version_from_the_notes_then_the_title() {
        let release = json!({
            "name": "Requiem para computador (0.1.12)",
            "body": "Requiem 0.1.12 para computador.\n<!-- requiem-version: 0.1.12 -->",
            "prerelease": true
        });
        assert_eq!(parse_release(&release).as_deref(), Some("0.1.12"));
        let old = json!({ "name": "Requiem para computador (0.1.5)", "body": "notas antigas" });
        assert_eq!(parse_release(&old).as_deref(), Some("0.1.5"));
        assert_eq!(parse_release(&json!({ "name": "sem versão" })), None);
    }

    #[test]
    fn compares_numbers_not_text() {
        assert!(is_newer("0.1.10", "0.1.9"));
        assert!(is_newer("0.2.0", "0.1.99"));
        assert!(!is_newer("0.1.9", "0.1.10"));
        assert!(!is_newer("0.1.9", "0.1.9"));
        assert!(!is_newer("x", "0.1.9"));
        let release = json!({ "body": "<!-- requiem-version: 0.1.20 -->" });
        assert_eq!(newer(&release, "0.1.19"), Some(Update { version: "0.1.20".into(), url: RELEASE_PAGE.into() }));
        assert_eq!(newer(&release, "0.1.20"), None);
    }
}
