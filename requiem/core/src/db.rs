//! The library database: every video Requiem knows about (downloaded or waiting), playlists,
//! lyrics cache and settings.

use anyhow::Result;
use rusqlite::{params, Connection, OptionalExtension, Row};
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
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
    pub liked_at: Option<i64>,
    pub track_number: Option<i64>,
    pub disc_number: Option<i64>,
    pub year: Option<i64>,
    /// Integrated loudness in LUFS and sample peak in dBFS, for even volume between songs.
    pub loudness: Option<f64>,
    pub peak: Option<f64>,
    /// Where a long item (a podcast, a lecture) was left, in seconds.
    pub resume_at: Option<f64>,
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
    pub track_number: Option<i64>,
    pub disc_number: Option<i64>,
    pub year: Option<i64>,
}

/// A song or video already on the computer, found in one of the person's folders.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct LocalFile {
    pub id: String,
    pub kind: String,
    pub title: String,
    pub artist: String,
    pub album: Option<String>,
    pub duration: Option<f64>,
    pub file_path: String,
    pub thumb_path: Option<String>,
    pub file_size: i64,
    /// The file's modification time, in ms. With the size, it tells a changed file.
    pub modified: i64,
    pub track_number: Option<i64>,
    pub disc_number: Option<i64>,
    pub year: Option<i64>,
}

/// One time a song or video was played past the counting point.
#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Play {
    pub id: i64,
    pub item_id: String,
    pub played_at: i64,
    pub seconds: f64,
}

/// What the person listened to since a moment: the Retrospectiva.
#[derive(Debug, Clone, Default, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Stats {
    pub plays: i64,
    pub seconds: f64,
    pub songs: i64,
    pub artists: i64,
    /// Item id, plays, seconds.
    pub top_items: Vec<(String, i64, f64)>,
    /// Artist, plays, seconds.
    pub top_artists: Vec<(String, i64, f64)>,
    /// The hour of the day (0-23, local time) with the most listening.
    pub top_hour: Option<i64>,
}

/// What the library knows about a local file, removed ones included.
#[derive(Debug, Clone, PartialEq)]
pub struct LocalRow {
    pub id: String,
    pub status: String,
    pub file_path: Option<String>,
    pub file_size: Option<i64>,
    pub modified: Option<i64>,
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
    thumb_path, thumbnail_url, file_size, width, height, added_at, seen_at, downloaded_at, play_count, last_played_at, \
    liked_at, track_number, disc_number, year, loudness, peak, resume_at";

fn item_from_row(r: &Row) -> rusqlite::Result<Item> {
    Ok(Item {
        id: r.get("id")?,
        kind: r.get("kind")?,
        title: r.get("title")?,
        artist: r.get("artist")?,
        album: r.get("album")?,
        channel: r.get("channel")?,
        duration: r.get("duration")?,
        source: r.get("source")?,
        status: r.get("status")?,
        error: r.get("error")?,
        file_path: r.get("file_path")?,
        thumb_path: r.get("thumb_path")?,
        thumbnail_url: r.get("thumbnail_url")?,
        file_size: r.get("file_size")?,
        width: r.get("width")?,
        height: r.get("height")?,
        added_at: r.get("added_at")?,
        seen_at: r.get("seen_at")?,
        downloaded_at: r.get("downloaded_at")?,
        play_count: r.get("play_count")?,
        last_played_at: r.get("last_played_at")?,
        liked_at: r.get("liked_at")?,
        track_number: r.get("track_number")?,
        disc_number: r.get("disc_number")?,
        year: r.get("year")?,
        loudness: r.get("loudness")?,
        peak: r.get("peak")?,
        resume_at: r.get("resume_at")?,
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
        conn.pragma_update(None, "foreign_keys", "ON")?;
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
        if version < 2 {
            // Likes, album order, even volume and resume; a log of plays for the history and the
            // Retrospectiva. Local files are read once more to pick up track numbers and years.
            conn.execute_batch(
                "BEGIN;
                ALTER TABLE items ADD COLUMN liked_at INTEGER;
                ALTER TABLE items ADD COLUMN track_number INTEGER;
                ALTER TABLE items ADD COLUMN disc_number INTEGER;
                ALTER TABLE items ADD COLUMN year INTEGER;
                ALTER TABLE items ADD COLUMN loudness REAL;
                ALTER TABLE items ADD COLUMN peak REAL;
                ALTER TABLE items ADD COLUMN loudness_source TEXT;
                ALTER TABLE items ADD COLUMN analyzed_at INTEGER;
                ALTER TABLE items ADD COLUMN resume_at REAL;
                CREATE TABLE plays (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    item_id TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
                    played_at INTEGER NOT NULL,
                    seconds REAL NOT NULL DEFAULT 0
                );
                CREATE INDEX plays_item ON plays(item_id, played_at);
                CREATE INDEX plays_time ON plays(played_at);
                UPDATE items SET file_size = NULL WHERE source = 'local';
                PRAGMA user_version = 2;
                COMMIT;",
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
                duration = ?7, file_path = ?8, thumb_path = ?9, file_size = ?10, width = ?11, height = ?12, downloaded_at = ?13,
                track_number = ?14, disc_number = ?15, year = ?16, loudness = NULL, peak = NULL, loudness_source = NULL,
                analyzed_at = NULL
             WHERE id = ?1",
            params![
                id,
                f.kind,
                f.title,
                f.artist,
                f.album,
                f.channel,
                f.duration,
                f.file_path,
                f.thumb_path,
                f.file_size,
                f.width,
                f.height,
                now_ms(),
                f.track_number,
                f.disc_number,
                f.year
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

    // Files already on the computer

    /// Local rows by id. A removed one keeps its file out of the library.
    pub fn local_rows(&self) -> Result<HashMap<String, LocalRow>> {
        let conn = self.lock();
        let mut stmt =
            conn.prepare("SELECT id, status, file_path, file_size, downloaded_at FROM items WHERE source = 'local'")?;
        let rows = stmt
            .query_map([], |r| {
                Ok(LocalRow {
                    id: r.get(0)?,
                    status: r.get(1)?,
                    file_path: r.get(2)?,
                    file_size: r.get(3)?,
                    modified: r.get(4)?,
                })
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(rows.into_iter().map(|r| (r.id.clone(), r)).collect())
    }

    /// Ids and files of everything the downloads brought, so a folder scan does not list a
    /// downloaded song a second time, finished or still being written.
    pub fn downloaded(&self) -> Result<(HashSet<String>, HashSet<String>)> {
        let conn = self.lock();
        let mut stmt = conn.prepare("SELECT id, file_path FROM items WHERE source <> 'local'")?;
        let mut ids = HashSet::new();
        let mut paths = HashSet::new();
        let rows = stmt.query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, Option<String>>(1)?)))?;
        for row in rows {
            let (id, path) = row?;
            ids.insert(id);
            paths.extend(path);
        }
        Ok((ids, paths))
    }

    /// Adds a local file to the library, or refreshes its tags. Never brings back one the
    /// person removed.
    pub fn put_local(&self, f: &LocalFile) -> Result<()> {
        self.lock().execute(
            "INSERT INTO items (id, kind, title, artist, album, channel, duration, source, status, file_path, thumb_path,
                file_size, added_at, seen_at, downloaded_at, track_number, disc_number, year)
             VALUES (?1, ?2, ?3, ?4, ?5, ?4, ?6, 'local', 'done', ?7, ?8, ?9, ?10, ?11, ?11, ?12, ?13, ?14)
             ON CONFLICT(id) DO UPDATE SET
                kind = excluded.kind, title = excluded.title, artist = excluded.artist, album = excluded.album,
                channel = excluded.channel, duration = excluded.duration, file_path = excluded.file_path,
                thumb_path = excluded.thumb_path, file_size = excluded.file_size, downloaded_at = excluded.downloaded_at,
                track_number = excluded.track_number, disc_number = excluded.disc_number, year = excluded.year,
                loudness = NULL, peak = NULL, loudness_source = NULL, analyzed_at = NULL
             WHERE items.status = 'done' AND items.source = 'local'",
            params![
                f.id,
                f.kind,
                f.title,
                f.artist,
                f.album,
                f.duration,
                f.file_path,
                f.thumb_path,
                f.file_size,
                now_ms(),
                f.modified,
                f.track_number,
                f.disc_number,
                f.year
            ],
        )?;
        Ok(())
    }

    /// Forgets local files that left the disk or the chosen folders.
    pub fn drop_local(&self, ids: &[String]) -> Result<()> {
        let mut conn = self.lock();
        let tx = conn.transaction()?;
        for id in ids {
            tx.execute("DELETE FROM playlist_items WHERE item_id = ?1", [id])?;
            tx.execute("DELETE FROM items WHERE id = ?1 AND source = 'local'", [id])?;
        }
        tx.commit()?;
        Ok(())
    }

    /// Counts a play and opens its row in the log; the player reports the seconds later.
    pub fn played(&self, id: &str) -> Result<i64> {
        let conn = self.lock();
        let now = now_ms();
        conn.execute(
            "UPDATE items SET play_count = play_count + 1, last_played_at = ?2 WHERE id = ?1",
            params![id, now],
        )?;
        conn.execute("INSERT INTO plays (item_id, played_at, seconds) VALUES (?1, ?2, 0)", params![id, now])?;
        Ok(conn.last_insert_rowid())
    }

    /// How long a play lasted, in seconds actually listened (seeks do not count).
    pub fn play_seconds(&self, play_id: i64, seconds: f64) -> Result<()> {
        self.lock().execute("UPDATE plays SET seconds = MAX(seconds, ?2) WHERE id = ?1", params![play_id, seconds])?;
        Ok(())
    }

    pub fn like(&self, id: &str, liked: bool) -> Result<()> {
        self.lock().execute(
            "UPDATE items SET liked_at = CASE WHEN ?2 THEN COALESCE(liked_at, ?3) ELSE NULL END WHERE id = ?1",
            params![id, liked, now_ms()],
        )?;
        Ok(())
    }

    pub fn set_resume(&self, id: &str, seconds: Option<f64>) -> Result<()> {
        self.lock().execute("UPDATE items SET resume_at = ?2 WHERE id = ?1", params![id, seconds])?;
        Ok(())
    }

    /// The person's own names for a song: they win over tags and YouTube until edited again.
    pub fn edit_info(&self, id: &str, title: &str, artist: &str, album: Option<&str>) -> Result<()> {
        let album = album.map(str::trim).filter(|a| !a.is_empty());
        self.lock().execute(
            "UPDATE items SET title = ?2, artist = ?3, album = ?4 WHERE id = ?1",
            params![id, title.trim(), artist.trim(), album],
        )?;
        Ok(())
    }

    /// The most recent plays, newest first.
    pub fn history(&self, limit: i64) -> Result<Vec<Play>> {
        let conn = self.lock();
        let mut stmt = conn.prepare(
            "SELECT p.id, p.item_id, p.played_at, p.seconds FROM plays p JOIN items i ON i.id = p.item_id
             WHERE i.status = 'done' ORDER BY p.played_at DESC LIMIT ?1",
        )?;
        let rows = stmt
            .query_map([limit], |r| {
                Ok(Play { id: r.get(0)?, item_id: r.get(1)?, played_at: r.get(2)?, seconds: r.get(3)? })
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(rows)
    }

    /// Plays since `since` (ms): totals, the top songs and artists, the favorite hour.
    pub fn stats(&self, since: i64) -> Result<Stats> {
        let conn = self.lock();
        let (plays, seconds, songs): (i64, f64, i64) = conn.query_row(
            "SELECT COUNT(*), COALESCE(SUM(seconds), 0), COUNT(DISTINCT item_id) FROM plays WHERE played_at >= ?1",
            [since],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )?;
        let top_items = conn
            .prepare(
                "SELECT p.item_id, COUNT(*) AS n, SUM(p.seconds) AS s FROM plays p JOIN items i ON i.id = p.item_id
                 WHERE p.played_at >= ?1 AND i.status = 'done' GROUP BY p.item_id ORDER BY n DESC, s DESC LIMIT 20",
            )?
            .query_map([since], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)))?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        let top_artists: Vec<(String, i64, f64)> = conn
            .prepare(
                "SELECT MIN(i.artist), COUNT(*) AS n, SUM(p.seconds) AS s FROM plays p JOIN items i ON i.id = p.item_id
                 WHERE p.played_at >= ?1 AND i.artist <> '' AND i.kind = 'music'
                 GROUP BY lower(i.artist) ORDER BY n DESC, s DESC LIMIT 10",
            )?
            .query_map([since], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)))?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        let artists: i64 = conn.query_row(
            "SELECT COUNT(DISTINCT lower(i.artist)) FROM plays p JOIN items i ON i.id = p.item_id
             WHERE p.played_at >= ?1 AND i.artist <> '' AND i.kind = 'music'",
            [since],
            |r| r.get(0),
        )?;
        let top_hour: Option<i64> = conn
            .query_row(
                "SELECT CAST(strftime('%H', played_at / 1000, 'unixepoch', 'localtime') AS INTEGER) AS h
                 FROM plays WHERE played_at >= ?1 GROUP BY h ORDER BY SUM(seconds) DESC, COUNT(*) DESC LIMIT 1",
                [since],
                |r| r.get(0),
            )
            .optional()?;
        Ok(Stats { plays, seconds, songs, artists, top_items, top_artists, top_hour })
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

    fn done(db: &Db, id: &str, artist: &str) {
        db.request(&new(id, "search")).unwrap();
        db.claim_next().unwrap();
        db.finish(id, &Finished { artist: artist.into(), ..finished(&format!("/m/{id}.m4a")) }).unwrap();
    }

    /// A database written by version 1 of the app opens, gains the new columns and the play log
    /// once, keeps its rows, and asks the folder scan to read local files again.
    #[test]
    fn migrates_a_version_1_database() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("requiem.db");
        {
            let conn = Connection::open(&path).unwrap();
            conn.execute_batch(
                "CREATE TABLE items (id TEXT PRIMARY KEY, kind TEXT NOT NULL, title TEXT NOT NULL,
                    artist TEXT NOT NULL DEFAULT '', album TEXT, channel TEXT NOT NULL DEFAULT '', duration REAL,
                    source TEXT NOT NULL, status TEXT NOT NULL, error TEXT, file_path TEXT, thumb_path TEXT,
                    thumbnail_url TEXT, file_size INTEGER, width INTEGER, height INTEGER, added_at INTEGER NOT NULL,
                    seen_at INTEGER, downloaded_at INTEGER, play_count INTEGER NOT NULL DEFAULT 0,
                    last_played_at INTEGER, priority INTEGER NOT NULL DEFAULT 0, lyrics TEXT);
                CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, created_at INTEGER NOT NULL);
                CREATE TABLE playlist_items (playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
                    item_id TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE, position INTEGER NOT NULL,
                    PRIMARY KEY (playlist_id, item_id));
                CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT NOT NULL);
                INSERT INTO items (id, kind, title, source, status, file_path, file_size, added_at, play_count)
                    VALUES ('local-1', 'music', 'Velha', 'local', 'done', '/m/a.mp3', 100, 1, 3),
                           ('dQw4w9WgXcQ', 'music', 'Baixada', 'search', 'done', '/m/b.m4a', 200, 1, 1);
                PRAGMA user_version = 1;",
            )
            .unwrap();
        }
        let db = Db::open(&path).unwrap();
        let local = db.get("local-1").unwrap().unwrap();
        assert_eq!((local.title.as_str(), local.play_count, local.file_size), ("Velha", 3, None));
        assert_eq!(db.get("dQw4w9WgXcQ").unwrap().unwrap().file_size, Some(200));
        assert_eq!(local.liked_at, None);
        drop(db);
        let db = Db::open(&path).unwrap();
        let version: i64 = db.lock().pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
        assert_eq!(version, 2);
        assert!(db.played("local-1").unwrap() > 0);
    }

    #[test]
    fn plays_likes_resume_and_edits() {
        let db = Db::in_memory().unwrap();
        done(&db, "aaaaaaaaaaa", "Os Coveiros");
        let play = db.played("aaaaaaaaaaa").unwrap();
        db.play_seconds(play, 120.0).unwrap();
        db.play_seconds(play, 90.0).unwrap();
        let history = db.history(10).unwrap();
        assert_eq!((history.len(), history[0].seconds), (1, 120.0));

        db.like("aaaaaaaaaaa", true).unwrap();
        let first = db.get("aaaaaaaaaaa").unwrap().unwrap().liked_at;
        assert!(first.is_some());
        db.like("aaaaaaaaaaa", true).unwrap();
        assert_eq!(db.get("aaaaaaaaaaa").unwrap().unwrap().liked_at, first);
        db.like("aaaaaaaaaaa", false).unwrap();
        assert_eq!(db.get("aaaaaaaaaaa").unwrap().unwrap().liked_at, None);

        db.set_resume("aaaaaaaaaaa", Some(1234.5)).unwrap();
        assert_eq!(db.get("aaaaaaaaaaa").unwrap().unwrap().resume_at, Some(1234.5));

        db.edit_info("aaaaaaaaaaa", " O Corvo ", "Os Coveiros", Some("  ")).unwrap();
        let item = db.get("aaaaaaaaaaa").unwrap().unwrap();
        assert_eq!((item.title.as_str(), item.album), ("O Corvo", None));

        // Forgetting an item for good takes its plays with it.
        db.lock().execute("DELETE FROM items WHERE id = 'aaaaaaaaaaa'", []).unwrap();
        assert!(db.history(10).unwrap().is_empty());
    }

    #[test]
    fn stats_count_time_songs_artists_and_the_favorite_hour() {
        let db = Db::in_memory().unwrap();
        done(&db, "aaaaaaaaaaa", "Os Coveiros");
        done(&db, "bbbbbbbbbbb", "os coveiros");
        done(&db, "ccccccccccc", "Noite Alta");
        for (id, seconds) in
            [("aaaaaaaaaaa", 200.0), ("aaaaaaaaaaa", 180.0), ("bbbbbbbbbbb", 100.0), ("ccccccccccc", 50.0)]
        {
            let play = db.played(id).unwrap();
            db.play_seconds(play, seconds).unwrap();
        }
        let stats = db.stats(0).unwrap();
        assert_eq!((stats.plays, stats.seconds, stats.songs, stats.artists), (4, 530.0, 3, 2));
        assert_eq!(stats.top_items[0], ("aaaaaaaaaaa".to_string(), 2, 380.0));
        assert_eq!(stats.top_artists[0].1, 3);
        assert!(stats.top_hour.is_some());
        assert_eq!(db.stats(now_ms() + 60_000).unwrap(), Stats::default());
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
