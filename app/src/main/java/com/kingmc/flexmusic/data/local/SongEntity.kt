package com.kingmc.flexmusic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val albumArtUri: String?,
    val displayName: String?,
    val relativePath: String?,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val onlineCoverUrl: String? = null,
    val cachedLyrics: String? = null,
    val cachedLyricsSource: String? = null,
    val cachedLyricsOffset: Long = 0L
)
