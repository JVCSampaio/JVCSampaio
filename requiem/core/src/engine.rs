//! The running engine: queue workers, the periodic sync, the media server and every action
//! the interface can ask for. Events go out on a broadcast channel the app forwards to the UI.

use anyhow::{anyhow, Context, Result};
use serde::Serialize;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, RwLock};
use std::time::Duration;
use tokio::sync::{broadcast, Notify};

use crate::cookies::{self, CookieRecord};
use crate::db::{now_ms, Db, Finished, Item, NewItem, Play, Playlist, Stats};
use crate::links::{self, Pasted};
use crate::local;
use crate::lyrics::{self, Cached, Lyrics};
use crate::media::{self, Decision, Entry, Kind, Rules};
use crate::server::{self, Asset};
use crate::settings::{Account, Settings};
use crate::tools::{InstallStep, Tools, ToolsStatus};
use crate::update::{self, Update};
use crate::ytdlp::{Auth, DownloadPlan, Progress, YtDlp};

const WORKERS: usize = 2;

#[derive(Debug, Clone)]
pub struct Paths {
    /// The app's private folder: tools, database, cookies, covers.
    pub data: PathBuf,
    /// Where songs and videos go when the person never chose a folder.
    pub default_library: PathBuf,
    /// The computer's Music folder, read into the library unless the person picks others.
    pub music: Option<PathBuf>,
}

impl Paths {
    pub fn bin(&self) -> PathBuf {
        self.data.join("bin")
    }
    pub fn database(&self) -> PathBuf {
        self.data.join("requiem.db")
    }
    pub fn cookies(&self) -> PathBuf {
        self.data.join("youtube-cookies.txt")
    }
    pub fn covers(&self) -> PathBuf {
        self.data.join("covers")
    }
    pub fn tmp(&self) -> PathBuf {
        self.data.join("tmp")
    }
}

#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncStatus {
    pub running: bool,
    pub last_run_at: Option<i64>,
    pub last_found: u32,
    pub last_error: Option<String>,
    pub next_run_at: Option<i64>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type", rename_all = "camelCase")]
pub enum Event {
    Item {
        item: Box<Item>,
    },
    #[serde(rename_all = "camelCase")]
    Progress {
        id: String,
        fraction: Option<f64>,
        speed: Option<f64>,
        eta: Option<f64>,
        stage: String,
    },
    Removed {
        id: String,
    },
    Library,
    Playlists,
    Sync {
        status: SyncStatus,
    },
    Tools(ToolsReport),
    Notice {
        message: String,
    },
    Settings {
        settings: Box<Settings>,
    },
    Local(LocalStatus),
    Update(Update),
}

/// Reading the folders of songs already on the computer.
#[derive(Debug, Clone, Copy, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalStatus {
    pub running: bool,
    /// Songs and videos from the folders in the library (so far, while running).
    pub found: usize,
}

/// Where the tools stand: what is installed, an install under way, the last failure. Kept by
/// the engine so a window that opens late still sees a failure that happened before it.
#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ToolsReport {
    pub status: ToolsStatus,
    pub installing: bool,
    pub step: Option<InstallStep>,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type", rename_all = "camelCase")]
pub enum Resolved {
    Video { entry: Entry },
    Playlist { title: String, entries: Vec<Entry> },
    Query { text: String },
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AccountTest {
    pub ok: bool,
    pub message: String,
    pub sample: Vec<Entry>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Overview {
    pub version: String,
    pub media_base: String,
    pub data_dir: String,
    pub library_dir: String,
    pub platform: String,
    /// The folders read into the library, the Music folder when the person chose none.
    pub local_folders: Vec<String>,
    pub local: LocalStatus,
    /// A newer published version, when there is one.
    pub update: Option<Update>,
}

pub struct Engine {
    pub paths: Paths,
    pub db: Db,
    http: reqwest::Client,
    tools: Tools,
    settings: RwLock<Settings>,
    events: broadcast::Sender<Event>,
    wake_downloads: Notify,
    wake_sync: Notify,
    cancels: Mutex<HashMap<String, Arc<Notify>>>,
    sync: Mutex<SyncStatus>,
    installing: AtomicBool,
    tools_report: Mutex<ToolsReport>,
    wake_scan: Notify,
    local: Mutex<LocalStatus>,
    update: Mutex<Option<Update>>,
    media_base: RwLock<String>,
}

impl Engine {
    /// Opens the library, starts the media server, the download workers, the sync loop and,
    /// on first run, the download of the tools.
    pub async fn start(paths: Paths) -> Result<Arc<Engine>> {
        for dir in [paths.data.clone(), paths.bin(), paths.covers(), paths.tmp()] {
            std::fs::create_dir_all(&dir).with_context(|| format!("criando {}", dir.display()))?;
        }
        let db = Db::open(&paths.database())?;
        let mut settings: Settings =
            db.kv_get("settings")?.and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default();
        if settings.library_dir.is_empty() {
            settings.library_dir = paths.default_library.to_string_lossy().into_owned();
        }
        let settings = settings.sanitized();
        let http = reqwest::Client::builder()
            .user_agent(concat!("Requiem/", env!("CARGO_PKG_VERSION"), " (+https://github.com/JVCSampaio/JVCSampaio)"))
            .connect_timeout(Duration::from_secs(20))
            .build()?;
        let (events, _) = broadcast::channel(512);
        let requeued = db.requeue_interrupted()?;
        if requeued > 0 {
            log::info!("{requeued} interrupted downloads back in the queue");
        }
        let engine = Arc::new(Engine {
            tools: Tools::new(paths.bin(), http.clone()),
            paths,
            db,
            http,
            settings: RwLock::new(settings),
            events,
            wake_downloads: Notify::new(),
            wake_sync: Notify::new(),
            cancels: Mutex::new(HashMap::new()),
            sync: Mutex::new(SyncStatus::default()),
            installing: AtomicBool::new(false),
            tools_report: Mutex::new(ToolsReport::default()),
            wake_scan: Notify::new(),
            local: Mutex::new(LocalStatus::default()),
            update: Mutex::new(None),
            media_base: RwLock::new(String::new()),
        });

        let lookup_engine = Arc::downgrade(&engine);
        let base = server::start(Arc::new(move |id, asset| {
            let engine = lookup_engine.upgrade()?;
            let item = engine.db.get(id).ok()??;
            let path = match asset {
                Asset::Media => item.file_path?,
                Asset::Cover => item.thumb_path?,
            };
            let path = PathBuf::from(path);
            path.is_file().then_some(path)
        }))
        .await?;
        *engine.media_base.write().unwrap() = base;

        for _ in 0..WORKERS {
            let worker = engine.clone();
            tokio::spawn(async move { worker.worker_loop().await });
        }
        let syncer = engine.clone();
        tokio::spawn(async move { syncer.sync_loop().await });
        let setup = engine.clone();
        tokio::spawn(async move { setup.prepare_tools().await });
        let reader = engine.clone();
        tokio::spawn(async move { reader.scan_loop().await });
        let updater = engine.clone();
        tokio::spawn(async move { updater.update_loop().await });
        Ok(engine)
    }

    pub fn subscribe(&self) -> broadcast::Receiver<Event> {
        self.events.subscribe()
    }

    fn emit(&self, event: Event) {
        let _ = self.events.send(event);
    }

    fn emit_item(&self, id: &str) {
        if let Ok(Some(item)) = self.db.get(id) {
            self.emit(Event::Item { item: Box::new(item) });
        }
    }

    pub fn settings(&self) -> Settings {
        self.settings.read().unwrap().clone()
    }

    pub fn overview(&self) -> Overview {
        Overview {
            version: env!("CARGO_PKG_VERSION").into(),
            media_base: self.media_base.read().unwrap().clone(),
            data_dir: self.paths.data.to_string_lossy().into_owned(),
            library_dir: self.settings().library_dir,
            platform: std::env::consts::OS.into(),
            local_folders: self.local_folders().iter().map(|f| f.to_string_lossy().into_owned()).collect(),
            local: *self.local.lock().unwrap(),
            update: self.update.lock().unwrap().clone(),
        }
    }

    pub fn set_settings(&self, next: Settings) -> Result<Settings> {
        let mut next = next.sanitized();
        if next.library_dir.trim().is_empty() {
            next.library_dir = self.paths.default_library.to_string_lossy().into_owned();
        }
        self.db.kv_set("settings", &serde_json::to_string(&next)?)?;
        let (account_changed, folders_changed) = {
            let mut current = self.settings.write().unwrap();
            let changed = (
                current.account != next.account,
                current.music_folders != next.music_folders || current.library_dir != next.library_dir,
            );
            *current = next.clone();
            changed
        };
        if folders_changed {
            self.wake_scan.notify_one();
        }
        self.wake_sync.notify_waiters();
        // The login window and the tray change settings too; every view hears about it.
        self.emit(Event::Settings { settings: Box::new(next.clone()) });
        if account_changed {
            self.emit(Event::Sync { status: self.sync_status() });
        }
        Ok(next)
    }

    fn ytdlp(&self) -> YtDlp {
        let account = self.settings().account;
        YtDlp { tools: self.tools.paths(), auth: Auth::from_account(&account, &self.paths.cookies()) }
    }

    /// Searches never use the account: results are the same for everyone and nothing is
    /// tied to the person's session.
    fn ytdlp_anonymous(&self) -> YtDlp {
        YtDlp { tools: self.tools.paths(), auth: Auth::None }
    }

    // Tools

    pub async fn tools_status(&self) -> ToolsReport {
        let mut report = self.tools_report.lock().unwrap().clone();
        if !report.installing {
            report.status = self.tools.status().await;
        }
        report
    }

    fn report_tools(&self, report: ToolsReport) {
        *self.tools_report.lock().unwrap() = report.clone();
        self.emit(Event::Tools(report));
    }

    async fn prepare_tools(self: Arc<Self>) {
        if !self.tools.missing().is_empty() {
            self.install_tools().await;
        } else {
            self.wake_downloads.notify_waiters();
            self.daily_ytdlp_update().await;
        }
    }

    pub async fn install_tools(&self) {
        if self.installing.swap(true, Ordering::SeqCst) {
            return;
        }
        let result = self
            .tools
            .install(|step| {
                self.report_tools(ToolsReport { installing: true, step: Some(step), ..Default::default() });
            })
            .await;
        self.installing.store(false, Ordering::SeqCst);
        let status = self.tools.status().await;
        let error = result.err().map(|e| {
            log::warn!("installing the tools failed: {e:#}");
            "Não deu para baixar o motor do Requiem (yt-dlp, Deno e FFmpeg). Confira a internet e tente de novo."
                .to_string()
        });
        self.report_tools(ToolsReport { status, installing: false, step: None, error });
        self.wake_downloads.notify_waiters();
        self.wake_sync.notify_waiters();
    }

    async fn daily_ytdlp_update(&self) {
        let last: i64 = self.db.kv_get("ytdlp_checked_at").ok().flatten().and_then(|v| v.parse().ok()).unwrap_or(0);
        if now_ms() - last < 24 * 3600 * 1000 {
            return;
        }
        let _ = self.db.kv_set("ytdlp_checked_at", &now_ms().to_string());
        if let Err(e) = self.tools.update_ytdlp().await {
            log::warn!("yt-dlp update failed: {e:#}");
        }
    }

    pub async fn update_tools(&self) -> Result<ToolsStatus> {
        self.tools.update_ytdlp().await?;
        let status = self.tools.status().await;
        self.report_tools(ToolsReport { status: status.clone(), ..Default::default() });
        Ok(status)
    }

    // Updates

    /// Looks for a newer release once a day; what the last look found shows until the next.
    async fn update_loop(self: Arc<Self>) {
        let current = env!("CARGO_PKG_VERSION");
        if !update::is_release_build(current) {
            return;
        }
        if let Some(version) = self.db.kv_get("update_latest").ok().flatten() {
            if update::is_newer(&version, current) {
                self.set_update(Some(Update { version, url: update::RELEASE_PAGE.into() }));
            }
        }
        loop {
            let last: i64 =
                self.db.kv_get("update_checked_at").ok().flatten().and_then(|v| v.parse().ok()).unwrap_or(0);
            let wait = (last + 24 * 3600 * 1000 - now_ms()).max(30_000) as u64;
            tokio::time::sleep(Duration::from_millis(wait)).await;
            if let Err(e) = self.check_update().await {
                log::info!("update check failed: {e:#}");
                let _ = self.db.kv_set("update_checked_at", &now_ms().to_string());
            }
        }
    }

    pub async fn check_update(&self) -> Result<Option<Update>> {
        let found = update::check(&self.http, env!("CARGO_PKG_VERSION")).await?;
        let _ = self.db.kv_set("update_checked_at", &now_ms().to_string());
        let _ = self.db.kv_set("update_latest", found.as_ref().map(|u| u.version.as_str()).unwrap_or(""));
        self.set_update(found.clone());
        Ok(found)
    }

    fn set_update(&self, found: Option<Update>) {
        *self.update.lock().unwrap() = found.clone();
        if let Some(found) = found {
            self.emit(Event::Update(found));
        }
    }

    // Songs already on the computer

    /// The folders read into the library: the ones the person chose (the Music folder until
    /// then) and the folder downloads go to. A folder inside another is read with it.
    pub fn local_folders(&self) -> Vec<PathBuf> {
        let settings = self.settings();
        let mut folders: Vec<PathBuf> = match &settings.music_folders {
            Some(list) => list.iter().filter(|f| !f.trim().is_empty()).map(PathBuf::from).collect(),
            None => self.paths.music.iter().cloned().collect(),
        };
        folders.push(PathBuf::from(&settings.library_dir));
        folders.sort();
        folders.dedup();
        let all = folders.clone();
        folders.retain(|f| !all.iter().any(|other| other != f && f.starts_with(other)));
        folders
    }

    /// Reads the folders again, after the current reading if one is running.
    pub fn rescan(&self) {
        self.wake_scan.notify_one();
    }

    async fn scan_loop(self: Arc<Self>) {
        loop {
            self.scan_local().await;
            self.wake_scan.notified().await;
        }
    }

    async fn scan_local(self: &Arc<Self>) {
        let folders = self.local_folders();
        let found = self.local.lock().unwrap().found;
        self.set_local(LocalStatus { running: true, found });
        let engine = self.clone();
        let result = tokio::task::spawn_blocking(move || {
            local::scan(&engine.db, &folders, &engine.paths.covers(), |found| {
                engine.set_local(LocalStatus { running: true, found })
            })
        })
        .await;
        let found = match result {
            Ok(Ok(report)) => {
                if report.changed() {
                    log::info!("music folders: {report:?}");
                    self.emit(Event::Library);
                }
                if report.removed > 0 {
                    self.emit(Event::Playlists);
                }
                report.found
            }
            Ok(Err(e)) => {
                log::warn!("reading the music folders failed: {e:#}");
                found
            }
            Err(e) => {
                log::warn!("reading the music folders stopped: {e}");
                found
            }
        };
        self.set_local(LocalStatus { running: false, found });
    }

    fn set_local(&self, status: LocalStatus) {
        *self.local.lock().unwrap() = status;
        self.emit(Event::Local(status));
    }

    // Library

    pub fn library(&self) -> Result<Vec<Item>> {
        self.db.library()
    }

    pub fn queue(&self) -> Result<Vec<Item>> {
        self.db.pending()
    }

    /// Counts a play; the answer is the play's row, for the seconds the player reports later.
    pub fn played(&self, id: &str) -> Result<i64> {
        let play = self.db.played(id)?;
        self.emit_item(id);
        Ok(play)
    }

    pub fn play_seconds(&self, play: i64, seconds: f64) -> Result<()> {
        self.db.play_seconds(play, seconds)
    }

    pub fn like(&self, id: &str, liked: bool) -> Result<()> {
        self.db.like(id, liked)?;
        self.emit_item(id);
        Ok(())
    }

    /// Saved often while a long item plays, so no event: the player keeps its own copy.
    pub fn resume(&self, id: &str, seconds: Option<f64>) -> Result<()> {
        self.db.set_resume(id, seconds)
    }

    pub fn edit_info(&self, id: &str, title: &str, artist: &str, album: Option<&str>) -> Result<()> {
        if title.trim().is_empty() {
            return Err(anyhow!("O título não pode ficar vazio."));
        }
        self.db.edit_info(id, title, artist, album)?;
        self.emit_item(id);
        Ok(())
    }

    pub fn history(&self, limit: i64) -> Result<Vec<Play>> {
        self.db.history(limit)
    }

    pub fn stats(&self, since: i64) -> Result<Stats> {
        self.db.stats(since)
    }

    pub fn delete(&self, id: &str, delete_files: bool) -> Result<()> {
        if let Some(cancel) = self.cancels.lock().unwrap().get(id) {
            cancel.notify_waiters();
        }
        if let Some(item) = self.db.get(id)? {
            if delete_files {
                for path in [item.file_path, item.thumb_path].into_iter().flatten() {
                    let _ = std::fs::remove_file(path);
                }
            }
        }
        self.db.remove(id)?;
        self.emit(Event::Removed { id: id.to_string() });
        self.emit(Event::Playlists);
        Ok(())
    }

    // Search and requests

    pub async fn search(&self, query: &str, kind: Kind) -> Result<Vec<Entry>> {
        let query = query.trim();
        if query.is_empty() {
            return Ok(vec![]);
        }
        self.ready_ytdlp()?;
        self.ytdlp_anonymous().search(query, kind, 24).await
    }

    /// Understands what was pasted in the search field: a video link, a playlist, or words.
    pub async fn resolve(&self, text: &str) -> Result<Resolved> {
        match links::classify(text) {
            Pasted::Query(text) => Ok(Resolved::Query { text }),
            Pasted::Video(id) => {
                self.ready_ytdlp()?;
                let (info, _) = self.ytdlp_anonymous().info(&links::watch_url(&id)).await?;
                let (title, artist, _) = info.song();
                Ok(Resolved::Video {
                    entry: Entry {
                        thumbnail: info
                            .thumbnail
                            .clone()
                            .unwrap_or_else(|| format!("https://i.ytimg.com/vi/{id}/hqdefault.jpg")),
                        is_short: info.is_short(),
                        channel: info.channel_name(),
                        duration: info.duration,
                        id,
                        title,
                        artist,
                    },
                })
            }
            Pasted::Playlist(list) => {
                self.ready_ytdlp()?;
                let (title, entries) = self.ytdlp().listing(&links::playlist_url(&list), 500).await?;
                Ok(Resolved::Playlist { title, entries })
            }
        }
    }

    fn ready_ytdlp(&self) -> Result<()> {
        if self.tools.paths().ytdlp.is_none() {
            return Err(anyhow!("O yt-dlp ainda está sendo preparado. Espere alguns segundos."));
        }
        Ok(())
    }

    /// Queues a download the person asked for. Goes ahead of anything the sync found.
    pub fn request(&self, entry: &Entry, kind: Kind, source: &str) -> Result<Item> {
        let item = self.db.request(&NewItem {
            id: entry.id.clone(),
            kind: kind.as_str().into(),
            title: entry.title.clone(),
            artist: entry.artist.clone(),
            channel: entry.channel.clone(),
            duration: entry.duration,
            thumbnail: Some(entry.thumbnail.clone()),
            source: source.into(),
        })?;
        self.emit(Event::Item { item: Box::new(item.clone()) });
        self.wake_downloads.notify_waiters();
        Ok(item)
    }

    /// Queues every entry and, with a name, makes a playlist of them in the same order.
    pub fn request_many(&self, entries: &[Entry], kind: Kind, playlist: Option<&str>) -> Result<Option<i64>> {
        for entry in entries {
            self.request(entry, kind, "playlist")?;
        }
        let created = match playlist.map(str::trim).filter(|n| !n.is_empty()) {
            Some(name) => {
                let ids: Vec<String> = entries.iter().map(|e| e.id.clone()).collect();
                let id = self.db.playlist_create(name, &ids)?;
                self.emit(Event::Playlists);
                Some(id)
            }
            None => None,
        };
        Ok(created)
    }

    pub fn retry(&self, id: &str) -> Result<()> {
        self.db.requeue(id)?;
        self.emit_item(id);
        self.wake_downloads.notify_waiters();
        Ok(())
    }

    pub fn cancel(&self, id: &str) -> Result<()> {
        if let Some(cancel) = self.cancels.lock().unwrap().get(id) {
            cancel.notify_waiters();
        }
        self.db.remove(id)?;
        self.emit(Event::Removed { id: id.into() });
        Ok(())
    }

    pub fn clear_failed(&self) -> Result<usize> {
        let n = self.db.clear_failed()?;
        self.emit(Event::Library);
        Ok(n)
    }

    // Playlists

    pub fn playlists(&self) -> Result<Vec<Playlist>> {
        self.db.playlists()
    }

    pub fn playlist_create(&self, name: &str, item_ids: &[String]) -> Result<i64> {
        let id = self.db.playlist_create(name, item_ids)?;
        self.emit(Event::Playlists);
        Ok(id)
    }

    pub fn playlist_rename(&self, id: i64, name: &str) -> Result<()> {
        self.db.playlist_rename(id, name)?;
        self.emit(Event::Playlists);
        Ok(())
    }

    pub fn playlist_delete(&self, id: i64) -> Result<()> {
        self.db.playlist_delete(id)?;
        self.emit(Event::Playlists);
        Ok(())
    }

    pub fn playlist_add(&self, id: i64, item_ids: &[String]) -> Result<()> {
        self.db.playlist_add(id, item_ids)?;
        self.emit(Event::Playlists);
        Ok(())
    }

    pub fn playlist_remove(&self, id: i64, item_id: &str) -> Result<()> {
        self.db.playlist_remove(id, item_id)?;
        self.emit(Event::Playlists);
        Ok(())
    }

    pub fn playlist_reorder(&self, id: i64, item_ids: &[String]) -> Result<()> {
        self.db.playlist_reorder(id, item_ids)?;
        self.emit(Event::Playlists);
        Ok(())
    }

    // Lyrics

    pub async fn lyrics(&self, id: &str) -> Result<Option<Lyrics>> {
        if let Some(json) = self.db.lyrics(id)? {
            if let Ok(cached) = serde_json::from_str::<Cached>(&json) {
                let fresh = now_ms() - cached.fetched_at < 3 * 24 * 3600 * 1000;
                if cached.lyrics.is_some() || fresh {
                    return Ok(cached.lyrics);
                }
            }
        }
        let item = self.db.get(id)?.ok_or_else(|| anyhow!("música não encontrada"))?;
        let found = lyrics::fetch(&self.http, &item.title, &item.artist, item.album.as_deref(), item.duration).await?;
        let cached = Cached { lyrics: found.clone(), fetched_at: now_ms() };
        self.db.set_lyrics(id, &serde_json::to_string(&cached)?)?;
        Ok(found)
    }

    // Account

    /// Saves the session captured from the in-app login window.
    pub fn save_app_cookies(&self, cookies: &[CookieRecord]) -> Result<bool> {
        if !cookies::has_session(cookies) {
            return Ok(false);
        }
        cookies::write(&self.paths.cookies(), cookies, now_ms() / 1000)?;
        let mut next = self.settings();
        next.account = Account::App;
        self.set_settings(next)?;
        Ok(true)
    }

    /// Copies a cookies.txt the person exported; yt-dlp rewrites its copy, never theirs.
    pub fn import_cookie_file(&self, source: &Path) -> Result<()> {
        let text = std::fs::read_to_string(source).context("não deu para ler o arquivo")?;
        if !text.contains("youtube.com") {
            return Err(anyhow!("Esse arquivo não tem cookies do YouTube."));
        }
        std::fs::write(self.paths.cookies(), text)?;
        let mut next = self.settings();
        next.account = Account::File;
        self.set_settings(next)?;
        Ok(())
    }

    pub fn disconnect(&self) -> Result<()> {
        let _ = std::fs::remove_file(self.paths.cookies());
        let mut next = self.settings();
        next.account = Account::None;
        self.set_settings(next)?;
        Ok(())
    }

    /// Reads the five most recent videos of the history to prove the account works.
    pub async fn test_account(&self) -> AccountTest {
        if self.settings().account == Account::None {
            return AccountTest { ok: false, message: "Nenhuma conta conectada.".into(), sample: vec![] };
        }
        if let Err(e) = self.ready_ytdlp() {
            return AccountTest { ok: false, message: e.to_string(), sample: vec![] };
        }
        match self.ytdlp().listing(":ythistory", 5).await {
            Ok((_, sample)) if !sample.is_empty() => {
                AccountTest { ok: true, message: "A conta respondeu com o seu histórico.".into(), sample }
            }
            Ok(_) => AccountTest {
                ok: false,
                message:
                    "A conta respondeu, mas o histórico veio vazio. Confira se o histórico do YouTube está ativado."
                        .into(),
                sample: vec![],
            },
            Err(e) => AccountTest { ok: false, message: e.to_string(), sample: vec![] },
        }
    }

    // Sync

    pub fn sync_status(&self) -> SyncStatus {
        self.sync.lock().unwrap().clone()
    }

    fn set_sync(&self, change: impl FnOnce(&mut SyncStatus)) {
        let status = {
            let mut s = self.sync.lock().unwrap();
            change(&mut s);
            s.clone()
        };
        self.emit(Event::Sync { status });
    }

    pub fn sync_now(self: &Arc<Self>) {
        let engine = self.clone();
        tokio::spawn(async move {
            if let Err(e) = engine.run_sync().await {
                log::warn!("sync failed: {e:#}");
            }
        });
    }

    async fn sync_loop(self: Arc<Self>) {
        let mut last_run = 0i64;
        loop {
            let settings = self.settings();
            let interval_ms = settings.sync_interval_minutes as i64 * 60_000;
            let can_run = settings.auto_sync && settings.account != Account::None && self.tools.paths().ytdlp.is_some();
            let due = if last_run == 0 { now_ms() + 15_000 } else { last_run + interval_ms };
            self.set_sync(|s| s.next_run_at = can_run.then_some(due));
            let wait = (due - now_ms()).max(1000) as u64;
            tokio::select! {
                _ = tokio::time::sleep(Duration::from_millis(wait)) => {
                    if can_run {
                        last_run = now_ms();
                        if let Err(e) = self.run_sync().await {
                            log::warn!("sync failed: {e:#}");
                        }
                    }
                }
                _ = self.wake_sync.notified() => {}
            }
        }
    }

    /// Reads history and likes, records what is new and wakes the download workers.
    pub async fn run_sync(&self) -> Result<u32> {
        {
            let mut s = self.sync.lock().unwrap();
            if s.running {
                return Ok(0);
            }
            s.running = true;
        }
        self.emit(Event::Sync { status: self.sync_status() });
        let result = self.sync_feeds().await;
        let (found, error) = match &result {
            Ok((found, errors)) => (*found, errors.first().cloned()),
            Err(e) => (0, Some(e.to_string())),
        };
        self.set_sync(|s| {
            s.running = false;
            s.last_run_at = Some(now_ms());
            s.last_found = found;
            s.last_error = error;
        });
        if found > 0 {
            self.emit(Event::Library);
            self.wake_downloads.notify_waiters();
        }
        result.map(|(found, _)| found)
    }

    async fn sync_feeds(&self) -> Result<(u32, Vec<String>)> {
        let settings = self.settings();
        if settings.account == Account::None {
            return Err(anyhow!("Conecte sua conta do YouTube para sincronizar."));
        }
        self.ready_ytdlp()?;
        let ytdlp = self.ytdlp();
        let feeds = [
            (settings.sync_history, ":ythistory", "history", "Histórico"),
            (settings.sync_likes, ":ytfav", "liked", "Vídeos curtidos"),
            (
                settings.sync_music_likes,
                "https://music.youtube.com/playlist?list=LM",
                "liked_music",
                "Curtidas do YouTube Music",
            ),
        ];
        let mut found = 0u32;
        let mut errors = vec![];
        for (enabled, url, source, label) in feeds {
            if !enabled {
                continue;
            }
            let key = format!("feed:{source}");
            let first_time = self.db.kv_get(&key)?.is_none();
            let limit = if first_time { settings.first_sync_limit } else { 100 };
            match ytdlp.listing(url, limit).await {
                Ok((_, entries)) => {
                    let now = now_ms();
                    for (i, e) in entries.iter().enumerate() {
                        if e.is_short && !settings.download_shorts {
                            continue;
                        }
                        let new = self.db.discover(
                            &NewItem {
                                id: e.id.clone(),
                                kind: Kind::Auto.as_str().into(),
                                title: e.title.clone(),
                                artist: e.artist.clone(),
                                channel: e.channel.clone(),
                                duration: e.duration,
                                thumbnail: Some(e.thumbnail.clone()),
                                source: source.into(),
                            },
                            now - i as i64,
                        )?;
                        if new {
                            found += 1;
                        }
                    }
                    self.db.kv_set(&key, &now.to_string())?;
                }
                Err(e) => errors.push(format!("{label}: {e}")),
            }
        }
        Ok((found, errors))
    }

    // Downloads

    async fn worker_loop(self: Arc<Self>) {
        loop {
            let notified = self.wake_downloads.notified();
            tokio::pin!(notified);
            notified.as_mut().enable();
            let next = if self.tools.paths().ytdlp.is_some() { self.db.claim_next() } else { Ok(None) };
            match next {
                Ok(Some(item)) => self.run_job(item).await,
                Ok(None) => {
                    let _ = tokio::time::timeout(Duration::from_secs(60), notified).await;
                }
                Err(e) => {
                    log::error!("queue read failed: {e:#}");
                    tokio::time::sleep(Duration::from_secs(5)).await;
                }
            }
        }
    }

    async fn run_job(&self, item: Item) {
        let id = item.id.clone();
        let cancel = Arc::new(Notify::new());
        self.cancels.lock().unwrap().insert(id.clone(), cancel.clone());
        self.emit(Event::Item { item: Box::new(item.clone()) });
        let result = self.download(&item, cancel).await;
        self.cancels.lock().unwrap().remove(&id);
        match result {
            Ok(None) => {}
            Ok(Some(reason)) => {
                let _ = self.db.skip(&id, &reason);
            }
            Err(e) => {
                let message = e.to_string();
                // A cancelled item was already removed; do not resurrect it as failed.
                if message != "Cancelado" {
                    let _ = self.db.fail(&id, &message);
                }
            }
        }
        match self.db.get(&id) {
            Ok(Some(item)) if item.status != "removed" => self.emit(Event::Item { item: Box::new(item) }),
            _ => self.emit(Event::Removed { id }),
        }
    }

    /// Ok(None) when the file is in the library, Ok(Some(reason)) when the rules skipped it.
    async fn download(&self, item: &Item, cancel: Arc<Notify>) -> Result<Option<String>> {
        let settings = self.settings();
        let ytdlp = self.ytdlp();
        self.emit_progress(&item.id, None, "Lendo o vídeo", None, None);
        let (info, raw) = ytdlp.info(&links::watch_url(&item.id)).await?;

        let kind = match Kind::parse(&item.kind) {
            Kind::Auto => {
                let rules = Rules {
                    music: settings.download_music,
                    videos: settings.download_videos,
                    shorts: settings.download_shorts,
                    max_music_minutes: settings.max_music_minutes,
                    max_video_minutes: settings.max_video_minutes,
                };
                match media::decide(&info, &rules) {
                    Decision::Skip(reason) => {
                        // Asking for it again later ("Baixar assim mesmo") skips the rules.
                        self.db.set_kind(&item.id, media::natural_kind(&info, rules.max_music_minutes).as_str())?;
                        return Ok(Some(reason.to_string()));
                    }
                    Decision::Keep(kind) => {
                        self.db.set_kind(&item.id, kind.as_str())?;
                        kind
                    }
                }
            }
            fixed => {
                if info.is_live_now() {
                    return Err(anyhow!("Transmissões ao vivo não podem ser baixadas enquanto acontecem."));
                }
                fixed
            }
        };

        let (title, artist, album) = match kind {
            Kind::Music => info.song(),
            _ => (info.title.clone(), info.channel_name(), None),
        };
        let folder =
            PathBuf::from(&settings.library_dir).join(if kind == Kind::Music { "Músicas" } else { "Vídeos" });
        tokio::fs::create_dir_all(&folder).await.with_context(|| format!("criando {}", folder.display()))?;
        let stem = folder.join(file_stem(&artist, &title, &item.id));
        let cover_stem = self.paths.covers().join(&item.id);
        let info_path = self.paths.tmp().join(format!("{}.info.json", item.id));
        tokio::fs::write(&info_path, serde_json::to_vec(&raw)?).await?;

        let plan = DownloadPlan {
            kind,
            video_height: settings.video_height,
            audio_format: settings.audio_format.clone(),
            output_stem: stem.clone(),
            thumbnail_stem: cover_stem.clone(),
        };
        let parts = if kind == Kind::Video && self.tools.paths().ffmpeg.is_some() { 2 } else { 1 };
        let id = item.id.clone();
        let mut last_emit = std::time::Instant::now() - Duration::from_secs(1);
        let result = ytdlp
            .download(&info_path, &plan, cancel, |p| {
                let (fraction, stage, speed, eta) = match p {
                    Progress::Bytes { done, total, speed, eta, part } => {
                        let part_fraction = total.filter(|t| *t > 0).map(|t| (done as f64 / t as f64).min(1.0));
                        let fraction = part_fraction.map(|f| overall_fraction(f, part, parts));
                        (fraction, "Baixando".to_string(), speed, eta)
                    }
                    Progress::Processing(name) => (None, processing_label(&name).to_string(), None, None),
                };
                if last_emit.elapsed() >= Duration::from_millis(250) || fraction == Some(1.0) || speed.is_none() {
                    last_emit = std::time::Instant::now();
                    self.emit_progress(&id, fraction, &stage, speed, eta);
                }
            })
            .await;
        let _ = tokio::fs::remove_file(&info_path).await;
        let path = match result {
            Ok(path) => path,
            Err(e) => {
                remove_partials(&stem);
                return Err(e);
            }
        };

        let thumb = find_with_stem(&cover_stem);
        let size = std::fs::metadata(&path).ok().map(|m| m.len() as i64);
        self.db.finish(
            &item.id,
            &Finished {
                kind: kind.as_str().into(),
                title,
                artist,
                album,
                channel: info.channel_name(),
                duration: info.duration,
                file_path: path.to_string_lossy().into_owned(),
                thumb_path: thumb.map(|p| p.to_string_lossy().into_owned()),
                file_size: size,
                width: info.width,
                height: info.height,
                track_number: info.track_number,
                disc_number: info.disc_number,
                // The year of a song is its release, never the upload date of the video.
                year: if kind == Kind::Music { info.release_year } else { None },
            },
        )?;
        Ok(None)
    }

    fn emit_progress(&self, id: &str, fraction: Option<f64>, stage: &str, speed: Option<f64>, eta: Option<f64>) {
        self.emit(Event::Progress { id: id.into(), fraction, speed, eta, stage: stage.into() });
    }
}

/// Video downloads come in two parts (image, then sound); the image is most of the bytes.
fn overall_fraction(part_fraction: f64, part: u32, parts: u32) -> f64 {
    if parts < 2 {
        return part_fraction;
    }
    match part {
        0 => part_fraction * 0.9,
        _ => 0.9 + part_fraction * 0.1,
    }
    .min(1.0)
}

fn processing_label(postprocessor: &str) -> &'static str {
    match postprocessor {
        "Merger" => "Juntando imagem e som",
        "ExtractAudio" | "FFmpegExtractAudio" => "Convertendo o áudio",
        "EmbedThumbnail" | "FFmpegMetadata" | "Metadata" => "Gravando capa e dados",
        _ => "Finalizando",
    }
}

/// "Artist - Title [id]", safe on every file system and short enough for Windows paths.
pub fn file_stem(artist: &str, title: &str, id: &str) -> String {
    let base = if artist.trim().is_empty() || title.to_lowercase().contains(&artist.to_lowercase()) {
        title.to_string()
    } else {
        format!("{artist} - {title}")
    };
    let mut clean: String =
        base.chars().map(|c| if c.is_control() || "<>:\"/\\|?*".contains(c) { '_' } else { c }).collect();
    clean = clean.split_whitespace().collect::<Vec<_>>().join(" ");
    let mut clean: String = clean.chars().take(120).collect();
    while clean.ends_with('.') || clean.ends_with(' ') {
        clean.pop();
    }
    if clean.is_empty() {
        clean = "Sem título".into();
    }
    format!("{clean} [{id}]")
}

fn find_with_stem(stem: &Path) -> Option<PathBuf> {
    let dir = stem.parent()?;
    let name = stem.file_name()?.to_string_lossy().to_string();
    std::fs::read_dir(dir)
        .ok()?
        .filter_map(|e| e.ok())
        .map(|e| e.path())
        .find(|p| p.file_stem().map(|s| s.to_string_lossy() == name).unwrap_or(false))
}

fn remove_partials(stem: &Path) {
    let (Some(dir), Some(name)) = (stem.parent(), stem.file_name()) else { return };
    let name = name.to_string_lossy().to_string();
    if let Ok(entries) = std::fs::read_dir(dir) {
        for entry in entries.flatten() {
            if entry.file_name().to_string_lossy().starts_with(&name) {
                let _ = std::fs::remove_file(entry.path());
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Stand-in for yt-dlp: answers the version, the update, the read of one video and its
    /// download, with the output lines the real one prints for Requiem.
    #[cfg(unix)]
    const FAKE_YTDLP: &str = r#"#!/bin/sh
for arg in "$@"; do
  case "$arg" in
    --version) echo 2026.01.01; exit 0 ;;
    -U) exit 0 ;;
  esac
done
out=""; load=""; prev=""
for arg in "$@"; do
  case "$prev" in
    -o) case "$arg" in thumbnail:*) ;; *) out="$arg" ;; esac ;;
    --load-info-json) load="$arg" ;;
  esac
  prev="$arg"
done
if [ -n "$load" ]; then
  file=$(printf '%s' "$out" | sed 's/\.%(ext)s$/.m4a/')
  printf 'not really audio' > "$file"
  echo "RQDL 8 16 NA 1000 1 140"
  echo "RQDL 16 16 NA 1000 0 140"
  echo "RQPATH $file"
  exit 0
fi
echo '{"id":"dQw4w9WgXcQ","title":"Rick Astley - Never Gonna Give You Up (Official Video)","channel":"Rick Astley","duration":213,"categories":["Music"]}'
"#;

    /// The path the person depends on, with everything but YouTube real: a song asked for in
    /// the search downloads, lands in the library with its file, stays single after the music
    /// folders are read, and the player's server streams it.
    #[cfg(unix)]
    #[tokio::test(flavor = "multi_thread", worker_threads = 2)]
    async fn a_requested_song_lands_in_the_library_and_plays() {
        use std::os::unix::fs::PermissionsExt;

        let dir = tempfile::tempdir().unwrap();
        let paths = Paths { data: dir.path().join("data"), default_library: dir.path().join("Requiem"), music: None };
        std::fs::create_dir_all(paths.bin()).unwrap();
        let version = |line: &str| format!("#!/bin/sh\necho '{line}'\n");
        for (name, body) in [
            ("yt-dlp", FAKE_YTDLP.to_string()),
            ("deno", version("deno 2.0.0")),
            ("ffmpeg", version("ffmpeg version 7.0")),
            ("ffprobe", version("ffprobe version 7.0")),
        ] {
            let path = paths.bin().join(name);
            std::fs::write(&path, body).unwrap();
            std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o755)).unwrap();
        }
        let engine = Engine::start(paths).await.unwrap();

        let entry = Entry {
            id: "dQw4w9WgXcQ".into(),
            title: "Never Gonna Give You Up".into(),
            artist: "Rick Astley".into(),
            channel: "Rick Astley".into(),
            duration: Some(213.0),
            thumbnail: String::new(),
            is_short: false,
        };
        engine.request(&entry, Kind::Music, "search").unwrap();
        let mut finished = None;
        for _ in 0..200 {
            let item = engine.db.get(&entry.id).unwrap().unwrap();
            if item.status == "done" || item.status == "failed" {
                finished = Some(item);
                break;
            }
            tokio::time::sleep(Duration::from_millis(50)).await;
        }
        let item = finished.expect("the download never finished");
        assert_eq!(item.status, "done", "{:?}", item.error);
        assert_eq!((item.title.as_str(), item.artist.as_str()), ("Never Gonna Give You Up", "Rick Astley"));
        let file = PathBuf::from(item.file_path.unwrap());
        assert!(file.starts_with(dir.path().join("Requiem").join("Músicas")), "{}", file.display());

        let mut events = engine.subscribe();
        engine.rescan();
        tokio::time::timeout(Duration::from_secs(10), async {
            let mut started = false;
            loop {
                if let Ok(Event::Local(status)) = events.recv().await {
                    if status.running {
                        started = true;
                    } else if started {
                        break;
                    }
                }
            }
        })
        .await
        .expect("the folders were not read again");
        let library = engine.library().unwrap();
        assert_eq!(library.len(), 1, "{library:?}");

        let url = format!("{}/m/{}", engine.overview().media_base, entry.id);
        let http = reqwest::Client::builder().no_proxy().build().unwrap();
        let body = http.get(url).send().await.unwrap().bytes().await.unwrap();
        assert_eq!(&body[..], b"not really audio");
    }

    #[test]
    fn events_have_the_shape_the_interface_reads() {
        let settings = serde_json::to_value(Event::Settings { settings: Box::new(Settings::default()) }).unwrap();
        assert_eq!(settings["type"], "settings");
        assert_eq!(settings["settings"]["autoSync"], true);
        let progress = serde_json::to_value(Event::Progress {
            id: "x".into(),
            fraction: Some(0.5),
            speed: None,
            eta: None,
            stage: "Baixando".into(),
        })
        .unwrap();
        assert_eq!(progress["type"], "progress");
        assert_eq!(progress["fraction"], 0.5);
        let tools =
            serde_json::to_value(Event::Tools(ToolsReport { error: Some("falhou".into()), ..Default::default() }))
                .unwrap();
        assert_eq!(tools["type"], "tools");
        assert_eq!(tools["installing"], false);
        assert_eq!(tools["error"], "falhou");
        assert_eq!(tools["status"]["ready"], false);
    }

    #[test]
    fn stems_are_safe() {
        assert_eq!(file_stem("AC/DC", "Back In Black", "abc"), "AC_DC - Back In Black [abc]");
        assert_eq!(file_stem("", "What?: A song.", "x"), "What__ A song [x]");
        assert_eq!(
            file_stem("Rick Astley", "Rick Astley - Never Gonna Give You Up", "y"),
            "Rick Astley - Never Gonna Give You Up [y]"
        );
        assert_eq!(file_stem("A", "   ", "z"), "A - [z]");
    }

    #[test]
    fn video_progress_weights_the_image() {
        assert!((overall_fraction(0.5, 0, 2) - 0.45).abs() < 1e-9);
        assert!((overall_fraction(0.5, 1, 2) - 0.95).abs() < 1e-9);
        assert!((overall_fraction(0.5, 0, 1) - 0.5).abs() < 1e-9);
    }
}
