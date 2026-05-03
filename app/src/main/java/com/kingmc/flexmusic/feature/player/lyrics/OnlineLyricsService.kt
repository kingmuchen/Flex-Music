package com.kingmc.flexmusic.feature.player.lyrics

import android.content.Context
import android.util.Log
import com.kingmc.flexmusic.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineLyricsService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class OnlineLyricsResult(
        val lyrics: String,
        val source: String,
        val offset: Long = 0L,
        val confidence: Double = 0.0
    )

    data class OnlineCoverResult(
        val coverUrl: String,
        val source: String,
        val confidence: Double = 0.0
    )

    private data class SongMatch(
        val id: Long = 0,
        val hash: String = "",
        val songMid: String = "",
        val title: String = "",
        val artist: String = "",
        val durationMs: Long = 0,
        val coverUrl: String = "",
        val durationDiff: Long = Long.MAX_VALUE,
        val source: String = ""
    )

    suspend fun searchLyrics(song: Song, audioUri: android.net.Uri? = null): OnlineLyricsResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching lyrics for: ${song.title} - ${song.artist}")

            val candidates = mutableListOf<OnlineLyricsResult>()

            val oiapiResult = searchOiapi(song.title, song.artist)
            if (oiapiResult != null) {
                Log.d(TAG, "OIAPI result found")
                candidates.add(oiapiResult)
            }

            val qqsuuResult = searchQqsuu(song.title, song.artist)
            if (qqsuuResult != null) {
                Log.d(TAG, "Qqsuu result found")
                candidates.add(qqsuuResult)
            }

            val geciResult = searchGeciMe(song.title, song.artist, song.durationMs)
            if (geciResult != null) {
                Log.d(TAG, "Geci.me result found")
                candidates.add(geciResult)
            }

            val qqResult = searchQQMusic(song.title, song.artist, song.durationMs)
            if (qqResult != null) {
                Log.d(TAG, "QQ Music result found")
                candidates.add(qqResult)
            }

            val neteaseResult = searchNeteaseCloudByMetadata(song.title, song.artist, song.durationMs)
            if (neteaseResult != null) {
                Log.d(TAG, "Netease result found")
                candidates.add(neteaseResult)
            }

            val kugouResult = searchKugouByMetadata(song.title, song.artist, song.durationMs)
            if (kugouResult != null) {
                Log.d(TAG, "Kugou result found")
                candidates.add(kugouResult)
            }

            if (candidates.isEmpty()) {
                Log.d(TAG, "No lyrics found for: ${song.title} - ${song.artist}")
                return@withContext null
            }

            candidates.maxByOrNull { it.confidence }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search lyrics: ${e.message}")
            null
        }
    }

    suspend fun searchCover(song: Song): OnlineCoverResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching cover for: ${song.title} - ${song.artist}")

            val candidates = mutableListOf<OnlineCoverResult>()

            coroutineScope {
                val qqDeferred = async { searchQQMusicCover(song.title, song.artist, song.durationMs) }
                val neteaseDeferred = async { searchNeteaseCloudCover(song.title, song.artist, song.durationMs) }
                val kugouDeferred = async { searchKugouCover(song.title, song.artist, song.durationMs) }

                qqDeferred.await()?.let {
                    Log.d(TAG, "QQ Music cover found: ${it.coverUrl}")
                    candidates.add(it)
                }
                neteaseDeferred.await()?.let {
                    Log.d(TAG, "Netease cover found: ${it.coverUrl}")
                    candidates.add(it)
                }
                kugouDeferred.await()?.let {
                    Log.d(TAG, "Kugou cover found: ${it.coverUrl}")
                    candidates.add(it)
                }
            }

            if (candidates.isEmpty()) {
                Log.d(TAG, "No cover found for: ${song.title} - ${song.artist}")
                return@withContext null
            }

            val sourcePriority = mapOf("qq_music" to 1, "netease_cloud" to 2, "kugou" to 3)
            val best = candidates.sortedWith(
                compareBy(
                    { sourcePriority[it.source] ?: Int.MAX_VALUE },
                    { -it.confidence }
                )
            ).first()

            Log.d(TAG, "Best cover from ${best.source}: ${best.coverUrl} (confidence=${best.confidence})")
            best
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search cover: ${e.message}")
            null
        }
    }

    private suspend fun searchOiapi(title: String, artist: String): OnlineLyricsResult? {
        if (title.isBlank()) return null
        return try {
            val cleanTitle = cleanTitle(title)
            val cleanArtist = cleanArtist(artist)
            val keyword = URLEncoder.encode("$cleanTitle $cleanArtist", "UTF-8")
            val url = "https://oiapi.net/api/QQMusicLyric?keyword=$keyword"

            val response = performHttpRequest(url, referer = "https://oiapi.net/")
            if (response == null) return null

            val json = JSONObject(response)
            val lrc = json.optString("lrc", "")
            if (lrc.isBlank()) return null

            val offset = extractOffsetFromLyrics(lrc)
            OnlineLyricsResult(
                lyrics = lrc,
                source = "oiapi",
                offset = offset,
                confidence = 0.85
            )
        } catch (e: Exception) {
            Log.w(TAG, "OIAPI search failed: ${e.message}")
            null
        }
    }

    private suspend fun searchQqsuu(title: String, artist: String): OnlineLyricsResult? {
        if (title.isBlank()) return null
        return try {
            val cleanTitle = cleanTitle(title)
            val cleanArtist = cleanArtist(artist)
            val name = URLEncoder.encode(cleanTitle, "UTF-8")
            val artistParam = URLEncoder.encode(cleanArtist, "UTF-8")
            val url = "https://api.qqsuu.cn/api/music?name=$name&artist=$artistParam"

            val response = performHttpRequest(url, referer = "https://api.qqsuu.cn/")
            if (response == null) return null

            val json = JSONObject(response)
            if (json.optInt("code") != 200) return null

            val lrc = json.optString("lrc", "")
            if (lrc.isBlank()) return null

            val offset = extractOffsetFromLyrics(lrc)
            OnlineLyricsResult(
                lyrics = lrc,
                source = "qqsuu",
                offset = offset,
                confidence = 0.8
            )
        } catch (e: Exception) {
            Log.w(TAG, "Qqsuu search failed: ${e.message}")
            null
        }
    }

    private suspend fun searchGeciMe(
        title: String,
        artist: String,
        durationMs: Long
    ): OnlineLyricsResult? {
        if (title.isBlank()) return null
        return try {
            val cleanTitle = cleanTitle(title)
            val cleanArtist = cleanArtist(artist)
            val searchQueries = generateSearchQueries(cleanTitle, cleanArtist)

            for (query in searchQueries) {
                val matches = searchGeciMeSongs(query, durationMs)
                val best = selectBestMatch(matches, durationMs)
                if (best != null) {
                    val lyrics = fetchGeciMeLyrics(best.id)
                    if (lyrics != null && lyrics.isNotBlank()) {
                        val offset = extractOffsetFromLyrics(lyrics)
                        return OnlineLyricsResult(
                            lyrics = lyrics,
                            source = "geci_me",
                            offset = offset,
                            confidence = calculateConfidence(best, durationMs)
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Geci.me search failed: ${e.message}")
            null
        }
    }

    private suspend fun searchGeciMeSongs(query: String, durationMs: Long): List<SongMatch> {
        val matches = mutableListOf<SongMatch>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "http://api.geci.me/song/search?q=$encodedQuery"

            val response = performHttpRequest(searchUrl, referer = "http://api.geci.me/")
            if (response == null) return matches

            val json = JSONObject(response)
            val result = json.optJSONObject("result") ?: return matches
            val songs = result.optJSONArray("data") ?: return matches

            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                val songId = song.optLong("songid", song.optLong("id"))
                val songName = song.optString("songname", song.optString("name"))
                val artistName = song.optString("artistname", song.optString("artist"))
                val songDuration = song.optLong("duration", song.optLong("length")) * 1000

                matches.add(SongMatch(
                    id = songId,
                    title = songName,
                    artist = artistName,
                    durationMs = songDuration,
                    durationDiff = if (durationMs > 0 && songDuration > 0) {
                        kotlin.math.abs(songDuration - durationMs)
                    } else {
                        Long.MAX_VALUE
                    },
                    source = "geci_me"
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geci.me song search failed: ${e.message}")
        }
        return matches
    }

    private suspend fun fetchGeciMeLyrics(songId: Long): String? {
        return try {
            val lyricsUrl = "http://api.geci.me/lrc/$songId"
            val response = performHttpRequest(lyricsUrl, referer = "http://api.geci.me/")
            if (response == null) return null

            val json = JSONObject(response)
            val result = json.optJSONObject("result") ?: return null
            val lrc = result.optString("lrc", "")
            if (lrc.isNotBlank()) return lrc

            val lrcList = result.optJSONArray("lrc") ?: return null
            if (lrcList.length() > 0) {
                lrcList.optString(0)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Geci.me lyrics fetch failed: ${e.message}")
            null
        }
    }

    private suspend fun searchQQMusic(
        title: String,
        artist: String,
        durationMs: Long
    ): OnlineLyricsResult? {
        if (title.isBlank()) return null
        return try {
            val cleanTitle = cleanTitle(title)
            val cleanArtist = cleanArtist(artist)
            val searchQueries = generateSearchQueries(cleanTitle, cleanArtist)

            for (query in searchQueries) {
                val matches = searchQQMusicSongs(query, durationMs)
                val best = selectBestMatch(matches, durationMs)
                if (best != null) {
                    val lyrics = fetchQQMusicLyrics(best.songMid)
                    if (lyrics != null && lyrics.isNotBlank()) {
                        val offset = extractOffsetFromLyrics(lyrics)
                        return OnlineLyricsResult(
                            lyrics = lyrics,
                            source = "qq_music",
                            offset = offset,
                            confidence = calculateConfidence(best, durationMs)
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "QQ Music search failed: ${e.message}")
            null
        }
    }

    private suspend fun searchQQMusicSongs(query: String, durationMs: Long): List<SongMatch> {
        val matches = mutableListOf<SongMatch>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encodedQuery&format=json&n=10&p=1"

            val response = performHttpRequest(searchUrl, referer = "https://y.qq.com/portal/player.html")
            if (response == null) {
                Log.w(TAG, "QQ Music song search: no response for query='$query'")
                return matches
            }

            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: run {
                Log.w(TAG, "QQ Music song search: no data field in response")
                return matches
            }
            val songList = data.optJSONObject("song")?.optJSONArray("list") ?: run {
                Log.w(TAG, "QQ Music song search: no song list in response")
                return matches
            }

            for (i in 0 until songList.length()) {
                val song = songList.optJSONObject(i) ?: continue
                val songMid = song.optString("songmid", "")
                val songName = song.optString("songname", song.optString("name"))
                val singerList = song.optJSONArray("singer")
                val singerName = singerList?.optJSONObject(0)?.optString("name") ?: ""
                val songDuration = song.optLong("interval") * 1000
                val albumMid = song.optJSONObject("album")?.optString("mid") ?: song.optString("albummid", "")

                val coverUrl = if (albumMid.isNotBlank()) {
                    "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albumMid}.jpg"
                } else ""

                matches.add(SongMatch(
                    songMid = songMid,
                    title = songName,
                    artist = singerName,
                    durationMs = songDuration,
                    coverUrl = coverUrl,
                    durationDiff = if (durationMs > 0 && songDuration > 0) {
                        kotlin.math.abs(songDuration - durationMs)
                    } else {
                        Long.MAX_VALUE
                    },
                    source = "qq_music"
                ))
            }
            Log.d(TAG, "QQ Music song search: found ${matches.size} songs, ${matches.count { it.coverUrl.isNotBlank() }} with covers")
        } catch (e: Exception) {
            Log.w(TAG, "QQ Music song search failed: ${e.message}")
        }
        return matches
    }

    private suspend fun fetchQQMusicLyrics(songMid: String): String? {
        if (songMid.isBlank()) return null
        return try {
            val lyricsUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid&format=json&nobase64=1"
            val response = performHttpRequest(lyricsUrl, referer = "https://y.qq.com/portal/player.html")
            if (response == null) return null

            val json = JSONObject(response)
            val lyric = json.optString("lyric", "")
            if (lyric.isBlank()) return null

            val trans = json.optString("trans", "")
            if (trans.isNotBlank()) {
                mergeTranslation(lyric, trans)
            } else {
                lyric
            }
        } catch (e: Exception) {
            Log.w(TAG, "QQ Music lyrics fetch failed: ${e.message}")
            null
        }
    }

    private suspend fun searchQQMusicCover(
        title: String,
        artist: String,
        durationMs: Long
    ): OnlineCoverResult? {
        return try {
            val cleanTitle = cleanTitle(title)
            val cleanArtist = cleanArtist(artist)
            val searchQueries = generateSearchQueries(cleanTitle, cleanArtist)

            for (query in searchQueries) {
                val matches = searchQQMusicSongs(query, durationMs)
                Log.d(TAG, "QQ Music cover search: query='$query', found ${matches.size} matches, covers=${matches.count { it.coverUrl.isNotBlank() }}")
                val best = selectBestMatchForCover(matches, durationMs)
                if (best != null && best.coverUrl.isNotBlank()) {
                    Log.d(TAG, "QQ Music cover best match: title='${best.title}', coverUrl=${best.coverUrl}")
                    return OnlineCoverResult(
                        coverUrl = best.coverUrl,
                        source = "qq_music",
                        confidence = calculateConfidence(best, durationMs)
                    )
                }
            }
            Log.d(TAG, "QQ Music cover: no match found for '$title' '$artist'")
            null
        } catch (e: Exception) {
            Log.w(TAG, "QQ Music cover search failed: ${e.message}")
            null
        }
    }

    private suspend fun searchNeteaseCloudByMetadata(
        title: String,
        artist: String,
        durationMs: Long
    ): OnlineLyricsResult? {
        if (title.isBlank()) return null

        val cleanTitle = cleanTitle(title)
        val cleanArtist = cleanArtist(artist)
        val searchQueries = generateSearchQueries(cleanTitle, cleanArtist)

        for (query in searchQueries) {
            try {
                val matches = searchNeteaseCloudSongs(query, durationMs)
                val best = selectBestMatch(matches, durationMs)
                if (best != null) {
                    val lyrics = fetchNeteaseLyrics(best.id)
                    if (lyrics != null) {
                        val offset = extractOffsetFromLyrics(lyrics)
                        return OnlineLyricsResult(
                            lyrics = lyrics,
                            source = "netease_cloud",
                            offset = offset,
                            confidence = calculateConfidence(best, durationMs)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Netease search failed for query: $query, error: ${e.message}")
            }
        }
        return null
    }

    private suspend fun searchNeteaseCloudSongs(query: String, durationMs: Long): List<SongMatch> {
        val matches = mutableListOf<SongMatch>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://music.163.com/api/search/get/web?type=1&s=$encodedQuery&limit=10"

            val response = performHttpRequest(searchUrl)
            if (response == null) return matches

            val json = JSONObject(response)
            if (json.optInt("code") != 200) return matches

            val result = json.optJSONObject("result") ?: return matches
            val songs = result.optJSONArray("songs") ?: return matches

            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                val songId = song.optLong("id")
                val songDuration = song.optLong("duration")
                val songName = song.optString("name")
                val artists = song.optJSONArray("artists")
                val artistName = artists?.optJSONObject(0)?.optString("name") ?: ""
                val album = song.optJSONObject("album")
                val coverUrl = album?.optString("picUrl") ?: ""

                matches.add(SongMatch(
                    id = songId,
                    title = songName,
                    artist = artistName,
                    durationMs = songDuration,
                    coverUrl = coverUrl,
                    durationDiff = kotlin.math.abs(songDuration - durationMs),
                    source = "netease"
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Netease song search failed: ${e.message}")
        }
        return matches
    }

    private suspend fun fetchNeteaseLyrics(songId: Long): String? {
        return try {
            val lyricsUrl = "https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1"
            val response = performHttpRequest(lyricsUrl)
            if (response == null) return null

            val json = JSONObject(response)
            if (json.optInt("code") != 200) return null

            val lrc = json.optJSONObject("lrc")
            val lyricText = lrc?.optString("lyric") ?: ""
            if (lyricText.isBlank()) return null

            val tlyric = json.optJSONObject("tlyric")
            val translationText = tlyric?.optString("lyric") ?: ""

            if (translationText.isNotBlank()) {
                mergeTranslation(lyricText, translationText)
            } else {
                lyricText
            }
        } catch (e: Exception) {
            Log.w(TAG, "Netease lyrics fetch failed: ${e.message}")
            null
        }
    }

    private fun mergeTranslation(originalLrc: String, translationLrc: String): String {
        val translationMap = mutableMapOf<Long, String>()
        val timeRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})]""")

        translationLrc.lines().forEach { line ->
            val match = timeRegex.find(line) ?: return@forEach
            val text = timeRegex.replace(line, "").trim()
            if (text.isBlank()) return@forEach

            val min = match.groupValues[1].toLongOrNull() ?: return@forEach
            val sec = match.groupValues[2].toLongOrNull() ?: return@forEach
            val msStr = match.groupValues[3]
            val ms = if (msStr.length == 2) msStr.toLongOrNull()?.times(10) else msStr.toLongOrNull()
            if (ms == null) return@forEach

            val timeMs = min * 60000 + sec * 1000 + ms
            translationMap[timeMs] = text
        }

        val result = StringBuilder()
        originalLrc.lines().forEach { line ->
            result.append(line).append("\n")
            val match = timeRegex.find(line) ?: return@forEach
            val min = match.groupValues[1].toLongOrNull() ?: return@forEach
            val sec = match.groupValues[2].toLongOrNull() ?: return@forEach
            val msStr = match.groupValues[3]
            val ms = if (msStr.length == 2) msStr.toLongOrNull()?.times(10) else msStr.toLongOrNull()
            if (ms == null) return@forEach

            val timeMs = min * 60000 + sec * 1000 + ms
            val translation = translationMap[timeMs]
            if (translation != null) {
                val timeTag = match.value
                result.append("$timeTag$translation\n")
            }
        }
        return result.toString().trimEnd()
    }

    private suspend fun searchKugouByMetadata(
        title: String,
        artist: String,
        durationMs: Long
    ): OnlineLyricsResult? {
        if (title.isBlank()) return null

        val cleanTitle = cleanTitle(title)
        val cleanArtist = cleanArtist(artist)
        val searchQueries = generateSearchQueries(cleanTitle, cleanArtist)

        for (query in searchQueries) {
            try {
                val matches = searchKugouSongs(query, durationMs)
                val best = selectBestMatch(matches, durationMs)
                if (best != null) {
                    val lyrics = fetchKugouLyrics(best.hash)
                    if (lyrics != null) {
                        val cleanLyrics = cleanKugouLyrics(lyrics)
                        if (cleanLyrics.isNotBlank()) {
                            val offset = extractOffsetFromLyrics(cleanLyrics)
                            return OnlineLyricsResult(
                                lyrics = cleanLyrics,
                                source = "kugou",
                                offset = offset,
                                confidence = calculateConfidence(best, durationMs)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Kugou search failed for query: $query, error: ${e.message}")
            }
        }
        return null
    }

    private suspend fun searchKugouSongs(query: String, durationMs: Long): List<SongMatch> {
        val matches = mutableListOf<SongMatch>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=$encodedQuery&page=1&pagesize=10&showtype=1"

            val response = performHttpRequest(searchUrl, referer = "https://www.kugou.com")
            if (response == null) {
                return searchKugouSongsFallback(query, durationMs)
            }

            val json = JSONObject(response)
            if (json.optInt("errcode") != 0) {
                return searchKugouSongsFallback(query, durationMs)
            }

            val data = json.optJSONObject("data") ?: return matches
            val info = data.optJSONArray("info") ?: return matches

            for (i in 0 until info.length()) {
                val song = info.optJSONObject(i) ?: continue
                val hash = song.optString("hash")
                val songDuration = song.optLong("duration") * 1000
                val songName = song.optString("songname")
                val singerName = song.optString("singername")

                var coverUrl = song.optString("album_img", "")
                if (coverUrl.isBlank()) {
                    coverUrl = song.optString("imgurl", "")
                }
                coverUrl = coverUrl.replace("{size}", "400")

                matches.add(SongMatch(
                    hash = hash,
                    title = songName,
                    artist = singerName,
                    durationMs = songDuration,
                    coverUrl = coverUrl,
                    durationDiff = kotlin.math.abs(songDuration - durationMs),
                    source = "kugou"
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kugou song search failed: ${e.message}")
        }
        return matches
    }

    private suspend fun searchKugouSongsFallback(query: String, durationMs: Long): List<SongMatch> {
        val matches = mutableListOf<SongMatch>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://songsearch.kugou.com/song_search_v2?keyword=$encodedQuery&page=1&pagesize=10&platform=WebFilter"

            val response = performHttpRequest(searchUrl, referer = "https://www.kugou.com")
            if (response == null) return matches

            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: return matches
            val lists = data.optJSONArray("lists") ?: return matches

            for (i in 0 until lists.length()) {
                val song = lists.optJSONObject(i) ?: continue
                val hash = song.optString("FileHash")
                val songDuration = song.optLong("Duration") * 1000
                val songName = song.optString("SongName")
                val singerName = song.optString("SingerName")

                var coverUrl = song.optString("Image", "")
                if (coverUrl.isBlank()) {
                    val albumId = song.optString("AlbumID", "")
                    if (albumId.isNotBlank()) {
                        coverUrl = "https://img.kugou.com/album/400/${albumId}.jpg"
                    }
                }
                coverUrl = coverUrl.replace("{size}", "400")

                matches.add(SongMatch(
                    hash = hash,
                    title = songName,
                    artist = singerName,
                    durationMs = songDuration,
                    coverUrl = coverUrl,
                    durationDiff = kotlin.math.abs(songDuration - durationMs),
                    source = "kugou"
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kugou fallback song search failed: ${e.message}")
        }
        return matches
    }

    private suspend fun fetchKugouLyrics(hash: String): String? {
        if (hash.isBlank()) return null
        return try {
            val lyricsUrl = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=&duration=&hash=$hash"

            val response = performHttpRequest(lyricsUrl, referer = "https://www.kugou.com")
            if (response == null) return null

            val json = JSONObject(response)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) return null

            val candidate = candidates.optJSONObject(0) ?: return null
            val id = candidate.optLong("id")
            val accessKey = candidate.optString("accesskey")
            if (accessKey.isBlank()) return null

            val downloadUrl = "https://krcs.kugou.com/download?ver=1&client=mobi&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"

            val downloadResponse = performHttpRequest(downloadUrl, referer = "https://www.kugou.com")
            if (downloadResponse == null) return null

            val downloadJson = JSONObject(downloadResponse)
            val content = downloadJson.optString("content")
            if (content.isBlank()) return null

            try {
                java.util.Base64.getDecoder().decode(content).toString(Charsets.UTF_8)
            } catch (e: Exception) {
                content
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Kugou lyrics: ${e.message}")
            null
        }
    }

    private fun selectBestMatch(matches: List<SongMatch>, targetDurationMs: Long): SongMatch? {
        if (matches.isEmpty()) return null

        val sorted = matches.sortedBy { it.durationDiff }

        if (targetDurationMs <= 0) return sorted.firstOrNull()

        val best = sorted.first()
        val toleranceMs = (targetDurationMs * 0.1).toLong().coerceIn(3000L, 15000L)

        return if (best.durationDiff <= toleranceMs) best else null
    }

    private fun selectBestMatchForCover(matches: List<SongMatch>, targetDurationMs: Long): SongMatch? {
        if (matches.isEmpty()) return null

        val withCover = matches.filter { it.coverUrl.isNotBlank() }
        if (withCover.isEmpty()) return null

        val sorted = withCover.sortedBy { it.durationDiff }

        if (targetDurationMs <= 0) return sorted.firstOrNull()

        val best = sorted.first()
        val toleranceMs = (targetDurationMs * 0.2).toLong().coerceIn(5000L, 30000L)

        return if (best.durationDiff <= toleranceMs) best else sorted.firstOrNull()
    }

    private fun calculateConfidence(match: SongMatch, targetDurationMs: Long): Double {
        if (targetDurationMs <= 0) return 0.5
        val ratio = 1.0 - (match.durationDiff.toDouble() / targetDurationMs).coerceIn(0.0, 1.0)
        return (ratio * 0.8 + 0.2).coerceIn(0.0, 1.0)
    }

    private suspend fun searchNeteaseCloudCover(
        title: String,
        artist: String,
        durationMs: Long
    ): OnlineCoverResult? {
        return try {
            val cleanTitle = cleanTitle(title)
            val cleanArtist = cleanArtist(artist)
            val searchQueries = generateSearchQueries(cleanTitle, cleanArtist)

            for (query in searchQueries) {
                val matches = searchNeteaseCloudSongs(query, durationMs)
                val best = selectBestMatch(matches, durationMs)
                if (best != null) {
                    val coverUrl = fetchNeteaseCoverUrl(best.id)
                    if (coverUrl.isNotBlank()) {
                        Log.d(TAG, "Netease cover found: url=$coverUrl")
                        return OnlineCoverResult(
                            coverUrl = coverUrl,
                            source = "netease_cloud",
                            confidence = calculateConfidence(best, durationMs)
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Netease Cloud cover search failed: ${e.message}")
            null
        }
    }

    private suspend fun fetchNeteaseCoverUrl(songId: Long): String {
        return try {
            val detailUrl = "https://music.163.com/api/song/detail?id=$songId&ids=%5B$songId%5D"
            val response = performHttpRequest(detailUrl)
            if (response == null) return ""

            val json = JSONObject(response)
            if (json.optInt("code") != 200) return ""

            val songs = json.optJSONArray("songs") ?: return ""
            if (songs.length() == 0) return ""

            val song = songs.optJSONObject(0) ?: return ""
            val album = song.optJSONObject("album") ?: return ""
            album.optString("picUrl", "")
        } catch (e: Exception) {
            Log.w(TAG, "Netease cover URL fetch failed: ${e.message}")
            ""
        }
    }

    private suspend fun searchKugouCover(
        title: String,
        artist: String,
        durationMs: Long
    ): OnlineCoverResult? {
        return try {
            val cleanTitle = cleanTitle(title)
            val cleanArtist = cleanArtist(artist)
            val searchQueries = generateSearchQueries(cleanTitle, cleanArtist)

            for (query in searchQueries) {
                val matches = searchKugouSongs(query, durationMs)
                val best = selectBestMatchForCover(matches, durationMs)
                if (best != null && best.coverUrl.isNotBlank()) {
                    Log.d(TAG, "Kugou cover found: url=${best.coverUrl}")
                    return OnlineCoverResult(
                        coverUrl = best.coverUrl,
                        source = "kugou",
                        confidence = calculateConfidence(best, durationMs)
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Kugou cover search failed: ${e.message}")
            null
        }
    }

    private fun cleanKugouLyrics(lyrics: String): String {
        return lyrics.lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun extractOffsetFromLyrics(lyrics: String): Long {
        val offsetRegex = Regex("""\[offset:(-?\d+)\]""", RegexOption.IGNORE_CASE)
        val match = offsetRegex.find(lyrics)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun generateSearchQueries(title: String, artist: String): List<String> {
        val queries = mutableListOf<String>()
        if (title.isNotBlank() && artist.isNotBlank()) {
            queries.add("$title $artist")
            queries.add("$artist $title")
        }
        if (title.isNotBlank()) {
            queries.add(title)
        }
        return queries.distinct()
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""\(.*?\)"""), "")
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""【.*?】"""), "")
            .replace(Regex("""（.*?）"""), "")
            .trim()
    }

    private fun cleanArtist(artist: String): String {
        return artist
            .replace(Regex("""\(.*?\)"""), "")
            .replace(Regex("""\[.*?\]"""), "")
            .split("/", ",", "、", "&", "feat.", "ft.")
            .firstOrNull()?.trim() ?: artist.trim()
    }

    private fun performHttpRequest(url: String, referer: String = "https://music.163.com/"): String? {
        return try {
            val connection = java.net.URL(url).openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.setRequestProperty("Referer", referer)
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            val responseCode = (connection as? java.net.HttpURLConnection)?.responseCode ?: -1
            val result = connection.getInputStream().bufferedReader().use { it.readText() }
            if (result.isBlank()) {
                Log.w(TAG, "HTTP request returned empty response for $url (code=$responseCode)")
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "HTTP request failed for $url: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "OnlineLyricsService"
    }
}
