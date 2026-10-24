package io.github.jvcsampaio.sonar.download

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** The music metadata yt-dlp finds (YouTube Music "Song" and "Artist" fields). */
data class YtDlpInfo(
    val track: String?,
    val artist: String?,
    val album: String?,
    val durationSeconds: Double?,
    val title: String? = null,
    val channel: String? = null,
) {
    companion object {
        fun parse(text: String): YtDlpInfo {
            val obj = Json.parseToJsonElement(text).jsonObject
            val artists = (obj["artists"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
            return YtDlpInfo(
                track = obj.text("track"),
                artist = artists ?: obj.text("artist") ?: obj.text("creator"),
                album = obj.text("album"),
                durationSeconds = (obj["duration"] as? JsonPrimitive)?.doubleOrNull,
                title = obj.text("title"),
                channel = obj.text("channel") ?: obj.text("uploader"),
            )
        }

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }
}
