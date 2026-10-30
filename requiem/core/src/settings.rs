//! The person's choices. Stored as one JSON value in the database so new fields get their
//! defaults on old installs.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(tag = "mode", rename_all = "camelCase")]
pub enum Account {
    /// Search and links work; nothing is synced.
    #[default]
    None,
    /// Signed in inside Requiem; cookies were saved to the app's own cookie file.
    App,
    /// yt-dlp reads the session of a browser on this computer.
    Browser { browser: String },
    /// A cookies.txt exported by the person, copied into the app's folder.
    File,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AudioFormat {
    M4a,
    Mp3,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Settings {
    pub library_dir: String,
    pub account: Account,
    pub auto_sync: bool,
    pub sync_interval_minutes: u32,
    pub sync_history: bool,
    pub sync_likes: bool,
    pub sync_music_likes: bool,
    /// How many items to take from each feed the first time it is read.
    pub first_sync_limit: u32,
    pub download_music: bool,
    pub download_videos: bool,
    pub download_shorts: bool,
    pub max_music_minutes: u32,
    pub max_video_minutes: u32,
    pub video_height: u32,
    pub audio_format: AudioFormat,
    pub close_to_tray: bool,
    pub launch_at_login: bool,
    /// Folders whose songs and videos join the library. None until the person changes the
    /// list: the computer's Music folder.
    pub music_folders: Option<Vec<String>>,
    /// When the queue runs out, similar songs keep playing.
    pub autoplay: bool,
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            library_dir: String::new(),
            account: Account::None,
            auto_sync: true,
            sync_interval_minutes: 30,
            sync_history: true,
            sync_likes: true,
            sync_music_likes: true,
            first_sync_limit: 30,
            download_music: true,
            download_videos: true,
            download_shorts: false,
            max_music_minutes: 20,
            max_video_minutes: 180,
            video_height: 720,
            audio_format: AudioFormat::M4a,
            close_to_tray: true,
            launch_at_login: false,
            music_folders: None,
            autoplay: true,
        }
    }
}

impl Settings {
    /// Keeps values inside the ranges the interface offers, whatever arrives.
    pub fn sanitized(mut self) -> Settings {
        self.sync_interval_minutes = self.sync_interval_minutes.clamp(10, 24 * 60);
        self.first_sync_limit = self.first_sync_limit.clamp(5, 500);
        self.max_music_minutes = self.max_music_minutes.clamp(5, 120);
        self.max_video_minutes = self.max_video_minutes.min(24 * 60);
        if ![360, 480, 720, 1080, 1440, 2160].contains(&self.video_height) {
            self.video_height = 720;
        }
        if let Account::Browser { browser } = &self.account {
            if !BROWSERS.contains(&browser.as_str()) {
                self.account = Account::None;
            }
        }
        self
    }
}

/// Browsers yt-dlp can read cookies from.
pub const BROWSERS: &[&str] =
    &["firefox", "chrome", "edge", "brave", "opera", "vivaldi", "chromium", "safari", "whale"];

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn old_json_gets_defaults() {
        let s: Settings = serde_json::from_str(r#"{"autoSync": false}"#).unwrap();
        assert!(!s.auto_sync);
        assert_eq!(s.sync_interval_minutes, 30);
        assert_eq!(s.account, Account::None);
    }

    #[test]
    fn account_round_trip() {
        let s = Settings { account: Account::Browser { browser: "firefox".into() }, ..Settings::default() };
        let json = serde_json::to_string(&s).unwrap();
        assert!(json.contains(r#""account":{"mode":"browser","browser":"firefox"}"#), "{json}");
        let back: Settings = serde_json::from_str(&json).unwrap();
        assert_eq!(back.account, s.account);
    }

    #[test]
    fn sanitize_rejects_unknown_browser_and_odd_values() {
        let s = Settings {
            account: Account::Browser { browser: "netscape".into() },
            video_height: 999,
            sync_interval_minutes: 1,
            ..Settings::default()
        }
        .sanitized();
        assert_eq!(s.account, Account::None);
        assert_eq!(s.video_height, 720);
        assert_eq!(s.sync_interval_minutes, 10);
    }
}
