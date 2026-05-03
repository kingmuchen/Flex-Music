package com.kingmc.flexmusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun observeSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentSongs(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getSongsOnce(): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("UPDATE songs SET isFavorite = :favorite WHERE id = :songId")
    suspend fun updateFavorite(songId: Long, favorite: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :songId")
    suspend fun markSongPlayed(songId: Long, playedAt: Long)

    @Query("DELETE FROM songs")
    suspend fun clearSongs()

    @Query("UPDATE songs SET onlineCoverUrl = :coverUrl WHERE id = :songId")
    suspend fun updateCoverUrl(songId: Long, coverUrl: String?)

    @Query("SELECT onlineCoverUrl FROM songs WHERE id = :songId")
    suspend fun getCoverUrl(songId: Long): String?

    @Query("UPDATE songs SET cachedLyrics = :lyrics, cachedLyricsSource = :source, cachedLyricsOffset = :offset WHERE id = :songId")
    suspend fun updateCachedLyrics(songId: Long, lyrics: String?, source: String?, offset: Long = 0L)

    @Query("SELECT cachedLyrics, cachedLyricsSource, cachedLyricsOffset FROM songs WHERE id = :songId")
    suspend fun getCachedLyrics(songId: Long): CachedLyricsData?

    @Query("SELECT * FROM songs WHERE (onlineCoverUrl IS NULL OR onlineCoverUrl = '') AND id IN (:songIds)")
    suspend fun getSongsMissingCover(songIds: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE (cachedLyrics IS NULL OR cachedLyrics = '') AND id IN (:songIds)")
    suspend fun getSongsMissingLyrics(songIds: List<Long>): List<SongEntity>
}

data class CachedLyricsData(
    val cachedLyrics: String?,
    val cachedLyricsSource: String?,
    val cachedLyricsOffset: Long
)
