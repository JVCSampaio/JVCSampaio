package io.github.jvcsampaio.sonar.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.jvcsampaio.sonar.data.SettingsRepository
import io.github.jvcsampaio.sonar.data.db.SonarDatabase
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class QueueEntry(val mediaId: String, val title: String, val artist: String, val artwork: Uri?)

data class PlayerState(
    val connected: Boolean = false,
    val current: QueueEntry? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = 0,
    val hasNext: Boolean = false,
)

/** The UI side of playback: a MediaController bound to [PlaybackService]. */
class PlayerConnection(
    private val context: Context,
    private val database: SonarDatabase,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var restoreChecked = false

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Position is read on demand; polling it into a flow would recompose the whole tree. */
    val positionMs: Long get() = controller?.currentPosition ?: 0
    val bufferedMs: Long get() = controller?.bufferedPosition ?: 0

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener({
            val connected = runCatching { pending.get() }.getOrNull() ?: return@addListener
            controller = connected
            connected.addListener(listener)
            refresh()
            restoreLastQueue(connected)
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        _state.value = PlayerState()
    }

    fun play(tracks: List<TrackEntity>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        val playable = tracks.filter { it.status == TrackStatus.DONE && it.filePath != null }
        if (playable.isEmpty()) return
        val target = tracks.getOrNull(startIndex)?.let { wanted -> playable.indexOfFirst { it.videoId == wanted.videoId } } ?: 0
        val start = if (shuffle) playable.indices.random() else target.coerceAtLeast(0)
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(playable.map { it.toMediaItem() }, start, 0)
        c.prepare()
        c.play()
    }

    fun playNext(track: TrackEntity) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(track))
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun addToQueue(track: TrackEntity) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(track))
        c.addMediaItem(track.toMediaItem())
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)
    fun skipTo(index: Int) = controller?.run { seekToDefaultPosition(index); play() }
    fun removeAt(index: Int) = controller?.removeMediaItem(index)
    fun toggleShuffle() = controller?.run { shuffleModeEnabled = !shuffleModeEnabled }

    fun cycleRepeat() = controller?.run {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toEntry() }
        val index = c.currentMediaItemIndex
        _state.value = PlayerState(
            connected = true,
            current = queue.getOrNull(index),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue,
            currentIndex = index,
            hasNext = c.hasNextMediaItem(),
        )
    }

    /** After a restart, put back what was playing (paused) so one tap resumes it. */
    private fun restoreLastQueue(c: MediaController) {
        if (restoreChecked || c.mediaItemCount > 0) return
        restoreChecked = true
        scope.launch {
            val saved = settings.savedQueue() ?: return@launch
            val byId = database.tracks().getMany(saved.videoIds).associateBy { it.videoId }
            val tracks = saved.videoIds.mapNotNull { byId[it] }.filter { it.status == TrackStatus.DONE && it.filePath != null }
            if (tracks.isEmpty() || controller !== c || c.mediaItemCount > 0) return@launch
            val currentId = saved.videoIds.getOrNull(saved.index)
            val index = tracks.indexOfFirst { it.videoId == currentId }
            c.setMediaItems(tracks.map { it.toMediaItem() }, index.coerceAtLeast(0), if (index >= 0) saved.positionMs else 0L)
            c.prepare()
        }
    }

    private fun MediaItem.toEntry() = QueueEntry(
        mediaId = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        artwork = mediaMetadata.artworkUri,
    )

    companion object {
        fun TrackEntity.toMediaItem(): MediaItem {
            val uri = Uri.fromFile(File(requireNotNull(filePath)))
            val artwork = coverPath?.let { Uri.fromFile(File(it)) } ?: thumbnailUrl?.let(Uri::parse)
            return MediaItem.Builder()
                .setMediaId(videoId)
                .setUri(uri)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .setArtworkUri(artwork)
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .build(),
                )
                .build()
        }
    }
}
