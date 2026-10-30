//! Turns a YouTube video title and channel into a song title and artist:
//! "Artist - Song (Official Video)" on "ArtistVEVO" becomes ("Song", "Artist").
//! Ported from the Sonar Android app, where the same rules are covered by tests.

use regex::Regex;
use std::sync::LazyLock;
use unicode_normalization::char::is_combining_mark;
use unicode_normalization::UnicodeNormalization;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SongInfo {
    pub title: String,
    pub artist: String,
}

const SEPARATORS: [&str; 5] = [" - ", " – ", " — ", " -- ", " ~ "];

/// Words that only describe the video, never the song.
const STRONG_NOISE: &[&str] = &[
    "official",
    "oficial",
    "video",
    "videoclip",
    "videoclipe",
    "clip",
    "clipe",
    "audio",
    "lyric",
    "lyrics",
    "letra",
    "visualizer",
    "visualiser",
    "mv",
    "m/v",
    "legendado",
    "legenda",
    "traducao",
    "hd",
    "hq",
    "4k",
    "8k",
    "1080p",
    "720p",
];

/// Words that are noise only next to strong noise ("Official Music Video").
const WEAK_NOISE: &[&str] =
    &["music", "musical", "musica", "full", "com", "with", "e", "and", "&", "new", "nova", "novo"];

/// A trailing run of noise is removed only when it contains one of these.
const TRAILING_MARKERS: &[&str] = &[
    "official",
    "oficial",
    "clipe",
    "videoclipe",
    "videoclip",
    "lyric",
    "lyrics",
    "letra",
    "visualizer",
    "visualiser",
];

static BRACKET_GROUP: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"\(([^()]*)\)|\[([^\[\]]*)\]|【([^【】]*)】").unwrap());
static HASHTAG: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"(^|\s)#[\p{L}\p{N}_]+").unwrap());
static QUOTED: LazyLock<Regex> = LazyLock::new(|| Regex::new(r#"^(.+?)\s+["“](.+?)["”]\s*$"#).unwrap());
static OFFICIAL_SUFFIX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(?i)\s*\b(official|oficial)\b(\s+(channel|canal))?\s*$").unwrap());
static TRAILING_BRACKETS: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(\s*(\([^()]*\)|\[[^\[\]]*\]))+\s*$").unwrap());
static PIPES: LazyLock<Regex> = LazyLock::new(|| Regex::new(r" (?:\||｜) ").unwrap());
static SPACES: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\s+").unwrap());

pub fn clean(raw_title: &str, channel: &str) -> SongInfo {
    let mut title = remove_noise(raw_title);
    if title.trim().is_empty() {
        title = raw_title.trim().to_string();
    }
    if let Some(artist) = channel.strip_suffix(" - Topic") {
        return SongInfo { title, artist: artist.trim().to_string() };
    }
    let channel_artist = clean_channel(channel);

    let split = SEPARATORS
        .iter()
        .filter_map(|sep| title.find(sep).filter(|&i| i > 0).map(|i| (i, *sep)))
        .min_by_key(|(i, _)| *i);
    if let Some((i, sep)) = split {
        let left = title[..i].trim();
        let right = title[i + sep.len()..].trim();
        if !left.is_empty() && !right.is_empty() {
            let channel_key = key(&channel_artist);
            let swap = channel_key.chars().count() >= 3
                && key(right).contains(&channel_key)
                && !key(left).contains(&channel_key);
            return if swap { move_brackets(right, left) } else { move_brackets(left, right) };
        }
    }

    if let Some(c) = QUOTED.captures(&title) {
        return SongInfo { title: c[2].trim().to_string(), artist: c[1].trim().to_string() };
    }
    SongInfo { title, artist: channel_artist }
}

/// "TaylorSwiftVEVO" becomes "Taylor Swift", "Band Official" becomes "Band".
pub fn clean_channel(channel: &str) -> String {
    let trimmed = channel.trim();
    let mut name = trimmed.strip_suffix(" - Topic").unwrap_or(trimmed).to_string();
    if name.ends_with("VEVO") || name.ends_with("Vevo") {
        name = name[..name.len() - 4].trim().to_string();
        if !name.contains(' ') {
            name = split_camel_case(&name);
        }
    }
    let name = OFFICIAL_SUFFIX.replace(&name, "").trim().to_string();
    if name.is_empty() {
        trimmed.to_string()
    } else {
        name
    }
}

fn split_camel_case(word: &str) -> String {
    let mut out = String::with_capacity(word.len() + 4);
    let mut prev_lower = false;
    for c in word.chars() {
        if prev_lower && c.is_uppercase() {
            out.push(' ');
        }
        prev_lower = c.is_lowercase();
        out.push(c);
    }
    out
}

pub fn remove_noise(raw: &str) -> String {
    let text = HASHTAG.replace_all(raw, " ").to_string();
    let text = BRACKET_GROUP
        .replace_all(&text, |caps: &regex::Captures| {
            let whole = &caps[0];
            let open = whole.chars().next().unwrap_or('(');
            let close = whole.chars().last().unwrap_or(')');
            let inner: String = {
                let chars: Vec<char> = whole.chars().collect();
                chars[1..chars.len() - 1].iter().collect()
            };
            match strip_noise_words(&inner) {
                None => whole.to_string(),
                Some(kept) if kept.is_empty() => String::new(),
                Some(kept) => format!("{open}{kept}{close}"),
            }
        })
        .to_string();
    // "Song | Official Video", "Artist - Song - Clipe Oficial"
    let text = PIPES
        .split(&text)
        .enumerate()
        .filter(|(index, part)| *index == 0 || !is_noise_suffix(part))
        .map(|(_, part)| part)
        .collect::<Vec<_>>()
        .join(" | ");
    let dash_parts: Vec<&str> = text.split(" - ").collect();
    let text = if dash_parts.len() > 1 && is_noise_suffix(dash_parts[dash_parts.len() - 1]) {
        dash_parts[..dash_parts.len() - 1].join(" - ")
    } else {
        text
    };
    let text = remove_trailing_noise(&text);
    SPACES.replace_all(&text, " ").trim().trim_matches(|c| matches!(c, '-' | '|' | '–' | '—' | ' ')).to_string()
}

fn words(text: &str) -> Vec<&str> {
    text.split_whitespace().collect()
}

fn is_strong(word: &str) -> bool {
    STRONG_NOISE.contains(&norm(word).as_str())
}

fn is_weak(word: &str) -> bool {
    WEAK_NOISE.contains(&norm(word).as_str())
}

fn is_marker(word: &str) -> bool {
    TRAILING_MARKERS.contains(&norm(word).as_str())
}

fn is_noise(word: &str) -> bool {
    is_strong(word) || is_weak(word) || is_punctuation(word)
}

/// None when the group has no noise, "" when it is only noise, and otherwise the group
/// without its noise words ("Official Live Video" becomes "Live").
fn strip_noise_words(inner: &str) -> Option<String> {
    let all = words(inner);
    if !all.iter().any(|w| is_strong(w)) {
        return None;
    }
    if all.iter().all(|w| is_noise(w)) {
        return Some(String::new());
    }
    let mut kept: Vec<&str> = all.into_iter().filter(|w| !is_strong(w)).collect();
    while kept.last().is_some_and(|w| is_weak(w) || is_punctuation(w)) {
        kept.pop();
    }
    while kept.first().is_some_and(|w| is_weak(w) || is_punctuation(w)) {
        kept.remove(0);
    }
    Some(kept.join(" "))
}

/// A " - " or " | " segment made only of noise. A lone "Video" or "Audio" is kept, because it
/// can be a song title ("India.Arie - Video").
fn is_noise_suffix(part: &str) -> bool {
    let all = words(part);
    !all.is_empty()
        && all.iter().any(|w| is_strong(w))
        && all.iter().all(|w| is_noise(w))
        && (all.len() > 1 || is_marker(all[0]))
}

fn remove_trailing_noise(text: &str) -> String {
    let all = words(text);
    let mut cut = all.len();
    while cut > 0 && is_noise(all[cut - 1]) {
        cut -= 1;
    }
    let tail = &all[cut..];
    if cut == 0 || !tail.iter().any(|w| is_marker(w)) {
        return text.to_string();
    }
    all[..cut].join(" ")
}

fn move_brackets(artist: &str, title: &str) -> SongInfo {
    if let Some(m) = TRAILING_BRACKETS.find(artist) {
        let trailing = m.as_str().trim();
        if !trailing.is_empty() && trailing.len() != artist.len() {
            return SongInfo { title: format!("{title} {trailing}"), artist: artist[..m.start()].trim().to_string() };
        }
    }
    SongInfo { title: title.to_string(), artist: artist.to_string() }
}

fn is_punctuation(word: &str) -> bool {
    word.chars().all(|c| !c.is_alphanumeric())
}

fn strip_accents(text: &str) -> String {
    text.nfd().filter(|c| !is_combining_mark(*c)).collect()
}

fn norm(word: &str) -> String {
    strip_accents(&word.to_lowercase())
        .trim_matches(|c| matches!(c, '.' | ',' | ':' | ';' | '!' | '?' | '"' | '\''))
        .to_string()
}

/// Lowercase letters and digits only, for fuzzy comparisons and search.
pub fn key(text: &str) -> String {
    strip_accents(&text.to_lowercase()).chars().filter(|c| c.is_alphanumeric()).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn check(title: &str, channel: &str, expected_title: &str, expected_artist: &str) {
        assert_eq!(
            clean(title, channel),
            SongInfo { title: expected_title.into(), artist: expected_artist.into() },
            "{title} / {channel}"
        );
    }

    #[test]
    fn artist_dash_title_with_official_video() {
        check("Coldplay - Yellow (Official Video)", "Coldplay", "Yellow", "Coldplay");
    }

    #[test]
    fn topic_channel_keeps_title() {
        check("Yellow", "Coldplay - Topic", "Yellow", "Coldplay");
    }

    #[test]
    fn portuguese_clipe_oficial() {
        check("Henrique e Juliano - ARRANHÃO (Clipe Oficial)", "Henrique e Juliano", "ARRANHÃO", "Henrique e Juliano");
    }

    #[test]
    fn title_dash_artist_is_swapped_and_keeps_live() {
        check(
            "Evidências - Chitãozinho & Xororó (Ao Vivo)",
            "Chitãozinho & Xororó",
            "Evidências (Ao Vivo)",
            "Chitãozinho & Xororó",
        );
    }

    #[test]
    fn vevo_channel_is_split_into_words() {
        check("Blinding Lights", "TheWeekndVEVO", "Blinding Lights", "The Weeknd");
    }

    #[test]
    fn official_audio_removed() {
        check("The Weeknd - Blinding Lights (Official Audio)", "TheWeekndVEVO", "Blinding Lights", "The Weeknd");
    }

    #[test]
    fn official_live_video_keeps_live() {
        check("Daft Punk - Get Lucky (Official Live Video)", "Daft Punk", "Get Lucky (Live)", "Daft Punk");
    }

    #[test]
    fn pipe_suffix_and_official_channel() {
        check("Song | Official Music Video", "Band Official", "Song", "Band");
    }

    #[test]
    fn hashtags_removed() {
        check("Anitta - Envolver (Official Music Video) #shorts #anitta", "Anitta", "Envolver", "Anitta");
    }

    #[test]
    fn feat_kept() {
        check(
            "Calvin Harris - Summer (feat. X) [Official Video]",
            "CalvinHarrisVEVO",
            "Summer (feat. X)",
            "Calvin Harris",
        );
    }

    #[test]
    fn trailing_noise_without_brackets() {
        check("Evidências Clipe Oficial", "Chitãozinho & Xororó", "Evidências", "Chitãozinho & Xororó");
    }

    #[test]
    fn quoted_title() {
        check("Coldplay \"Yellow\"", "SomeUploader", "Yellow", "Coldplay");
    }

    #[test]
    fn lyrics_in_portuguese() {
        check("Marília Mendonça - Supera (Letra)", "Marília Mendonça", "Supera", "Marília Mendonça");
    }

    #[test]
    fn mixed_group_keeps_meaningful_words() {
        check("Ana Castela - Pipoco (Ao Vivo e Clipe Oficial)", "Ana Castela", "Pipoco (Ao Vivo)", "Ana Castela");
    }

    #[test]
    fn song_called_video_is_not_emptied() {
        check("India.Arie - Video", "IndiaArieVEVO", "Video", "India.Arie");
    }

    #[test]
    fn remaster_kept() {
        check(
            "Queen – Bohemian Rhapsody (Remastered 2011)",
            "Queen Official",
            "Bohemian Rhapsody (Remastered 2011)",
            "Queen",
        );
    }

    #[test]
    fn plain_title_uses_channel() {
        check("Garota de Ipanema", "Tom Jobim", "Garota de Ipanema", "Tom Jobim");
    }

    #[test]
    fn key_ignores_accents_and_symbols() {
        assert_eq!(key("Chitãozinho & Xororó"), "chitaozinhoxororo");
    }
}
