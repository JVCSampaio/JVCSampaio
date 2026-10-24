package io.github.jvcsampaio.sonar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLinksTest {
    @Test fun watchUrl() = assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))

    @Test fun watchUrlWithOtherParameters() =
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoId("https://www.youtube.com/watch?list=PL1&v=dQw4w9WgXcQ&t=42s"))

    @Test fun youtubeMusicShare() =
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=abc"))

    @Test fun shortLinkInsideSharedText() =
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoId("Olha essa: https://youtu.be/dQw4w9WgXcQ?si=xyz"))

    @Test fun shorts() = assertEquals("aBcDeFgHiJk", YouTubeLinks.videoId("https://youtube.com/shorts/aBcDeFgHiJk"))

    @Test fun mobileSite() = assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))

    @Test fun bareId() = assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoId("  dQw4w9WgXcQ "))

    @Test fun notALink() = assertNull(YouTubeLinks.videoId("https://example.com/watch?v=123"))
}
