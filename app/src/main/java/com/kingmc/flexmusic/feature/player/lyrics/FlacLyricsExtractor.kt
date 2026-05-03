package com.kingmc.flexmusic.feature.player.lyrics

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kingmc.flexmusic.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlacLyricsExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FlexMusic.FlacLyrics"
        private const val FLAC_MAGIC = "fLaC"
        private const val VORBIS_COMMENT_TYPE = 4
        private const val MAX_READ_SIZE = 1024 * 1024L
    }

    fun extractLrc(song: Song): String? {
        val uri = runCatching { Uri.parse(song.contentUri) }.getOrNull() ?: return null
        
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                if (input.read(header) != 4) return@use null
                if (!header.contentEquals(FLAC_MAGIC.toByteArray(Charsets.ISO_8859_1))) return@use null
                
                var bytesRead = 4L
                var vorbisData: ByteArray? = null
                
                while (bytesRead < MAX_READ_SIZE) {
                    val blockHeader = input.read()
                    if (blockHeader == -1) break
                    bytesRead++
                    
                    val isLast = (blockHeader and 0x80) != 0
                    val blockType = blockHeader and 0x7F
                    
                    val sizeBytes = ByteArray(3)
                    if (input.read(sizeBytes) != 3) break
                    bytesRead += 3
                    
                    val blockSize = ((sizeBytes[0].toInt() and 0xFF) shl 16) or
                                   ((sizeBytes[1].toInt() and 0xFF) shl 8) or
                                   (sizeBytes[2].toInt() and 0xFF)
                    
                    if (blockType == VORBIS_COMMENT_TYPE) {
                        val data = ByteArray(blockSize)
                        if (input.read(data) == blockSize) {
                            vorbisData = data
                        }
                        break
                    } else {
                        input.skip(blockSize.toLong())
                        bytesRead += blockSize
                    }
                    
                    if (isLast) break
                }
                
                vorbisData?.let { parseVorbisComments(it, song) }
            }
        }.getOrNull()
    }

    private fun parseVorbisComments(data: ByteArray, song: Song): String? {
        var offset = 0
        
        if (offset + 4 > data.size) return null
        val vendorLength = readLittleEndianInt(data, offset)
        offset += 4 + vendorLength
        if (offset > data.size) return null
        
        if (offset + 4 > data.size) return null
        val commentCount = readLittleEndianInt(data, offset)
        offset += 4
        
        var lyricsLrc: String? = null
        var lyricsUnsynced: String? = null
        
        repeat(commentCount) {
            if (offset + 4 > data.size) return@repeat
            val commentLength = readLittleEndianInt(data, offset)
            offset += 4
            
            if (offset + commentLength > data.size) return@repeat
            val comment = data.copyOfRange(offset, offset + commentLength).toString(Charsets.UTF_8)
            offset += commentLength
            
            val eqIndex = comment.indexOf('=')
            if (eqIndex > 0) {
                val key = comment.substring(0, eqIndex).uppercase()
                val value = comment.substring(eqIndex + 1)
                
                when (key) {
                    "LYRICS" -> if (lyricsLrc == null && value.contains("[") && value.contains("]")) {
                        lyricsLrc = value
                    }
                    "UNSYNCEDLYRICS" -> if (lyricsUnsynced == null) {
                        lyricsUnsynced = value
                    }
                    "LYRICIST" -> { }
                }
            }
        }
        
        return lyricsLrc ?: lyricsUnsynced?.let { buildUnsyncedLrc(it, song.durationMs) }
    }

    private fun buildUnsyncedLrc(text: String, durationMs: Long): String {
        val lines = text.replace("\r", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return ""
        
        val step = (durationMs / lines.size.coerceAtLeast(1)).coerceIn(2000L, 8000L)
        val sb = StringBuilder()
        lines.forEachIndexed { index, line ->
            val t = formatLrcTime(index * step)
            sb.append('[').append(t).append(']').append(line).append('\n')
        }
        return sb.toString().trim()
    }

    private fun readLittleEndianInt(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
               ((data[offset + 1].toInt() and 0xFF) shl 8) or
               ((data[offset + 2].toInt() and 0xFF) shl 16) or
               ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun formatLrcTime(ms: Long): String {
        val totalCs = (ms.coerceAtLeast(0L) / 10L)
        val minute = totalCs / 6000L
        val second = (totalCs / 100L) % 60L
        val cent = totalCs % 100L
        return "%02d:%02d.%02d".format(minute, second, cent)
    }
}
