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
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

@Singleton
class AudioSilenceDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val silenceThresholdDb = -40.0
    private val maxSilenceDurationMs = 3000L
    private val minSilenceDurationMs = 200L
    private val nonSilentFramesRequired = 5

    var enabled: Boolean = true
        private set

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    suspend fun detectSilenceAtStart(uri: Uri): Long = withContext(Dispatchers.IO) {
        if (!enabled) {
            return@withContext 0L
        }
        
        try {
            detectVoiceStartUsingWaveform(uri)
        } catch (e: Exception) {
            Log.w(TAG, "Voice start detection failed: ${e.message}")
            0L
        }
    }

    private fun detectVoiceStartUsingWaveform(uri: Uri): Long {
        val extractor = MediaExtractor()
        
        return try {
            extractor.setDataSource(context, uri, null)
            
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }
            
            if (audioTrackIndex < 0) {
                Log.w(TAG, "No audio track found")
                return 0L
            }
            
            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            
            val frameSizeMs = 50L
            val samplesPerFrame = (sampleRate * frameSizeMs / 1000).toInt()
            val bytesPerFrame = samplesPerFrame * channelCount * 2
            val buffer = java.nio.ByteBuffer.allocate(bytesPerFrame * 2)
            
            var voiceStartTimeUs = 0L
            var consecutiveSilentFrames = 0
            var consecutiveNonSilentFrames = 0
            var foundVoiceStart = false
            var totalFramesAnalyzed = 0
            val maxFramesToAnalyze = (maxSilenceDurationMs / frameSizeMs).toInt()
            
            val energyHistory = mutableListOf<Double>()
            val historySize = 8
            
            while (!foundVoiceStart && totalFramesAnalyzed < maxFramesToAnalyze) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                
                val sampleTimeUs = extractor.sampleTime
                val frameEnergy = calculateFrameEnergy(buffer, sampleSize)
                val frameZCR = calculateZeroCrossingRate(buffer, sampleSize)
                
                energyHistory.add(frameEnergy)
                if (energyHistory.size > historySize) {
                    energyHistory.removeAt(0)
                }
                
                val avgEnergy = if (energyHistory.isNotEmpty()) energyHistory.average() else 0.0
                val energyDb = if (avgEnergy > 0) 20 * log10(avgEnergy / 32767.0) else -100.0
                
                val isSilent = energyDb < silenceThresholdDb && frameZCR < 0.15
                
                if (isSilent) {
                    consecutiveSilentFrames++
                    consecutiveNonSilentFrames = 0
                } else {
                    consecutiveNonSilentFrames++
                    if (consecutiveNonSilentFrames >= nonSilentFramesRequired) {
                        voiceStartTimeUs = sampleTimeUs - (nonSilentFramesRequired * frameSizeMs * 1000)
                        foundVoiceStart = true
                        Log.d(TAG, "Voice start detected at ${voiceStartTimeUs / 1000}ms, energy=${energyDb}dB, zcr=$frameZCR")
                    }
                }
                
                extractor.advance()
                totalFramesAnalyzed++
            }
            
            if (!foundVoiceStart) {
                Log.d(TAG, "No voice start detected, assuming no silence at start")
                return 0L
            }
            
            val silenceMs = (voiceStartTimeUs / 1000).coerceIn(0L, maxSilenceDurationMs)
            
            if (silenceMs >= minSilenceDurationMs) {
                Log.d(TAG, "Detected silence duration: ${silenceMs}ms")
                silenceMs
            } else {
                Log.d(TAG, "Silence too short ($silenceMs ms), ignoring")
                0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting voice start: ${e.message}", e)
            0L
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
        
        return if (count > 0) sqrt(sumSquares / count) else 0.0
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

    companion object {
        private const val TAG = "AudioSilenceDetector"
    }
}
