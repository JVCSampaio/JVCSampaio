//! The desktop shell around the engine: window, tray, commands for the interface and the
//! in-app YouTube login.

mod commands;
mod login;
mod tray;

use requiem_core::engine::{Engine, Paths};
use std::path::PathBuf;
use std::sync::Arc;
use tauri::{Emitter, Manager, WindowEvent};

pub struct AppState {
    pub engine: Arc<Engine>,
    /// Without a tray icon (a desktop with no status area), closing the window quits.
    pub has_tray: bool,
}

pub fn show_main<R: tauri::Runtime>(app: &tauri::AppHandle<R>) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

fn data_paths(app: &tauri::App) -> anyhow::Result<Paths> {
    // The REQUIEM_* variables let the CI screenshot tour use prepared folders.
    let data = match std::env::var_os("REQUIEM_DATA_DIR") {
        Some(dir) => PathBuf::from(dir),
        None => app.path().app_data_dir()?,
    };
    let music = std::env::var_os("REQUIEM_MUSIC_DIR").map(PathBuf::from).or_else(|| app.path().audio_dir().ok());
    let library = std::env::var_os("REQUIEM_LIBRARY_DIR")
        .map(PathBuf::from)
        .or_else(|| music.as_ref().map(|d| d.join("Requiem")))
        .unwrap_or_else(|| data.join("Biblioteca"));
    Ok(Paths { data, default_library: library, music })
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // WebKitGTK's DMA-BUF renderer draws a blank window on many GPUs and on virtual screens.
    #[cfg(target_os = "linux")]
    if std::env::var_os("WEBKIT_DISABLE_DMABUF_RENDERER").is_none() {
        std::env::set_var("WEBKIT_DISABLE_DMABUF_RENDERER", "1");
    }

    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| show_main(app)))
        .plugin(
            tauri_plugin_log::Builder::new()
                .level(log::LevelFilter::Info)
                .level_for("tao", log::LevelFilter::Warn)
                .build(),
        )
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_autostart::init(
            tauri_plugin_autostart::MacosLauncher::LaunchAgent,
            Some(vec!["--minimized"]),
        ))
        .setup(|app| {
            let paths = data_paths(app)?;
            let engine = tauri::async_runtime::block_on(Engine::start(paths))?;
            let has_tray = match tray::build(app) {
                Ok(()) => true,
                Err(e) => {
                    log::warn!("no tray icon: {e}");
                    false
                }
            };
            app.manage(AppState { engine: engine.clone(), has_tray });

            let handle = app.handle().clone();
            let mut events = engine.subscribe();
            tauri::async_runtime::spawn(async move {
                use tokio::sync::broadcast::error::RecvError;
                loop {
                    match events.recv().await {
                        Ok(event) => {
                            let _ = handle.emit("engine", &event);
                        }
                        Err(RecvError::Lagged(_)) => continue,
                        Err(RecvError::Closed) => break,
                    }
                }
            });

            if has_tray && std::env::args().any(|a| a == "--minimized") {
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.hide();
                }
            }
            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                if window.label() != "main" {
                    return;
                }
                let keep_running = window
                    .try_state::<AppState>()
                    .map(|s| s.has_tray && s.engine.settings().close_to_tray)
                    .unwrap_or(false);
                if keep_running {
                    api.prevent_close();
                    let _ = window.hide();
                }
            }
        })
        .invoke_handler(tauri::generate_handler![
            commands::overview,
            commands::tools_status,
            commands::tools_install,
            commands::tools_update,
            commands::library,
            commands::queue,
            commands::playlists,
            commands::item_played,
            commands::play_seconds,
            commands::item_like,
            commands::item_resume,
            commands::item_edit,
            commands::history,
            commands::stats,
            commands::item_delete,
            commands::item_reveal,
            commands::search,
            commands::resolve,
            commands::download,
            commands::download_many,
            commands::download_retry,
            commands::download_cancel,
            commands::downloads_clear,
            commands::playlist_create,
            commands::playlist_rename,
            commands::playlist_delete,
            commands::playlist_add,
            commands::playlist_remove,
            commands::playlist_reorder,
            commands::settings_get,
            commands::settings_set,
            commands::sync_status,
            commands::sync_now,
            commands::account_test,
            commands::account_import,
            commands::account_disconnect,
            commands::lyrics,
            commands::open_library_folder,
            commands::library_rescan,
            commands::update_check,
            commands::update_open,
            commands::tour_mark,
            login::account_login,
        ])
        .run(tauri::generate_context!())
        .expect("Requiem failed to start");
}
