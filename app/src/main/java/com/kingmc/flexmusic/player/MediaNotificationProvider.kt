package com.kingmc.flexmusic.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.kingmc.flexmusic.MainActivity
import com.kingmc.flexmusic.R
import com.kingmc.flexmusic.feature.player.lyrics.LyricsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@UnstableApi
class MediaNotificationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lyricsRepository: LyricsRepository
) {

    companion object {
        private const val CHANNEL_ID = "flex_music_playback_channel"
        private const val CHANNEL_NAME = "Flex Music 播放控制"
        private const val CHANNEL_DESCRIPTION = "显示当前播放歌曲和控制按钮"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "MediaNotification"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val albumArtCache = mutableMapOf<Long, Bitmap>()
    var onNotificationUpdateNeeded: (() -> Unit)? = null

    private var currentSongId: Long = -1
    private var currentSongTitle: String = ""
    private var currentSongArtist: String = ""
    private var currentOnlineCoverUrl: String? = null
    private var isLoadingCover = false
    private var needsCoverReload = false

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateCurrentSong(songId: Long, title: String, artist: String, onlineCoverUrl: String? = null) {
        if (currentSongId != songId) {
            currentSongId = songId
            currentSongTitle = title
            currentSongArtist = artist
            currentOnlineCoverUrl = null
            isLoadingCover = false
            needsCoverReload = false
        } else if (onlineCoverUrl != null && currentOnlineCoverUrl != onlineCoverUrl) {
            currentOnlineCoverUrl = onlineCoverUrl
            needsCoverReload = true
        }
    }

    fun createNotification(
        player: Player,
        mediaSession: MediaSession
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(player.mediaMetadata.title)
            .setContentText(player.mediaMetadata.artist)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(player.isPlaying)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setDeleteIntent(createStopIntent())
            .addAction(createPreviousAction())
            .addAction(createPlayPauseAction(player))
            .addAction(createNextAction())
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        albumArtCache[currentSongId]?.let { bitmap ->
            builder.setLargeIcon(bitmap)
        }

        val shouldLoadCover = currentSongId > 0 &&
            (albumArtCache[currentSongId] == null || needsCoverReload) &&
            !isLoadingCover

        if (shouldLoadCover) {
            needsCoverReload = false
            loadAlbumArtAsync(player)
        }

        return builder.build()
    }

    private fun createPlayPauseAction(player: Player): NotificationCompat.Action {
        val icon = if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val intent = PendingIntent.getBroadcast(
            context,
            1,
            Intent("com.kingmc.flexmusic.PLAY_PAUSE").apply {
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(icon, "播放/暂停", intent).build()
    }

    private fun createPreviousAction(): NotificationCompat.Action {
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent("com.kingmc.flexmusic.SKIP_PREVIOUS").apply {
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_skip_previous, "上一首", intent).build()
    }

    private fun createNextAction(): NotificationCompat.Action {
        val intent = PendingIntent.getBroadcast(
            context,
            2,
            Intent("com.kingmc.flexmusic.SKIP_NEXT").apply {
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_skip_next, "下一首", intent).build()
    }

    private fun createStopIntent(): PendingIntent {
        val intent = Intent("com.kingmc.flexmusic.STOP").apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun loadAlbumArtAsync(player: Player) {
        val songId = currentSongId
        if (songId <= 0) return

        isLoadingCover = true
        val artworkUri = player.mediaMetadata.artworkUri
        val mediaItemUri = player.currentMediaItem?.localConfiguration?.uri

        scope.launch {
            try {
                var bitmap: Bitmap? = null

                val dbCoverUrl = lyricsRepository.getCachedCoverUrl(songId)
                if (!dbCoverUrl.isNullOrBlank()) {
                    Log.d(TAG, "Loading cover from DB cache for song $songId: $dbCoverUrl")
                    bitmap = withContext(Dispatchers.IO) {
                        downloadBitmap(dbCoverUrl)
                    }
                    if (bitmap != null) {
                        currentOnlineCoverUrl = dbCoverUrl
                    }
                }

                if (bitmap == null && !currentOnlineCoverUrl.isNullOrBlank() && currentOnlineCoverUrl != dbCoverUrl) {
                    Log.d(TAG, "Loading cover from currentOnlineCoverUrl for song $songId")
                    bitmap = withContext(Dispatchers.IO) {
                        downloadBitmap(currentOnlineCoverUrl!!)
                    }
                }

                if (bitmap == null && artworkUri != null) {
                    Log.d(TAG, "Loading cover from content URI for song $songId")
                    bitmap = withContext(Dispatchers.IO) {
                        loadAlbumArtFromContentUri(artworkUri)
                    }
                }

                if (bitmap == null && mediaItemUri != null) {
                    Log.d(TAG, "Loading cover from MediaRetriever for song $songId")
                    bitmap = withContext(Dispatchers.IO) {
                        loadAlbumArtFromMediaRetriever(mediaItemUri)
                    }
                }

                if (bitmap != null && currentSongId == songId) {
                    albumArtCache[songId] = bitmap
                    Log.d(TAG, "Cover loaded and cached for song $songId")
                    withContext(Dispatchers.Main) {
                        isLoadingCover = false
                        onNotificationUpdateNeeded?.invoke()
                    }
                } else {
                    isLoadingCover = false
                    if (bitmap == null) {
                        Log.w(TAG, "Failed to load cover for song $songId")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cover loading error for song $songId: ${e.message}")
                isLoadingCover = false
            }
        }
    }

    private fun loadAlbumArtFromContentUri(uri: android.net.Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadAlbumArtFromMediaRetriever(uri: android.net.Uri): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getEmbeddedPicture()?.let { data ->
                BitmapFactory.decodeByteArray(data, 0, data.size)
            }
            retriever.release()
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download cover failed: ${e.message}")
            null
        }
    }
}
