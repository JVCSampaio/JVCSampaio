package io.github.jvcsampaio.sonar.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.jvcsampaio.sonar.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Live state of the download queue for the UI. */
data class DownloadProgress(
    val title: String,
    val artist: String,
    val fraction: Float,
    val position: Int,
    val total: Int,
)

class WorkScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    val syncRunning: Flow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(SYNC_NOW)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

    val downloadProgress: Flow<DownloadProgress?> = workManager.getWorkInfosForUniqueWorkFlow(DOWNLOADS)
        .map { infos ->
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: return@map null
            val data = running.progress
            val title = data.getString(DownloadWorker.KEY_TITLE) ?: return@map null
            DownloadProgress(
                title = title,
                artist = data.getString(DownloadWorker.KEY_ARTIST).orEmpty(),
                fraction = data.getFloat(DownloadWorker.KEY_FRACTION, 0f),
                position = data.getInt(DownloadWorker.KEY_POSITION, 0),
                total = data.getInt(DownloadWorker.KEY_TOTAL, 0),
            )
        }

    /** True while downloads wait for Wi-Fi or another constraint. */
    val downloadsWaiting: Flow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(DOWNLOADS)
        .map { infos ->
            infos.none { it.state == WorkInfo.State.RUNNING } && infos.any { it.state == WorkInfo.State.ENQUEUED }
        }

    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(SYNC_NOW, ExistingWorkPolicy.KEEP, request)
    }

    fun schedulePeriodic(settings: Settings) {
        if (!settings.connected || !settings.autoSync) {
            workManager.cancelUniqueWork(SYNC_PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(settings.syncIntervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(SYNC_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Starts the download queue, or queues one follow-up run behind a running one so new
     * tracks are picked up without stacking duplicate workers.
     */
    suspend fun enqueueDownloads(wifiOnly: Boolean) {
        val infos = withContext(Dispatchers.IO) { workManager.getWorkInfosForUniqueWork(DOWNLOADS).get() }
        val running = infos.any { it.state == WorkInfo.State.RUNNING }
        val waiting = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
        if (running && waiting) return
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        val policy = if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        workManager.enqueueUniqueWork(DOWNLOADS, policy, request)
    }

    fun pauseDownloads() {
        workManager.cancelUniqueWork(DOWNLOADS)
    }

    companion object {
        const val SYNC_NOW = "sync-now"
        const val SYNC_PERIODIC = "sync-periodic"
        const val DOWNLOADS = "downloads"
    }
}
