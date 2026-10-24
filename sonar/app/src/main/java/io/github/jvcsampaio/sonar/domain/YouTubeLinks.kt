package io.github.jvcsampaio.sonar.domain

/** Finds the video id in anything shared from YouTube or YouTube Music. */
object YouTubeLinks {
    private val patterns = listOf(
        Regex("""(?:youtube\.com|youtube-nocookie\.com)/(?:watch|embed|shorts|live|v)(?:\?(?:\S*?&)?v=|/)([\w-]{11})"""),
        Regex("""youtu\.be/([\w-]{11})"""),
    )
    private val bareId = Regex("""[\w-]{11}""")

    fun videoId(text: String): String? =
        patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
            ?: text.trim().takeIf { bareId.matches(it) }
}
