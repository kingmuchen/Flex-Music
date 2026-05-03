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
class CoverCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val cacheDir = File(context.filesDir, "cover_cache").apply {
        if (!exists()) mkdirs()
    }

    fun getCachedCoverUrl(songId: Long, title: String, artist: String): String? {
        val cacheKey = generateCacheKey(songId, title, artist)
        val cacheFile = File(cacheDir, "$cacheKey.json")

        if (!cacheFile.exists()) return null

        return try {
            val json = JSONObject(cacheFile.readText())
            val coverUrl = json.optString("coverUrl", "")
            val timestamp = json.optLong("timestamp", 0)
            val age = System.currentTimeMillis() - timestamp
            if (coverUrl.isNotBlank() && age < CACHE_DURATION_MS) {
                Log.d(TAG, "Cover cache hit for: $title - $artist")
                coverUrl
            } else if (age >= CACHE_DURATION_MS) {
                cacheFile.delete()
                null
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached cover: ${e.message}")
            null
        }
    }

    fun cacheCoverUrl(
        songId: Long,
        title: String,
        artist: String,
        coverUrl: String
    ) {
        if (coverUrl.isBlank()) return
        val cacheKey = generateCacheKey(songId, title, artist)
        val cacheFile = File(cacheDir, "$cacheKey.json")

        try {
            val json = JSONObject().apply {
                put("songId", songId)
                put("songTitle", title)
                put("songArtist", artist)
                put("coverUrl", coverUrl)
                put("timestamp", System.currentTimeMillis())
            }
            cacheFile.writeText(json.toString())
            Log.d(TAG, "Cached cover for: $title - $artist")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache cover: ${e.message}")
        }
    }

    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "Cleared cover cache")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear cache: ${e.message}")
        }
    }

    private fun generateCacheKey(songId: Long, title: String, artist: String): String {
        val input = "$songId-$title-$artist"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "CoverCacheManager"
        private const val CACHE_DURATION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
