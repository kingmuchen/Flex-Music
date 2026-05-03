package com.kingmc.flexmusic.player

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.kingmc.flexmusic.feature.settings.AppSettingsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@UnstableApi
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerController: Media3PlayerController

    @Inject
    lateinit var appSettingsManager: AppSettingsManager

    @Inject
    lateinit var notificationProvider: MediaNotificationProvider

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var showNotification = true
    private var isNotificationPosted = false

    private val notificationUpdateHandler = Handler(Looper.getMainLooper())
    private val notificationUpdateInterval = 1000L
    private var isProgressUpdateActive = false

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            if (isProgressUpdateActive && playerController.getPlayer().isPlaying) {
                updateNotification()
                notificationUpdateHandler.postDelayed(this, notificationUpdateInterval)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("FlexMusic.PlaybackService", "onCreate")

        notificationProvider.onNotificationUpdateNeeded = {
            updateNotification()
        }

        if (mediaSession == null) {
            val player = playerController.getPlayer()

            mediaSession = MediaSession.Builder(this, player)
                .setId("flex-music-session")
                .setCallback(object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo
                    ): MediaSession.ConnectionResult {
                        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                        return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
                    }
                })
                .build()
        }

        playerController.getPlayer().addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d("FlexMusic.PlaybackService", "onIsPlayingChanged: $isPlaying")
                    updateNotification()
                    if (isPlaying) {
                        startProgressUpdates()
                    } else {
                        stopProgressUpdates()
                    }
                }

                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    Log.d("FlexMusic.PlaybackService", "onMediaItemTransition: ${mediaItem?.mediaMetadata?.title}")
                    val state = playerController.playbackState.value
                    val songId = state.currentSong?.id ?: -1L
                    val title = mediaItem?.mediaMetadata?.title?.toString() ?: ""
                    val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: ""
                    notificationProvider.updateCurrentSong(songId, title, artist, null)
                    updateNotification()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.d("FlexMusic.PlaybackService", "onPlaybackStateChanged: $playbackState")
                    updateNotification()
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                        events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                        updateNotification()
                    }
                }
            }
        )

        serviceScope.launch {
            appSettingsManager.settings.collectLatest { settings ->
                showNotification = settings.showNotification
                if (!showNotification && isNotificationPosted) {
                    stopProgressUpdates()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isNotificationPosted = false
                } else if (showNotification && playerController.getPlayer().currentMediaItem != null) {
                    updateNotification()
                }
            }
        }

        serviceScope.launch {
            playerController.playbackState
                .distinctUntilChanged { old, new ->
                    old.onlineCoverUrl == new.onlineCoverUrl && old.currentSong?.id == new.currentSong?.id
                }
                .collectLatest { state ->
                    if (state.currentSong != null) {
                        notificationProvider.updateCurrentSong(
                            state.currentSong.id,
                            state.currentSong.title,
                            state.currentSong.artist,
                            state.onlineCoverUrl
                        )
                        updateNotification()
                    }
                }
        }

        if (playerController.getPlayer().currentMediaItem != null) {
            updateNotification()
            if (playerController.getPlayer().isPlaying) {
                startProgressUpdates()
            }
        }
    }

    override fun onUpdateNotification(mediaSession: MediaSession, startInForegroundRequired: Boolean) {
        updateNotification()
    }

    private fun startProgressUpdates() {
        if (!isProgressUpdateActive) {
            isProgressUpdateActive = true
            notificationUpdateHandler.postDelayed(progressUpdateRunnable, notificationUpdateInterval)
        }
    }

    private fun stopProgressUpdates() {
        isProgressUpdateActive = false
        notificationUpdateHandler.removeCallbacks(progressUpdateRunnable)
    }

    private fun updateNotification() {
        if (!showNotification) return

        val player = playerController.getPlayer()
        val session = mediaSession

        if (session == null || player.currentMediaItem == null) {
            if (isNotificationPosted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isNotificationPosted = false
            }
            return
        }

        try {
            val notification = notificationProvider.createNotification(player, session)

            if (player.isPlaying) {
                startForeground(MediaNotificationProvider.NOTIFICATION_ID, notification)
            } else {
                if (isNotificationPosted) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.notify(MediaNotificationProvider.NOTIFICATION_ID, notification)
            }
            isNotificationPosted = true
        } catch (e: Exception) {
            Log.e("FlexMusic.PlaybackService", "Failed to update notification", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val isPlaying = playerController.getPlayer().isPlaying
        if (!isPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopProgressUpdates()
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
