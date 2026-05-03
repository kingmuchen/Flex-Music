package com.kingmc.flexmusic.feature.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kingmc.flexmusic.feature.player.lyrics.AudioAnalyzer
import com.kingmc.flexmusic.feature.player.lyrics.CachedOffset
import com.kingmc.flexmusic.feature.player.lyrics.LyricAlignManager
import com.kingmc.flexmusic.feature.player.lyrics.LyricDocument
import com.kingmc.flexmusic.feature.player.lyrics.LyricOffsetCache
import com.kingmc.flexmusic.feature.player.lyrics.LyricsRepository
import com.kingmc.flexmusic.feature.player.lyrics.OffsetInfo
import com.kingmc.flexmusic.feature.player.lyrics.OnlineLyricsService
import com.kingmc.flexmusic.player.PlaybackMode
import com.kingmc.flexmusic.player.PlaybackUiState
import com.kingmc.flexmusic.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FullPlayerUiState(
    val lyrics: LyricDocument? = null,
    val loadingLyrics: Boolean = false,
    val lyricsSource: String = "",
    val lyricsSettings: LyricsSettings = LyricsSettings(),
    val offsetInfo: OffsetInfo = OffsetInfo(0L, 0L),
    val analysisResult: AudioAnalyzer.AnalysisResult? = null,
    val analyzing: Boolean = false,
    val onlineCoverUrl: String? = null,
    val loadingCover: Boolean = false
)

@HiltViewModel
class FullPlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val lyricsRepository: LyricsRepository,
    private val lyricsSettingsManager: LyricsSettingsManager,
    private val lyricAlignManager: LyricAlignManager,
    private val lyricOffsetCache: LyricOffsetCache,
    private val audioAnalyzer: AudioAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow(FullPlayerUiState())
    val uiState: StateFlow<FullPlayerUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackUiState> = playerController.playbackState

    init {
        viewModelScope.launch {
            playbackState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collectLatest {
                    val song = playbackState.value.currentSong ?: return@collectLatest
                    _uiState.update { it.copy(loadingLyrics = true) }
                    
                    val doc = lyricsRepository.loadLyrics(song)
                    _uiState.update {
                        it.copy(
                            lyrics = doc,
                            loadingLyrics = false,
                            lyricsSource = doc.source,
                            offsetInfo = lyricAlignManager.getOffsetInfo()
                        )
                    }
                }
        }

        viewModelScope.launch {
            playbackState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collectLatest {
                    val song = playbackState.value.currentSong ?: return@collectLatest
                    _uiState.update { it.copy(loadingCover = true, onlineCoverUrl = null) }
                    try {
                        val coverResult = lyricsRepository.searchOnlineCover(song)
                        if (coverResult != null) {
                            android.util.Log.d("FullPlayerVM", "Cover found: url=${coverResult.coverUrl}, source=${coverResult.source}")
                            _uiState.update {
                                it.copy(
                                    onlineCoverUrl = coverResult.coverUrl,
                                    loadingCover = false
                                )
                            }
                            playerController.updateOnlineCoverUrl(song.id, coverResult.coverUrl)
                        } else {
                            android.util.Log.d("FullPlayerVM", "No cover found for: ${song.title} - ${song.artist}")
                            _uiState.update { it.copy(loadingCover = false) }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FullPlayerVM", "Cover search error: ${e.message}")
                        _uiState.update { it.copy(loadingCover = false) }
                    }
                }
        }
        
        viewModelScope.launch {
            lyricsSettingsManager.settings.collect { settings ->
                _uiState.update { it.copy(lyricsSettings = settings) }
            }
        }
    }

    fun playOrPause() = playerController.playOrPause()
    fun skipNext() = playerController.skipNext()
    fun skipPrevious() = playerController.skipPrevious()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    
    fun cyclePlaybackMode() {
        val currentMode = playbackState.value.mode
        val nextMode = when (currentMode) {
            PlaybackMode.ORDER -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.ORDER
        }
        playerController.setMode(nextMode)
    }
    
    fun updateLyricsSettings(settings: LyricsSettings) {
        viewModelScope.launch {
            lyricsSettingsManager.updateSettings(settings)
        }
    }

    fun adjustLyricOffset(deltaMs: Long) {
        val songId = playbackState.value.currentSong?.id ?: return
        lyricsRepository.adjustUserOffset(songId, deltaMs)
        _uiState.update { it.copy(offsetInfo = lyricAlignManager.getOffsetInfo()) }
    }

    fun resetLyricOffset() {
        val songId = playbackState.value.currentSong?.id ?: return
        lyricsRepository.resetOffsets(songId)
        _uiState.update { it.copy(offsetInfo = lyricAlignManager.getOffsetInfo()) }
    }

    fun getAdjustedPosition(currentPos: Long): Long {
        return lyricAlignManager.getAdjustedPosition(currentPos)
    }

    fun getTotalOffset(): Long {
        return lyricAlignManager.totalOffset
    }
    
    fun analyzeCurrentSong() {
        val song = playbackState.value.currentSong ?: return
        val uri = Uri.parse(song.contentUri)
        
        viewModelScope.launch {
            _uiState.update { it.copy(analyzing = true) }
            val result = audioAnalyzer.analyzeAudio(uri)
            _uiState.update { 
                it.copy(
                    analysisResult = result,
                    analyzing = false
                )
            }
        }
    }
    
    fun applyAnalysisOffset() {
        val result = _uiState.value.analysisResult ?: return
        val songId = playbackState.value.currentSong?.id ?: return
        
        if (result.silenceDurationMs > 0) {
            lyricsRepository.adjustUserOffset(songId, -result.silenceDurationMs)
            _uiState.update { it.copy(offsetInfo = lyricAlignManager.getOffsetInfo()) }
        }
    }
    
    fun clearAnalysisResult() {
        _uiState.update { it.copy(analysisResult = null) }
    }
    
    fun searchCover() {
        val song = playbackState.value.currentSong ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(loadingCover = true) }
            val coverResult = lyricsRepository.searchOnlineCover(song)
            if (coverResult != null) {
                _uiState.update { 
                    it.copy(
                        onlineCoverUrl = coverResult.coverUrl,
                        loadingCover = false
                    )
                }
                playerController.updateOnlineCoverUrl(song.id, coverResult.coverUrl)
            } else {
                _uiState.update { it.copy(loadingCover = false) }
            }
        }
    }
}
