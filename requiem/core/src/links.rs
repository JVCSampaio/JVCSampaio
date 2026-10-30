//! Recognizes what someone pasted: a YouTube video, a playlist, or plain search text.

use regex::Regex;
use std::sync::LazyLock;

static VIDEO_PATTERNS: LazyLock<[Regex; 2]> = LazyLock::new(|| {
    [
        Regex::new(
            r"(?:youtube\.com|youtube-nocookie\.com)/(?:watch|embed|shorts|live|v)(?:\?(?:\S*?&)?v=|/)([\w-]{11})",
        )
        .unwrap(),
        Regex::new(r"youtu\.be/([\w-]{11})").unwrap(),
    ]
});
static PLAYLIST: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(?:youtube\.com|youtu\.be)/\S*?[?&]list=([\w-]{10,})").unwrap());
static BARE_ID: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"^[\w-]{11}$").unwrap());

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Pasted {
    Video(String),
    Playlist(String),
    Query(String),
}

/// The video id in anything shared from YouTube or YouTube Music.
pub fn video_id(text: &str) -> Option<String> {
    VIDEO_PATTERNS.iter().find_map(|re| re.captures(text).map(|c| c[1].to_string())).or_else(|| {
        let trimmed = text.trim();
        // An 11-character word is only an id when it mixes cases or has digits or symbols;
        // "inquisitive" is a search, not a video.
        let looks_like_id = BARE_ID.is_match(trimmed)
            && (trimmed.chars().any(|c| c.is_ascii_digit() || c == '-' || c == '_')
                || (trimmed.chars().any(|c| c.is_ascii_uppercase())
                    && trimmed.chars().any(|c| c.is_ascii_lowercase())));
        looks_like_id.then(|| trimmed.to_string())
    })
}

pub fn playlist_id(text: &str) -> Option<String> {
    PLAYLIST.captures(text).map(|c| c[1].to_string())
}

/// A pasted link with only a playlist (no video) becomes a playlist; a watch link inside a
/// playlist is the video the person was looking at.
pub fn classify(text: &str) -> Pasted {
    let has_url = text.contains("youtube.com") || text.contains("youtu.be");
    if has_url {
        if let Some(id) = VIDEO_PATTERNS.iter().find_map(|re| re.captures(text).map(|c| c[1].to_string())) {
            return Pasted::Video(id);
        }
        if let Some(list) = playlist_id(text) {
            return Pasted::Playlist(list);
        }
    }
    match video_id(text) {
        Some(id) if !has_url => Pasted::Video(id),
        _ => Pasted::Query(text.trim().to_string()),
    }
}

pub fn watch_url(id: &str) -> String {
    format!("https://www.youtube.com/watch?v={id}")
}

pub fn playlist_url(id: &str) -> String {
    format!("https://www.youtube.com/playlist?list={id}")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn watch_url_id() {
        assert_eq!(video_id("https://www.youtube.com/watch?v=dQw4w9WgXcQ").as_deref(), Some("dQw4w9WgXcQ"));
    }

    #[test]
    fn watch_url_with_other_parameters() {
        assert_eq!(
            video_id("https://www.youtube.com/watch?list=PL1&v=dQw4w9WgXcQ&t=42s").as_deref(),
            Some("dQw4w9WgXcQ")
        );
    }

    #[test]
    fn youtube_music_share() {
        assert_eq!(video_id("https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=abc").as_deref(), Some("dQw4w9WgXcQ"));
    }

    #[test]
    fn short_link_inside_text() {
        assert_eq!(video_id("Olha essa: https://youtu.be/dQw4w9WgXcQ?si=xyz").as_deref(), Some("dQw4w9WgXcQ"));
    }

    #[test]
    fn shorts() {
        assert_eq!(video_id("https://youtube.com/shorts/aBcDeFgHiJk").as_deref(), Some("aBcDeFgHiJk"));
    }

    #[test]
    fn bare_id() {
        assert_eq!(video_id("  dQw4w9WgXcQ ").as_deref(), Some("dQw4w9WgXcQ"));
    }

    #[test]
    fn plain_word_is_not_an_id() {
        assert_eq!(video_id("inquisitive"), None);
        assert_eq!(classify("inquisitive"), Pasted::Query("inquisitive".into()));
    }

    #[test]
    fn not_a_youtube_link() {
        assert_eq!(video_id("https://example.com/watch?v=123"), None);
    }

    #[test]
    fn playlist_link() {
        assert_eq!(
            classify("https://www.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"),
            Pasted::Playlist("PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG".into())
        );
    }

    #[test]
    fn video_inside_playlist_is_the_video() {
        assert_eq!(
            classify("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"),
            Pasted::Video("dQw4w9WgXcQ".into())
        );
    }

    #[test]
    fn search_text() {
        assert_eq!(classify("  danse macabre  "), Pasted::Query("danse macabre".into()));
    }
}
