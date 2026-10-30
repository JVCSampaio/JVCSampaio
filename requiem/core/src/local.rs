//! Songs and videos already on the computer. The folders the person picked are read into the
//! library with their tags and covers. The files stay where they are, and a file removed from
//! the library stays out of it on the next read.

use anyhow::Result;
use lofty::file::{AudioFile, TaggedFileExt};
use lofty::picture::{MimeType, PictureType};
use lofty::tag::Accessor;
use regex::Regex;
use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::LazyLock;
use std::time::UNIX_EPOCH;
use walkdir::WalkDir;

use crate::clean;
use crate::db::{Db, LocalFile};
use crate::media::Kind;

/// What a web view plays: the formats every platform's player opens, or most of them.
const AUDIO: &[&str] = &["mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav"];
const VIDEO: &[&str] = &["mp4", "m4v", "webm", "mov"];

/// Album art sitting next to the songs, as many rippers and stores leave it.
const FOLDER_ART: &[&str] = &["cover", "folder", "front", "album"];

/// "01 - Song", "01. Song", "1 Song": the track number of a file name is not its title.
static TRACK_NUMBER: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"^\d{1,3}(\s*[-._)]\s*|\s+)").unwrap());

/// The `[id]` Requiem writes at the end of the files it downloads.
static DOWNLOAD_ID: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\[([A-Za-z0-9_-]{11})\]$").unwrap());

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub struct ScanReport {
    /// Songs and videos from the folders that are in the library.
    pub found: usize,
    pub added: usize,
    pub updated: usize,
    pub removed: usize,
}

impl ScanReport {
    pub fn changed(&self) -> bool {
        self.added + self.updated + self.removed > 0
    }
}

pub fn kind_of(path: &Path) -> Option<Kind> {
    let ext = path.extension()?.to_str()?.to_ascii_lowercase();
    if AUDIO.contains(&ext.as_str()) {
        Some(Kind::Music)
    } else if VIDEO.contains(&ext.as_str()) {
        Some(Kind::Video)
    } else {
        None
    }
}

/// A stable id made from the path, so a file keeps its plays and playlists between reads.
pub fn local_id(path: &Path) -> String {
    let mut hash: u64 = 0xcbf2_9ce4_8422_2325;
    for byte in path.to_string_lossy().as_bytes() {
        hash ^= u64::from(*byte);
        hash = hash.wrapping_mul(0x0100_0000_01b3);
    }
    format!("local-{hash:016x}")
}

/// Reads the folders into the library: new files are added, changed ones read again, files
/// that left are forgotten. A folder that is missing right now (an unplugged drive) keeps its
/// songs. `progress` hears how many files were found so far.
pub fn scan(db: &Db, folders: &[PathBuf], covers: &Path, mut progress: impl FnMut(usize)) -> Result<ScanReport> {
    let known = db.local_rows()?;
    let (downloaded_ids, downloaded_paths) = db.downloaded()?;
    let mut report = ScanReport::default();
    let mut seen = HashSet::new();
    let mut art = HashMap::new();

    for folder in folders.iter().filter(|f| f.is_dir()) {
        let walker = WalkDir::new(folder)
            .follow_links(false)
            .into_iter()
            .filter_entry(|e| e.depth() == 0 || !e.file_name().to_string_lossy().starts_with('.'));
        for entry in walker.filter_map(|e| e.ok()) {
            if !entry.file_type().is_file() {
                continue;
            }
            let path = entry.path();
            let Some(kind) = kind_of(path) else { continue };
            if downloaded_paths.contains(path.to_string_lossy().as_ref()) || is_download(path, &downloaded_ids) {
                continue;
            }
            let id = local_id(path);
            if !seen.insert(id.clone()) {
                continue;
            }
            let row = known.get(&id);
            if row.is_some_and(|r| r.status == "removed") {
                continue;
            }
            report.found += 1;
            if report.found % 100 == 0 {
                progress(report.found);
            }
            let Ok(meta) = entry.metadata() else { continue };
            let size = meta.len() as i64;
            let modified = meta
                .modified()
                .ok()
                .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                .map(|d| d.as_millis() as i64)
                .unwrap_or(0);
            if row.is_some_and(|r| r.file_size == Some(size) && r.modified == Some(modified)) {
                continue;
            }
            let file = read(path, kind, &id, covers, size, modified, &mut art);
            db.put_local(&file)?;
            if row.is_some() {
                report.updated += 1;
            } else {
                report.added += 1;
            }
        }
    }

    let gone: Vec<String> = known
        .values()
        .filter(|r| r.status == "done" && !seen.contains(&r.id))
        .filter(|r| {
            let Some(path) = r.file_path.as_deref().map(Path::new) else { return true };
            match folders.iter().find(|root| path.starts_with(root)) {
                Some(root) => root.is_dir(),
                None => true,
            }
        })
        .map(|r| r.id.clone())
        .collect();
    if !gone.is_empty() {
        db.drop_local(&gone)?;
        for id in &gone {
            for ext in ["jpg", "png", "gif", "bmp", "tif"] {
                let _ = std::fs::remove_file(covers.join(format!("{id}.{ext}")));
            }
        }
        report.removed = gone.len();
    }
    progress(report.found);
    Ok(report)
}

/// A file Requiem downloaded itself, finished or still being written.
fn is_download(path: &Path, ids: &HashSet<String>) -> bool {
    let stem = path.file_stem().map(|s| s.to_string_lossy()).unwrap_or_default();
    DOWNLOAD_ID.captures(&stem).is_some_and(|c| ids.contains(&c[1]))
}

/// Title, artist, album, length and cover from the tags, with the file name as the fallback.
fn read(
    path: &Path,
    kind: Kind,
    id: &str,
    covers: &Path,
    size: i64,
    modified: i64,
    art: &mut HashMap<PathBuf, Option<String>>,
) -> LocalFile {
    let stem = path.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
    let named = clean::clean(&TRACK_NUMBER.replace(&stem, ""), "");
    let mut file = LocalFile {
        id: id.to_string(),
        kind: kind.as_str().into(),
        title: named.title,
        artist: named.artist,
        album: None,
        duration: None,
        file_path: path.to_string_lossy().into_owned(),
        thumb_path: None,
        file_size: size,
        modified,
    };

    if let Ok(tagged) = lofty::read_from_path(path) {
        let seconds = tagged.properties().duration().as_secs_f64();
        if seconds > 0.0 {
            file.duration = Some(seconds);
        }
        if let Some(tag) = tagged.primary_tag().or_else(|| tagged.first_tag()) {
            let text = |value: Option<std::borrow::Cow<'_, str>>| {
                value.map(|v| v.trim().to_string()).filter(|v| !v.is_empty())
            };
            if let Some(title) = text(tag.title()) {
                file.title = title;
            }
            if let Some(artist) = text(tag.artist()) {
                file.artist = artist;
            }
            file.album = text(tag.album());
            let pictures = tag.pictures();
            let picture =
                pictures.iter().find(|p| p.pic_type() == PictureType::CoverFront).or_else(|| pictures.first());
            if let Some(picture) = picture {
                let ext = picture.mime_type().and_then(MimeType::ext).unwrap_or("jpg");
                let target = covers.join(format!("{id}.{ext}"));
                if std::fs::write(&target, picture.data()).is_ok() {
                    file.thumb_path = Some(target.to_string_lossy().into_owned());
                }
            }
        }
    }

    if file.thumb_path.is_none() {
        if let Some(dir) = path.parent() {
            file.thumb_path = art.entry(dir.to_path_buf()).or_insert_with(|| folder_art(dir)).clone();
        }
    }
    if file.title.is_empty() {
        file.title = stem;
    }
    file
}

fn folder_art(dir: &Path) -> Option<String> {
    let entries = std::fs::read_dir(dir).ok()?;
    entries
        .filter_map(|e| e.ok())
        .map(|e| e.path())
        .find(|p| {
            let stem = p.file_stem().map(|s| s.to_string_lossy().to_lowercase()).unwrap_or_default();
            let ext = p.extension().map(|s| s.to_string_lossy().to_lowercase()).unwrap_or_default();
            FOLDER_ART.contains(&stem.as_str()) && (ext == "jpg" || ext == "jpeg" || ext == "png")
        })
        .map(|p| p.to_string_lossy().into_owned())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::NewItem;
    use lofty::config::WriteOptions;
    use lofty::picture::Picture;
    use lofty::tag::{Tag, TagExt, TagType};

    /// One second of silence as a 16-bit mono PCM WAV.
    fn wav(path: &Path) {
        let rate: u32 = 8000;
        let data_len: u32 = rate * 2;
        let mut bytes = Vec::new();
        bytes.extend_from_slice(b"RIFF");
        bytes.extend_from_slice(&(36 + data_len).to_le_bytes());
        bytes.extend_from_slice(b"WAVEfmt ");
        bytes.extend_from_slice(&16u32.to_le_bytes());
        bytes.extend_from_slice(&1u16.to_le_bytes());
        bytes.extend_from_slice(&1u16.to_le_bytes());
        bytes.extend_from_slice(&rate.to_le_bytes());
        bytes.extend_from_slice(&(rate * 2).to_le_bytes());
        bytes.extend_from_slice(&2u16.to_le_bytes());
        bytes.extend_from_slice(&16u16.to_le_bytes());
        bytes.extend_from_slice(b"data");
        bytes.extend_from_slice(&data_len.to_le_bytes());
        bytes.resize(bytes.len() + data_len as usize, 0);
        std::fs::create_dir_all(path.parent().unwrap()).unwrap();
        std::fs::write(path, bytes).unwrap();
    }

    fn tagged_wav(path: &Path, title: &str, artist: &str, album: &str) {
        wav(path);
        let mut tag = Tag::new(TagType::Id3v2);
        tag.set_title(title.into());
        tag.set_artist(artist.into());
        tag.set_album(album.into());
        tag.push_picture(
            Picture::unchecked(b"\x89PNG not really".to_vec())
                .pic_type(PictureType::CoverFront)
                .mime_type(MimeType::Png)
                .build(),
        );
        tag.save_to_path(path, WriteOptions::default()).unwrap();
    }

    struct Setup {
        _dir: tempfile::TempDir,
        music: PathBuf,
        covers: PathBuf,
        db: Db,
    }

    fn setup() -> Setup {
        let dir = tempfile::tempdir().unwrap();
        let music = dir.path().join("Música");
        let covers = dir.path().join("covers");
        std::fs::create_dir_all(&music).unwrap();
        std::fs::create_dir_all(&covers).unwrap();
        Setup { music, covers, db: Db::in_memory().unwrap(), _dir: dir }
    }

    #[test]
    fn tags_names_and_covers() {
        let s = setup();
        tagged_wav(&s.music.join("a.wav"), "Danse Macabre", "Camille Saint-Saëns", "Poemas");
        wav(&s.music.join("Disco/03 - Os Coveiros - O Corvo.wav"));
        std::fs::write(s.music.join("Disco/cover.jpg"), b"jpeg").unwrap();
        std::fs::write(s.music.join("notas.txt"), b"not music").unwrap();
        wav(&s.music.join(".escondida/b.wav"));

        let report = scan(&s.db, std::slice::from_ref(&s.music), &s.covers, |_| {}).unwrap();
        assert_eq!(report, ScanReport { found: 2, added: 2, updated: 0, removed: 0 });

        let library = s.db.library().unwrap();
        let tagged = library.iter().find(|i| i.title == "Danse Macabre").unwrap();
        assert_eq!(tagged.artist, "Camille Saint-Saëns");
        assert_eq!(tagged.album.as_deref(), Some("Poemas"));
        assert_eq!(tagged.source, "local");
        assert_eq!(tagged.kind, "music");
        assert!((tagged.duration.unwrap() - 1.0).abs() < 0.05);
        assert!(tagged.thumb_path.as_deref().unwrap().ends_with(".png"));

        let named = library.iter().find(|i| i.title == "O Corvo").unwrap();
        assert_eq!(named.artist, "Os Coveiros");
        assert!(named.thumb_path.as_deref().unwrap().ends_with("cover.jpg"));
    }

    #[test]
    fn reading_again_keeps_what_did_not_change_and_forgets_what_left() {
        let s = setup();
        wav(&s.music.join("Banda - Um.wav"));
        wav(&s.music.join("Banda - Dois.wav"));
        scan(&s.db, std::slice::from_ref(&s.music), &s.covers, |_| {}).unwrap();

        let again = scan(&s.db, std::slice::from_ref(&s.music), &s.covers, |_| {}).unwrap();
        assert_eq!(again, ScanReport { found: 2, added: 0, updated: 0, removed: 0 });

        std::fs::remove_file(s.music.join("Banda - Dois.wav")).unwrap();
        let after = scan(&s.db, std::slice::from_ref(&s.music), &s.covers, |_| {}).unwrap();
        assert_eq!(after, ScanReport { found: 1, added: 0, updated: 0, removed: 1 });
        assert_eq!(s.db.library().unwrap().len(), 1);
    }

    #[test]
    fn a_removed_file_stays_out_and_an_unplugged_folder_keeps_its_songs() {
        let s = setup();
        let path = s.music.join("Banda - Um.wav");
        wav(&path);
        scan(&s.db, std::slice::from_ref(&s.music), &s.covers, |_| {}).unwrap();
        s.db.remove(&local_id(&path)).unwrap();
        let report = scan(&s.db, std::slice::from_ref(&s.music), &s.covers, |_| {}).unwrap();
        assert_eq!(report.found, 0);
        assert!(s.db.library().unwrap().is_empty());

        let drive = s.music.parent().unwrap().join("Pendrive");
        wav(&drive.join("Banda - Dois.wav"));
        let folders = [s.music.clone(), drive.clone()];
        scan(&s.db, &folders, &s.covers, |_| {}).unwrap();
        std::fs::rename(&drive, drive.with_file_name("Pendrive fora")).unwrap();
        let report = scan(&s.db, &folders, &s.covers, |_| {}).unwrap();
        assert_eq!(report.removed, 0);
        assert_eq!(s.db.library().unwrap().len(), 1);
    }

    #[test]
    fn downloads_are_not_listed_twice() {
        let s = setup();
        let item = NewItem { id: "dQw4w9WgXcQ".into(), kind: "music".into(), title: "x".into(), ..Default::default() };
        s.db.request(&item).unwrap();
        wav(&s.music.join("Rick Astley - Never Gonna Give You Up [dQw4w9WgXcQ].wav"));
        let report = scan(&s.db, std::slice::from_ref(&s.music), &s.covers, |_| {}).unwrap();
        assert_eq!(report.found, 0);
    }

    #[test]
    fn ids_are_stable_and_distinct() {
        let a = local_id(Path::new("/m/a.mp3"));
        assert_eq!(a, local_id(Path::new("/m/a.mp3")));
        assert_ne!(a, local_id(Path::new("/m/b.mp3")));
        assert_eq!(a.len(), 22);
        assert_eq!(kind_of(Path::new("x.MP3")), Some(Kind::Music));
        assert_eq!(kind_of(Path::new("x.webm")), Some(Kind::Video));
        assert_eq!(kind_of(Path::new("x.txt")), None);
    }
}
