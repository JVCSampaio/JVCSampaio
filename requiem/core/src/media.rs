//! What yt-dlp tells about a video, and the decision between keeping it as a song (audio) or
//! as a video.

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::clean;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Kind {
    Music,
    Video,
    /// Found by the sync; decided after reading the full metadata.
    Auto,
}

impl Kind {
    pub fn as_str(self) -> &'static str {
        match self {
            Kind::Music => "music",
            Kind::Video => "video",
            Kind::Auto => "auto",
        }
    }

    pub fn parse(s: &str) -> Kind {
        match s {
            "music" => Kind::Music,
            "video" => Kind::Video,
            _ => Kind::Auto,
        }
    }
}

/// The fields of a full `yt-dlp -J` dump that the app uses. Everything is optional because
/// extractors differ and YouTube changes.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct Info {
    pub id: String,
    #[serde(default)]
    pub title: String,
    pub track: Option<String>,
    pub artist: Option<String>,
    #[serde(default)]
    pub artists: Vec<String>,
    pub album: Option<String>,
    pub creator: Option<String>,
    pub channel: Option<String>,
    pub uploader: Option<String>,
    pub duration: Option<f64>,
    #[serde(default)]
    pub categories: Vec<String>,
    pub webpage_url: Option<String>,
    pub original_url: Option<String>,
    pub thumbnail: Option<String>,
    pub is_live: Option<bool>,
    pub was_live: Option<bool>,
    pub live_status: Option<String>,
    pub media_type: Option<String>,
    pub width: Option<i64>,
    pub height: Option<i64>,
    pub availability: Option<String>,
}

impl Info {
    pub fn channel_name(&self) -> String {
        self.channel.clone().or_else(|| self.uploader.clone()).unwrap_or_default()
    }

    fn url(&self) -> String {
        self.webpage_url.clone().or_else(|| self.original_url.clone()).unwrap_or_default()
    }

    pub fn is_short(&self) -> bool {
        self.media_type.as_deref() == Some("short")
            || self.url().contains("/shorts/")
            || self.duration.is_some_and(|d| d > 0.0 && d <= 60.0)
    }

    pub fn is_live_now(&self) -> bool {
        self.is_live == Some(true)
            || matches!(self.live_status.as_deref(), Some("is_live") | Some("is_upcoming") | Some("post_live"))
    }

    /// Signals that this is a song: YouTube's Music category, YouTube Music track metadata, an
    /// auto-generated "Topic" channel or a VEVO channel.
    pub fn looks_like_music(&self) -> bool {
        let channel = self.channel_name();
        self.categories.iter().any(|c| c == "Music")
            || self.track.is_some()
            || channel.ends_with(" - Topic")
            || channel.to_lowercase().contains("vevo")
            || self.url().contains("music.youtube.com")
    }

    /// Display metadata: YouTube Music's own fields when present, otherwise a cleaned title.
    pub fn song(&self) -> (String, String, Option<String>) {
        let channel = self.channel_name();
        if let Some(track) = self.track.as_deref().filter(|t| !t.trim().is_empty()) {
            let artist = self
                .artists
                .first()
                .cloned()
                .or_else(|| self.artist.clone())
                .or_else(|| self.creator.clone())
                .map(|a| first_artist(&a))
                .unwrap_or_else(|| clean::clean_channel(&channel));
            return (track.trim().to_string(), artist, self.album.clone());
        }
        let info = clean::clean(&self.title, &channel);
        (info.title, info.artist, self.album.clone())
    }
}

/// "Artist A, Artist B" from yt-dlp keeps the first name for grouping; the full list stays in
/// the title of the song page.
fn first_artist(artist: &str) -> String {
    artist.split(", ").next().unwrap_or(artist).trim().to_string()
}

/// One row of a flat listing (search, history, likes, playlist).
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Entry {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub channel: String,
    pub duration: Option<f64>,
    pub thumbnail: String,
    pub is_short: bool,
}

impl Entry {
    /// Builds a row from one entry of `--flat-playlist -J`. Rows without an 11-character id
    /// (channels, playlists, ads) are dropped.
    pub fn from_flat(v: &Value) -> Option<Entry> {
        let id = v.get("id")?.as_str()?.to_string();
        if id.len() != 11 {
            return None;
        }
        let title = v.get("title").and_then(Value::as_str).unwrap_or("").to_string();
        if title.is_empty() || title == "[Private video]" || title == "[Deleted video]" {
            return None;
        }
        let channel = ["channel", "uploader", "artist", "creator"]
            .iter()
            .find_map(|k| v.get(*k).and_then(Value::as_str).filter(|s| !s.is_empty()))
            .unwrap_or("")
            .to_string();
        let duration = v.get("duration").and_then(Value::as_f64);
        let url = v.get("url").and_then(Value::as_str).unwrap_or("");
        let is_short = url.contains("/shorts/") || duration.is_some_and(|d| d > 0.0 && d <= 60.0);
        let song = clean::clean(&title, &channel);
        Some(Entry {
            thumbnail: best_thumbnail(v).unwrap_or_else(|| format!("https://i.ytimg.com/vi/{id}/hqdefault.jpg")),
            id,
            title: song.title,
            artist: song.artist,
            channel,
            duration,
            is_short,
        })
    }
}

/// The largest thumbnail listed, preferring square art (YouTube Music covers) when present.
fn best_thumbnail(v: &Value) -> Option<String> {
    let list = v.get("thumbnails")?.as_array()?;
    list.iter()
        .filter_map(|t| {
            let url = t.get("url")?.as_str()?;
            let w = t.get("width").and_then(Value::as_i64).unwrap_or(0);
            let h = t.get("height").and_then(Value::as_i64).unwrap_or(0);
            Some((url, w * h))
        })
        .max_by_key(|(_, area)| *area)
        .map(|(url, _)| url.to_string())
}

pub fn entries(listing: &Value) -> Vec<Entry> {
    listing
        .get("entries")
        .and_then(Value::as_array)
        .map(|list| list.iter().filter_map(Entry::from_flat).collect())
        .unwrap_or_default()
}

/// What to keep for a video the person watched, given their choices.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Decision {
    Keep(Kind),
    Skip(&'static str),
}

pub struct Rules {
    pub music: bool,
    pub videos: bool,
    pub shorts: bool,
    pub max_music_minutes: u32,
    pub max_video_minutes: u32,
}

/// What a video is, before any rule about what to keep: a song if it looks like one and is
/// short enough, a video otherwise.
pub fn natural_kind(info: &Info, max_music_minutes: u32) -> Kind {
    let minutes = info.duration.unwrap_or(0.0) / 60.0;
    if info.looks_like_music() && minutes <= max_music_minutes as f64 {
        Kind::Music
    } else {
        Kind::Video
    }
}

pub fn decide(info: &Info, rules: &Rules) -> Decision {
    if info.is_live_now() {
        return Decision::Skip("Transmissão ao vivo");
    }
    if info.is_short() && !rules.shorts {
        return Decision::Skip("Short");
    }
    let minutes = info.duration.unwrap_or(0.0) / 60.0;
    match natural_kind(info, rules.max_music_minutes) {
        Kind::Music if !rules.music => Decision::Skip("Músicas desligadas em Ajustes"),
        Kind::Video if !rules.videos => Decision::Skip("Vídeos desligados em Ajustes"),
        Kind::Video if rules.max_video_minutes > 0 && minutes > rules.max_video_minutes as f64 => {
            Decision::Skip("Mais longo que o limite de vídeo")
        }
        other => Decision::Keep(other),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn rules() -> Rules {
        Rules { music: true, videos: true, shorts: false, max_music_minutes: 20, max_video_minutes: 180 }
    }

    fn info(v: Value) -> Info {
        serde_json::from_value(v).unwrap()
    }

    #[test]
    fn music_category_is_a_song() {
        let i = info(json!({"id": "a", "title": "x", "categories": ["Music"], "duration": 200.0}));
        assert_eq!(decide(&i, &rules()), Decision::Keep(Kind::Music));
    }

    #[test]
    fn long_music_is_kept_as_video() {
        let i = info(json!({"id": "a", "title": "Live concert", "categories": ["Music"], "duration": 5400.0}));
        assert_eq!(decide(&i, &rules()), Decision::Keep(Kind::Video));
    }

    #[test]
    fn a_skipped_video_keeps_what_it_is() {
        // "Baixar assim mesmo" downloads a skipped item as this kind, past the rules.
        let short = info(json!({"id": "a", "title": "Trailer", "duration": 40.0, "media_type": "short"}));
        assert_eq!(decide(&short, &rules()), Decision::Skip("Short"));
        assert_eq!(natural_kind(&short, 20), Kind::Video);

        let song = info(json!({"id": "b", "title": "Yellow", "categories": ["Music"], "duration": 266.0}));
        let no_music = Rules { music: false, ..rules() };
        assert_eq!(decide(&song, &no_music), Decision::Skip("Músicas desligadas em Ajustes"));
        assert_eq!(natural_kind(&song, 20), Kind::Music);
    }

    #[test]
    fn topic_channel_is_a_song() {
        let i = info(json!({"id": "a", "title": "Yellow", "channel": "Coldplay - Topic", "duration": 266.0}));
        assert_eq!(decide(&i, &rules()), Decision::Keep(Kind::Music));
    }

    #[test]
    fn gaming_video_is_a_video() {
        let i = info(json!({"id": "a", "title": "Speedrun", "categories": ["Gaming"], "duration": 900.0}));
        assert_eq!(decide(&i, &rules()), Decision::Keep(Kind::Video));
    }

    #[test]
    fn shorts_are_skipped_by_default() {
        let i = info(json!({"id": "a", "title": "x", "categories": ["Music"], "duration": 30.0}));
        assert_eq!(decide(&i, &rules()), Decision::Skip("Short"));
    }

    #[test]
    fn live_is_skipped() {
        let i = info(json!({"id": "a", "title": "x", "is_live": true}));
        assert!(matches!(decide(&i, &rules()), Decision::Skip(_)));
    }

    #[test]
    fn videos_off_skips_videos_only() {
        let r = Rules { videos: false, ..rules() };
        let v = info(json!({"id": "a", "title": "x", "categories": ["Education"], "duration": 600.0}));
        let m = info(json!({"id": "b", "title": "y", "categories": ["Music"], "duration": 200.0}));
        assert!(matches!(decide(&v, &r), Decision::Skip(_)));
        assert_eq!(decide(&m, &r), Decision::Keep(Kind::Music));
    }

    #[test]
    fn youtube_music_metadata_wins() {
        let i = info(json!({
            "id": "a", "title": "Coldplay - Yellow (Official Video)", "track": "Yellow",
            "artist": "Coldplay, Chris Martin", "album": "Parachutes", "channel": "Coldplay"
        }));
        assert_eq!(i.song(), ("Yellow".into(), "Coldplay".into(), Some("Parachutes".into())));
    }

    #[test]
    fn flat_entry_parses_and_cleans() {
        let e = Entry::from_flat(&json!({
            "id": "dQw4w9WgXcQ", "title": "Rick Astley - Never Gonna Give You Up (Official Video)",
            "channel": "Rick Astley", "duration": 213.0, "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "thumbnails": [{"url": "s.jpg", "width": 120, "height": 90}, {"url": "l.jpg", "width": 480, "height": 360}]
        }))
        .unwrap();
        assert_eq!(e.title, "Never Gonna Give You Up");
        assert_eq!(e.artist, "Rick Astley");
        assert_eq!(e.thumbnail, "l.jpg");
        assert!(!e.is_short);
    }

    #[test]
    fn flat_entry_drops_private_and_channels() {
        assert!(Entry::from_flat(&json!({"id": "UCabcdefghijklmnop", "title": "A channel"})).is_none());
        assert!(Entry::from_flat(&json!({"id": "dQw4w9WgXcQ", "title": "[Private video]"})).is_none());
    }
}
