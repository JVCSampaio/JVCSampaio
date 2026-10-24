package io.github.jvcsampaio.sonar.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.github.jvcsampaio.sonar.data.AudioFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class DownloadedAudio(val file: File, val info: YtDlpInfo?, val workDir: File)

/**
 * Wraps yt-dlp (with its bundled Python, ffmpeg and QuickJS) to turn a video into an audio file.
 * Downloads are anonymous: no account cookies are ever passed to yt-dlp.
 */
class AudioDownloader(private val context: Context) {
    private val initLock = Mutex()

    @Volatile
    private var ready = false

    suspend fun ensureReady() {
        if (ready) return
        initLock.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                YoutubeDL.init(context)
                FFmpeg.init(context)
            }
            ready = true
        }
    }

    /** Release tag of the last self-update; null while the version bundled in the APK is in use. */
    fun engineVersion(): String? = YoutubeDL.version(context)

    /**
     * yt-dlp must follow YouTube's changes, so it updates itself from its GitHub releases.
     * Returns false when it was already up to date.
     */
    suspend fun updateEngine(): Boolean {
        ensureReady()
        val status = withContext(Dispatchers.IO) { YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE) }
        return status == YoutubeDL.UpdateStatus.DONE
    }

    suspend fun download(
        videoId: String,
        format: AudioFormat,
        workRoot: File,
        onProgress: (Float) -> Unit,
    ): DownloadedAudio {
        ensureReady()
        val dir = File(workRoot, videoId)
        dir.deleteRecursively()
        dir.mkdirs()
        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
            addOption("-f", format.selector)
            addOption("-x")
            addOption("--audio-format", format.extension)
            addOption("--audio-quality", "0")
            addOption("--embed-metadata")
            addOption("--write-info-json")
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--socket-timeout", "30")
            addOption("--retries", "3")
            addOption("-o", File(dir, "%(id)s.%(ext)s").absolutePath)
        }
        try {
            // Cancelling the coroutine interrupts the thread, which makes the library kill yt-dlp.
            runInterruptible(Dispatchers.IO) {
                YoutubeDL.execute(request, null) { progress, _, _ -> onProgress(progress) }
            }
        } catch (e: YoutubeDL.CanceledException) {
            throw CancellationException("Download cancelado")
        }
        val files = dir.listFiles().orEmpty()
        val audio = files.firstOrNull { it.extension.lowercase() in AUDIO_EXTENSIONS }
            ?: throw IOException("O yt-dlp terminou sem gerar o arquivo de áudio")
        val info = files.firstOrNull { it.name.endsWith(".info.json") }
            ?.let { runCatching { YtDlpInfo.parse(it.readText()) }.getOrNull() }
        return DownloadedAudio(audio, info, dir)
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf("opus", "m4a", "mp3", "ogg", "webm", "aac", "mka", "flac", "wav")
    }
}
