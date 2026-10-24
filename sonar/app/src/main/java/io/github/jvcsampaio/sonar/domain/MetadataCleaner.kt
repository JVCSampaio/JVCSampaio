package io.github.jvcsampaio.sonar.domain

import java.text.Normalizer

data class SongInfo(val title: String, val artist: String)

/**
 * Turns a YouTube video title and channel into a song title and artist:
 * "Artist - Song (Official Video)" on "ArtistVEVO" becomes ("Song", "Artist").
 */
object MetadataCleaner {
    private val separators = listOf(" - ", " – ", " — ", " -- ", " ~ ")

    /** Words that only describe the video, never the song. */
    private val strongNoise = setOf(
        "official", "oficial", "video", "videoclip", "videoclipe", "clip", "clipe", "audio",
        "lyric", "lyrics", "letra", "visualizer", "visualiser", "mv", "m/v", "legendado",
        "legenda", "traducao", "hd", "hq", "4k", "8k", "1080p", "720p",
    )

    /** Words that are noise only next to strong noise ("Official Music Video"). */
    private val weakNoise = setOf("music", "musical", "musica", "full", "com", "with", "e", "and", "&", "new", "nova", "novo")

    /** A trailing run of noise is removed only when it contains one of these. */
    private val trailingMarkers = setOf(
        "official", "oficial", "clipe", "videoclipe", "videoclip", "lyric", "lyrics", "letra",
        "visualizer", "visualiser",
    )

    private val bracketGroup = Regex("""\(([^()]*)\)|\[([^\[\]]*)]|【([^【】]*)】""")
    private val hashtag = Regex("""(^|\s)#[\p{L}\p{N}_]+""")
    private val quoted = Regex("""^(.+?)\s+["“](.+?)["”]\s*$""")
    private val officialSuffix = Regex("""\s*\b(official|oficial)\b(\s+(channel|canal))?\s*$""", RegexOption.IGNORE_CASE)
    private val trailingBrackets = Regex("""(\s*(\([^()]*\)|\[[^\[\]]*]))+\s*$""")

    fun clean(rawTitle: String, channel: String): SongInfo {
        val title = removeNoise(rawTitle).ifBlank { rawTitle.trim() }
        if (channel.endsWith(" - Topic")) {
            return SongInfo(title, channel.removeSuffix(" - Topic").trim())
        }
        val channelArtist = cleanChannel(channel)

        val split = separators
            .mapNotNull { sep -> title.indexOf(sep).takeIf { it > 0 }?.let { it to sep } }
            .minByOrNull { it.first }
        if (split != null) {
            val left = title.substring(0, split.first).trim()
            val right = title.substring(split.first + split.second.length).trim()
            if (left.isNotEmpty() && right.isNotEmpty()) {
                val channelKey = key(channelArtist)
                val swap = channelKey.length >= 3 &&
                    key(right).contains(channelKey) && !key(left).contains(channelKey)
                return if (swap) moveBrackets(artist = right, title = left) else moveBrackets(artist = left, title = right)
            }
        }

        quoted.find(title)?.let { match ->
            return SongInfo(match.groupValues[2].trim(), match.groupValues[1].trim())
        }
        return SongInfo(title, channelArtist)
    }

    /** "TaylorSwiftVEVO" -> "Taylor Swift", "Band Official" -> "Band". */
    fun cleanChannel(channel: String): String {
        var name = channel.trim().removeSuffix(" - Topic")
        if (name.endsWith("VEVO") || name.endsWith("Vevo")) {
            name = name.dropLast(4).trim()
            if (!name.contains(' ')) name = name.replace(Regex("""(?<=\p{Ll})(?=\p{Lu})"""), " ")
        }
        name = name.replace(officialSuffix, "").trim()
        return name.ifBlank { channel.trim() }
    }

    fun removeNoise(raw: String): String {
        var text = raw.replace(hashtag, " ")
        text = bracketGroup.replace(text) { match ->
            val open = match.value.first()
            val close = match.value.last()
            val inner = match.value.substring(1, match.value.length - 1)
            val kept = stripNoiseWords(inner)
            when {
                kept == null -> match.value
                kept.isEmpty() -> ""
                else -> "$open$kept$close"
            }
        }
        // "Song | Official Video", "Artist - Song - Clipe Oficial"
        text = text.split(" | ", " ｜ ")
            .filterIndexed { index, part -> index == 0 || !isNoiseSuffix(part) }
            .joinToString(" | ")
        val dashParts = text.split(" - ")
        if (dashParts.size > 1 && isNoiseSuffix(dashParts.last())) {
            text = dashParts.dropLast(1).joinToString(" - ")
        }
        text = removeTrailingNoise(text)
        return text.replace(Regex("""\s+"""), " ").trim().trim('-', '|', '–', '—', ' ')
    }

    /**
     * Returns null when the group has no noise, "" when it is only noise, and otherwise the
     * group without its noise words ("Official Live Video" -> "Live").
     */
    private fun stripNoiseWords(inner: String): String? {
        val words = inner.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.none { norm(it) in strongNoise }) return null
        if (words.all { norm(it) in strongNoise || norm(it) in weakNoise || isPunctuation(it) }) return ""
        val kept = words.filter { norm(it) !in strongNoise }.toMutableList()
        while (kept.isNotEmpty() && (norm(kept.last()) in weakNoise || isPunctuation(kept.last()))) kept.removeAt(kept.lastIndex)
        while (kept.isNotEmpty() && (norm(kept.first()) in weakNoise || isPunctuation(kept.first()))) kept.removeAt(0)
        return kept.joinToString(" ")
    }

    /**
     * A " - " or " | " segment made only of noise. A lone "Video" or "Audio" is kept, because
     * it can be a song title ("India.Arie - Video").
     */
    private fun isNoiseSuffix(part: String): Boolean {
        val words = part.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        return words.isNotEmpty() &&
            words.any { norm(it) in strongNoise } &&
            words.all { norm(it) in strongNoise || norm(it) in weakNoise || isPunctuation(it) } &&
            (words.size > 1 || norm(words.single()) in trailingMarkers)
    }

    private fun removeTrailingNoise(text: String): String {
        val words = text.trim().split(Regex("""\s+""")).toMutableList()
        var cut = words.size
        while (cut > 0) {
            val w = norm(words[cut - 1])
            if (w in strongNoise || w in weakNoise || isPunctuation(words[cut - 1])) cut-- else break
        }
        val tail = words.subList(cut, words.size)
        if (cut == 0 || tail.none { norm(it) in trailingMarkers }) return text
        return words.subList(0, cut).joinToString(" ")
    }

    private fun moveBrackets(artist: String, title: String): SongInfo {
        val trailing = trailingBrackets.find(artist)?.value?.trim().orEmpty()
        if (trailing.isEmpty() || trailing.length == artist.length) return SongInfo(title, artist)
        return SongInfo("$title $trailing", artist.removeSuffix(trailingBrackets.find(artist)!!.value).trim())
    }

    private fun isPunctuation(word: String) = word.all { !it.isLetterOrDigit() }

    private fun norm(word: String): String = stripAccents(word.lowercase()).trim('.', ',', ':', ';', '!', '?', '"', '\'')

    /** Lowercase letters and digits only, for fuzzy comparisons. */
    fun key(text: String): String = stripAccents(text.lowercase()).filter { it.isLetterOrDigit() }

    private fun stripAccents(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
}
