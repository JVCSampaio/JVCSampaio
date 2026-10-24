package io.github.jvcsampaio.sonar.domain

data class FilterSettings(
    val musicOnly: Boolean = true,
    val skipShort: Boolean = true,
    val maxDurationMinutes: Int = 15,
)

/** Decides which liked videos are worth downloading as music. */
object TrackFilter {
    const val MUSIC_CATEGORY = "10"
    private const val SHORT_LIMIT_MS = 60_000L

    /** Null means "download it"; otherwise the reason shown to the user. */
    fun skipReason(categoryId: String?, durationMs: Long, filters: FilterSettings): String? = when {
        durationMs <= 0 -> "Transmissão ao vivo ou sem duração"
        filters.skipShort && durationMs <= SHORT_LIMIT_MS -> "Muito curto (até 1 min)"
        filters.maxDurationMinutes > 0 && durationMs > filters.maxDurationMinutes * 60_000L ->
            "Mais longo que ${filters.maxDurationMinutes} min"
        filters.musicOnly && categoryId != MUSIC_CATEGORY -> "Fora da categoria Música do YouTube"
        else -> null
    }
}
