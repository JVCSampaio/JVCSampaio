package io.github.jvcsampaio.sonar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.jvcsampaio.sonar.domain.FilterSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AudioFormat(
    val label: String,
    val description: String,
    /** yt-dlp format selector. */
    val selector: String,
    /** yt-dlp --audio-format value. */
    val extension: String,
) {
    OPUS("Opus", "Melhor qualidade por MB. Recomendado.", "bestaudio[acodec=opus]/bestaudio/best", "opus"),
    M4A("M4A (AAC)", "Toca em qualquer aparelho, carro ou computador.", "bestaudio[ext=m4a]/bestaudio/best", "m4a"),
    MP3("MP3", "Máxima compatibilidade; é recodificado e perde um pouco de qualidade.", "bestaudio/best", "mp3"),
}

data class Settings(
    val audioFormat: AudioFormat = AudioFormat.OPUS,
    val musicOnly: Boolean = true,
    val skipShort: Boolean = true,
    val maxDurationMinutes: Int = 15,
    val wifiOnly: Boolean = true,
    val autoSync: Boolean = true,
    val syncIntervalHours: Int = 6,
    val exportToMusic: Boolean = false,
    val removeUnliked: Boolean = false,
    val connected: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastSyncFound: Int = 0,
    val lastSyncError: String? = null,
    val engineCheckedAt: Long = 0,
) {
    val filters get() = FilterSettings(musicOnly, skipShort, maxDurationMinutes)
}

/** What was playing when the app last stopped, so it can be resumed. */
data class SavedQueue(val videoIds: List<String>, val index: Int, val positionMs: Long)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.dataStore

    val settings: Flow<Settings> = store.data
        .catch { emit(emptyPreferences()) }
        .map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    suspend fun saveQueue(queue: SavedQueue) {
        store.edit {
            it[Keys.queueIds] = queue.videoIds.joinToString(",")
            it[Keys.queueIndex] = queue.index
            it[Keys.queuePosition] = queue.positionMs
        }
    }

    suspend fun savedQueue(): SavedQueue? {
        val prefs = store.data.catch { emit(emptyPreferences()) }.first()
        val ids = prefs[Keys.queueIds]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        if (ids.isEmpty()) return null
        return SavedQueue(ids, prefs[Keys.queueIndex] ?: 0, prefs[Keys.queuePosition] ?: 0)
    }

    private fun Preferences.toSettings() = Settings(
        audioFormat = this[Keys.audioFormat]?.let { name -> AudioFormat.entries.firstOrNull { it.name == name } }
            ?: AudioFormat.OPUS,
        musicOnly = this[Keys.musicOnly] ?: true,
        skipShort = this[Keys.skipShort] ?: true,
        maxDurationMinutes = this[Keys.maxDuration] ?: 15,
        wifiOnly = this[Keys.wifiOnly] ?: true,
        autoSync = this[Keys.autoSync] ?: true,
        syncIntervalHours = this[Keys.syncInterval] ?: 6,
        exportToMusic = this[Keys.exportToMusic] ?: false,
        removeUnliked = this[Keys.removeUnliked] ?: false,
        connected = this[Keys.connected] ?: false,
        lastSyncAt = this[Keys.lastSyncAt] ?: 0,
        lastSyncFound = this[Keys.lastSyncFound] ?: 0,
        lastSyncError = this[Keys.lastSyncError],
        engineCheckedAt = this[Keys.engineCheckedAt] ?: 0,
    )

    private fun MutablePreferences.write(s: Settings) {
        this[Keys.audioFormat] = s.audioFormat.name
        this[Keys.musicOnly] = s.musicOnly
        this[Keys.skipShort] = s.skipShort
        this[Keys.maxDuration] = s.maxDurationMinutes
        this[Keys.wifiOnly] = s.wifiOnly
        this[Keys.autoSync] = s.autoSync
        this[Keys.syncInterval] = s.syncIntervalHours
        this[Keys.exportToMusic] = s.exportToMusic
        this[Keys.removeUnliked] = s.removeUnliked
        this[Keys.connected] = s.connected
        this[Keys.lastSyncAt] = s.lastSyncAt
        this[Keys.lastSyncFound] = s.lastSyncFound
        if (s.lastSyncError != null) this[Keys.lastSyncError] = s.lastSyncError else remove(Keys.lastSyncError)
        this[Keys.engineCheckedAt] = s.engineCheckedAt
    }

    private object Keys {
        val audioFormat = stringPreferencesKey("audio_format")
        val musicOnly = booleanPreferencesKey("music_only")
        val skipShort = booleanPreferencesKey("skip_short")
        val maxDuration = intPreferencesKey("max_duration_minutes")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val autoSync = booleanPreferencesKey("auto_sync")
        val syncInterval = intPreferencesKey("sync_interval_hours")
        val exportToMusic = booleanPreferencesKey("export_to_music")
        val removeUnliked = booleanPreferencesKey("remove_unliked")
        val connected = booleanPreferencesKey("connected")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncFound = intPreferencesKey("last_sync_found")
        val lastSyncError = stringPreferencesKey("last_sync_error")
        val engineCheckedAt = longPreferencesKey("engine_checked_at")
        val queueIds = stringPreferencesKey("queue_ids")
        val queueIndex = intPreferencesKey("queue_index")
        val queuePosition = longPreferencesKey("queue_position")
    }
}
