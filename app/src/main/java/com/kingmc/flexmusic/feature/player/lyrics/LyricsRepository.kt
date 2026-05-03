package com.kingmc.flexmusic.feature.player.lyrics

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.kingmc.flexmusic.data.local.FlexMusicDatabase
import com.kingmc.flexmusic.data.model.Song
import com.kingmc.flexmusic.feature.settings.AppSettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FlexMusicDatabase,
    private val id3LyricsExtractor: Id3LyricsExtractor,
    private val flacLyricsExtractor: FlacLyricsExtractor,
    private val onlineLyricsService: OnlineLyricsService,
    private val lyricAlignManager: LyricAlignManager,
    private val lyricOffsetCache: LyricOffsetCache,
    private val appSettingsManager: AppSettingsManager
) {
    private val parser = LrcParser()
    private val songDao = database.songDao()

    suspend fun loadLyrics(song: Song): LyricDocument = withContext(Dispatchers.IO) {
        val sidecar = readSidecarLrc(song)
        if (!sidecar.isNullOrBlank()) {
            val parsed = parser.parse(sidecar, source = "sidecar_lrc")
            if (parsed.lines.isNotEmpty()) {
                applyUserOffset(song)
                return@withContext parsed
            }
        }

        val cachedLyricsData = songDao.getCachedLyrics(song.id)
        if (cachedLyricsData != null && !cachedLyricsData.cachedLyrics.isNullOrBlank()) {
            val parsed = parser.parse(cachedLyricsData.cachedLyrics, source = cachedLyricsData.cachedLyricsSource ?: "room_cache")
            if (parsed.lines.isNotEmpty()) {
                applyUserOffset(song)
                return@withContext parsed
            }
        }

        val flacLyrics = flacLyricsExtractor.extractLrc(song)
        if (!flacLyrics.isNullOrBlank()) {
            val parsed = parser.parse(flacLyrics, source = "flac_vorbis")
            if (parsed.lines.isNotEmpty()) {
                applyUserOffset(song)
                return@withContext parsed
            }
        }

        val id3Lyrics = id3LyricsExtractor.extractLrc(song)
        if (!id3Lyrics.isNullOrBlank()) {
            val parsed = parser.parse(id3Lyrics, source = "id3_uslt_sylt")
            if (parsed.lines.isNotEmpty()) {
                applyUserOffset(song)
                return@withContext parsed
            }
        }

        Log.d(TAG, "Searching online lyrics for: ${song.title} - ${song.artist}")
        val smartMatch = appSettingsManager.settings.first().smartLyricsMatch
        if (smartMatch) {
            val songUri = Uri.parse(song.contentUri)
            val onlineResult = onlineLyricsService.searchLyrics(song, songUri)
            if (onlineResult != null && onlineResult.lyrics.isNotBlank()) {
                val parsed = parser.parse(onlineResult.lyrics, source = onlineResult.source)
                if (parsed.lines.isNotEmpty()) {
                    songDao.updateCachedLyrics(
                        songId = song.id,
                        lyrics = onlineResult.lyrics,
                        source = onlineResult.source,
                        offset = onlineResult.offset
                    )

                    applyUserOffset(song)
                    return@withContext parsed
                }
            }
        }

        val embedded = embeddedLrcFor(song)
        if (!embedded.isNullOrBlank()) {
            val parsed = parser.parse(embedded, source = "embedded_lrc")
            if (parsed.lines.isNotEmpty()) {
                applyUserOffset(song)
                return@withContext parsed
            }
        }

        val defaultDoc = parser.parse(DEFAULT_LRC, source = "default_lrc")
        lyricAlignManager.resetAllOffsets()
        defaultDoc
    }

    private fun applyUserOffset(song: Song) {
        val cachedOffset = lyricOffsetCache.getOffset(song.id)
        val userOffset = cachedOffset?.userFixOffset ?: 0L
        lyricAlignManager.updateUserFixOffset(userOffset)
        Log.d(TAG, "Applied user offset for song ${song.id}: ${userOffset}ms")
    }

    fun adjustUserOffset(songId: Long, deltaMs: Long) {
        lyricAlignManager.adjustUserFixOffset(deltaMs)
        lyricOffsetCache.adjustUserFixOffset(songId, deltaMs)
        Log.d(TAG, "Adjusted user offset for song $songId by $deltaMs ms")
    }

    fun resetOffsets(songId: Long) {
        lyricAlignManager.resetAllOffsets()
        lyricOffsetCache.markForRedetection(songId)
        Log.d(TAG, "Marked song $songId for redetection")
    }

    fun clearAllCache() {
        lyricOffsetCache.clearAll()
        lyricAlignManager.resetAllOffsets()
        Log.d(TAG, "Cleared all cache")
    }

    private fun readSidecarLrc(song: Song): String? {
        val displayName = song.displayName ?: return null
        val baseName = displayName.substringBeforeLast('.')
        val targetFileName = "$baseName.lrc"

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH
        )

        val uri = MediaStore.Files.getContentUri("external")
        val relativePath = song.relativePath

        val selection: String
        val args: Array<String>
        if (!relativePath.isNullOrBlank()) {
            selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME}=? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH}=?"
            args = arrayOf(targetFileName, relativePath)
        } else {
            selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME}=?"
            args = arrayOf(targetFileName)
        }

        context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(idIndex)
                val lrcUri = ContentUris.withAppendedId(uri, id)
                return context.contentResolver.openInputStream(lrcUri)?.use { input ->
                    decodeLyricsBytes(input.readBytes())
                }
            }
        }

        return null
    }

    private fun decodeLyricsBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        
        val bomUtf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bomUtf16Le = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bomUtf16Be = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        
        return when {
            bytes.size >= 3 && bytes.copyOfRange(0, 3).contentEquals(bomUtf8) -> {
                bytes.toString(Charsets.UTF_8)
            }
            bytes.size >= 2 && bytes.copyOfRange(0, 2).contentEquals(bomUtf16Le) -> {
                bytes.toString(Charsets.UTF_16LE)
            }
            bytes.size >= 2 && bytes.copyOfRange(0, 2).contentEquals(bomUtf16Be) -> {
                bytes.toString(Charsets.UTF_16BE)
            }
            else -> {
                val utf8 = bytes.toString(Charsets.UTF_8)
                if (utf8.contains('\uFFFD')) {
                    bytes.toString(Charset.forName("GBK"))
                } else {
                    utf8
                }
            }
        }
    }

    private fun embeddedLrcFor(song: Song): String? {
        return null
    }

    suspend fun hasLocalLyrics(song: Song): Boolean = withContext(Dispatchers.IO) {
        val sidecar = readSidecarLrc(song)
        if (!sidecar.isNullOrBlank()) {
            val parsed = parser.parse(sidecar, source = "sidecar_lrc")
            if (parsed.lines.isNotEmpty()) return@withContext true
        }

        val flacLyrics = flacLyricsExtractor.extractLrc(song)
        if (!flacLyrics.isNullOrBlank()) {
            val parsed = parser.parse(flacLyrics, source = "flac_vorbis")
            if (parsed.lines.isNotEmpty()) return@withContext true
        }

        val id3Lyrics = id3LyricsExtractor.extractLrc(song)
        if (!id3Lyrics.isNullOrBlank()) {
            val parsed = parser.parse(id3Lyrics, source = "id3_uslt_sylt")
            if (parsed.lines.isNotEmpty()) return@withContext true
        }

        false
    }

    suspend fun hasLocalCover(song: Song): Boolean = withContext(Dispatchers.IO) {
        val albumArtUriStr = song.albumArtUri
        if (!albumArtUriStr.isNullOrBlank()) {
            try {
                val uri = Uri.parse(albumArtUriStr)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.isNotEmpty()) return@withContext true
                }
            } catch (_: Exception) {
            }
        }

        try {
            val songUri = Uri.parse(song.contentUri)
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, songUri)
            val picture = retriever.embeddedPicture
            retriever.release()
            if (picture != null && picture.isNotEmpty()) return@withContext true
        } catch (_: Exception) {
        }

        false
    }

    suspend fun searchOnlineCover(song: Song): OnlineLyricsService.OnlineCoverResult? {
        val cached = songDao.getCoverUrl(song.id)
        if (!cached.isNullOrBlank()) {
            return OnlineLyricsService.OnlineCoverResult(
                coverUrl = cached,
                source = "room_cache",
                confidence = 1.0
            )
        }
        val result = onlineLyricsService.searchCover(song)
        if (result != null) {
            songDao.updateCoverUrl(song.id, result.coverUrl)
        }
        return result
    }

    suspend fun getCachedCoverUrl(songId: Long): String? {
        return songDao.getCoverUrl(songId)
    }

    suspend fun updateCoverUrl(songId: Long, coverUrl: String) {
        songDao.updateCoverUrl(songId, coverUrl)
    }

    suspend fun updateCachedLyrics(songId: Long, lyrics: String, source: String, offset: Long = 0L) {
        songDao.updateCachedLyrics(songId, lyrics, source, offset)
    }

    companion object {
        private const val TAG = "LyricsRepository"
        
        private const val DEFAULT_LRC = """
[00:00.00]纯音乐或暂无歌词
[00:05.00]可将同名 .lrc 文件放在歌曲目录
[00:10.00]或使用内嵌歌词内容
"""
    }
}
