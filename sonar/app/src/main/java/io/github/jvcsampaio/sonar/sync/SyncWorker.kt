package io.github.jvcsampaio.sonar.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.common.api.ApiException
import io.github.jvcsampaio.sonar.Notifications
import io.github.jvcsampaio.sonar.container
import io.github.jvcsampaio.sonar.data.youtube.GoogleAuth
import io.github.jvcsampaio.sonar.data.youtube.YouTubeApiException
import java.io.IOException

/** Lists the liked videos and queues the new ones for download. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app = context.container

    override suspend fun doWork(): Result = try {
        when (val outcome = app.likeSync.run()) {
            LikeSync.Outcome.NeedsSignIn -> {
                val message = "Entre de novo na sua conta Google para continuar sincronizando."
                app.settings.update { it.copy(lastSyncError = message) }
                Notifications.syncNeedsAttention(applicationContext, message)
                Result.failure()
            }
            is LikeSync.Outcome.Success -> {
                app.scheduler.enqueueDownloads(app.settings.current().wifiOnly)
                Result.success(workDataOf(KEY_LIKED to outcome.liked, KEY_ADDED to outcome.added))
            }
        }
    } catch (e: YouTubeApiException) {
        Log.w(TAG, "YouTube API error ${e.code} ${e.reason}", e)
        app.settings.update { it.copy(lastSyncError = e.userMessage) }
        if (e.retryable && runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    } catch (e: ApiException) {
        Log.w(TAG, "Google authorization error ${e.statusCode}", e)
        app.settings.update { it.copy(lastSyncError = GoogleAuth.describe(e)) }
        Result.failure()
    } catch (e: IOException) {
        Log.w(TAG, "Network error", e)
        app.settings.update { it.copy(lastSyncError = "Sem conexão com o YouTube. Vou tentar de novo.") }
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val MAX_RETRIES = 3
        const val KEY_LIKED = "liked"
        const val KEY_ADDED = "added"
    }
}
