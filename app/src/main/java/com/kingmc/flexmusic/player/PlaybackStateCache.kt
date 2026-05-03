package com.kingmc.flexmusic.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_state")

data class SavedPlaybackState(
    val currentSongId: Long = -1,
    val positionMs: Long = 0,
    val queueSongIds: List<Long> = emptyList(),
    val playbackMode: PlaybackMode = PlaybackMode.ORDER,
    val wasPlaying: Boolean = false
)

@Singleton
class PlaybackStateCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val CURRENT_SONG_ID = longPreferencesKey("current_song_id")
        val POSITION_MS = longPreferencesKey("position_ms")
        val QUEUE_SONG_IDS = stringSetPreferencesKey("queue_song_ids")
        val PLAYBACK_MODE = stringPreferencesKey("playback_mode")
        val WAS_PLAYING = stringPreferencesKey("was_playing")
    }

    val savedState: Flow<SavedPlaybackState> = context.playbackStateDataStore.data.map { preferences ->
        val songId = preferences[Keys.CURRENT_SONG_ID] ?: -1
        val positionMs = preferences[Keys.POSITION_MS] ?: 0
        val queueIds = preferences[Keys.QUEUE_SONG_IDS]?.mapNotNull { it.toLongOrNull() } ?: emptyList()
        val modeName = preferences[Keys.PLAYBACK_MODE] ?: PlaybackMode.ORDER.name
        val wasPlaying = preferences[Keys.WAS_PLAYING] == "true"

        SavedPlaybackState(
            currentSongId = songId,
            positionMs = positionMs,
            queueSongIds = queueIds,
            playbackMode = try { PlaybackMode.valueOf(modeName) } catch (_: Exception) { PlaybackMode.ORDER },
            wasPlaying = wasPlaying
        )
    }

    suspend fun saveState(state: SavedPlaybackState) {
        context.playbackStateDataStore.edit { preferences ->
            preferences[Keys.CURRENT_SONG_ID] = state.currentSongId
            preferences[Keys.POSITION_MS] = state.positionMs
            preferences[Keys.QUEUE_SONG_IDS] = state.queueSongIds.map { it.toString() }.toSet()
            preferences[Keys.PLAYBACK_MODE] = state.playbackMode.name
            preferences[Keys.WAS_PLAYING] = if (state.wasPlaying) "true" else "false"
        }
    }

    suspend fun clearState() {
        context.playbackStateDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
