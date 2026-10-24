package io.github.jvcsampaio.sonar.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration

/** A liked video as returned by the YouTube Data API. */
data class LikedVideo(
    val id: String,
    val title: String,
    val channelTitle: String,
    val categoryId: String?,
    val durationMs: Long,
    val thumbnailUrl: String?,
)

@Serializable
data class VideoListResponse(
    val items: List<VideoItem> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class VideoItem(
    val id: String,
    val snippet: Snippet? = null,
    val contentDetails: ContentDetails? = null,
)

@Serializable
data class Snippet(
    val title: String = "",
    val channelTitle: String = "",
    val categoryId: String? = null,
    val thumbnails: Map<String, Thumbnail> = emptyMap(),
)

@Serializable
data class Thumbnail(val url: String, val width: Int? = null, val height: Int? = null)

@Serializable
data class ContentDetails(val duration: String? = null)

class YouTubeApiException(val code: Int, val reason: String?, message: String) : IOException(message) {
    val isAuthError get() = code == 401

    /** Short Portuguese explanation for the sync screen. */
    val userMessage: String
        get() = when (reason) {
            "accessNotConfigured", "SERVICE_DISABLED" ->
                "A YouTube Data API v3 não está ativada no seu projeto do Google Cloud."
            "quotaExceeded", "dailyLimitExceeded", "RATE_LIMIT_EXCEEDED" ->
                "A cota diária da API do YouTube acabou. A sincronização volta amanhã."
            "insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT" ->
                "O Sonar não recebeu permissão para ler suas curtidas. Conecte a conta de novo."
            else -> if (isAuthError) "Sua sessão do Google expirou. Conecte a conta de novo."
            else "Erro da API do YouTube ($code): $message"
        }

    val retryable get() = code >= 500 || reason in setOf("quotaExceeded", "dailyLimitExceeded", "RATE_LIMIT_EXCEEDED")
}

class YouTubeApi(private val http: OkHttpClient) {

    /** Fetches every liked video, newest like first. */
    suspend fun likedVideos(accessToken: String, maxPages: Int = 200): List<LikedVideo> {
        val result = ArrayList<LikedVideo>()
        var pageToken: String? = null
        var pages = 0
        do {
            val page = likedPage(accessToken, pageToken)
            page.items.mapTo(result) { it.toLikedVideo() }
            pageToken = page.nextPageToken
            pages++
        } while (pageToken != null && pages < maxPages)
        return result.distinctBy { it.id }
    }

    private suspend fun likedPage(accessToken: String, pageToken: String?): VideoListResponse =
        withContext(Dispatchers.IO) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("www.googleapis.com")
                .addPathSegments("youtube/v3/videos")
                .addQueryParameter("part", "snippet,contentDetails")
                .addQueryParameter("myRating", "like")
                .addQueryParameter("maxResults", "50")
                .addQueryParameter(
                    "fields",
                    "nextPageToken,items(id,snippet(title,channelTitle,categoryId,thumbnails),contentDetails(duration))",
                )
                .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw parseError(response.code, body)
                parseVideoList(body)
            }
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseVideoList(body: String): VideoListResponse = json.decodeFromString(body)

        fun parseError(code: Int, body: String): YouTubeApiException {
            val error = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
            val message = (error?.get("message") as? JsonPrimitive)?.contentOrNull ?: "HTTP $code"
            val reason = runCatching {
                val details = error?.get("details")?.jsonArray?.firstNotNullOfOrNull { detail ->
                    ((detail as? JsonObject)?.get("reason") as? JsonPrimitive)?.contentOrNull
                }
                val errors = error?.get("errors")?.jsonArray?.firstNotNullOfOrNull { item ->
                    ((item as? JsonObject)?.get("reason") as? JsonPrimitive)?.contentOrNull
                }
                errors ?: details
            }.getOrNull()
            return YouTubeApiException(code, reason, message)
        }

        /** ISO 8601 ("PT4M13S") to milliseconds; 0 for live streams and unknown values. */
        fun parseDuration(iso: String?): Long =
            iso?.let { runCatching { Duration.parse(it).toMillis() }.getOrNull() } ?: 0L

        fun VideoItem.toLikedVideo(): LikedVideo {
            val thumbs = snippet?.thumbnails.orEmpty()
            val best = listOf("maxres", "standard", "high", "medium", "default").firstNotNullOfOrNull { thumbs[it] }
            return LikedVideo(
                id = id,
                title = snippet?.title.orEmpty(),
                channelTitle = snippet?.channelTitle.orEmpty(),
                categoryId = snippet?.categoryId,
                durationMs = parseDuration(contentDetails?.duration),
                thumbnailUrl = best?.url,
            )
        }
    }
}
