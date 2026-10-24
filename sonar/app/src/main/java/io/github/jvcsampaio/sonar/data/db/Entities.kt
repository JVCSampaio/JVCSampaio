package io.github.jvcsampaio.sonar.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

enum class TrackStatus { QUEUED, DOWNLOADING, DONE, FAILED, SKIPPED }

/** One liked YouTube video, from discovery to a playable file on the device. */
@Entity(tableName = "tracks", indices = [Index("status"), Index("likedAt")])
data class TrackEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val sourceTitle: String,
    val channelTitle: String,
    val durationMs: Long,
    val categoryId: String?,
    val thumbnailUrl: String?,
    /** Ordering key: newer likes have larger values. */
    val likedAt: Long,
    val discoveredAt: Long,
    val stillLiked: Boolean = true,
    val status: TrackStatus = TrackStatus.QUEUED,
    /** Why the track was skipped, or the last download error. */
    val statusNote: String? = null,
    /** The user overrode the automatic filters for this track. */
    val manual: Boolean = false,
    val attempts: Int = 0,
    val filePath: String? = null,
    val coverPath: String? = null,
    val fileBytes: Long = 0,
    val downloadedAt: Long? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
)

/** Local cover when downloaded, YouTube thumbnail otherwise. */
val TrackEntity.artwork: Any? get() = coverPath?.let(::File) ?: thumbnailUrl

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["videoId"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("videoId")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val videoId: String,
    val position: Long,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
    val coverPath: String?,
    val thumbnailUrl: String?,
)

val PlaylistSummary.artwork: Any? get() = coverPath?.let(::File) ?: thumbnailUrl

data class StatusCount(val status: TrackStatus, val count: Int)
