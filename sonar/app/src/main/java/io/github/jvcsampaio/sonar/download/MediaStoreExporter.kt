package io.github.jvcsampaio.sonar.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import java.io.File
import java.io.IOException

/** Copies a downloaded song into the shared Music/Sonar folder so other players see it. */
object MediaStoreExporter {
    val supported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    fun export(context: Context, file: File, track: TrackEntity): Uri {
        val resolver = context.contentResolver
        val name = "${track.artist} - ${track.title}".replace(Regex("""[\\/:*?"<>|]"""), "_").take(120)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.${file.extension}")
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType(file.extension))
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Sonar")
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, track.artist)
            track.album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore recusou o arquivo")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Não foi possível abrir $uri")
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun mimeType(extension: String) = when (extension.lowercase()) {
        "opus", "ogg" -> "audio/ogg"
        "m4a", "mp4", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "webm" -> "audio/webm"
        else -> "audio/*"
    }
}
