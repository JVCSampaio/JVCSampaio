package io.github.jvcsampaio.sonar.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpErrorsTest {
    @Test fun summarizesLastErrorLine() {
        val stderr = """
            WARNING: [youtube] Some formats may be missing
            ERROR: [youtube] dQw4w9WgXcQ: Video unavailable. This video is private
        """.trimIndent()
        assertEquals("Video unavailable. This video is private", YtDlpErrors.summarize(stderr))
    }

    @Test fun fallsBackToLastLine() = assertEquals("boom", YtDlpErrors.summarize("\nsomething\nboom\n"))

    @Test fun emptyMessage() = assertEquals("Erro desconhecido", YtDlpErrors.summarize(""))

    @Test fun networkErrorsAreRecognized() {
        assertTrue(YtDlpErrors.isNetworkError("ERROR: Unable to download webpage: <urlopen error [Errno 7] No address associated with hostname>"))
        assertFalse(YtDlpErrors.isNetworkError("ERROR: [youtube] abc: Video unavailable"))
    }
}
