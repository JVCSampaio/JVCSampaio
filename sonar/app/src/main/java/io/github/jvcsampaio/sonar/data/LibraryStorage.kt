package io.github.jvcsampaio.sonar.data

import android.content.Context
import android.os.Environment
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import java.io.File

/** Where audio files, covers and in-progress downloads live. */
class LibraryStorage(context: Context) {
    val musicDir: File = (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "music"))
        .apply { mkdirs() }
    val coverDir: File = File(context.filesDir, "covers").apply { mkdirs() }
    val workDir: File = File(context.cacheDir, "downloads").apply { mkdirs() }

    fun deleteFiles(track: TrackEntity) {
        track.filePath?.let { File(it).delete() }
        track.coverPath?.let { File(it).delete() }
    }

    /** Moves across file systems too (cache and external storage can differ). */
    fun moveInto(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }
}
