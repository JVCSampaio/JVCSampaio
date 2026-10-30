//! The commands the interface calls. Errors travel as the sentence shown to the person.

use requiem_core::db::{Item, Play, Playlist, Stats};
use requiem_core::engine::{AccountTest, Overview, Resolved, SyncStatus, ToolsReport};
use requiem_core::lyrics::Lyrics;
use requiem_core::media::{Entry, Kind};
use requiem_core::settings::Settings;
use requiem_core::tools::ToolsStatus;
use requiem_core::update::{self, Update};
use std::path::PathBuf;
use tauri::{AppHandle, Manager, State};
use tauri_plugin_autostart::ManagerExt as _;
use tauri_plugin_opener::OpenerExt as _;

use crate::AppState;

type Res<T> = Result<T, String>;

fn msg(e: impl std::fmt::Display) -> String {
    e.to_string()
}

fn kind(value: &str) -> Res<Kind> {
    match value {
        "music" => Ok(Kind::Music),
        "video" => Ok(Kind::Video),
        other => Err(format!("tipo desconhecido: {other}")),
    }
}

#[tauri::command]
pub fn overview(state: State<'_, AppState>) -> Overview {
    state.engine.overview()
}

#[tauri::command]
pub async fn tools_status(state: State<'_, AppState>) -> Res<ToolsReport> {
    Ok(state.engine.tools_status().await)
}

#[tauri::command]
pub async fn tools_install(state: State<'_, AppState>) -> Res<()> {
    let engine = state.engine.clone();
    tauri::async_runtime::spawn(async move { engine.install_tools().await });
    Ok(())
}

#[tauri::command]
pub async fn tools_update(state: State<'_, AppState>) -> Res<ToolsStatus> {
    state.engine.update_tools().await.map_err(msg)
}

#[tauri::command]
pub fn library(state: State<'_, AppState>) -> Res<Vec<Item>> {
    state.engine.library().map_err(msg)
}

#[tauri::command]
pub fn queue(state: State<'_, AppState>) -> Res<Vec<Item>> {
    state.engine.queue().map_err(msg)
}

#[tauri::command]
pub fn playlists(state: State<'_, AppState>) -> Res<Vec<Playlist>> {
    state.engine.playlists().map_err(msg)
}

#[tauri::command]
pub fn item_played(state: State<'_, AppState>, id: String) -> Res<i64> {
    state.engine.played(&id).map_err(msg)
}

#[tauri::command]
pub fn play_seconds(state: State<'_, AppState>, play: i64, seconds: f64) -> Res<()> {
    state.engine.play_seconds(play, seconds).map_err(msg)
}

#[tauri::command]
pub fn item_like(state: State<'_, AppState>, id: String, liked: bool) -> Res<()> {
    state.engine.like(&id, liked).map_err(msg)
}

#[tauri::command]
pub fn item_resume(state: State<'_, AppState>, id: String, seconds: Option<f64>) -> Res<()> {
    state.engine.resume(&id, seconds).map_err(msg)
}

#[tauri::command]
pub fn item_edit(
    state: State<'_, AppState>,
    id: String,
    title: String,
    artist: String,
    album: Option<String>,
) -> Res<()> {
    state.engine.edit_info(&id, &title, &artist, album.as_deref()).map_err(msg)
}

#[tauri::command]
pub fn history(state: State<'_, AppState>, limit: i64) -> Res<Vec<Play>> {
    state.engine.history(limit).map_err(msg)
}

#[tauri::command]
pub fn stats(state: State<'_, AppState>, since: i64) -> Res<Stats> {
    state.engine.stats(since).map_err(msg)
}

#[tauri::command]
pub fn item_delete(state: State<'_, AppState>, id: String, delete_files: bool) -> Res<()> {
    state.engine.delete(&id, delete_files).map_err(msg)
}

#[tauri::command]
pub fn item_reveal(app: AppHandle, state: State<'_, AppState>, id: String) -> Res<()> {
    let item = state.engine.db.get(&id).map_err(msg)?.ok_or("Item não encontrado")?;
    let path = item.file_path.ok_or("Esse item ainda não tem arquivo")?;
    app.opener().reveal_item_in_dir(path).map_err(msg)
}

#[tauri::command]
pub async fn search(state: State<'_, AppState>, query: String, kind: String) -> Res<Vec<Entry>> {
    let k = self::kind(&kind)?;
    state.engine.search(&query, k).await.map_err(msg)
}

#[tauri::command]
pub async fn resolve(state: State<'_, AppState>, text: String) -> Res<Resolved> {
    state.engine.resolve(&text).await.map_err(msg)
}

#[tauri::command]
pub fn download(state: State<'_, AppState>, entry: Entry, kind: String, source: Option<String>) -> Res<Item> {
    let k = self::kind(&kind)?;
    state.engine.request(&entry, k, source.as_deref().unwrap_or("search")).map_err(msg)
}

#[tauri::command]
pub fn download_many(
    state: State<'_, AppState>,
    entries: Vec<Entry>,
    kind: String,
    playlist: Option<String>,
) -> Res<Option<i64>> {
    let k = self::kind(&kind)?;
    state.engine.request_many(&entries, k, playlist.as_deref()).map_err(msg)
}

#[tauri::command]
pub fn download_retry(state: State<'_, AppState>, id: String) -> Res<()> {
    state.engine.retry(&id).map_err(msg)
}

#[tauri::command]
pub fn download_cancel(state: State<'_, AppState>, id: String) -> Res<()> {
    state.engine.cancel(&id).map_err(msg)
}

#[tauri::command]
pub fn downloads_clear(state: State<'_, AppState>) -> Res<usize> {
    state.engine.clear_failed().map_err(msg)
}

#[tauri::command]
pub fn playlist_create(state: State<'_, AppState>, name: String, item_ids: Vec<String>) -> Res<i64> {
    state.engine.playlist_create(&name, &item_ids).map_err(msg)
}

#[tauri::command]
pub fn playlist_rename(state: State<'_, AppState>, id: i64, name: String) -> Res<()> {
    state.engine.playlist_rename(id, &name).map_err(msg)
}

#[tauri::command]
pub fn playlist_delete(state: State<'_, AppState>, id: i64) -> Res<()> {
    state.engine.playlist_delete(id).map_err(msg)
}

#[tauri::command]
pub fn playlist_add(state: State<'_, AppState>, id: i64, item_ids: Vec<String>) -> Res<()> {
    state.engine.playlist_add(id, &item_ids).map_err(msg)
}

#[tauri::command]
pub fn playlist_remove(state: State<'_, AppState>, id: i64, item_id: String) -> Res<()> {
    state.engine.playlist_remove(id, &item_id).map_err(msg)
}

#[tauri::command]
pub fn playlist_reorder(state: State<'_, AppState>, id: i64, item_ids: Vec<String>) -> Res<()> {
    state.engine.playlist_reorder(id, &item_ids).map_err(msg)
}

#[tauri::command]
pub fn settings_get(state: State<'_, AppState>) -> Settings {
    state.engine.settings()
}

#[tauri::command]
pub fn settings_set(app: AppHandle, state: State<'_, AppState>, settings: Settings) -> Res<Settings> {
    let before = state.engine.settings();
    let saved = state.engine.set_settings(settings).map_err(msg)?;
    if before.launch_at_login != saved.launch_at_login {
        let launcher = app.autolaunch();
        let result = if saved.launch_at_login { launcher.enable() } else { launcher.disable() };
        result.map_err(msg)?;
    }
    Ok(saved)
}

#[tauri::command]
pub fn sync_status(state: State<'_, AppState>) -> SyncStatus {
    state.engine.sync_status()
}

#[tauri::command]
pub fn sync_now(state: State<'_, AppState>) {
    state.engine.sync_now();
}

#[tauri::command]
pub async fn account_test(state: State<'_, AppState>) -> Res<AccountTest> {
    Ok(state.engine.test_account().await)
}

#[tauri::command]
pub fn account_import(state: State<'_, AppState>, path: String) -> Res<()> {
    state.engine.import_cookie_file(&PathBuf::from(path)).map_err(msg)
}

#[tauri::command]
pub fn account_disconnect(app: AppHandle, state: State<'_, AppState>) -> Res<()> {
    state.engine.disconnect().map_err(msg)?;
    // The login window shares the cookie store with the main window: forget the session there too.
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.clear_all_browsing_data();
    }
    Ok(())
}

#[tauri::command]
pub async fn lyrics(state: State<'_, AppState>, id: String) -> Res<Option<Lyrics>> {
    state.engine.lyrics(&id).await.map_err(msg)
}

#[tauri::command]
pub fn open_library_folder(app: AppHandle, state: State<'_, AppState>) -> Res<()> {
    let dir = PathBuf::from(state.engine.settings().library_dir);
    std::fs::create_dir_all(&dir).map_err(msg)?;
    app.opener().open_path(dir.to_string_lossy(), None::<&str>).map_err(msg)
}

#[tauri::command]
pub fn library_rescan(state: State<'_, AppState>) {
    state.engine.rescan();
}

#[tauri::command]
pub async fn update_check(state: State<'_, AppState>) -> Res<Option<Update>> {
    state
        .engine
        .check_update()
        .await
        .map_err(|_| "Não deu para procurar atualização agora. Confira a internet.".to_string())
}

/// Opens the release page in the browser, and only that page.
#[tauri::command]
pub fn update_open(app: AppHandle) -> Res<()> {
    app.opener().open_url(update::RELEASE_PAGE, None::<&str>).map_err(msg)
}

/// The CI screenshot tour: the interface walks its screens and prints a mark at each one, and
/// the workflow captures the window. Off unless REQUIEM_TOUR is set; the answer says which.
#[tauri::command]
pub fn tour_mark(name: String) -> bool {
    let on = std::env::var_os("REQUIEM_TOUR").is_some();
    if on {
        println!("TOUR {name}");
    }
    on
}
