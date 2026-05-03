package com.kingmc.flexmusic.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kingmc.flexmusic.data.local.FlexMusicDatabase
import com.kingmc.flexmusic.data.model.Song
import com.kingmc.flexmusic.data.repository.MusicRepository
import com.kingmc.flexmusic.feature.player.lyrics.LyricsRepository
import com.kingmc.flexmusic.feature.player.lyrics.OnlineLyricsService
import com.kingmc.flexmusic.feature.settings.AppSettingsManager
import com.kingmc.flexmusic.player.Media3PlayerController
import com.kingmc.flexmusic.player.PlaybackUiState
import com.kingmc.flexmusic.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val recentSongs: List<Song> = emptyList(),
    val statusMessage: String? = null,
    val showAllSongs: Boolean = false,
    val isFetchingExtras: Boolean = false,
    val fetchProgress: String? = null,
    val hasScanned: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playerController: PlayerController,
    private val media3PlayerController: Media3PlayerController,
    private val appSettingsManager: AppSettingsManager,
    private val lyricsRepository: LyricsRepository,
    private val onlineLyricsService: OnlineLyricsService,
    private val database: FlexMusicDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackUiState> = playerController.playbackState

    private var autoPlayAttempted = false
    private var allSongs = emptyList<Song>()

    init {
        viewModelScope.launch {
            repository.observeSongs().collectLatest { songs ->
                allSongs = songs
                _uiState.update {
                    it.copy(
                        songs = if (it.query.isBlank()) songs else songs.filter { song ->
                            song.title.contains(it.query, ignoreCase = true) ||
                                song.artist.contains(it.query, ignoreCase = true)
                        },
                        hasScanned = it.hasScanned || songs.isNotEmpty()
                    )
                }

                if (!autoPlayAttempted && songs.isNotEmpty()) {
                    autoPlayAttempted = true
                    tryRestoreOrAutoPlay(songs)
                }
            }
        }

        viewModelScope.launch {
            _uiState.map { it.query }.distinctUntilChanged().collectLatest { query ->
                _uiState.update {
                    it.copy(
                        songs = if (query.isBlank()) allSongs else allSongs.filter { song ->
                            song.title.contains(query, ignoreCase = true) ||
                                song.artist.contains(query, ignoreCase = true)
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.observeRecentSongs(limit = 8).collectLatest { recentSongs ->
                _uiState.update { it.copy(recentSongs = recentSongs) }
            }
        }

        viewModelScope.launch {
            playbackState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collectLatest { currentSongId ->
                    if (currentSongId != null) {
                        repository.markSongPlayed(currentSongId)
                    }
                }
        }

        viewModelScope.launch {
            playbackState
                .map { it.errorMessage }
                .distinctUntilChanged()
                .collectLatest { errorMessage ->
                    if (errorMessage != null) {
                        _uiState.update { it.copy(statusMessage = errorMessage) }
                    }
                }
        }
    }

    private suspend fun tryRestoreOrAutoPlay(allSongs: List<Song>) {
        if (allSongs.isEmpty()) return

        val restored = media3PlayerController.restorePlaybackState(allSongs)
        if (restored) return

        val settings = appSettingsManager.settings.first()
        if (settings.autoPlay) {
            val recentSongs = allSongs.sortedByDescending { it.lastPlayedAt }.filter { it.lastPlayedAt > 0 }
            val song = recentSongs.firstOrNull() ?: allSongs.first()
            playerController.playSong(song, allSongs)
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun toggleShowAllSongs() {
        _uiState.update { it.copy(showAllSongs = !_uiState.value.showAllSongs) }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null, hasScanned = true) }
            val result = repository.refreshLocalLibrary()
            _uiState.update {
                if (result.isSuccess) {
                    val count = result.getOrDefault(0)
                    val message = if (count == 0) {
                        "\u672a\u53d1\u73b0\u53ef\u64ad\u653e\u97f3\u9891\uff0c\u8bf7\u68c0\u67e5\u97f3\u9891\u76ee\u5f55"
                    } else {
                        "\u5df2\u626b\u63cf $count \u9996\u6b4c\u66f2"
                    }
                    it.copy(isLoading = false, statusMessage = message)
                } else {
                    val error = result.exceptionOrNull()
                    val message = if (error is SecurityException) {
                        "\u7f3a\u5c11\u5a92\u4f53\u8bfb\u53d6\u6743\u9650\uff0c\u8bf7\u5148\u6388\u6743"
                    } else {
                        "\u626b\u63cf\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
                    }
                    it.copy(isLoading = false, statusMessage = message)
                }
            }

            fetchMissingExtras()
        }
    }

    private suspend fun fetchMissingExtras() {
        val songs = database.songDao().getSongsOnce()
        if (songs.isEmpty()) return

        val songIds = songs.map { it.id }
        val songsMissingCover = database.songDao().getSongsMissingCover(songIds)
        val songsMissingLyrics = database.songDao().getSongsMissingLyrics(songIds)

        val songsNeedCover = songsMissingCover.filter { entity ->
            val song = entityToSong(entity)
            !lyricsRepository.hasLocalCover(song)
        }

        val songsNeedLyrics = songsMissingLyrics.filter { entity ->
            val song = entityToSong(entity)
            !lyricsRepository.hasLocalLyrics(song)
        }

        val totalMissing = songsNeedCover.size + songsNeedLyrics.size
        if (totalMissing == 0) {
            _uiState.update { it.copy(statusMessage = "\u6240\u6709\u6b4c\u66f2\u5747\u5df2\u6709\u5c01\u9762\u548c\u6b4c\u8bcd") }
            return
        }

        _uiState.update { it.copy(isFetchingExtras = true, fetchProgress = "\u6b63\u5728\u83b7\u53d6\u7f3a\u5931\u7684\u5c01\u9762\u548c\u6b4c\u8bcd...") }

        var processed = 0

        for (entity in songsNeedCover) {
            try {
                val song = entityToSong(entity)
                val coverResult = onlineLyricsService.searchCover(song)
                if (coverResult != null) {
                    database.songDao().updateCoverUrl(entity.id, coverResult.coverUrl)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch cover for: ${entity.title}", e)
            }
            processed++
            _uiState.update {
                it.copy(fetchProgress = "\u6b63\u5728\u83b7\u53d6\u5c01\u9762 ($processed/${songsNeedCover.size})...")
            }
        }

        processed = 0
        for (entity in songsNeedLyrics) {
            try {
                val song = entityToSong(entity)
                val lyricsResult = onlineLyricsService.searchLyrics(song)
                if (lyricsResult != null && lyricsResult.lyrics.isNotBlank()) {
                    database.songDao().updateCachedLyrics(
                        songId = entity.id,
                        lyrics = lyricsResult.lyrics,
                        source = lyricsResult.source,
                        offset = lyricsResult.offset
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch lyrics for: ${entity.title}", e)
            }
            processed++
            _uiState.update {
                it.copy(fetchProgress = "\u6b63\u5728\u83b7\u53d6\u6b4c\u8bcd ($processed/${songsNeedLyrics.size})...")
            }
        }

        _uiState.update {
            it.copy(
                isFetchingExtras = false,
                fetchProgress = null,
                statusMessage = "\u5df2\u5b8c\u6210\u5c01\u9762\u548c\u6b4c\u8bcd\u83b7\u53d6"
            )
        }
    }

    private fun entityToSong(entity: com.kingmc.flexmusic.data.local.SongEntity): Song {
        return Song(
            id = entity.id,
            title = entity.title,
            artist = entity.artist,
            album = entity.album,
            durationMs = entity.durationMs,
            contentUri = entity.contentUri,
            albumArtUri = entity.albumArtUri,
            displayName = entity.displayName,
            relativePath = entity.relativePath
        )
    }

    fun playSong(song: Song) {
        val queue = _uiState.value.songs
        if (queue.isNotEmpty()) {
            playerController.playSong(song, queue)
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.setFavorite(song.id, !song.isFavorite)
        }
    }

    fun playOrPause() = playerController.playOrPause()
    fun skipNext() = playerController.skipNext()
    fun skipPrevious() = playerController.skipPrevious()

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
