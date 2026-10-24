package io.github.jvcsampaio.sonar.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/** Saves a square cover for a video, cut from the centre of its YouTube thumbnail. */
class CoverFetcher(private val http: OkHttpClient, private val coverDir: File) {

    suspend fun fetch(videoId: String): File? = withContext(Dispatchers.IO) {
        for (name in listOf("maxresdefault", "hqdefault")) {
            val bytes = runCatching {
                http.newCall(Request.Builder().url("https://i.ytimg.com/vi/$videoId/$name.jpg").build())
                    .execute()
                    .use { if (it.isSuccessful) it.body?.bytes() else null }
            }.getOrNull() ?: continue
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            val square = cropSquare(bitmap)
            val target = File(coverDir, "$videoId.jpg")
            target.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (square !== bitmap) square.recycle()
            bitmap.recycle()
            return@withContext target
        }
        null
    }

    private fun cropSquare(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        // 4:3 thumbnails ("hqdefault") letterbox a 16:9 frame; the picture is the middle band.
        val contentHeight = if (abs(w * 3 - h * 4) <= 4) w * 9 / 16 else h
        val side = min(w, contentHeight)
        return Bitmap.createBitmap(bitmap, (w - side) / 2, (h - side) / 2, side, side)
    }
}
