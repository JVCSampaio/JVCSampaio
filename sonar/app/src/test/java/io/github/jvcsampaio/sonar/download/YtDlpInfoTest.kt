package io.github.jvcsampaio.sonar.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtDlpInfoTest {
    @Test fun readsMusicFields() {
        val info = YtDlpInfo.parse(
            """{"id":"x","title":"Yellow","track":"Yellow","artists":["Coldplay"],"artist":"Coldplay",
               "album":"Parachutes","duration":269.0,"formats":[{"format_id":"251"}]}""",
        )
        assertEquals(YtDlpInfo("Yellow", "Coldplay", "Parachutes", 269.0), info)
    }

    @Test fun joinsSeveralArtists() {
        val info = YtDlpInfo.parse("""{"track":"Song","artists":["A","B","A"],"album":null}""")
        assertEquals("A, B", info.artist)
        assertNull(info.album)
    }

    @Test fun plainVideoHasNoMusicFields() {
        val info = YtDlpInfo.parse("""{"title":"Vlog","duration":600}""")
        assertNull(info.track)
        assertNull(info.artist)
        assertEquals(600.0, info.durationSeconds!!, 0.0)
    }
}
