package com.kingmc.flexmusic.player

import com.kingmc.flexmusic.data.model.Song

enum class PlaybackMode {
    ORDER,
    SHUFFLE,
    REPEAT_ONE
}

data class PlaybackUiState(
    val queue: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val mode: PlaybackMode = PlaybackMode.ORDER,
    val errorMessage: String? = null,
    val onlineCoverUrl: String? = null
)
