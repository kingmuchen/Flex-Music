package com.kingmc.flexmusic.feature.player.lyrics

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class AnalysisResult(
        val duration: Long,
        val sampleRate: Int,
        val channels: Int,
        val silenceDurationMs: Long,
        val voiceStartMs: Long,
        val energyProfile: List<EnergyFrame>,
        val recommendations: List<String>
    )

    data class EnergyFrame(
        val timeMs: Long,
        val energy: Double,
        val energyDb: Double,
        val zcr: Double,
        val isSilent: Boolean
    )

    suspend fun analyzeAudio(uri: Uri): AnalysisResult? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        
        try {
            extractor.setDataSource(context, uri, null)
            
            var audioTrackIndex = -1
            var duration = 0L
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    duration = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        format.getLong(MediaFormat.KEY_DURATION) / 1000
                    } else {
                        0L
                    }
                    break
                }
            }
            
            if (audioTrackIndex < 0) {
                Log.e(TAG, "No audio track found")
                return@withContext null
            }
            
            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            
            val frameSizeMs = 20L
            val samplesPerFrame = (sampleRate * frameSizeMs / 1000).toInt()
            val bytesPerFrame = samplesPerFrame * channelCount * 2
            val buffer = java.nio.ByteBuffer.allocate(bytesPerFrame * 2)
            
            val energyProfile = mutableListOf<EnergyFrame>()
            var voiceStartMs = 0L
            var consecutiveSilent = 0
            var consecutiveNonSilent = 0
            var foundVoiceStart = false
            var frameCount = 0
            
            val silenceThresholdDb = -40.0
            val maxAnalyzeMs = 15000L
            val maxFrames = (maxAnalyzeMs / frameSizeMs).toInt()
            
            while (!foundVoiceStart && frameCount < maxFrames) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                
                val sampleTimeUs = extractor.sampleTime
                val timeMs = sampleTimeUs / 1000
                
                val frameEnergy = calculateFrameEnergy(buffer, sampleSize)
                val frameZcr = calculateZeroCrossingRate(buffer, sampleSize)
                val energyDb = if (frameEnergy > 0) 20 * kotlin.math.log10(frameEnergy / 32767.0) else -100.0
                
                val isSilent = energyDb < silenceThresholdDb && frameZcr < 0.15
                
                energyProfile.add(EnergyFrame(
                    timeMs = timeMs,
                    energy = frameEnergy,
                    energyDb = energyDb,
                    zcr = frameZcr,
                    isSilent = isSilent
                ))
                
                if (isSilent) {
                    consecutiveSilent++
                    consecutiveNonSilent = 0
                } else {
                    consecutiveNonSilent++
                    if (consecutiveNonSilent >= 3) {
                        voiceStartMs = timeMs - (2 * frameSizeMs)
                        foundVoiceStart = true
                        Log.d(TAG, "Voice start detected at ${voiceStartMs}ms")
                    }
                }
                
                extractor.advance()
                frameCount++
            }
            
            val silenceDurationMs = voiceStartMs.coerceAtLeast(0L)
            
            val recommendations = generateRecommendations(silenceDurationMs, energyProfile)
            
            AnalysisResult(
                duration = duration,
                sampleRate = sampleRate,
                channels = channelCount,
                silenceDurationMs = silenceDurationMs,
                voiceStartMs = voiceStartMs,
                energyProfile = energyProfile.take(100),
                recommendations = recommendations
            )
        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed: ${e.message}", e)
            null
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release extractor: ${e.message}")
            }
        }
    }

    private fun calculateFrameEnergy(buffer: java.nio.ByteBuffer, size: Int): Double {
        if (size < 2) return 0.0
        
        var sumSquares = 0.0
        var count = 0
        
        for (i in 0 until size - 1 step 2) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low
            
            sumSquares += sample.toDouble() * sample.toDouble()
            count++
        }
        
        return if (count > 0) kotlin.math.sqrt(sumSquares / count) else 0.0
    }

    private fun calculateZeroCrossingRate(buffer: java.nio.ByteBuffer, size: Int): Double {
        if (size < 4) return 0.0
        
        var crossings = 0
        var count = 0
        var prevSample = 0
        
        for (i in 0 until size - 1 step 2) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low
            
            if (prevSample != 0 && count > 0) {
                if ((prevSample > 0 && sample < 0) || (prevSample < 0 && sample > 0)) {
                    crossings++
                }
            }
            
            prevSample = sample
            count++
        }
        
        return if (count > 0) crossings.toDouble() / count else 0.0
    }

    private fun generateRecommendations(
        silenceDurationMs: Long,
        energyProfile: List<EnergyFrame>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (silenceDurationMs > 2000) {
            recommendations.add("检测到较长的前置静默 (${silenceDurationMs}ms)，建议歌词时间偏移 -${silenceDurationMs}ms")
        } else if (silenceDurationMs > 500) {
            recommendations.add("检测到中等长度的前置静默 (${silenceDurationMs}ms)，可能需要微调歌词偏移")
        } else if (silenceDurationMs > 0) {
            recommendations.add("前置静默较短 (${silenceDurationMs}ms)，通常无需调整")
        } else {
            recommendations.add("未检测到明显的前置静默，音频直接开始")
        }
        
        val avgEnergy = energyProfile.map { it.energy }.average()
        val energyVariance = energyProfile.map { it.energy }.let { list ->
            val avg = list.average()
            list.map { (it - avg) * (it - avg) }.average()
        }
        
        if (energyVariance > avgEnergy * avgEnergy * 0.5) {
            recommendations.add("音频能量变化较大，可能存在动态范围问题")
        }
        
        val silentRatio = energyProfile.count { it.isSilent }.toDouble() / energyProfile.size
        if (silentRatio > 0.3) {
            recommendations.add("前${energyProfile.size * 20}ms内有${(silentRatio * 100).toInt()}%为静默，可能影响歌词同步")
        }
        
        return recommendations
    }

    companion object {
        private const val TAG = "AudioAnalyzer"
    }
}
