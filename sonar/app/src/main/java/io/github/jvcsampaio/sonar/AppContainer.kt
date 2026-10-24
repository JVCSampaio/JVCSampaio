package io.github.jvcsampaio.sonar

import android.content.Context
import io.github.jvcsampaio.sonar.data.LibraryRepository
import io.github.jvcsampaio.sonar.data.LibraryStorage
import io.github.jvcsampaio.sonar.data.SettingsRepository
import io.github.jvcsampaio.sonar.data.db.SonarDatabase
import io.github.jvcsampaio.sonar.data.youtube.GoogleAuth
import io.github.jvcsampaio.sonar.data.youtube.YouTubeApi
import io.github.jvcsampaio.sonar.download.AudioDownloader
import io.github.jvcsampaio.sonar.download.CoverFetcher
import io.github.jvcsampaio.sonar.playback.PlayerConnection
import io.github.jvcsampaio.sonar.sync.LikeSync
import io.github.jvcsampaio.sonar.sync.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-wired dependencies, created once per process. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = SonarDatabase.build(context)
    val settings = SettingsRepository(context)
    val storage = LibraryStorage(context)
    val scheduler = WorkScheduler(context)
    val auth = GoogleAuth(context)
    val downloader = AudioDownloader(context)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    val youtube = YouTubeApi(http)
    val covers = CoverFetcher(http, storage.coverDir)
    val likeSync = LikeSync(database, settings, youtube, auth, storage)
    val library = LibraryRepository(database, settings, storage, scheduler)
    val player = PlayerConnection(context, database, settings)

    fun start() {
        scope.launch {
            settings.settings
                .map { listOf(it.connected, it.autoSync, it.syncIntervalHours, it.wifiOnly) to it }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (_, current) -> scheduler.schedulePeriodic(current) }
        }
        scope.launch {
            settings.settings.map { it.filters }.distinctUntilChanged().collect { library.reapplyFilters(it) }
        }
    }
}
