package com.kingmc.flexmusic.data.repository

import com.kingmc.flexmusic.data.model.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun observeSongs(): Flow<List<Song>>
    fun observeFavoriteSongs(): Flow<List<Song>>
    fun observeRecentSongs(limit: Int): Flow<List<Song>>
    suspend fun refreshLocalLibrary(): Result<Int>
    suspend fun setFavorite(songId: Long, favorite: Boolean)
    suspend fun markSongPlayed(songId: Long)
}
