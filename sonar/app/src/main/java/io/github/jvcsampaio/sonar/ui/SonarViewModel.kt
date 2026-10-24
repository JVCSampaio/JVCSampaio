package io.github.jvcsampaio.sonar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jvcsampaio.sonar.container
import io.github.jvcsampaio.sonar.data.Settings
import io.github.jvcsampaio.sonar.data.db.PlaylistSummary
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import io.github.jvcsampaio.sonar.sync.DownloadProgress
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One view model for the whole app; screens read slices of it. */
class SonarViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.container
    val player = app.player
    val auth = app.auth

    val tracks: StateFlow<List<TrackEntity>> = app.library.downloaded.stateIn(emptyList())
    val pending: StateFlow<List<TrackEntity>> = app.library.pending.stateIn(emptyList())
    val counts: StateFlow<Map<TrackStatus, Int>> = app.library.statusCounts.stateIn(emptyMap())
    val storageBytes: StateFlow<Long> = app.library.storageBytes.stateIn(0L)
    val playlists: StateFlow<List<PlaylistSummary>> = app.library.playlists.stateIn(emptyList())
    val settings: StateFlow<Settings> = app.settings.settings.stateIn(Settings())
    val syncRunning: StateFlow<Boolean> = app.scheduler.syncRunning.stateIn(false)
    val downloadProgress: StateFlow<DownloadProgress?> = app.scheduler.downloadProgress.stateIn(null)
    val downloadsWaiting: StateFlow<Boolean> = app.scheduler.downloadsWaiting.stateIn(false)

    private val _engineVersion = MutableStateFlow(app.downloader.engineVersion())
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()
    private val _engineUpdating = MutableStateFlow(false)
    val engineUpdating: StateFlow<Boolean> = _engineUpdating.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun playlist(id: Long) = app.library.playlist(id)
    fun playlistTracks(id: Long) = app.library.playlistTracks(id)

    fun play(tracks: List<TrackEntity>, index: Int = 0) = player.play(tracks, index)
    fun shuffle(tracks: List<TrackEntity>) = player.play(tracks, shuffle = true)
    fun playNext(track: TrackEntity) {
        player.playNext(track)
        say("Toca em seguida: ${track.title}")
    }

    fun addToQueue(track: TrackEntity) {
        player.addToQueue(track)
        say("Adicionada à fila")
    }

    fun onSignedIn() = launch {
        app.settings.update { it.copy(connected = true, lastSyncError = null) }
        app.scheduler.syncNow()
    }

    fun disconnect() = launch {
        app.settings.update { it.copy(connected = false) }
        say("Conta desconectada. As músicas baixadas continuam no aparelho.")
    }

    fun syncNow() = app.scheduler.syncNow()
    fun retryFailed() = launch { app.library.retryFailed() }
    fun resumeDownloads() = launch { app.library.resumeDownloads() }
    fun pauseDownloads() = app.scheduler.pauseDownloads()
    fun downloadAnyway(track: TrackEntity) = launch { app.library.downloadAnyway(track) }
    fun skip(track: TrackEntity) = launch { app.library.skip(track) }

    fun removeFromDevice(track: TrackEntity) = launch {
        app.library.removeFromDevice(track)
        say("Removida do aparelho")
    }

    fun createPlaylist(name: String, firstTrack: TrackEntity? = null) = launch {
        app.library.createPlaylist(name, firstTrack)
        say(if (firstTrack != null) "Adicionada a \"$name\"" else "Playlist criada")
    }

    fun addToPlaylist(playlist: PlaylistSummary, track: TrackEntity) = launch {
        app.library.addToPlaylist(playlist.id, track)
        say("Adicionada a \"${playlist.name}\"")
    }

    fun removeFromPlaylist(playlistId: Long, track: TrackEntity) = launch { app.library.removeFromPlaylist(playlistId, track) }
    fun renamePlaylist(playlistId: Long, name: String) = launch { app.library.renamePlaylist(playlistId, name) }
    fun deletePlaylist(playlistId: Long) = launch { app.library.deletePlaylist(playlistId) }

    fun updateSettings(transform: (Settings) -> Settings) = launch { app.settings.update(transform) }

    /** Waiting downloads are re-queued so they pick up the new network constraint. */
    fun setWifiOnly(value: Boolean) = launch {
        app.settings.update { it.copy(wifiOnly = value) }
        app.library.resumeDownloads()
    }

    fun updateEngine() = launch {
        _engineUpdating.value = true
        val message = runCatching { app.downloader.updateEngine() }.fold(
            onSuccess = { updated ->
                app.settings.update { it.copy(engineCheckedAt = System.currentTimeMillis()) }
                if (updated) "yt-dlp atualizado" else "O yt-dlp já está na versão mais nova"
            },
            onFailure = { "Não foi possível atualizar: ${it.message}" },
        )
        _engineVersion.value = app.downloader.engineVersion()
        _engineUpdating.value = false
        say(message)
    }

    fun say(message: String) {
        _messages.trySend(message)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun <T> Flow<T>.stateIn(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
