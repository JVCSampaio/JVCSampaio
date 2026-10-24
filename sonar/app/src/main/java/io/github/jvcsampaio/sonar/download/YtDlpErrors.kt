package io.github.jvcsampaio.sonar.download

/** Reads yt-dlp's stderr: was it the network, and what should the user see? */
object YtDlpErrors {
    private val networkHints = listOf(
        "Unable to download webpage", "Temporary failure in name resolution", "Network is unreachable",
        "timed out", "Connection reset", "getaddrinfo", "No address associated with hostname",
        "Failed to resolve", "Connection refused", "Remote end closed connection", "HTTP Error 5",
        "HTTP Error 429", "SSL:",
    )

    fun isNetworkError(message: String): Boolean = networkHints.any { message.contains(it, ignoreCase = true) }

    /** The last "ERROR:" line without its prefix, e.g. "Video unavailable". */
    fun summarize(message: String): String {
        val lines = message.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val line = lines.lastOrNull { it.startsWith("ERROR:") } ?: lines.lastOrNull() ?: return "Erro desconhecido"
        return line.removePrefix("ERROR:")
            .replace(Regex("""^\s*\[[^]]+]\s*[\w-]{6,}:\s*"""), "")
            .trim()
            .take(300)
    }
}
