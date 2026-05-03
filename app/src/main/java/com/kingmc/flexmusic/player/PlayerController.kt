package com.kingmc.flexmusic.player

import com.kingmc.flexmusic.data.model.Song
import kotlinx.coroutines.flow.StateFlow

interface PlayerController {
    val playbackState: StateFlow<PlaybackUiState>

    fun setQueue(queue: List<Song>, startIndex: Int = 0)
    fun playSong(song: Song, queue: List<Song>)
    fun playOrPause()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun setMode(mode: PlaybackMode)
    fun updateOnlineCoverUrl(songId: Long, coverUrl: String?)
}
