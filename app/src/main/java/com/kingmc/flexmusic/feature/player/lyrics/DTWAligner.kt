package com.kingmc.flexmusic.feature.player.lyrics

import android.util.Log
import kotlin.math.abs
import kotlin.math.min

object DTWAligner {

    data class AlignmentResult(
        val warpingPath: List<Pair<Int, Int>>,
        val distance: Double,
        val offsetMs: Long
    )

    fun alignLyricsToAudio(
        lyricTimestamps: List<Long>,
        audioEnergyProfile: List<Double>,
        lyricEnergyProfile: List<Double>,
        frameSizeMs: Long = 100L
    ): AlignmentResult {
        if (lyricTimestamps.isEmpty() || audioEnergyProfile.isEmpty() || lyricEnergyProfile.isEmpty()) {
            return AlignmentResult(emptyList(), 0.0, 0L)
        }
        
        val n = audioEnergyProfile.size
        val m = lyricEnergyProfile.size
        
        val dtwMatrix = Array(n + 1) { DoubleArray(m + 1) { Double.MAX_VALUE } }
        dtwMatrix[0][0] = 0.0
        
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = calculateDistance(
                    audioEnergyProfile[i - 1],
                    lyricEnergyProfile[j - 1]
                )
                
                dtwMatrix[i][j] = cost + minOf(
                    dtwMatrix[i - 1][j],
                    dtwMatrix[i][j - 1],
                    dtwMatrix[i - 1][j - 1]
                )
            }
        }
        
        val warpingPath = extractWarpingPath(dtwMatrix, n, m)
        
        val offsetMs = calculateOffset(warpingPath, frameSizeMs)
        
        return AlignmentResult(
            warpingPath = warpingPath,
            distance = dtwMatrix[n][m],
            offsetMs = offsetMs
        )
    }

    private fun calculateDistance(a: Double, b: Double): Double {
        return abs(a - b)
    }

    private fun extractWarpingPath(matrix: Array<DoubleArray>, n: Int, m: Int): List<Pair<Int, Int>> {
        val path = mutableListOf<Pair<Int, Int>>()
        var i = n
        var j = m
        
        while (i > 0 && j > 0) {
            path.add(0, Pair(i - 1, j - 1))
            
            val diag = if (i > 0 && j > 0) matrix[i - 1][j - 1] else Double.MAX_VALUE
            val left = if (j > 0) matrix[i][j - 1] else Double.MAX_VALUE
            val up = if (i > 0) matrix[i - 1][j] else Double.MAX_VALUE
            
            when {
                diag <= left && diag <= up -> { i--; j-- }
                left <= up -> j--
                else -> i--
            }
        }
        
        return path
    }

    private fun calculateOffset(warpingPath: List<Pair<Int, Int>>, frameSizeMs: Long): Long {
        if (warpingPath.isEmpty()) return 0L
        
        val offsets = mutableListOf<Long>()
        val sampleSize = minOf(warpingPath.size / 4, 20)
        
        for (k in 0 until sampleSize) {
            val idx = (warpingPath.size * k / sampleSize).coerceIn(0, warpingPath.size - 1)
            val (audioFrame, lyricFrame) = warpingPath[idx]
            val offset = (audioFrame - lyricFrame) * frameSizeMs
            offsets.add(offset)
        }
        
        if (offsets.isEmpty()) return 0L
        
        offsets.sort()
        return offsets[offsets.size / 2]
    }

    fun calculateOptimalOffset(
        lyricLines: List<LyricLine>,
        audioEnergyProfile: List<Double>,
        frameSizeMs: Long = 100L
    ): Long {
        if (lyricLines.isEmpty() || audioEnergyProfile.isEmpty()) return 0L
        
        val lyricEnergyProfile = generateLyricEnergyProfile(lyricLines, audioEnergyProfile.size)
        
        val result = alignLyricsToAudio(
            lyricTimestamps = lyricLines.map { it.startMs },
            audioEnergyProfile = audioEnergyProfile,
            lyricEnergyProfile = lyricEnergyProfile,
            frameSizeMs = frameSizeMs
        )
        
        Log.d("DTWAligner", "DTW offset: ${result.offsetMs}ms, distance: ${result.distance}")
        
        return result.offsetMs
    }

    private fun generateLyricEnergyProfile(lyricLines: List<LyricLine>, targetSize: Int): List<Double> {
        if (lyricLines.isEmpty() || targetSize <= 0) return List(targetSize) { 0.0 }
        
        val profile = MutableList(targetSize) { 0.0 }
        val totalDuration = lyricLines.lastOrNull()?.let { it.startMs + 10000 } ?: 10000L
        
        for (line in lyricLines) {
            val normalizedPos = (line.startMs.toDouble() / totalDuration * targetSize).toInt()
                .coerceIn(0, targetSize - 1)
            
            profile[normalizedPos] = 1.0
            
            if (normalizedPos > 0) {
                profile[normalizedPos - 1] = 0.5
            }
            if (normalizedPos < targetSize - 1) {
                profile[normalizedPos + 1] = 0.5
            }
        }
        
        return profile
    }

    fun findBestAlignment(
        lyricLines: List<LyricLine>,
        audioEnergyProfile: List<Double>,
        searchRangeMs: Long = 5000L,
        stepMs: Long = 100L
    ): Long {
        if (lyricLines.isEmpty() || audioEnergyProfile.isEmpty()) return 0L
        
        var bestOffset = 0L
        var bestScore = Double.MAX_VALUE
        
        for (offset in -searchRangeMs..searchRangeMs step stepMs) {
            val score = calculateAlignmentScore(lyricLines, audioEnergyProfile, offset)
            if (score < bestScore) {
                bestScore = score
                bestOffset = offset
            }
        }
        
        Log.d("DTWAligner", "Best alignment offset: ${bestOffset}ms, score: $bestScore")
        return bestOffset
    }

    private fun calculateAlignmentScore(
        lyricLines: List<LyricLine>,
        audioEnergyProfile: List<Double>,
        offsetMs: Long
    ): Double {
        if (lyricLines.isEmpty() || audioEnergyProfile.isEmpty()) return Double.MAX_VALUE
        
        var score = 0.0
        val frameSizeMs = 100L
        
        for (line in lyricLines) {
            val adjustedTime = line.startMs + offsetMs
            val frameIndex = (adjustedTime / frameSizeMs).toInt().coerceIn(0, audioEnergyProfile.size - 1)
            
            val energy = audioEnergyProfile[frameIndex]
            
            score += (1.0 - energy)
        }
        
        return score / lyricLines.size
    }
}
