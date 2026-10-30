//! Bringing playlists from Spotify. The public embed page of a playlist, album or song carries
//! its track list in the page data (`__NEXT_DATA__`): no account, no API key. Each song is then
//! looked up on YouTube and matched by title, artist and length. Pasted lists ("Artist - Title"
//! lines, or just the words of a song, an Exportify CSV, copied song links) go through the same
//! matching.

use anyhow::{anyhow, Context, Result};
use regex::Regex;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashSet;
use std::sync::LazyLock;

use crate::clean;
use crate::media::Entry;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    Playlist,
    Album,
    Track,
}

impl Kind {
    fn as_str(self) -> &'static str {
        match self {
            Kind::Playlist => "playlist",
            Kind::Album => "album",
            Kind::Track => "track",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Link {
    pub kind: Kind,
    pub id: String,
}

/// A song as Spotify (or a pasted list) names it.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Track {
    pub title: String,
    /// All artists, comma separated; the first one leads the match.
    pub artist: String,
    pub duration: Option<f64>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct List {
    pub title: String,
    pub tracks: Vec<Track>,
}

static LINK: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"(?:open\.spotify\.com/(?:intl-[a-zA-Z-]+/)?(?:embed/)?|spotify:)(playlist|album|track)[/:]([A-Za-z0-9]{22})",
    )
    .unwrap()
});
static SHORT_LINK: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"https?://(?:spotify\.link|spotify\.app\.link)/[A-Za-z0-9]+").unwrap());
static NEXT_DATA: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r#"(?s)<script id="__NEXT_DATA__"[^>]*>(.*?)</script>"#).unwrap());

/// A playlist, album or song link, in any of the forms Spotify shares.
pub fn parse_link(text: &str) -> Option<Link> {
    let c = LINK.captures(text)?;
    let kind = match &c[1] {
        "playlist" => Kind::Playlist,
        "album" => Kind::Album,
        _ => Kind::Track,
    };
    Some(Link { kind, id: c[2].to_string() })
}

/// The app's short share link, which redirects to the full one.
pub fn short_link(text: &str) -> Option<String> {
    SHORT_LINK.find(text).map(|m| m.as_str().to_string())
}

pub fn embed_url(link: &Link) -> String {
    format!("https://open.spotify.com/embed/{}/{}", link.kind.as_str(), link.id)
}

fn text(v: &Value, keys: &[&str]) -> Option<String> {
    keys.iter().find_map(|k| v.get(*k)?.as_str()).map(str::trim).filter(|s| !s.is_empty()).map(String::from)
}

fn artists(v: &Value) -> Option<String> {
    text(v, &["subtitle", "artist"]).or_else(|| {
        let names: Vec<&str> =
            v.get("artists")?.as_array()?.iter().filter_map(|a| a.get("name").and_then(Value::as_str)).collect();
        (!names.is_empty()).then(|| names.join(", "))
    })
}

fn track(v: &Value) -> Option<Track> {
    let title = text(v, &["title", "name"])?;
    let artist = artists(v).unwrap_or_default();
    let ms = v.get("duration").or_else(|| v.get("duration_ms")).and_then(Value::as_f64);
    Some(Track { title, artist, duration: ms.filter(|ms| *ms > 0.0).map(|ms| ms / 1000.0) })
}

/// The first object holding a `trackList`, wherever the page keeps it.
fn find_list(v: &Value) -> Option<&Value> {
    match v {
        Value::Object(map) => {
            if map.get("trackList").is_some_and(Value::is_array) {
                return Some(v);
            }
            map.values().find_map(find_list)
        }
        Value::Array(items) => items.iter().find_map(find_list),
        _ => None,
    }
}

/// The page's entity, for a single song (its embed has no track list).
fn find_entity(v: &Value) -> Option<&Value> {
    match v {
        Value::Object(map) => {
            if map.get("type").and_then(Value::as_str) == Some("track") && text(v, &["title", "name"]).is_some() {
                return Some(v);
            }
            map.values().find_map(find_entity)
        }
        Value::Array(items) => items.iter().find_map(find_entity),
        _ => None,
    }
}

/// Reads the track list out of an embed page.
pub fn parse_embed(html: &str) -> Result<List> {
    let raw =
        NEXT_DATA.captures(html).ok_or_else(|| anyhow!("O Spotify mudou a página e o Requiem não achou a lista."))?;
    let data: Value = serde_json::from_str(&raw[1]).context("dados da página do Spotify")?;
    if let Some(list) = find_list(&data) {
        let tracks: Vec<Track> = list["trackList"].as_array().into_iter().flatten().filter_map(track).collect();
        let title = text(list, &["name", "title"]).unwrap_or_else(|| "Do Spotify".into());
        return Ok(List { title, tracks });
    }
    let single = find_entity(&data).and_then(track).ok_or_else(|| anyhow!("O Spotify não mostrou nenhuma música."))?;
    Ok(List { title: single.title.clone(), tracks: vec![single] })
}

/// Fetches a list from its public embed page, following the app's short links first.
pub async fn fetch(http: &reqwest::Client, text: &str) -> Result<List> {
    let link = match parse_link(text) {
        Some(link) => link,
        None => {
            let short = short_link(text).ok_or_else(|| anyhow!("Isso não parece um link do Spotify."))?;
            let landed = browser(http.get(&short)).send().await?.url().to_string();
            parse_link(&landed)
                .ok_or_else(|| anyhow!("O link do Spotify não levou a uma playlist, álbum ou música."))?
        }
    };
    let response = browser(http.get(embed_url(&link))).send().await?;
    if response.status() == reqwest::StatusCode::NOT_FOUND {
        return Err(anyhow!("O Spotify não achou essa lista. Ela pode ser privada."));
    }
    parse_embed(&response.error_for_status()?.text().await?)
}

fn browser(request: reqwest::RequestBuilder) -> reqwest::RequestBuilder {
    request
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36",
        )
        .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
}

// Pasted lists

/// One line of a pasted list: a song by name, or a Spotify link to read.
#[derive(Debug, Clone, PartialEq)]
pub enum Pasted {
    Song(Track),
    Link(String),
}

/// Lines of "Artist - Title", Spotify song links, or an Exportify CSV (with its header).
pub fn parse_pasted(text: &str) -> Vec<Pasted> {
    let lines: Vec<&str> = text.lines().map(str::trim).filter(|l| !l.is_empty()).collect();
    if let Some(header) = lines.first().map(|h| csv_fields(h)) {
        let column = |names: &[&str]| header.iter().position(|h| names.iter().any(|n| h.eq_ignore_ascii_case(n)));
        if let (Some(title), Some(artist)) =
            (column(&["Track Name", "Nome da faixa"]), column(&["Artist Name(s)", "Artist Name"]))
        {
            let ms = column(&["Duration (ms)", "Track Duration (ms)"]);
            return lines[1..]
                .iter()
                .map(|l| csv_fields(l))
                .filter_map(|f| {
                    let title = f.get(title)?.trim().to_string();
                    let artist = f.get(artist).map(|a| a.trim().replace(';', ", ")).unwrap_or_default();
                    let duration = ms.and_then(|i| f.get(i)?.trim().parse::<f64>().ok()).map(|ms| ms / 1000.0);
                    (!title.is_empty()).then_some(Pasted::Song(Track { title, artist, duration }))
                })
                .collect();
        }
    }
    lines
        .into_iter()
        .filter_map(|line| {
            if parse_link(line).is_some() || short_link(line).is_some() {
                return Some(Pasted::Link(line.to_string()));
            }
            let line = line.trim_start_matches(|c: char| c.is_ascii_digit() || matches!(c, '.' | ')' | '-' | ' '));
            let (artist, title) = line.split_once(" - ").or_else(|| line.split_once(" – ")).unwrap_or(("", line));
            let title = title.trim();
            (!title.is_empty())
                .then(|| Pasted::Song(Track { title: title.into(), artist: artist.trim().into(), duration: None }))
        })
        .collect()
}

/// Fields of one CSV line, quotes respected.
fn csv_fields(line: &str) -> Vec<String> {
    let mut fields = vec![String::new()];
    let mut quoted = false;
    let mut chars = line.chars().peekable();
    while let Some(c) = chars.next() {
        match c {
            '"' if quoted && chars.peek() == Some(&'"') => {
                chars.next();
                fields.last_mut().unwrap().push('"');
            }
            '"' => quoted = !quoted,
            ',' if !quoted => fields.push(String::new()),
            _ => fields.last_mut().unwrap().push(c),
        }
    }
    fields
}

// Matching a song to a YouTube result

/// Versions nobody asks for by accident: a match that has one of these words and a Spotify
/// title that does not loses points.
const VERSIONS: &[&str] = &[
    "live",
    "aovivo",
    "cover",
    "karaoke",
    "instrumental",
    "remix",
    "slowed",
    "spedup",
    "nightcore",
    "8d",
    "reverb",
    "acoustic",
    "acustico",
    "lyrics",
    "letra",
    "legendado",
    "traducao",
];
/// Words a YouTube title adds around a song.
const NOISE: &[&str] = &[
    "official",
    "oficial",
    "video",
    "audio",
    "clipe",
    "hd",
    "4k",
    "hq",
    "remastered",
    "remaster",
    "music",
    "musicvideo",
    "visualizer",
];

fn words(text: &str) -> Vec<String> {
    text.split(|c: char| !c.is_alphanumeric()).map(clean::key).filter(|w| !w.is_empty()).collect()
}

/// The words of a title, without "(feat. ...)", " - Remastered 2011" and the like.
fn title_words(title: &str) -> HashSet<String> {
    let cut = title.split(" - ").next().unwrap_or(title);
    let cut = cut.split(['(', '[']).next().unwrap_or(cut);
    words(cut).into_iter().filter(|w| !NOISE.contains(&w.as_str())).collect()
}

/// How well a YouTube result matches a song, from 0 to 1: the title (0.45), the artist (0.25)
/// and the length (0.30), less points for a live take, a cover or a remix nobody asked for.
pub fn score(track: &Track, candidate: &Entry) -> f64 {
    let wanted = title_words(&track.title);
    let found: HashSet<String> = words(&format!("{} {}", candidate.title, candidate.artist)).into_iter().collect();
    let title = if wanted.is_empty() {
        0.0
    } else {
        wanted.iter().filter(|w| found.contains(*w)).count() as f64 / wanted.len() as f64
    };

    let lead = track.artist.split(',').next().unwrap_or("").trim();
    let lead_words = words(lead);
    let around: HashSet<String> =
        words(&format!("{} {} {}", candidate.artist, candidate.channel, candidate.title)).into_iter().collect();
    let artist = if lead_words.is_empty() {
        0.5
    } else {
        lead_words.iter().filter(|w| around.contains(*w)).count() as f64 / lead_words.len() as f64
    };

    let length = match (track.duration, candidate.duration) {
        (Some(a), Some(b)) => (1.0 - ((a - b).abs() - 3.0).max(0.0) / 27.0).clamp(0.0, 1.0),
        _ => 0.5,
    };

    let asked: HashSet<String> = words(&track.title).into_iter().collect();
    let all: Vec<String> = words(&candidate.title);
    let joined = all.join("");
    let extras = VERSIONS
        .iter()
        .filter(|v| !asked.contains(**v) && (all.iter().any(|w| w == *v) || (v.len() > 4 && joined.contains(*v))))
        .count();
    let short = if candidate.is_short { 0.4 } else { 0.0 };
    (0.45 * title + 0.25 * artist + 0.30 * length - 0.25 * extras.min(2) as f64 - short).max(0.0)
}

/// Below this, a song stays out rather than bring the wrong one.
pub const ACCEPT: f64 = 0.6;

/// The best YouTube result for a song, when it is good enough.
pub fn best<'a>(track: &Track, candidates: &'a [Entry]) -> Option<&'a Entry> {
    candidates
        .iter()
        .map(|c| (c, score(track, c)))
        .filter(|(_, s)| *s >= ACCEPT)
        .max_by(|a, b| a.1.total_cmp(&b.1))
        .map(|(c, _)| c)
}

/// The same song in the library, whatever decorations its title carries: the lead artist and the
/// words of the title, folded.
pub fn key_for(artist: &str, title: &str) -> String {
    let lead = artist.split(',').next().unwrap_or("").trim();
    let mut title: Vec<String> = title_words(title).into_iter().collect();
    title.sort();
    format!("{}|{}", clean::key(lead), title.join(" "))
}

/// What to type in the YouTube search for a song.
pub fn query(track: &Track) -> String {
    let lead = track.artist.split(',').next().unwrap_or("").trim();
    if lead.is_empty() {
        track.title.clone()
    } else {
        format!("{lead} - {}", track.title)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn links_in_every_form() {
        let id = "37i9dQZF1DXcBWIGoYBM5M";
        for text in [
            format!("https://open.spotify.com/playlist/{id}?si=abc123"),
            format!("https://open.spotify.com/intl-pt/playlist/{id}"),
            format!("https://open.spotify.com/embed/playlist/{id}?utm_source=generator"),
            format!("spotify:playlist:{id}"),
            format!("olha essa: https://open.spotify.com/playlist/{id} muito boa"),
        ] {
            assert_eq!(parse_link(&text), Some(Link { kind: Kind::Playlist, id: id.into() }), "{text}");
        }
        assert_eq!(parse_link("https://open.spotify.com/album/4LH4d3cOWNNsVw41Gqt2kv").unwrap().kind, Kind::Album);
        assert_eq!(parse_link("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC").unwrap().kind, Kind::Track);
        assert_eq!(parse_link("https://open.spotify.com/artist/0OdUWJ0sBjDrqHygGUXeCF"), None);
        assert_eq!(short_link("https://spotify.link/AbCdEf123").as_deref(), Some("https://spotify.link/AbCdEf123"));
        assert_eq!(
            embed_url(&parse_link(&format!("spotify:album:{id}")).unwrap()),
            format!("https://open.spotify.com/embed/album/{id}")
        );
    }

    fn page(data: &str) -> String {
        format!(
            r#"<html><head></head><body><div id="__next"></div><script id="__NEXT_DATA__" type="application/json">{data}</script></body></html>"#
        )
    }

    #[test]
    fn reads_a_playlist_embed() {
        let html = page(
            r#"{"props":{"pageProps":{"state":{"data":{"entity":{"type":"playlist","name":"Noite de Halloween","title":"Noite de Halloween",
            "trackList":[
              {"uri":"spotify:track:1","title":"Danse Macabre, Op. 40","subtitle":"Camille Saint-Saëns, Orchestre National","duration":431000},
              {"uri":"spotify:track:2","title":"Thriller","subtitle":"Michael Jackson","duration":357266},
              {"uri":"spotify:track:3","subtitle":"sem título"}
            ]}}}}}}"#,
        );
        let list = parse_embed(&html).unwrap();
        assert_eq!(list.title, "Noite de Halloween");
        assert_eq!(list.tracks.len(), 2);
        assert_eq!(
            list.tracks[0],
            Track {
                title: "Danse Macabre, Op. 40".into(),
                artist: "Camille Saint-Saëns, Orchestre National".into(),
                duration: Some(431.0)
            }
        );
    }

    #[test]
    fn reads_a_song_embed_and_refuses_a_page_without_data() {
        let html = page(
            r#"{"props":{"pageProps":{"state":{"data":{"entity":{"type":"track","name":"Thriller","artists":[{"name":"Michael Jackson"}],"duration":357266}}}}}}"#,
        );
        let list = parse_embed(&html).unwrap();
        assert_eq!(
            list.tracks,
            vec![Track { title: "Thriller".into(), artist: "Michael Jackson".into(), duration: Some(357.266) }]
        );
        assert!(parse_embed("<html>nothing here</html>").is_err());
    }

    #[test]
    fn pasted_lines_links_and_exportify() {
        let lines = "1. Michael Jackson - Thriller\nSaint-Saëns – Danse Macabre\n\nhttps://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC\nSó o título";
        let pasted = parse_pasted(lines);
        assert_eq!(pasted.len(), 4);
        assert_eq!(
            pasted[0],
            Pasted::Song(Track { title: "Thriller".into(), artist: "Michael Jackson".into(), duration: None })
        );
        assert_eq!(
            pasted[1],
            Pasted::Song(Track { title: "Danse Macabre".into(), artist: "Saint-Saëns".into(), duration: None })
        );
        assert!(matches!(&pasted[2], Pasted::Link(l) if l.contains("4uLU6hMCjMI75M1A2tKUQC")));
        assert_eq!(pasted[3], Pasted::Song(Track { title: "Só o título".into(), artist: "".into(), duration: None }));

        let csv = "Track URI,Track Name,Artist Name(s),Album Name,Duration (ms)\n\
                   spotify:track:1,\"Danse Macabre, Op. 40\",Camille Saint-Saëns;Orchestre National,Poemas,431000\n\
                   spotify:track:2,Thriller,Michael Jackson,Thriller,357266";
        let pasted = parse_pasted(csv);
        assert_eq!(
            pasted,
            vec![
                Pasted::Song(Track {
                    title: "Danse Macabre, Op. 40".into(),
                    artist: "Camille Saint-Saëns, Orchestre National".into(),
                    duration: Some(431.0)
                }),
                Pasted::Song(Track {
                    title: "Thriller".into(),
                    artist: "Michael Jackson".into(),
                    duration: Some(357.266)
                }),
            ]
        );
    }

    fn entry(id: &str, title: &str, artist: &str, channel: &str, duration: f64) -> Entry {
        Entry {
            id: id.into(),
            title: title.into(),
            artist: artist.into(),
            channel: channel.into(),
            duration: Some(duration),
            thumbnail: String::new(),
            is_short: duration <= 60.0,
        }
    }

    /// The match table: what each candidate scores for a song, and which one wins.
    #[test]
    fn picks_the_studio_song_over_live_covers_and_remixes() {
        let song = Track { title: "Thriller".into(), artist: "Michael Jackson".into(), duration: Some(357.0) };
        let candidates = vec![
            entry("live0000001", "Thriller (Live in Bucharest)", "Michael Jackson", "Michael Jackson", 420.0),
            entry("cover000001", "Thriller", "Pentatonix", "PTXofficial", 250.0),
            entry("remix000001", "Thriller (Steve Aoki Remix)", "Michael Jackson", "Steve Aoki", 300.0),
            entry("short000001", "Thriller", "Michael Jackson", "Fan", 40.0),
            entry("studio00001", "Thriller", "Michael Jackson", "Michael Jackson", 358.0),
            entry("lyrics00001", "Thriller (Lyrics)", "Michael Jackson", "Lyrics Channel", 357.0),
        ];
        let scores: Vec<(String, f64)> = candidates.iter().map(|c| (c.id.clone(), score(&song, c))).collect();
        eprintln!("{scores:#?}");
        assert_eq!(best(&song, &candidates).unwrap().id, "studio00001");
        assert!(score(&song, &candidates[0]) < ACCEPT, "a live take is not the song");
        assert!(score(&song, &candidates[1]) < ACCEPT, "a cover by someone else is not the song");
        assert!(score(&song, &candidates[2]) < ACCEPT, "a remix is not the song");
        assert!(score(&song, &candidates[3]) < ACCEPT, "a Short is not the song");

        // Asked for the live take, the live take wins.
        let live = Track { title: "Thriller - Live".into(), ..song.clone() };
        assert!(score(&live, &candidates[0]) > score(&live, &candidates[2]));

        // Nothing close enough: nothing is brought.
        let other = Track { title: "Bad".into(), artist: "Michael Jackson".into(), duration: Some(247.0) };
        assert_eq!(best(&other, &candidates[..3]), None);
    }

    /// The page as Spotify serves it today, read on a machine that can reach it (the CI online
    /// job): `cargo test -- --ignored online_spotify`.
    #[tokio::test]
    #[ignore]
    async fn online_spotify_embed() {
        let http = reqwest::Client::new();
        for link in [
            "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            "https://open.spotify.com/album/4LH4d3cOWNNsVw41Gqt2kv",
        ] {
            let list = fetch(&http, link).await.unwrap();
            eprintln!("{} ({} songs)", list.title, list.tracks.len());
            for t in list.tracks.iter().take(5) {
                eprintln!("  {} | {} | {:?}", t.artist, t.title, t.duration);
            }
            assert!(list.tracks.len() >= 5, "{link}: {} songs", list.tracks.len());
            assert!(list.tracks.iter().all(|t| !t.title.is_empty() && !t.artist.is_empty()));
        }
    }

    /// A line with no separator is searched as it is: its words must all be in the result.
    #[test]
    fn a_line_of_plain_words_still_finds_its_song() {
        let pasted = parse_pasted("Saint-Saëns Danse Macabre");
        let Pasted::Song(song) = &pasted[0] else { panic!("{pasted:?}") };
        assert_eq!((song.title.as_str(), song.artist.as_str()), ("Saint-Saëns Danse Macabre", ""));
        let studio = entry("dmacabre001", "Danse Macabre", "Camille Saint-Saëns", "Camille Saint-Saëns", 431.0);
        let live = entry("dmlive00001", "Danse Macabre (Live)", "Camille Saint-Saëns", "Orquestra", 445.0);
        assert_eq!(best(song, &[live.clone(), studio.clone()]).unwrap().id, "dmacabre001");
        assert_eq!(query(song), "Saint-Saëns Danse Macabre");
    }

    #[test]
    fn titles_lose_their_decorations() {
        let song = Track {
            title: "Bohemian Rhapsody - Remastered 2011".into(),
            artist: "Queen".into(),
            duration: Some(355.0),
        };
        let video =
            entry("bohemian001", "Bohemian Rhapsody (Official Video Remastered)", "Queen", "Queen Official", 359.0);
        assert!(score(&song, &video) > 0.9, "{}", score(&song, &video));
        assert_eq!(query(&song), "Queen - Bohemian Rhapsody - Remastered 2011");
        assert_eq!(
            key_for("Queen, David Bowie", "Bohemian Rhapsody - Remastered 2011"),
            key_for("Queen", "Bohemian Rhapsody")
        );
        assert_eq!(query(&Track { title: "Só".into(), artist: "".into(), duration: None }), "Só");
    }
}
