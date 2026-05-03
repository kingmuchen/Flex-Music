package com.kingmc.flexmusic.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.kingmc.flexmusic.data.local.FlexMusicDatabase
import com.kingmc.flexmusic.data.local.SongEntity
import com.kingmc.flexmusic.data.model.Song
import com.kingmc.flexmusic.data.scanner.MediaStoreMusicScanner
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val database: FlexMusicDatabase,
    private val scanner: MediaStoreMusicScanner
) : MusicRepository {

    override fun observeSongs(): Flow<List<Song>> {
        return database.songDao().observeSongs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeFavoriteSongs(): Flow<List<Song>> {
        return database.songDao().observeFavoriteSongs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeRecentSongs(limit: Int): Flow<List<Song>> {
        return database.songDao().observeRecentSongs(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun refreshLocalLibrary(): Result<Int> {
        return runCatching {
            val songs = scanner.scanSongs()
            val existing = database.songDao().getSongsOnce().associateBy { it.id }
            val merged = songs.map { song ->
                val old = existing[song.id]
                song.toEntity(
                    isFavorite = old?.isFavorite ?: false,
                    playCount = old?.playCount ?: 0,
                    lastPlayedAt = old?.lastPlayedAt ?: 0L,
                    onlineCoverUrl = old?.onlineCoverUrl,
                    cachedLyrics = old?.cachedLyrics,
                    cachedLyricsSource = old?.cachedLyricsSource,
                    cachedLyricsOffset = old?.cachedLyricsOffset ?: 0L
                )
            }
            database.withTransaction {
                database.songDao().clearSongs()
                database.songDao().upsertSongs(merged)
            }
            Log.i("FlexMusic.Repository", "refreshLocalLibrary success count=${songs.size}")
            songs.size
        }.onFailure { throwable ->
            Log.e("FlexMusic.Repository", "refreshLocalLibrary failed", throwable)
        }
    }

    override suspend fun setFavorite(songId: Long, favorite: Boolean) {
        runCatching {
            database.songDao().updateFavorite(songId, favorite)
        }.onFailure { throwable ->
            Log.e("FlexMusic.Repository", "setFavorite failed songId=$songId", throwable)
        }
    }

    override suspend fun markSongPlayed(songId: Long) {
        runCatching {
            database.songDao().markSongPlayed(songId, Instant.now().toEpochMilli())
        }.onFailure { throwable ->
            Log.e("FlexMusic.Repository", "markSongPlayed failed songId=$songId", throwable)
        }
    }
}

private fun SongEntity.toDomain(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        contentUri = contentUri,
        albumArtUri = albumArtUri,
        displayName = displayName,
        relativePath = relativePath,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
        onlineCoverUrl = onlineCoverUrl
    )
}

private fun Song.toEntity(
    isFavorite: Boolean = this.isFavorite,
    playCount: Int = this.playCount,
    lastPlayedAt: Long = this.lastPlayedAt,
    onlineCoverUrl: String? = this.onlineCoverUrl,
    cachedLyrics: String? = null,
    cachedLyricsSource: String? = null,
    cachedLyricsOffset: Long = 0L
): SongEntity {
    return SongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        contentUri = contentUri,
        albumArtUri = albumArtUri,
        displayName = displayName,
        relativePath = relativePath,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
        onlineCoverUrl = onlineCoverUrl,
        cachedLyrics = cachedLyrics,
        cachedLyricsSource = cachedLyricsSource,
        cachedLyricsOffset = cachedLyricsOffset
    )
}
