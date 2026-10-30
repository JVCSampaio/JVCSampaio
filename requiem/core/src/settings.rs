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

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum NormalizeBy {
    /// Every song at the same loudness.
    #[default]
    Track,
    /// An album keeps the differences between its songs (a quiet intro stays quiet).
    Album,
}

/// How the player sounds. Kept here, with everything else, so it survives anything that clears
/// the web view's storage.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct AudioSettings {
    /// Even volume between songs, from each file's measured loudness.
    pub normalize: bool,
    pub normalize_by: NormalizeBy,
    pub eq_on: bool,
    /// A preset's id, or "custom" once the bands are moved by hand.
    pub eq_preset: String,
    /// Gains of the ten bands, 31 Hz to 16 kHz, in dB.
    pub eq_gains: Vec<f32>,
    pub eq_preamp: f32,
    /// Seconds over which one song fades into the next; 0 plays them one after the other.
    pub crossfade_seconds: u32,
    /// Songs that follow each other on an album play with no gap.
    pub gapless: bool,
}

pub const EQ_BANDS: usize = 10;

impl Default for AudioSettings {
    fn default() -> Self {
        AudioSettings {
            normalize: true,
            normalize_by: NormalizeBy::Track,
            eq_on: false,
            eq_preset: "flat".into(),
            eq_gains: vec![0.0; EQ_BANDS],
            eq_preamp: 0.0,
            crossfade_seconds: 0,
            gapless: true,
        }
    }
}

impl AudioSettings {
    fn sanitized(mut self) -> AudioSettings {
        self.eq_gains.resize(EQ_BANDS, 0.0);
        for gain in &mut self.eq_gains {
            *gain = if gain.is_finite() { gain.clamp(-12.0, 12.0) } else { 0.0 };
        }
        self.eq_preamp = if self.eq_preamp.is_finite() { self.eq_preamp.clamp(-12.0, 6.0) } else { 0.0 };
        self.crossfade_seconds = self.crossfade_seconds.min(12);
        if self.eq_preset.is_empty() || self.eq_preset.len() > 32 {
            self.eq_preset = "custom".into();
        }
        self
    }
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
    pub audio: AudioSettings,
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
            audio: AudioSettings::default(),
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
        self.audio = self.audio.sanitized();
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
    fn audio_settings_get_defaults_and_stay_in_range() {
        let s: Settings =
            serde_json::from_str(r#"{"audio": {"eqGains": [3, 40, -99], "crossfadeSeconds": 60}}"#).unwrap();
        let audio = s.sanitized().audio;
        assert!(audio.normalize && audio.gapless);
        assert_eq!(audio.eq_gains, vec![3.0, 12.0, -12.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]);
        assert_eq!(audio.crossfade_seconds, 12);
        let json = serde_json::to_string(&Settings::default()).unwrap();
        assert!(json.contains(r#""normalizeBy":"track""#), "{json}");
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
