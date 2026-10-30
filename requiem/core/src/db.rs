//! The library database: every video Requiem knows about (downloaded or waiting), playlists,
//! lyrics cache and settings.

use anyhow::Result;
use rusqlite::{params, Connection, OptionalExtension, Row};
use serde::{Deserialize, Serialize};
use std::path::Path;
use std::sync::Mutex;

pub fn now_ms() -> i64 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_millis() as i64).unwrap_or(0)
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Item {
    pub id: String,
    pub kind: String,
    pub title: String,
    pub artist: String,
    pub album: Option<String>,
    pub channel: String,
    pub duration: Option<f64>,
    pub source: String,
    pub status: String,
    pub error: Option<String>,
    pub file_path: Option<String>,
    pub thumb_path: Option<String>,
    pub thumbnail_url: Option<String>,
    pub file_size: Option<i64>,
    pub width: Option<i64>,
    pub height: Option<i64>,
    pub added_at: i64,
    pub seen_at: Option<i64>,
    pub downloaded_at: Option<i64>,
    pub play_count: i64,
    pub last_played_at: Option<i64>,
}

/// A row found by the sync or asked for by the person, before the download.
#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct NewItem {
    pub id: String,
    pub kind: String,
    pub title: String,
    pub artist: String,
    pub channel: String,
    pub duration: Option<f64>,
    pub thumbnail: Option<String>,
    pub source: String,
}

/// What a finished download wrote.
#[derive(Debug, Clone, Default)]
pub struct Finished {
    pub kind: String,
    pub title: String,
    pub artist: String,
    pub album: Option<String>,
    pub channel: String,
    pub duration: Option<f64>,
    pub file_path: String,
    pub thumb_path: Option<String>,
    pub file_size: Option<i64>,
    pub width: Option<i64>,
    pub height: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Playlist {
    pub id: i64,
    pub name: String,
    pub item_ids: Vec<String>,
    pub created_at: i64,
}

pub struct Db {
    conn: Mutex<Connection>,
}

const ITEM_COLUMNS: &str = "id, kind, title, artist, album, channel, duration, source, status, error, file_path, \
    thumb_path, thumbnail_url, file_size, width, height, added_at, seen_at, downloaded_at, play_count, last_played_at";

fn item_from_row(r: &Row) -> rusqlite::Result<Item> {
    Ok(Item {
        id: r.get(0)?,
        kind: r.get(1)?,
        title: r.get(2)?,
        artist: r.get(3)?,
        album: r.get(4)?,
        channel: r.get(5)?,
        duration: r.get(6)?,
        source: r.get(7)?,
        status: r.get(8)?,
        error: r.get(9)?,
        file_path: r.get(10)?,
        thumb_path: r.get(11)?,
        thumbnail_url: r.get(12)?,
        file_size: r.get(13)?,
        width: r.get(14)?,
        height: r.get(15)?,
        added_at: r.get(16)?,
        seen_at: r.get(17)?,
        downloaded_at: r.get(18)?,
        play_count: r.get(19)?,
        last_played_at: r.get(20)?,
    })
}

impl Db {
    pub fn open(path: &Path) -> Result<Db> {
        let conn = Connection::open(path)?;
        conn.pragma_update(None, "journal_mode", "WAL")?;
        conn.pragma_update(None, "foreign_keys", "ON")?;
        Self::migrate(&conn)?;
        Ok(Db { conn: Mutex::new(conn) })
    }

    pub fn in_memory() -> Result<Db> {
        let conn = Connection::open_in_memory()?;
        Self::migrate(&conn)?;
        Ok(Db { conn: Mutex::new(conn) })
    }

    fn migrate(conn: &Connection) -> Result<()> {
        let version: i64 = conn.pragma_query_value(None, "user_version", |r| r.get(0))?;
        if version < 1 {
            conn.execute_batch(
                "CREATE TABLE IF NOT EXISTS items (
                    id TEXT PRIMARY KEY,
                    kind TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL DEFAULT '',
                    album TEXT,
                    channel TEXT NOT NULL DEFAULT '',
                    duration REAL,
                    source TEXT NOT NULL,
                    status TEXT NOT NULL,
                    error TEXT,
                    file_path TEXT,
                    thumb_path TEXT,
                    thumbnail_url TEXT,
                    file_size INTEGER,
                    width INTEGER,
                    height INTEGER,
                    added_at INTEGER NOT NULL,
                    seen_at INTEGER,
                    downloaded_at INTEGER,
                    play_count INTEGER NOT NULL DEFAULT 0,
                    last_played_at INTEGER,
                    priority INTEGER NOT NULL DEFAULT 0,
                    lyrics TEXT
                );
                CREATE INDEX IF NOT EXISTS items_status ON items(status);
                CREATE TABLE IF NOT EXISTS playlists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS playlist_items (
                    playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
                    item_id TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
                    position INTEGER NOT NULL,
                    PRIMARY KEY (playlist_id, item_id)
                );
                CREATE TABLE IF NOT EXISTS kv (key TEXT PRIMARY KEY, value TEXT NOT NULL);
                PRAGMA user_version = 1;",
            )?;
        }
        Ok(())
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, Connection> {
        self.conn.lock().unwrap_or_else(|e| e.into_inner())
    }

    pub fn get(&self, id: &str) -> Result<Option<Item>> {
        let conn = self.lock();
        Ok(conn
            .query_row(&format!("SELECT {ITEM_COLUMNS} FROM items WHERE id = ?1"), [id], item_from_row)
            .optional()?)
    }

    fn query_items(&self, where_sql: &str) -> Result<Vec<Item>> {
        let conn = self.lock();
        let mut stmt = conn.prepare(&format!("SELECT {ITEM_COLUMNS} FROM items {where_sql}"))?;
        let rows = stmt.query_map([], item_from_row)?.collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(rows)
    }

    /// Downloaded songs and videos, newest first.
    pub fn library(&self) -> Result<Vec<Item>> {
        self.query_items("WHERE status = 'done' ORDER BY downloaded_at DESC")
    }

    /// Everything that is not in the library yet: the download queue and what failed or was
    /// skipped. The most recent first, with what the person asked for on top.
    pub fn pending(&self) -> Result<Vec<Item>> {
        self.query_items(
            "WHERE status IN ('queued', 'downloading', 'failed', 'skipped') \
             ORDER BY status = 'downloading' DESC, priority DESC, COALESCE(seen_at, added_at) DESC",
        )
    }

    /// A video found by the sync. Returns true when it is new. Known videos only get their
    /// "last seen" time moved forward, which orders "heard on YouTube recently".
    pub fn discover(&self, item: &NewItem, seen_at: i64) -> Result<bool> {
        let conn = self.lock();
        let exists: Option<String> =
            conn.query_row("SELECT status FROM items WHERE id = ?1", [&item.id], |r| r.get(0)).optional()?;
        if exists.is_some() {
            conn.execute(
                "UPDATE items SET seen_at = MAX(COALESCE(seen_at, 0), ?2) WHERE id = ?1",
                params![item.id, seen_at],
            )?;
            return Ok(false);
        }
        conn.execute(
            "INSERT INTO items (id, kind, title, artist, channel, duration, source, status, thumbnail_url, added_at, seen_at, priority)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 'queued', ?8, ?9, ?10, 0)",
            params![
                item.id, item.kind, item.title, item.artist, item.channel, item.duration, item.source,
                item.thumbnail, now_ms(), seen_at
            ],
        )?;
        Ok(true)
    }

    /// The person asked for this video: queue it ahead of the sync, even if it was skipped,
    /// failed or removed before.
    pub fn request(&self, item: &NewItem) -> Result<Item> {
        {
            let conn = self.lock();
            let now = now_ms();
            conn.execute(
                "INSERT INTO items (id, kind, title, artist, channel, duration, source, status, thumbnail_url, added_at, seen_at, priority)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 'queued', ?8, ?9, ?9, 10)
                 ON CONFLICT(id) DO UPDATE SET
                    status = CASE WHEN items.status IN ('done', 'downloading') AND items.kind = excluded.kind THEN items.status ELSE 'queued' END,
                    kind = excluded.kind, priority = 10, error = NULL, seen_at = excluded.seen_at",
                params![
                    item.id, item.kind, item.title, item.artist, item.channel, item.duration, item.source,
                    item.thumbnail, now
                ],
            )?;
        }
        self.get(&item.id)?.ok_or_else(|| anyhow::anyhow!("item vanished"))
    }

    /// Claims the next queued item for a worker.
    pub fn claim_next(&self) -> Result<Option<Item>> {
        let conn = self.lock();
        let id: Option<String> = conn
            .query_row(
                "SELECT id FROM items WHERE status = 'queued' ORDER BY priority DESC, COALESCE(seen_at, added_at) DESC LIMIT 1",
                [],
                |r| r.get(0),
            )
            .optional()?;
        let Some(id) = id else { return Ok(None) };
        conn.execute("UPDATE items SET status = 'downloading', error = NULL WHERE id = ?1", [&id])?;
        Ok(conn
            .query_row(&format!("SELECT {ITEM_COLUMNS} FROM items WHERE id = ?1"), [&id], item_from_row)
            .optional()?)
    }

    /// After a crash or quit, half-done downloads go back to the queue.
    pub fn requeue_interrupted(&self) -> Result<usize> {
        Ok(self.lock().execute("UPDATE items SET status = 'queued' WHERE status = 'downloading'", [])?)
    }

    pub fn set_kind(&self, id: &str, kind: &str) -> Result<()> {
        self.lock().execute("UPDATE items SET kind = ?2 WHERE id = ?1", params![id, kind])?;
        Ok(())
    }

    pub fn finish(&self, id: &str, f: &Finished) -> Result<()> {
        self.lock().execute(
            "UPDATE items SET status = 'done', error = NULL, kind = ?2, title = ?3, artist = ?4, album = ?5, channel = ?6,
                duration = ?7, file_path = ?8, thumb_path = ?9, file_size = ?10, width = ?11, height = ?12, downloaded_at = ?13
             WHERE id = ?1",
            params![
                id, f.kind, f.title, f.artist, f.album, f.channel, f.duration, f.file_path, f.thumb_path, f.file_size,
                f.width, f.height, now_ms()
            ],
        )?;
        Ok(())
    }

    pub fn fail(&self, id: &str, error: &str) -> Result<()> {
        self.lock().execute("UPDATE items SET status = 'failed', error = ?2 WHERE id = ?1", params![id, error])?;
        Ok(())
    }

    pub fn skip(&self, id: &str, reason: &str) -> Result<()> {
        self.lock().execute("UPDATE items SET status = 'skipped', error = ?2 WHERE id = ?1", params![id, reason])?;
        Ok(())
    }

    pub fn requeue(&self, id: &str) -> Result<()> {
        self.lock().execute(
            "UPDATE items SET status = 'queued', error = NULL, priority = 10 WHERE id = ?1 AND status IN ('failed', 'skipped', 'downloading')",
            [id],
        )?;
        Ok(())
    }

    /// Removed items stay as a tombstone so the sync does not bring them back.
    pub fn remove(&self, id: &str) -> Result<()> {
        let conn = self.lock();
        conn.execute(
            "UPDATE items SET status = 'removed', file_path = NULL, thumb_path = NULL, error = NULL WHERE id = ?1",
            [id],
        )?;
        conn.execute("DELETE FROM playlist_items WHERE item_id = ?1", [id])?;
        Ok(())
    }

    pub fn clear_failed(&self) -> Result<usize> {
        Ok(self.lock().execute("UPDATE items SET status = 'removed' WHERE status IN ('failed', 'skipped')", [])?)
    }

    pub fn played(&self, id: &str) -> Result<()> {
        self.lock().execute(
            "UPDATE items SET play_count = play_count + 1, last_played_at = ?2 WHERE id = ?1",
            params![id, now_ms()],
        )?;
        Ok(())
    }

    pub fn status_counts(&self) -> Result<Vec<(String, i64)>> {
        let conn = self.lock();
        let mut stmt = conn.prepare("SELECT status, COUNT(*) FROM items GROUP BY status")?;
        let rows = stmt.query_map([], |r| Ok((r.get(0)?, r.get(1)?)))?.collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(rows)
    }

    // Playlists

    pub fn playlists(&self) -> Result<Vec<Playlist>> {
        let conn = self.lock();
        let mut stmt = conn.prepare("SELECT id, name, created_at FROM playlists ORDER BY created_at ASC")?;
        let mut lists: Vec<Playlist> = stmt
            .query_map([], |r| {
                Ok(Playlist { id: r.get(0)?, name: r.get(1)?, created_at: r.get(2)?, item_ids: vec![] })
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        let mut items = conn.prepare(
            "SELECT pi.item_id FROM playlist_items pi JOIN items i ON i.id = pi.item_id
             WHERE pi.playlist_id = ?1 ORDER BY pi.position ASC",
        )?;
        for list in &mut lists {
            list.item_ids = items.query_map([list.id], |r| r.get(0))?.collect::<rusqlite::Result<Vec<String>>>()?;
        }
        Ok(lists)
    }

    pub fn playlist_create(&self, name: &str, item_ids: &[String]) -> Result<i64> {
        let id = {
            let conn = self.lock();
            conn.execute("INSERT INTO playlists (name, created_at) VALUES (?1, ?2)", params![name.trim(), now_ms()])?;
            conn.last_insert_rowid()
        };
        self.playlist_add(id, item_ids)?;
        Ok(id)
    }

    pub fn playlist_rename(&self, id: i64, name: &str) -> Result<()> {
        self.lock().execute("UPDATE playlists SET name = ?2 WHERE id = ?1", params![id, name.trim()])?;
        Ok(())
    }

    pub fn playlist_delete(&self, id: i64) -> Result<()> {
        let conn = self.lock();
        conn.execute("DELETE FROM playlist_items WHERE playlist_id = ?1", [id])?;
        conn.execute("DELETE FROM playlists WHERE id = ?1", [id])?;
        Ok(())
    }

    /// Adds at the end, skipping what is already there.
    pub fn playlist_add(&self, id: i64, item_ids: &[String]) -> Result<()> {
        let mut conn = self.lock();
        let tx = conn.transaction()?;
        let mut next: i64 = tx.query_row(
            "SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlist_id = ?1",
            [id],
            |r| r.get(0),
        )?;
        for item in item_ids {
            let exists: Option<i64> =
                tx.query_row("SELECT 1 FROM items WHERE id = ?1", [item], |r| r.get(0)).optional()?;
            if exists.is_none() {
                continue;
            }
            let added = tx.execute(
                "INSERT OR IGNORE INTO playlist_items (playlist_id, item_id, position) VALUES (?1, ?2, ?3)",
                params![id, item, next],
            )?;
            next += added as i64;
        }
        tx.commit()?;
        Ok(())
    }

    pub fn playlist_remove(&self, id: i64, item_id: &str) -> Result<()> {
        self.lock()
            .execute("DELETE FROM playlist_items WHERE playlist_id = ?1 AND item_id = ?2", params![id, item_id])?;
        Ok(())
    }

    pub fn playlist_reorder(&self, id: i64, item_ids: &[String]) -> Result<()> {
        let mut conn = self.lock();
        let tx = conn.transaction()?;
        for (position, item) in item_ids.iter().enumerate() {
            tx.execute(
                "UPDATE playlist_items SET position = ?3 WHERE playlist_id = ?1 AND item_id = ?2",
                params![id, item, position as i64],
            )?;
        }
        tx.commit()?;
        Ok(())
    }

    // Lyrics cache and key-value store

    pub fn lyrics(&self, id: &str) -> Result<Option<String>> {
        let conn = self.lock();
        Ok(conn.query_row("SELECT lyrics FROM items WHERE id = ?1", [id], |r| r.get(0)).optional()?.flatten())
    }

    pub fn set_lyrics(&self, id: &str, json: &str) -> Result<()> {
        self.lock().execute("UPDATE items SET lyrics = ?2 WHERE id = ?1", params![id, json])?;
        Ok(())
    }

    pub fn kv_get(&self, key: &str) -> Result<Option<String>> {
        let conn = self.lock();
        Ok(conn.query_row("SELECT value FROM kv WHERE key = ?1", [key], |r| r.get(0)).optional()?)
    }

    pub fn kv_set(&self, key: &str, value: &str) -> Result<()> {
        self.lock().execute(
            "INSERT INTO kv (key, value) VALUES (?1, ?2) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            params![key, value],
        )?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn new(id: &str, source: &str) -> NewItem {
        NewItem {
            id: id.into(),
            kind: "auto".into(),
            title: format!("Title {id}"),
            artist: "Artist".into(),
            channel: "Channel".into(),
            duration: Some(200.0),
            thumbnail: None,
            source: source.into(),
        }
    }

    fn finished(path: &str) -> Finished {
        Finished {
            kind: "music".into(),
            title: "T".into(),
            artist: "A".into(),
            file_path: path.into(),
            ..Default::default()
        }
    }

    #[test]
    fn discover_is_idempotent_and_moves_seen_at() {
        let db = Db::in_memory().unwrap();
        assert!(db.discover(&new("aaaaaaaaaaa", "history"), 100).unwrap());
        assert!(!db.discover(&new("aaaaaaaaaaa", "history"), 200).unwrap());
        assert_eq!(db.get("aaaaaaaaaaa").unwrap().unwrap().seen_at, Some(200));
        assert!(!db.discover(&new("aaaaaaaaaaa", "history"), 50).unwrap());
        assert_eq!(db.get("aaaaaaaaaaa").unwrap().unwrap().seen_at, Some(200));
    }

    #[test]
    fn requests_jump_the_queue() {
        let db = Db::in_memory().unwrap();
        db.discover(&new("synced00001", "history"), now_ms()).unwrap();
        db.request(&NewItem { kind: "music".into(), source: "search".into(), ..new("asked000001", "search") }).unwrap();
        assert_eq!(db.claim_next().unwrap().unwrap().id, "asked000001");
        assert_eq!(db.claim_next().unwrap().unwrap().id, "synced00001");
        assert!(db.claim_next().unwrap().is_none());
    }

    #[test]
    fn finished_items_reach_the_library() {
        let db = Db::in_memory().unwrap();
        db.discover(&new("bbbbbbbbbbb", "liked"), 1).unwrap();
        let claimed = db.claim_next().unwrap().unwrap();
        assert_eq!(claimed.status, "downloading");
        db.finish("bbbbbbbbbbb", &finished("/m/b.m4a")).unwrap();
        let lib = db.library().unwrap();
        assert_eq!(lib.len(), 1);
        assert_eq!(lib[0].file_path.as_deref(), Some("/m/b.m4a"));
        assert!(db.pending().unwrap().is_empty());
    }

    #[test]
    fn removed_items_are_not_rediscovered() {
        let db = Db::in_memory().unwrap();
        db.discover(&new("ccccccccccc", "history"), 1).unwrap();
        db.remove("ccccccccccc").unwrap();
        assert!(!db.discover(&new("ccccccccccc", "history"), 2).unwrap());
        assert_eq!(db.get("ccccccccccc").unwrap().unwrap().status, "removed");
        assert!(db.claim_next().unwrap().is_none());
    }

    #[test]
    fn asking_again_brings_a_removed_item_back() {
        let db = Db::in_memory().unwrap();
        db.discover(&new("ddddddddddd", "history"), 1).unwrap();
        db.remove("ddddddddddd").unwrap();
        let item = db.request(&NewItem { kind: "video".into(), ..new("ddddddddddd", "search") }).unwrap();
        assert_eq!(item.status, "queued");
        assert_eq!(item.kind, "video");
    }

    #[test]
    fn interrupted_downloads_requeue() {
        let db = Db::in_memory().unwrap();
        db.discover(&new("eeeeeeeeeee", "history"), 1).unwrap();
        db.claim_next().unwrap();
        assert_eq!(db.requeue_interrupted().unwrap(), 1);
        assert_eq!(db.get("eeeeeeeeeee").unwrap().unwrap().status, "queued");
    }

    #[test]
    fn playlists_keep_order_and_skip_duplicates() {
        let db = Db::in_memory().unwrap();
        for id in ["p0000000001", "p0000000002", "p0000000003"] {
            db.discover(&new(id, "search"), 1).unwrap();
        }
        let ids: Vec<String> = ["p0000000002", "p0000000001"].iter().map(|s| s.to_string()).collect();
        let pid = db.playlist_create("Noite", &ids).unwrap();
        db.playlist_add(pid, &["p0000000001".into(), "p0000000003".into(), "nope0000000".into()]).unwrap();
        let list = &db.playlists().unwrap()[0];
        assert_eq!(list.item_ids, vec!["p0000000002", "p0000000001", "p0000000003"]);
        db.playlist_reorder(pid, &["p0000000003".into(), "p0000000002".into(), "p0000000001".into()]).unwrap();
        assert_eq!(db.playlists().unwrap()[0].item_ids[0], "p0000000003");
        db.remove("p0000000002").unwrap();
        assert_eq!(db.playlists().unwrap()[0].item_ids.len(), 2);
    }

    #[test]
    fn kv_round_trip() {
        let db = Db::in_memory().unwrap();
        assert_eq!(db.kv_get("x").unwrap(), None);
        db.kv_set("x", "1").unwrap();
        db.kv_set("x", "2").unwrap();
        assert_eq!(db.kv_get("x").unwrap().as_deref(), Some("2"));
    }
}
