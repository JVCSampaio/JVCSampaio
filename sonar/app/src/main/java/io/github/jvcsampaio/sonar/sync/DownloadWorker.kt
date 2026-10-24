package io.github.jvcsampaio.sonar.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.jvcsampaio.sonar.Notifications
import io.github.jvcsampaio.sonar.container
import io.github.jvcsampaio.sonar.data.Settings
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import io.github.jvcsampaio.sonar.download.MediaStoreExporter
import io.github.jvcsampaio.sonar.download.YtDlpErrors
import io.github.jvcsampaio.sonar.domain.MetadataCleaner
import io.github.jvcsampaio.sonar.domain.SongInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Works through the QUEUED tracks one by one with yt-dlp. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app = context.container
    private val dao = app.database.tracks()

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("Preparando downloads", null, null)

    override suspend fun doWork(): Result {
        dao.requeueInterrupted()
        if (dao.queuedCount() == 0) return Result.success()

        // Foreground while the app is open: no time limit and a progress notification.
        // From the background Android may refuse; then the run is capped and continues later.
        val foreground = try {
            setForeground(foregroundInfo("Preparando downloads", null, null))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "Running without foreground service: ${e.message}")
            false
        }
        val deadline = if (foreground) Long.MAX_VALUE else SystemClock.elapsedRealtime() + BACKGROUND_BUDGET_MS

        try {
            app.downloader.ensureReady()
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp failed to start", e)
            app.settings.update { it.copy(lastSyncError = "O motor de download não iniciou: ${e.message}") }
            return Result.failure()
        }
        updateEngineIfDue()

        var done = 0
        while (!isStopped) {
            if (SystemClock.elapsedRealtime() > deadline) {
                app.scheduler.enqueueDownloads(app.settings.current().wifiOnly)
                break
            }
            val track = dao.nextQueued() ?: break
            val total = done + dao.queuedCount()
            val settings = app.settings.current()
            when (downloadOne(track, done + 1, total, settings, foreground)) {
                Step.Done, Step.Failed -> done++
                Step.NetworkProblem -> return Result.retry()
            }
        }
        return Result.success()
    }

    private enum class Step { Done, Failed, NetworkProblem }

    private suspend fun downloadOne(
        track: TrackEntity,
        position: Int,
        total: Int,
        settings: Settings,
        foreground: Boolean,
    ): Step = coroutineScope {
        dao.update(track.copy(status = TrackStatus.DOWNLOADING))
        var fraction = 0f
        val reporter = launch {
            while (isActive) {
                report(track, position, total, fraction, foreground)
                delay(PROGRESS_INTERVAL_MS)
            }
        }
        try {
            val result = app.downloader.download(track.videoId, settings.audioFormat, app.storage.workDir) {
                fraction = (it / 100f).coerceIn(0f, 1f)
            }
            val target = File(app.storage.musicDir, "${track.videoId}.${result.file.extension}")
            track.filePath?.takeIf { it != target.absolutePath }?.let { File(it).delete() }
            app.storage.moveInto(result.file, target)
            result.workDir.deleteRecursively()

            val cover = app.covers.fetch(track.videoId)
            val info = result.info
            val song = when {
                // YouTube Music knows the real song and artist names.
                info?.track != null && info.artist != null -> SongInfo(info.track, info.artist)
                // Shared links may arrive without a title; yt-dlp always has one.
                track.sourceTitle.isBlank() && info?.title != null -> MetadataCleaner.clean(info.title, info.channel.orEmpty())
                else -> SongInfo(track.title, track.artist)
            }
            val finished = track.copy(
                status = TrackStatus.DONE,
                statusNote = null,
                title = song.title,
                artist = song.artist,
                album = info?.album ?: track.album,
                durationMs = info?.durationSeconds?.let { (it * 1000).toLong() } ?: track.durationMs,
                filePath = target.absolutePath,
                coverPath = cover?.absolutePath ?: track.coverPath,
                fileBytes = target.length(),
                downloadedAt = System.currentTimeMillis(),
            )
            dao.update(finished)
            Log.i(TAG, "Downloaded ${track.videoId} as ${target.name} (${target.length()} bytes)")
            if (settings.exportToMusic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { MediaStoreExporter.export(applicationContext, target, finished) }
                    .onFailure { Log.w(TAG, "Export to Music failed", it) }
            }
            Step.Done
        } catch (e: CancellationException) {
            withContext(NonCancellable) { dao.update(track.copy(status = TrackStatus.QUEUED)) }
            throw e
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            Log.w(TAG, "Download failed for ${track.videoId}: $message")
            if (YtDlpErrors.isNetworkError(message)) {
                dao.update(track.copy(status = TrackStatus.QUEUED))
                Step.NetworkProblem
            } else {
                dao.update(
                    track.copy(
                        status = TrackStatus.FAILED,
                        statusNote = YtDlpErrors.summarize(message),
                        attempts = track.attempts + 1,
                    ),
                )
                Step.Failed
            }
        } finally {
            reporter.cancel()
        }
    }

    private suspend fun report(track: TrackEntity, position: Int, total: Int, fraction: Float, foreground: Boolean) {
        setProgress(
            workDataOf(
                KEY_TITLE to track.title,
                KEY_ARTIST to track.artist,
                KEY_FRACTION to fraction,
                KEY_POSITION to position,
                KEY_TOTAL to total,
            ),
        )
        if (foreground) {
            runCatching { setForeground(foregroundInfo(track.title, "$position de $total · ${track.artist}", fraction)) }
        }
    }

    private suspend fun updateEngineIfDue() {
        val settings = app.settings.current()
        val now = System.currentTimeMillis()
        if (now - settings.engineCheckedAt < ENGINE_CHECK_INTERVAL_MS) return
        runCatching { app.downloader.updateEngine() }
            .onSuccess { app.settings.update { it.copy(engineCheckedAt = now) } }
            .onFailure { Log.w(TAG, "yt-dlp update failed", it) }
    }

    private fun foregroundInfo(title: String, text: String?, fraction: Float?): ForegroundInfo {
        val notification = Notifications.downloadProgress(applicationContext, title, text, fraction, id)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(Notifications.ID_DOWNLOADS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(Notifications.ID_DOWNLOADS, notification)
        }
    }

    companion object {
        private const val TAG = "DownloadWorker"
        private const val BACKGROUND_BUDGET_MS = 8 * 60_000L
        private const val PROGRESS_INTERVAL_MS = 800L
        private const val ENGINE_CHECK_INTERVAL_MS = 24 * 60 * 60_000L
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_FRACTION = "fraction"
        const val KEY_POSITION = "position"
        const val KEY_TOTAL = "total"
    }
}
