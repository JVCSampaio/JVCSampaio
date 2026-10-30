//! A tiny HTTP server on 127.0.0.1 that hands audio, video and covers to the web view. It
//! answers byte ranges, so seeking works for long videos, and it only serves files the library
//! knows, behind a random token in the path.

use anyhow::Result;
use axum::body::Body;
use axum::extract::{Path as UrlPath, State};
use axum::http::{Request, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::Router;
use std::path::PathBuf;
use std::sync::Arc;
use tower::ServiceExt;
use tower_http::cors::CorsLayer;
use tower_http::services::ServeFile;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Asset {
    Media,
    Cover,
}

pub type Lookup = Arc<dyn Fn(&str, Asset) -> Option<PathBuf> + Send + Sync>;

struct ServerState {
    token: String,
    lookup: Lookup,
}

/// Starts the server and returns its base URL ("http://127.0.0.1:PORT/TOKEN").
pub async fn start(lookup: Lookup) -> Result<String> {
    let token = format!("{:032x}", rand::random::<u128>());
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await?;
    let port = listener.local_addr()?.port();
    let state = Arc::new(ServerState { token: token.clone(), lookup });
    let app = Router::new().route("/{token}/{asset}/{id}", get(serve)).layer(CorsLayer::permissive()).with_state(state);
    tokio::spawn(async move {
        if let Err(e) = axum::serve(listener, app).await {
            log::error!("media server stopped: {e}");
        }
    });
    Ok(format!("http://127.0.0.1:{port}/{token}"))
}

async fn serve(
    State(state): State<Arc<ServerState>>,
    UrlPath((token, asset, id)): UrlPath<(String, String, String)>,
    request: Request<Body>,
) -> Response {
    if token != state.token {
        return StatusCode::NOT_FOUND.into_response();
    }
    let asset = match asset.as_str() {
        "m" => Asset::Media,
        "c" => Asset::Cover,
        _ => return StatusCode::NOT_FOUND.into_response(),
    };
    let Some(path) = (state.lookup)(&id, asset) else {
        return StatusCode::NOT_FOUND.into_response();
    };
    match ServeFile::new(path).oneshot(request).await {
        Ok(response) => response.into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    async fn raw_get(base: &str, path: &str, range: Option<&str>) -> String {
        let addr = base.trim_start_matches("http://").split('/').next().unwrap().to_string();
        let mut stream = tokio::net::TcpStream::connect(&addr).await.unwrap();
        let range = range.map(|r| format!("Range: {r}\r\n")).unwrap_or_default();
        let req = format!(
            "GET {path} HTTP/1.1\r\nHost: {addr}\r\nOrigin: http://tauri.localhost\r\n{range}Connection: close\r\n\r\n"
        );
        stream.write_all(req.as_bytes()).await.unwrap();
        let mut out = Vec::new();
        stream.read_to_end(&mut out).await.unwrap();
        String::from_utf8_lossy(&out).to_string()
    }

    #[tokio::test]
    async fn serves_known_files_with_ranges_and_cors() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("song.m4a");
        std::fs::write(&file, b"0123456789").unwrap();
        let known = file.clone();
        let base = start(Arc::new(move |id, asset| (id == "abc" && asset == Asset::Media).then(|| known.clone())))
            .await
            .unwrap();
        let token_path = base.splitn(4, '/').nth(3).unwrap().to_string();

        let full = raw_get(&base, &format!("/{token_path}/m/abc"), None).await;
        assert!(full.starts_with("HTTP/1.1 200"), "{full}");
        assert!(full.to_lowercase().contains("access-control-allow-origin"), "{full}");
        assert!(full.ends_with("0123456789"));

        let part = raw_get(&base, &format!("/{token_path}/m/abc"), Some("bytes=2-4")).await;
        assert!(part.starts_with("HTTP/1.1 206"), "{part}");
        assert!(part.ends_with("234"), "{part}");

        let wrong_token = raw_get(&base, "/nope/m/abc", None).await;
        assert!(wrong_token.starts_with("HTTP/1.1 404"));
        let unknown = raw_get(&base, &format!("/{token_path}/m/zzz"), None).await;
        assert!(unknown.starts_with("HTTP/1.1 404"));
    }
}
