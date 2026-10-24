package io.github.jvcsampaio.sonar.data

import io.github.jvcsampaio.sonar.data.db.PlaylistEntity
import io.github.jvcsampaio.sonar.data.db.PlaylistSummary
import io.github.jvcsampaio.sonar.data.db.SonarDatabase
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import io.github.jvcsampaio.sonar.domain.FilterSettings
import io.github.jvcsampaio.sonar.domain.TrackFilter
import io.github.jvcsampaio.sonar.sync.LikeSync
import io.github.jvcsampaio.sonar.sync.WorkScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Library reads and the user's actions on tracks and playlists. */
class LibraryRepository(
    database: SonarDatabase,
    private val settings: SettingsRepository,
    private val storage: LibraryStorage,
    private val scheduler: WorkScheduler,
) {
    private val tracks = database.tracks()
    private val playlistDao = database.playlists()

    val downloaded: Flow<List<TrackEntity>> = tracks.observeDownloaded()
    val pending: Flow<List<TrackEntity>> = tracks.observePending()
    val statusCounts: Flow<Map<TrackStatus, Int>> = tracks.observeStatusCounts().map { rows -> rows.associate { it.status to it.count } }
    val storageBytes: Flow<Long> = tracks.observeStorageBytes()
    val playlists: Flow<List<PlaylistSummary>> = playlistDao.observeSummaries()

    fun playlist(id: Long): Flow<PlaylistEntity?> = playlistDao.observe(id)
    fun playlistTracks(id: Long): Flow<List<TrackEntity>> = playlistDao.observeTracks(id)

    suspend fun downloadAnyway(track: TrackEntity) {
        tracks.update(track.copy(status = TrackStatus.QUEUED, statusNote = null, manual = true, attempts = 0))
        scheduler.enqueueDownloads(settings.current().wifiOnly)
    }

    suspend fun skip(track: TrackEntity) {
        tracks.update(track.copy(status = TrackStatus.SKIPPED, statusNote = "Ignorada por você", manual = true))
    }

    suspend fun retryFailed() {
        tracks.retryAllFailed()
        scheduler.enqueueDownloads(settings.current().wifiOnly)
    }

    suspend fun resumeDownloads() = scheduler.enqueueDownloads(settings.current().wifiOnly)

    /** Deletes the file but remembers the like, so it is not downloaded again. */
    suspend fun removeFromDevice(track: TrackEntity) {
        storage.deleteFiles(track)
        tracks.update(
            track.copy(
                status = TrackStatus.SKIPPED,
                statusNote = "Removida do aparelho por você",
                manual = true,
                filePath = null,
                coverPath = null,
                fileBytes = 0,
            ),
        )
    }

    /** Re-evaluates automatic decisions after the filters change in Settings. */
    suspend fun reapplyFilters(filters: FilterSettings) {
        val changed = tracks.all()
            .filter { !it.manual && it.stillLiked && (it.status == TrackStatus.QUEUED || it.status == TrackStatus.SKIPPED) }
            .filter { it.statusNote != LikeSync.UNLIKED_NOTE }
            .mapNotNull { track ->
                val reason = TrackFilter.skipReason(track.categoryId, track.durationMs, filters)
                val status = if (reason == null) TrackStatus.QUEUED else TrackStatus.SKIPPED
                if (status == track.status && reason == track.statusNote) null else track.copy(status = status, statusNote = reason)
            }
        if (changed.isEmpty()) return
        tracks.updateAll(changed)
        if (changed.any { it.status == TrackStatus.QUEUED }) scheduler.enqueueDownloads(settings.current().wifiOnly)
    }

    suspend fun createPlaylist(name: String, firstTrack: TrackEntity? = null): Long {
        val id = playlistDao.insert(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
        if (firstTrack != null) playlistDao.append(id, firstTrack.videoId)
        return id
    }

    suspend fun addToPlaylist(playlistId: Long, track: TrackEntity) = playlistDao.append(playlistId, track.videoId)
    suspend fun removeFromPlaylist(playlistId: Long, track: TrackEntity) = playlistDao.removeTrack(playlistId, track.videoId)
    suspend fun renamePlaylist(playlistId: Long, name: String) = playlistDao.rename(playlistId, name.trim())
    suspend fun deletePlaylist(playlistId: Long) = playlistDao.delete(playlistId)
}
