package com.kingmc.flexmusic.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val smartLyricsMatch: Boolean = true,
    val autoPlay: Boolean = false,
    val showNotification: Boolean = true,
    val rememberProgress: Boolean = true
)

@Singleton
class AppSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SMART_LYRICS_MATCH = booleanPreferencesKey("smart_lyrics_match")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        val REMEMBER_PROGRESS = booleanPreferencesKey("remember_progress")
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { preferences ->
        AppSettings(
            smartLyricsMatch = preferences[Keys.SMART_LYRICS_MATCH] ?: true,
            autoPlay = preferences[Keys.AUTO_PLAY] ?: false,
            showNotification = preferences[Keys.SHOW_NOTIFICATION] ?: true,
            rememberProgress = preferences[Keys.REMEMBER_PROGRESS] ?: true
        )
    }

    suspend fun updateSmartLyricsMatch(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.SMART_LYRICS_MATCH] = enabled
        }
    }

    suspend fun updateAutoPlay(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.AUTO_PLAY] = enabled
        }
    }

    suspend fun updateShowNotification(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.SHOW_NOTIFICATION] = enabled
        }
    }

    suspend fun updateRememberProgress(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.REMEMBER_PROGRESS] = enabled
        }
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.SMART_LYRICS_MATCH] = settings.smartLyricsMatch
            preferences[Keys.AUTO_PLAY] = settings.autoPlay
            preferences[Keys.SHOW_NOTIFICATION] = settings.showNotification
            preferences[Keys.REMEMBER_PROGRESS] = settings.rememberProgress
        }
    }
}
