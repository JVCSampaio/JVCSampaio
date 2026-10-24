package io.github.jvcsampaio.sonar.sync

import io.github.jvcsampaio.sonar.data.LibraryStorage
import io.github.jvcsampaio.sonar.data.SettingsRepository
import io.github.jvcsampaio.sonar.data.db.SonarDatabase
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import io.github.jvcsampaio.sonar.data.youtube.GoogleAuth
import io.github.jvcsampaio.sonar.data.youtube.LikedVideo
import io.github.jvcsampaio.sonar.data.youtube.YouTubeApi
import io.github.jvcsampaio.sonar.data.youtube.YouTubeApiException
import io.github.jvcsampaio.sonar.domain.FilterSettings
import io.github.jvcsampaio.sonar.domain.MetadataCleaner
import io.github.jvcsampaio.sonar.domain.TrackFilter

/** Reads the liked videos from YouTube and brings the local library in line with them. */
class LikeSync(
    private val database: SonarDatabase,
    private val settings: SettingsRepository,
    private val youtube: YouTubeApi,
    private val auth: GoogleAuth,
    private val storage: LibraryStorage,
) {
    sealed interface Outcome {
        data class Success(val liked: Int, val added: Int) : Outcome
        data object NeedsSignIn : Outcome
    }

    suspend fun run(): Outcome {
        val token = auth.silentToken() ?: return Outcome.NeedsSignIn
        val liked = try {
            youtube.likedVideos(token)
        } catch (e: YouTubeApiException) {
            if (!e.isAuthError) throw e
            auth.invalidate(token)
            val fresh = auth.silentToken() ?: return Outcome.NeedsSignIn
            youtube.likedVideos(fresh)
        }
        return merge(liked)
    }

    private suspend fun merge(liked: List<LikedVideo>): Outcome.Success {
        val current = settings.current()
        val filters = current.filters
        val now = System.currentTimeMillis()
        val dao = database.tracks()
        val existing = dao.all().associateBy { it.videoId }
        // The API lists the newest like first; the position keeps that order in likedAt.
        val position = liked.withIndex().associate { it.value.id to it.index }

        val added = liked.mapIndexedNotNull { index, video ->
            if (video.id in existing) null else video.toTrack(likedAt = now - index, discoveredAt = now, filters = filters)
        }
        dao.insertAll(added)

        val changed = existing.values.mapNotNull { track ->
            val index = position[track.videoId]
            when {
                index == null && track.stillLiked -> unliked(track)
                index != null && !track.stillLiked -> reliked(track, likedAt = now - index, filters = filters)
                else -> null
            }
        }
        if (current.removeUnliked) {
            val gone = changed.filter { !it.stillLiked }
            gone.forEach(storage::deleteFiles)
            dao.deleteAll(gone)
            dao.updateAll(changed.filter { it.stillLiked })
        } else {
            dao.updateAll(changed)
        }

        dao.requeueRetryable(MAX_ATTEMPTS)
        settings.update { it.copy(lastSyncAt = now, lastSyncFound = liked.size, lastSyncError = null) }
        return Outcome.Success(liked = liked.size, added = added.size)
    }

    private fun unliked(track: TrackEntity): TrackEntity =
        if (track.status == TrackStatus.QUEUED || track.status == TrackStatus.FAILED) {
            track.copy(stillLiked = false, status = TrackStatus.SKIPPED, statusNote = UNLIKED_NOTE, manual = false)
        } else {
            track.copy(stillLiked = false)
        }

    private fun reliked(track: TrackEntity, likedAt: Long, filters: FilterSettings): TrackEntity {
        val back = track.copy(stillLiked = true, likedAt = likedAt)
        if (track.status != TrackStatus.SKIPPED || track.statusNote != UNLIKED_NOTE) return back
        val reason = TrackFilter.skipReason(track.categoryId, track.durationMs, filters)
        return back.copy(status = if (reason == null) TrackStatus.QUEUED else TrackStatus.SKIPPED, statusNote = reason, attempts = 0)
    }

    private fun LikedVideo.toTrack(likedAt: Long, discoveredAt: Long, filters: FilterSettings): TrackEntity {
        val song = MetadataCleaner.clean(title, channelTitle)
        val reason = TrackFilter.skipReason(categoryId, durationMs, filters)
        return TrackEntity(
            videoId = id,
            title = song.title,
            artist = song.artist,
            sourceTitle = title,
            channelTitle = channelTitle,
            durationMs = durationMs,
            categoryId = categoryId,
            thumbnailUrl = thumbnailUrl,
            likedAt = likedAt,
            discoveredAt = discoveredAt,
            status = if (reason == null) TrackStatus.QUEUED else TrackStatus.SKIPPED,
            statusNote = reason,
        )
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val UNLIKED_NOTE = "Você descurtiu no YouTube"
    }
}
