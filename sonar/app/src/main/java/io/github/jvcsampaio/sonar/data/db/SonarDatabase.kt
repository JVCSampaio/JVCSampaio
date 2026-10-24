package io.github.jvcsampaio.sonar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SonarDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun playlists(): PlaylistDao

    companion object {
        fun build(context: Context): SonarDatabase =
            Room.databaseBuilder(context, SonarDatabase::class.java, "sonar.db").build()
    }
}
