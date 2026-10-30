//! Signing in to YouTube inside Requiem: a window on Google's own login page. Once the session
//! cookies appear, they are written to the file yt-dlp reads and the window closes.

use requiem_core::cookies::CookieRecord;
use requiem_core::engine::Event;
use std::time::Duration;
use tauri::webview::cookie::Cookie;
use tauri::{AppHandle, Emitter, Manager, WebviewUrl, WebviewWindowBuilder};

use crate::AppState;

const LOGIN_URL: &str =
    "https://accounts.google.com/ServiceLogin?service=youtube&hl=pt-BR&continue=https%3A%2F%2Fwww.youtube.com%2F";

fn record(c: &Cookie<'static>) -> CookieRecord {
    CookieRecord {
        domain: c.domain().unwrap_or_default().to_string(),
        path: c.path().unwrap_or("/").to_string(),
        secure: c.secure().unwrap_or(false),
        http_only: c.http_only().unwrap_or(false),
        expires: c.expires().and_then(|e| e.datetime()).map(|d| d.unix_timestamp()).unwrap_or(0),
        name: c.name().to_string(),
        value: c.value().to_string(),
    }
}

#[tauri::command]
pub async fn account_login(app: AppHandle) -> Result<(), String> {
    if let Some(window) = app.get_webview_window("login") {
        let _ = window.set_focus();
        return Ok(());
    }
    let url = LOGIN_URL.parse().map_err(|e| format!("{e}"))?;
    // A private session: Google's cookies live only while the window is open, are copied to the
    // engine's own file, and never reach the main window's storage.
    WebviewWindowBuilder::new(&app, "login", WebviewUrl::External(url))
        .incognito(true)
        .title("Entrar no YouTube")
        .inner_size(480.0, 720.0)
        .center()
        .build()
        .map_err(|e| e.to_string())?;

    let handle = app.clone();
    tauri::async_runtime::spawn(async move {
        // Ten minutes to finish signing in; the window closing early ends the wait.
        for _ in 0..600 {
            tokio::time::sleep(Duration::from_secs(1)).await;
            let Some(window) = handle.get_webview_window("login") else { return };
            // Reading cookies off the main thread: on Windows the call deadlocks otherwise.
            let cookies = match window.cookies() {
                Ok(list) => list.iter().map(record).collect::<Vec<_>>(),
                Err(e) => {
                    log::warn!("reading login cookies failed: {e}");
                    continue;
                }
            };
            let Some(state) = handle.try_state::<AppState>() else { return };
            match state.engine.save_app_cookies(&cookies) {
                Ok(true) => {
                    let _ = window.close();
                    let _ = handle.emit(
                        "engine",
                        &Event::Notice {
                            message: "YouTube conectado. A primeira sincronização já começou.".into()
                        },
                    );
                    state.engine.sync_now();
                    return;
                }
                Ok(false) => {}
                Err(e) => log::warn!("saving cookies failed: {e:#}"),
            }
        }
    });
    Ok(())
}
