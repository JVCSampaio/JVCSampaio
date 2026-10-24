package io.github.jvcsampaio.sonar.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE status = 'DONE' ORDER BY likedAt DESC")
    fun observeDownloaded(): Flow<List<TrackEntity>>

    @Query(
        """SELECT * FROM tracks WHERE status != 'DONE'
        ORDER BY CASE status WHEN 'DOWNLOADING' THEN 0 WHEN 'QUEUED' THEN 1 WHEN 'FAILED' THEN 2 ELSE 3 END,
        likedAt DESC""",
    )
    fun observePending(): Flow<List<TrackEntity>>

    @Query("SELECT status, COUNT(*) AS count FROM tracks GROUP BY status")
    fun observeStatusCounts(): Flow<List<StatusCount>>

    @Query("SELECT COALESCE(SUM(fileBytes), 0) FROM tracks WHERE status = 'DONE'")
    fun observeStorageBytes(): Flow<Long>

    @Query("SELECT * FROM tracks WHERE videoId IN (:ids)")
    suspend fun getMany(ids: List<String>): List<TrackEntity>

    @Query("SELECT * FROM tracks")
    suspend fun all(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Update
    suspend fun update(track: TrackEntity)

    @Update
    suspend fun updateAll(tracks: List<TrackEntity>)

    @Delete
    suspend fun deleteAll(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE status = 'QUEUED' ORDER BY likedAt DESC LIMIT 1")
    suspend fun nextQueued(): TrackEntity?

    @Query("SELECT COUNT(*) FROM tracks WHERE status = 'QUEUED'")
    suspend fun queuedCount(): Int

    /** A download that was running when the process died goes back to the queue. */
    @Query("UPDATE tracks SET status = 'QUEUED' WHERE status = 'DOWNLOADING'")
    suspend fun requeueInterrupted()

    @Query("UPDATE tracks SET status = 'QUEUED', statusNote = NULL, attempts = 0 WHERE status = 'FAILED'")
    suspend fun retryAllFailed()

    @Query("UPDATE tracks SET status = 'QUEUED', statusNote = NULL WHERE status = 'FAILED' AND attempts < :maxAttempts")
    suspend fun requeueRetryable(maxAttempts: Int)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :at WHERE videoId = :videoId")
    suspend fun markPlayed(videoId: String, at: Long)
}

@Dao
abstract class PlaylistDao {
    @Query(
        """SELECT p.id, p.name, p.createdAt,
        (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount,
        (SELECT t.coverPath FROM playlist_tracks pt JOIN tracks t ON t.videoId = pt.videoId
            WHERE pt.playlistId = p.id ORDER BY pt.position LIMIT 1) AS coverPath,
        (SELECT t.thumbnailUrl FROM playlist_tracks pt JOIN tracks t ON t.videoId = pt.videoId
            WHERE pt.playlistId = p.id ORDER BY pt.position LIMIT 1) AS thumbnailUrl
        FROM playlists p ORDER BY p.createdAt DESC""",
    )
    abstract fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun observe(id: Long): Flow<PlaylistEntity?>

    @Query(
        """SELECT t.* FROM playlist_tracks pt JOIN tracks t ON t.videoId = pt.videoId
        WHERE pt.playlistId = :id AND t.status = 'DONE' ORDER BY pt.position""",
    )
    abstract fun observeTracks(id: Long): Flow<List<TrackEntity>>

    @Insert
    abstract suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :id")
    protected abstract suspend fun maxPosition(id: Long): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEntry(entry: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id AND videoId = :videoId")
    abstract suspend fun removeTrack(id: Long, videoId: String)

    @Transaction
    open suspend fun append(id: Long, videoId: String) {
        insertEntry(PlaylistTrackEntity(id, videoId, maxPosition(id) + 1))
    }
}
