package io.github.jvcsampaio.sonar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackFilterTest {
    private val defaults = FilterSettings()

    @Test fun musicWithinLimitsIsDownloaded() =
        assertNull(TrackFilter.skipReason("10", 215_000, defaults))

    @Test fun otherCategoryIsSkippedWhenMusicOnly() =
        assertEquals("Fora da categoria Música do YouTube", TrackFilter.skipReason("24", 215_000, defaults))

    @Test fun otherCategoryAllowedWhenFilterOff() =
        assertNull(TrackFilter.skipReason("24", 215_000, defaults.copy(musicOnly = false)))

    @Test fun liveStreamsAreSkipped() =
        assertEquals("Transmissão ao vivo ou sem duração", TrackFilter.skipReason("10", 0, defaults))

    @Test fun shortVideosAreSkipped() =
        assertEquals("Muito curto (até 1 min)", TrackFilter.skipReason("10", 45_000, defaults))

    @Test fun longVideosAreSkipped() =
        assertEquals("Mais longo que 15 min", TrackFilter.skipReason("10", 16 * 60_000L, defaults))

    @Test fun noDurationLimit() =
        assertNull(TrackFilter.skipReason("10", 3 * 3_600_000L, defaults.copy(maxDurationMinutes = 0)))
}
