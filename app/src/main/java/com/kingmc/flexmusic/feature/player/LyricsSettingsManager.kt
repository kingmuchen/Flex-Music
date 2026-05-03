package com.kingmc.flexmusic.feature.player

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lyricsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lyrics_settings")

@Singleton
class LyricsSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val ACTIVE_LINE_COLOR = intPreferencesKey("active_line_color")
        val ENABLE_WORD_BY_WORD = booleanPreferencesKey("enable_word_by_word")
    }

    val settings: Flow<LyricsSettings> = context.lyricsDataStore.data.map { preferences ->
        LyricsSettings(
            fontSize = (preferences[Keys.FONT_SIZE] ?: 20f).sp,
            activeLineColor = Color(preferences[Keys.ACTIVE_LINE_COLOR] ?: Color(0xFF10B981).toArgb()),
            enableWordByWord = preferences[Keys.ENABLE_WORD_BY_WORD] ?: false
        )
    }

    suspend fun updateFontSize(fontSize: Float) {
        context.lyricsDataStore.edit { preferences ->
            preferences[Keys.FONT_SIZE] = fontSize
        }
    }

    suspend fun updateActiveLineColor(color: Color) {
        context.lyricsDataStore.edit { preferences ->
            preferences[Keys.ACTIVE_LINE_COLOR] = color.toArgb()
        }
    }

    suspend fun updateWordByWordEnabled(enabled: Boolean) {
        context.lyricsDataStore.edit { preferences ->
            preferences[Keys.ENABLE_WORD_BY_WORD] = enabled
        }
    }

    suspend fun updateSettings(settings: LyricsSettings) {
        context.lyricsDataStore.edit { preferences ->
            preferences[Keys.FONT_SIZE] = settings.fontSize.value
            preferences[Keys.ACTIVE_LINE_COLOR] = settings.activeLineColor.toArgb()
            preferences[Keys.ENABLE_WORD_BY_WORD] = settings.enableWordByWord
        }
    }
}
