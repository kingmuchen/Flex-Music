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
class Id3LyricsExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun extractLrc(song: Song): String? {
        val uri = runCatching { Uri.parse(song.contentUri) }.getOrNull() ?: return null
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                readHeadBytes(input, 512 * 1024)
            }
        }.getOrNull() ?: return null

        if (bytes.size < 10) return null
        if (!bytes.copyOfRange(0, 3).contentEquals("ID3".toByteArray())) return null

        val versionMajor = bytes[3].toInt() and 0xFF
        val flags = bytes[5].toInt() and 0xFF
        val tagSize = decodeSynchsafeInt(bytes, 6)
        val totalTagSize = (10 + tagSize).coerceAtMost(bytes.size)
        var tagData = bytes.copyOfRange(10, totalTagSize)

        val unsync = (flags and 0x80) != 0
        if (unsync) {
            tagData = removeUnsynchronization(tagData)
        }

        return try {
            when (versionMajor) {
                2 -> parseV22(tagData, song)
                3, 4 -> parseV23OrV24(tagData, versionMajor, song)
                else -> null
            }
        } catch (t: Throwable) {
            Log.w("FlexMusic.Id3Lyrics", "parse id3 lyrics failed", t)
            null
        }
    }

    private fun parseV23OrV24(tagData: ByteArray, version: Int, song: Song): String? {
        var offset = 0
        var usltText: String? = null
        var syltLrc: String? = null

        while (offset + 10 <= tagData.size) {
            val frameId = tagData.copyOfRange(offset, offset + 4).toString(Charsets.ISO_8859_1)
            if (frameId.all { it == '\u0000' }) break

            val frameSize = if (version == 4) {
                decodeSynchsafeInt(tagData, offset + 4)
            } else {
                decodeInt(tagData, offset + 4)
            }
            if (frameSize <= 0 || offset + 10 + frameSize > tagData.size) break

            val payload = tagData.copyOfRange(offset + 10, offset + 10 + frameSize)
            when (frameId) {
                "USLT" -> if (usltText == null) usltText = parseUslt(payload)
                "SYLT" -> if (syltLrc == null) syltLrc = parseSyltToLrc(payload)
            }

            offset += 10 + frameSize
        }

        return syltLrc ?: usltText?.let { buildUnsyncedLrc(it, song.durationMs) }
    }

    private fun parseV22(tagData: ByteArray, song: Song): String? {
        var offset = 0
        var usltText: String? = null
        var syltLrc: String? = null

        while (offset + 6 <= tagData.size) {
            val frameId = tagData.copyOfRange(offset, offset + 3).toString(Charsets.ISO_8859_1)
            if (frameId.all { it == '\u0000' }) break
            val frameSize = ((tagData[offset + 3].toInt() and 0xFF) shl 16) or
                ((tagData[offset + 4].toInt() and 0xFF) shl 8) or
                (tagData[offset + 5].toInt() and 0xFF)
            if (frameSize <= 0 || offset + 6 + frameSize > tagData.size) break

            val payload = tagData.copyOfRange(offset + 6, offset + 6 + frameSize)
            when (frameId) {
                "ULT" -> if (usltText == null) usltText = parseUslt(payload)
                "SLT" -> if (syltLrc == null) syltLrc = parseSyltToLrc(payload)
            }

            offset += 6 + frameSize
        }

        return syltLrc ?: usltText?.let { buildUnsyncedLrc(it, song.durationMs) }
    }

    private fun parseUslt(payload: ByteArray): String? {
        if (payload.size < 4) return null
        val encoding = payload[0].toInt() and 0xFF
        val charset = charsetForEncoding(encoding)
        var cursor = 1 + 3 // encoding + language
        cursor = skipDescriptor(payload, cursor, encoding)
        if (cursor >= payload.size) return null
        return decodeText(payload, cursor, payload.size, charset, encoding)
            .replace("\u0000", "")
            .trim()
            .ifBlank { null }
    }

    private fun parseSyltToLrc(payload: ByteArray): String? {
        if (payload.size < 6) return null
        val encoding = payload[0].toInt() and 0xFF
        val charset = charsetForEncoding(encoding)

        var cursor = 1 + 3 // encoding + language
        val timestampFormat = payload.getOrNull(cursor)?.toInt()?.and(0xFF) ?: return null
        cursor++ // timestamp format
        cursor++ // content type

        cursor = skipDescriptor(payload, cursor, encoding)
        if (cursor >= payload.size) return null

        if (timestampFormat != 1) {
            // 1 = milliseconds. Other formats are not reliable for synchronized karaoke output here.
            return null
        }

        val builder = StringBuilder()
        while (cursor < payload.size) {
            val textEnd = findTerminator(payload, cursor, encoding)
            if (textEnd == -1) break

            val next = nextAfterTerminator(textEnd, encoding)
            if (next + 4 > payload.size) break

            val text = decodeText(payload, cursor, textEnd, charset, encoding).trim()
            val timeMs = decodeInt(payload, next).toLong().coerceAtLeast(0L)

            if (text.isNotBlank()) {
                val t = formatLrcTime(timeMs)
                builder.append('[').append(t).append(']')
                    .append(text)
                    .append('\n')
            }

            cursor = next + 4
        }

        return builder.toString().trim().ifBlank { null }
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

    private fun charsetForEncoding(encoding: Int): Charset {
        return when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charset.forName("UTF-16BE")
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }
    }

    private fun decodeText(data: ByteArray, start: Int, end: Int, charset: Charset, encoding: Int): String {
        if (start >= end) return ""
        var s = start
        var e = end
        if ((encoding == 1 || encoding == 2) && e - s >= 2) {
            if (data[s] == 0.toByte() && data[s + 1] == 0.toByte()) s += 2
            while (e - s >= 2 && data[e - 1] == 0.toByte() && data[e - 2] == 0.toByte()) {
                e -= 2
            }
        } else {
            while (s < e && data[s] == 0.toByte()) s++
            while (e > s && data[e - 1] == 0.toByte()) e--
        }
        return data.copyOfRange(s, e).toString(charset)
    }

    private fun skipDescriptor(data: ByteArray, start: Int, encoding: Int): Int {
        val end = findTerminator(data, start, encoding)
        if (end == -1) return data.size
        return nextAfterTerminator(end, encoding)
    }

    private fun findTerminator(data: ByteArray, start: Int, encoding: Int): Int {
        return if (encoding == 1 || encoding == 2) {
            var i = start
            while (i + 1 < data.size) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) return i
                i += 2
            }
            -1
        } else {
            var i = start
            while (i < data.size) {
                if (data[i] == 0.toByte()) return i
                i++
            }
            -1
        }
    }

    private fun nextAfterTerminator(termStart: Int, encoding: Int): Int {
        return termStart + if (encoding == 1 || encoding == 2) 2 else 1
    }

    private fun formatLrcTime(ms: Long): String {
        val totalCs = (ms.coerceAtLeast(0L) / 10L)
        val minute = totalCs / 6000L
        val second = (totalCs / 100L) % 60L
        val cent = totalCs % 100L
        return "%02d:%02d.%02d".format(minute, second, cent)
    }

    private fun decodeInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    private fun decodeSynchsafeInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0x7F) shl 21) or
            ((data[offset + 1].toInt() and 0x7F) shl 14) or
            ((data[offset + 2].toInt() and 0x7F) shl 7) or
            (data[offset + 3].toInt() and 0x7F)
    }

    private fun removeUnsynchronization(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            if (i + 1 < data.size && data[i] == 0xFF.toByte() && data[i + 1] == 0x00.toByte()) {
                out.write(0xFF)
                i += 2
            } else {
                out.write(data[i].toInt())
                i++
            }
        }
        return out.toByteArray()
    }

    private fun readHeadBytes(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        val read = input.read(buffer)
        return if (read <= 0) ByteArray(0) else buffer.copyOf(read)
    }
}
