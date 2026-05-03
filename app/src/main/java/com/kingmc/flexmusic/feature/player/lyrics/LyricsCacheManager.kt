package com.kingmc.flexmusic.feature.player.lyrics

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val cacheDir = File(context.filesDir, "lyrics_cache").apply {
        if (!exists()) mkdirs()
    }

    data class CachedLyrics(
        val songId: Long,
        val songTitle: String,
        val songArtist: String,
        val lyrics: String,
        val source: String,
        val offset: Long = 0L,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun getCachedLyrics(songId: Long, title: String, artist: String): CachedLyrics? {
        val cacheKey = generateCacheKey(songId, title, artist)
        val cacheFile = File(cacheDir, "$cacheKey.json")
        
        if (!cacheFile.exists()) return null
        
        return try {
            val json = JSONObject(cacheFile.readText())
            CachedLyrics(
                songId = json.optLong("songId"),
                songTitle = json.optString("songTitle"),
                songArtist = json.optString("songArtist"),
                lyrics = json.optString("lyrics"),
                source = json.optString("source"),
                offset = json.optLong("offset"),
                timestamp = json.optLong("timestamp")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached lyrics: ${e.message}")
            null
        }
    }

    fun cacheLyrics(
        songId: Long,
        title: String,
        artist: String,
        lyrics: String,
        source: String,
        offset: Long = 0L
    ) {
        val cacheKey = generateCacheKey(songId, title, artist)
        val cacheFile = File(cacheDir, "$cacheKey.json")
        
        try {
            val json = JSONObject().apply {
                put("songId", songId)
                put("songTitle", title)
                put("songArtist", artist)
                put("lyrics", lyrics)
                put("source", source)
                put("offset", offset)
                put("timestamp", System.currentTimeMillis())
            }
            cacheFile.writeText(json.toString())
            Log.d(TAG, "Cached lyrics for: $title - $artist")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache lyrics: ${e.message}")
        }
    }

    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "Cleared lyrics cache")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear cache: ${e.message}")
        }
    }

    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun generateCacheKey(songId: Long, title: String, artist: String): String {
        val input = "$songId-$title-$artist"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "LyricsCacheManager"
    }
}
