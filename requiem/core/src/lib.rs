//! Requiem's engine: finds what the person listens to on YouTube, keeps it on disk with
//! yt-dlp and serves it to the player. No GUI code lives here, so it is tested on its own.

pub mod clean;
pub mod cookies;
pub mod db;
pub mod engine;
pub mod links;
pub mod local;
pub mod lyrics;
pub mod media;
pub mod process;
pub mod radio;
pub mod server;
pub mod settings;
pub mod tools;
pub mod update;
pub mod ytdlp;
