//! The tray icon keeps Requiem syncing with the window closed, with the controls people
//! reach for most.

use tauri::menu::{Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{Emitter, Manager};

use crate::{show_main, AppState};

pub fn build(app: &tauri::App) -> tauri::Result<()> {
    let toggle = MenuItem::with_id(app, "toggle", "Tocar ou pausar", true, None::<&str>)?;
    let next = MenuItem::with_id(app, "next", "Próxima", true, None::<&str>)?;
    let sync = MenuItem::with_id(app, "sync", "Sincronizar agora", true, None::<&str>)?;
    let show = MenuItem::with_id(app, "show", "Abrir o Requiem", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Sair", true, None::<&str>)?;
    let separator = PredefinedMenuItem::separator(app)?;
    let menu = Menu::with_items(app, &[&show, &toggle, &next, &sync, &separator, &quit])?;

    let mut builder = TrayIconBuilder::with_id("main")
        .tooltip("Requiem")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id().as_ref() {
            "toggle" => {
                let _ = app.emit("tray", "toggle");
            }
            "next" => {
                let _ = app.emit("tray", "next");
            }
            "sync" => {
                if let Some(state) = app.try_state::<AppState>() {
                    state.engine.sync_now();
                }
            }
            "show" => show_main(app),
            "quit" => app.exit(0),
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = event {
                show_main(tray.app_handle());
            }
        });
    if let Some(icon) = app.default_window_icon() {
        builder = builder.icon(icon.clone());
    }
    builder.build(app)?;
    Ok(())
}
