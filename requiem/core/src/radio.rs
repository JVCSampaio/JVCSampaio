//! Radio: songs like one the person chose, taken from the mix YouTube builds around that video
//! (`list=RD<id>`). The engine lists the mix; this module decides what is worth queueing.

use std::collections::{HashMap, HashSet};

use crate::clean;
use crate::media::Entry;

/// The mix YouTube builds around a video.
pub fn mix_url(id: &str) -> String {
    format!("https://www.youtube.com/watch?v={id}&list=RD{id}")
}

/// Title and artist folded, so another upload of the same song ("Official Video", "Lyrics",
/// "Audio") counts as the same song.
pub fn song_key(artist: &str, title: &str) -> String {
    format!("{}|{}", clean::key(artist), clean::key(title))
}

/// What a radio can play from a mix: each song once, none of the songs it already played or
/// queued (`played`, by id or by song), no Shorts, nothing longer than `max_seconds`, and nothing
/// the person removed or that failed to download before (`blocked`). Mixes often leave the
/// length out, so an unknown length stays. A song the library already has (`library`, song key
/// to item id) comes with the library's id: it plays from there, with no second copy.
pub fn picks(
    mix: Vec<Entry>,
    played: &HashSet<String>,
    blocked: &HashSet<String>,
    library: &HashMap<String, String>,
    max_seconds: f64,
) -> Vec<Entry> {
    let mut seen = HashSet::new();
    mix.into_iter()
        .filter(|e| !blocked.contains(&e.id))
        .filter(|e| !e.is_short && e.duration.is_none_or(|d| d <= max_seconds))
        .filter_map(|mut e| {
            let key = song_key(&e.artist, &e.title);
            if let Some(id) = library.get(&key) {
                e.id = id.clone();
            }
            (!played.contains(&e.id) && !played.contains(&key) && seen.insert(key)).then_some(e)
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn entry(id: &str, artist: &str, title: &str, duration: Option<f64>) -> Entry {
        Entry {
            id: id.into(),
            title: title.into(),
            artist: artist.into(),
            channel: artist.into(),
            duration,
            thumbnail: String::new(),
            is_short: duration.is_some_and(|d| d <= 60.0),
        }
    }

    #[test]
    fn mix_url_keeps_the_video_and_its_mix() {
        assert_eq!(mix_url("dQw4w9WgXcQ"), "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ");
    }

    #[test]
    fn picks_songs_once_and_leaves_out_what_does_not_belong() {
        let mix = vec![
            entry("seed0000001", "Camille Saint-Saëns", "Danse Macabre", Some(420.0)),
            entry("aaaaaaaaaaa", "Edvard Grieg", "In the Hall of the Mountain King", Some(150.0)),
            entry("bbbbbbbbbbb", "Edvard Grieg", "In The Hall Of The Mountain King", Some(152.0)),
            entry("ccccccccccc", "Fantasma", "Danse em 30 segundos", Some(30.0)),
            entry("ddddddddddd", "Orquestra", "Clássicos de Halloween, 3 horas", Some(3.0 * 3600.0)),
            entry("eeeeeeeeeee", "Modest Mussorgsky", "Night on Bald Mountain", None),
            entry("fffffffffff", "Removida", "Tirada da biblioteca", Some(200.0)),
            entry("ggggggggggg", "Camille Saint-Saëns", "Danse macabre", Some(431.0)),
            entry("aaaaaaaaaaa", "Edvard Grieg", "In the Hall of the Mountain King", Some(150.0)),
        ];
        let played: HashSet<String> =
            ["seed0000001".to_string(), song_key("Camille Saint-Saëns", "Danse Macabre")].into();
        let blocked: HashSet<String> = ["fffffffffff".to_string()].into();
        let ids: Vec<String> =
            picks(mix.clone(), &played, &blocked, &HashMap::new(), 20.0 * 60.0).into_iter().map(|e| e.id).collect();
        assert_eq!(ids, vec!["aaaaaaaaaaa", "eeeeeeeeeee"]);

        // The library's copy of a song is used instead of a download.
        let library: HashMap<String, String> =
            [(song_key("Modest Mussorgsky", "Night on Bald Mountain"), "local-00000000000001".to_string())].into();
        let ids: Vec<String> = picks(mix, &played, &blocked, &library, 20.0 * 60.0).into_iter().map(|e| e.id).collect();
        assert_eq!(ids, vec!["aaaaaaaaaaa", "local-00000000000001"]);
    }
}
