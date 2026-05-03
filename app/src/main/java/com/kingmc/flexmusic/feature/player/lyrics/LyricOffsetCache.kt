package com.kingmc.flexmusic.feature.player.lyrics

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricOffsetCache @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val cacheFile = File(context.filesDir, "lyric_offsets_v4.json")
    private val cache = mutableMapOf<String, CachedOffset>()

    init {
        loadCache()
    }

    fun getOffset(songId: Long): CachedOffset? {
        return cache[songId.toString()]
    }

    fun getOffset(songId: String): CachedOffset? {
        return cache[songId]
    }

    fun saveOffset(songId: Long, offset: CachedOffset) {
        cache[songId.toString()] = offset
        saveCache()
    }

    fun saveOffset(songId: String, offset: CachedOffset) {
        cache[songId] = offset
        saveCache()
    }

    fun updateAutoSilenceOffset(songId: Long, offset: Long) {
        val existing = cache[songId.toString()] ?: CachedOffset(songId = songId.toString())
        cache[songId.toString()] = existing.copy(autoSilenceOffset = offset)
        saveCache()
    }

    fun updateUserFixOffset(songId: Long, offset: Long) {
        val existing = cache[songId.toString()] ?: CachedOffset(songId = songId.toString())
        cache[songId.toString()] = existing.copy(userFixOffset = offset)
        saveCache()
    }

    fun adjustUserFixOffset(songId: Long, delta: Long) {
        val existing = cache[songId.toString()] ?: CachedOffset(songId = songId.toString())
        cache[songId.toString()] = existing.copy(userFixOffset = existing.userFixOffset + delta)
        saveCache()
    }

    fun markForRedetection(songId: Long) {
        val existing = cache[songId.toString()]
        if (existing != null) {
            cache[songId.toString()] = existing.copy(needsRedetection = true)
        } else {
            cache[songId.toString()] = CachedOffset(
                songId = songId.toString(),
                needsRedetection = true
            )
        }
        saveCache()
    }

    fun clearOffset(songId: Long) {
        cache.remove(songId.toString())
        saveCache()
    }

    fun clearAll() {
        cache.clear()
        saveCache()
        Log.d(TAG, "Cleared all offset cache")
    }

    private fun loadCache() {
        try {
            if (!cacheFile.exists()) {
                val oldCacheFile = File(context.filesDir, "lyric_offsets.json")
                if (oldCacheFile.exists()) {
                    oldCacheFile.delete()
                    Log.d(TAG, "Deleted old cache file")
                }
                return
            }
            
            val json = cacheFile.readText()
            val jsonObject = JSONObject(json)
            
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = jsonObject.getJSONObject(key)
                cache[key] = CachedOffset(
                    songId = key,
                    autoSilenceOffset = obj.optLong("autoSilenceOffset", 0L),
                    lrcFileOffset = obj.optLong("lrcFileOffset", 0L),
                    userFixOffset = obj.optLong("userFixOffset", 0L),
                    lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis()),
                    needsRedetection = obj.optBoolean("needsRedetection", true)
                )
            }
            
            Log.d(TAG, "Loaded ${cache.size} cached offsets")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cache: ${e.message}")
            cache.clear()
        }
    }

    private fun saveCache() {
        try {
            val jsonObject = JSONObject()
            cache.forEach { (key, value) ->
                val obj = JSONObject().apply {
                    put("songId", value.songId)
                    put("autoSilenceOffset", value.autoSilenceOffset)
                    put("lrcFileOffset", value.lrcFileOffset)
                    put("userFixOffset", value.userFixOffset)
                    put("lastUpdated", value.lastUpdated)
                    put("needsRedetection", value.needsRedetection)
                }
                jsonObject.put(key, obj)
            }
            cacheFile.writeText(jsonObject.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save cache: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LyricOffsetCache"
    }
}

data class CachedOffset(
    val songId: String,
    val autoSilenceOffset: Long = 0L,
    val lrcFileOffset: Long = 0L,
    val userFixOffset: Long = 0L,
    val lastUpdated: Long = System.currentTimeMillis(),
    val needsRedetection: Boolean = true
) {
    val totalOffset: Long
        get() = autoSilenceOffset + lrcFileOffset + userFixOffset
}
