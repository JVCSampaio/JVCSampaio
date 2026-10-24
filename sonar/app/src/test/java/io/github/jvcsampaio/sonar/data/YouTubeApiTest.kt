package io.github.jvcsampaio.sonar.data

import io.github.jvcsampaio.sonar.data.youtube.YouTubeApi
import io.github.jvcsampaio.sonar.data.youtube.YouTubeApi.Companion.toLikedVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeApiTest {
    @Test fun parsesLikedPage() {
        val body = """
            {"kind":"youtube#videoListResponse","nextPageToken":"CAUQAA",
             "items":[{"id":"abc123","snippet":{"title":"Coldplay - Yellow (Official Video)",
               "channelTitle":"Coldplay","categoryId":"10","publishedAt":"2009-01-01T00:00:00Z",
               "thumbnails":{"default":{"url":"https://i.ytimg.com/d.jpg","width":120,"height":90},
                             "high":{"url":"https://i.ytimg.com/h.jpg","width":480,"height":360}}},
               "contentDetails":{"duration":"PT4M29S","definition":"hd"}}]}
        """.trimIndent()
        val page = YouTubeApi.parseVideoList(body)
        assertEquals("CAUQAA", page.nextPageToken)
        val video = page.items.single().toLikedVideo()
        assertEquals("abc123", video.id)
        assertEquals("10", video.categoryId)
        assertEquals(269_000L, video.durationMs)
        assertEquals("https://i.ytimg.com/h.jpg", video.thumbnailUrl)
    }

    @Test fun lastPageHasNoToken() {
        val page = YouTubeApi.parseVideoList("""{"items":[]}""")
        assertNull(page.nextPageToken)
    }

    @Test fun durations() {
        assertEquals(3_723_000L, YouTubeApi.parseDuration("PT1H2M3S"))
        assertEquals(0L, YouTubeApi.parseDuration("P0D"))
        assertEquals(0L, YouTubeApi.parseDuration(null))
        assertEquals(0L, YouTubeApi.parseDuration("garbage"))
    }

    @Test fun apiNotEnabledError() {
        val body = """{"error":{"code":403,"message":"YouTube Data API v3 has not been used in project 1 before or it is disabled.",
            "errors":[{"message":"...","domain":"usageLimits","reason":"accessNotConfigured"}],
            "status":"PERMISSION_DENIED","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"SERVICE_DISABLED"}]}}"""
        val error = YouTubeApi.parseError(403, body)
        assertEquals("accessNotConfigured", error.reason)
        assertEquals("A YouTube Data API v3 não está ativada no seu projeto do Google Cloud.", error.userMessage)
    }

    @Test fun unauthorizedError() {
        val error = YouTubeApi.parseError(401, """{"error":{"code":401,"message":"Request had invalid authentication credentials."}}""")
        assertEquals(true, error.isAuthError)
    }

    @Test fun nonJsonError() {
        val error = YouTubeApi.parseError(502, "<html>Bad gateway</html>")
        assertEquals("HTTP 502", error.message)
        assertEquals(true, error.retryable)
    }
}
