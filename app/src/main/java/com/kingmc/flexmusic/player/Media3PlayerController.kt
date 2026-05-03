package com.kingmc.flexmusic.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kingmc.flexmusic.data.model.Song
import com.kingmc.flexmusic.feature.player.lyrics.LyricsRepository
import com.kingmc.flexmusic.feature.settings.AppSettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class Media3PlayerController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appSettingsManager: AppSettingsManager,
    private val lyricsRepository: LyricsRepository,
    private val playbackStateCache: PlaybackStateCache
) : PlayerController {

    private val player = ExoPlayer.Builder(appContext).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        setHandleAudioBecomingNoisy(true)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _playbackState = MutableStateFlow(PlaybackUiState())
    @Volatile private var isRestoring = false

    override val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    syncState(isPlaying = isPlaying)
                    if (!isPlaying) {
                        scope.launch { savePlaybackStateIfNeeded() }
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    syncState()
                    scope.launch { savePlaybackStateIfNeeded() }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("FlexMusic.Player", "player error", error)
                    setError("\u64ad\u653e\u5931\u8d25\uff0c\u8bf7\u5c1d\u8bd5\u5207\u6362\u6b4c\u66f2")
                }
            }
        )

        scope.launch {
            while (true) {
                syncState()
                delay(100)
            }
        }

        scope.launch {
            while (true) {
                delay(2000)
                savePlaybackStateIfNeeded()
            }
        }
    }

    override fun setQueue(queue: List<Song>, startIndex: Int) {
        if (queue.isEmpty()) return
        runCatching {
            val items = queue.map { it.toMediaItem() }
            player.setMediaItems(items, startIndex.coerceAtLeast(0), 0L)
            player.prepare()
            _playbackState.value = _playbackState.value.copy(
                queue = queue,
                currentSong = queue.getOrNull(startIndex),
                currentIndex = startIndex.coerceAtLeast(0),
                durationMs = queue.getOrNull(startIndex)?.durationMs ?: 0L,
                errorMessage = null
            )
        }.onFailure { throwable ->
            Log.e("FlexMusic.Player", "setQueue failed", throwable)
            setError("\u64ad\u653e\u961f\u5217\u521d\u59cb\u5316\u5931\u8d25")
        }
    }

    override fun playSong(song: Song, queue: List<Song>) {
        if (queue.isEmpty()) return
        runCatching {
            ensurePlaybackService()
            val index = queue.indexOfFirst { it.id == song.id }.let { if (it == -1) 0 else it }
            setQueue(queue, index)
            player.playWhenReady = true
            player.play()
            syncState(isPlaying = true)
            clearError()
        }.onFailure { throwable ->
            Log.e("FlexMusic.Player", "playSong failed", throwable)
            setError("\u65e0\u6cd5\u64ad\u653e\u5f53\u524d\u6b4c\u66f2\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6\u6743\u9650")
        }
    }

    override fun playOrPause() {
        runCatching {
            if (player.isPlaying) {
                player.pause()
            } else {
                ensurePlaybackService()
                player.play()
            }
            syncState()
            clearError()
        }.onFailure { throwable ->
            Log.e("FlexMusic.Player", "playOrPause failed", throwable)
            setError("\u64ad\u653e\u63a7\u5236\u5931\u8d25")
        }
    }

    override fun seekTo(positionMs: Long) {
        runCatching {
            player.seekTo(positionMs)
            syncState()
        }.onFailure { throwable ->
            Log.e("FlexMusic.Player", "seekTo failed", throwable)
            setError("\u62d6\u52a8\u8fdb\u5ea6\u5931\u8d25")
        }
    }

    override fun skipNext() {
        runCatching {
            if (player.hasNextMediaItem()) {
                ensurePlaybackService()
                player.seekToNextMediaItem()
                if (!player.isPlaying) player.play()
                syncState()
            }
            clearError()
        }.onFailure { throwable ->
            Log.e("FlexMusic.Player", "skipNext failed", throwable)
            setError("\u5207\u6362\u4e0b\u4e00\u9996\u5931\u8d25")
        }
    }

    override fun skipPrevious() {
        runCatching {
            if (player.hasPreviousMediaItem()) {
                ensurePlaybackService()
                player.seekToPreviousMediaItem()
                if (!player.isPlaying) player.play()
                syncState()
            }
            clearError()
        }.onFailure { throwable ->
            Log.e("FlexMusic.Player", "skipPrevious failed", throwable)
            setError("\u5207\u6362\u4e0a\u4e00\u9996\u5931\u8d25")
        }
    }

    override fun setMode(mode: PlaybackMode) {
        when (mode) {
            PlaybackMode.ORDER -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ALL
            }
            PlaybackMode.SHUFFLE -> {
                player.shuffleModeEnabled = true
                player.repeatMode = Player.REPEAT_MODE_ALL
            }
            PlaybackMode.REPEAT_ONE -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ONE
            }
        }
        _playbackState.value = _playbackState.value.copy(mode = mode)
    }

    fun getPlayer(): ExoPlayer = player

    private fun ensurePlaybackService() {
        val intent = Intent(appContext, PlaybackService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }.onFailure { throwable ->
            Log.w("FlexMusic.Player", "start playback service failed", throwable)
        }
    }

    private fun syncState(isPlaying: Boolean = player.isPlaying) {
        if (isRestoring) return

        val queue = _playbackState.value.queue
        val index = player.currentMediaItemIndex
        val newSong = queue.getOrNull(index)
        val previousSongId = _playbackState.value.currentSong?.id

        _playbackState.value = _playbackState.value.copy(
            currentIndex = index,
            currentSong = newSong,
            isPlaying = isPlaying,
            positionMs = player.currentPosition,
            durationMs = if (player.duration > 0) player.duration else (newSong?.durationMs ?: 0L)
        )

        if (newSong != null && newSong.id != previousSongId) {
            scope.launch {
                val coverUrl = lyricsRepository.getCachedCoverUrl(newSong.id)
                if (_playbackState.value.currentSong?.id == newSong.id) {
                    _playbackState.update { it.copy(onlineCoverUrl = coverUrl) }
                }
            }
        }
    }

    override fun updateOnlineCoverUrl(songId: Long, coverUrl: String?) {
        if (_playbackState.value.currentSong?.id == songId) {
            _playbackState.update { it.copy(onlineCoverUrl = coverUrl) }
        }
    }

    private fun setError(message: String) {
        _playbackState.update { it.copy(errorMessage = message) }
    }

    private fun clearError() {
        _playbackState.update { state ->
            if (state.errorMessage == null) state else state.copy(errorMessage = null)
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
        if (!albumArtUri.isNullOrEmpty()) {
            metadataBuilder.setArtworkUri(Uri.parse(albumArtUri))
        }
        return MediaItem.Builder()
            .setUri(Uri.parse(contentUri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private suspend fun savePlaybackStateIfNeeded() {
        val settings = appSettingsManager.settings.first()
        if (!settings.rememberProgress) {
            playbackStateCache.clearState()
            return
        }
        val state = _playbackState.value
        val currentSong = state.currentSong ?: return
        if (state.queue.isEmpty()) return

        playbackStateCache.saveState(
            SavedPlaybackState(
                currentSongId = currentSong.id,
                positionMs = player.currentPosition,
                queueSongIds = state.queue.map { it.id },
                playbackMode = state.mode,
                wasPlaying = player.isPlaying
            )
        )
    }

    suspend fun restorePlaybackState(songs: List<com.kingmc.flexmusic.data.model.Song>): Boolean {
        val saved = playbackStateCache.savedState.first()
        if (saved.currentSongId < 0 || saved.queueSongIds.isEmpty()) return false

        val settings = appSettingsManager.settings.first()
        if (!settings.rememberProgress && !settings.autoPlay) return false

        val songMap = songs.associateBy { it.id }
        val restoredQueue = saved.queueSongIds.mapNotNull { songMap[it] }
        if (restoredQueue.isEmpty()) return false

        val currentIndex = restoredQueue.indexOfFirst { it.id == saved.currentSongId }
        if (currentIndex < 0) return false

        val startPositionMs = if (settings.rememberProgress) saved.positionMs.coerceAtLeast(0) else 0L

        isRestoring = true

        val items = restoredQueue.map { it.toMediaItem() }
        player.setMediaItems(items, currentIndex, startPositionMs)
        player.prepare()

        setMode(saved.playbackMode)

        _playbackState.value = _playbackState.value.copy(
            queue = restoredQueue,
            currentSong = restoredQueue.getOrNull(currentIndex),
            currentIndex = currentIndex,
            positionMs = startPositionMs,
            durationMs = restoredQueue.getOrNull(currentIndex)?.durationMs ?: 0L,
            mode = saved.playbackMode,
            isPlaying = false,
            errorMessage = null
        )

        if (settings.autoPlay) {
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
        }

        scope.launch {
            while (isRestoring) {
                if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED) {
                    isRestoring = false
                    syncState()
                } else {
                    delay(50)
                }
            }
        }

        Log.i("FlexMusic.Player", "Restored playback: song=${restoredQueue.getOrNull(currentIndex)?.title}, pos=${startPositionMs}, autoPlay=${settings.autoPlay}, wasPlaying=${saved.wasPlaying}")
        return true
    }
}
