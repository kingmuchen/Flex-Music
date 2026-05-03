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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.PI
import kotlin.math.sqrt

@Singleton
class AudioFingerprinter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class Fingerprint(
        val duration: Long,
        val hash: String,
        val peaks: List<Int>,
        val energyProfile: List<Double>
    )

    suspend fun generateFingerprint(uri: Uri): Fingerprint? = withContext(Dispatchers.IO) {
        try {
            extractFingerprint(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate fingerprint: ${e.message}")
            null
        }
    }

    private fun extractFingerprint(uri: Uri): Fingerprint? {
        val extractor = MediaExtractor()
        
        return try {
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
            
            if (audioTrackIndex < 0) return null
            
            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            
            val frameSizeMs = 100L
            val samplesPerFrame = (sampleRate * frameSizeMs / 1000).toInt()
            val bytesPerFrame = samplesPerFrame * channelCount * 2
            val buffer = java.nio.ByteBuffer.allocate(bytesPerFrame * 4)
            
            val energyProfile = mutableListOf<Double>()
            val spectralPeaks = mutableListOf<Int>()
            var totalEnergy = 0.0
            var frameCount = 0
            val maxFrames = 300
            
            while (frameCount < maxFrames) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                
                val samples = extractSamples(buffer, sampleSize, channelCount)
                val energy = calculateEnergy(samples)
                energyProfile.add(energy)
                totalEnergy += energy
                
                if (frameCount > 0 && energy > energyProfile[frameCount - 1] * 1.5) {
                    spectralPeaks.add(frameCount)
                }
                
                extractor.advance()
                frameCount++
            }
            
            val normalizedProfile = if (energyProfile.isNotEmpty()) {
                val max = energyProfile.maxOrNull() ?: 1.0
                energyProfile.map { it / max }
            } else {
                emptyList()
            }
            
            val hash = generateHash(duration, normalizedProfile, spectralPeaks)
            
            Fingerprint(
                duration = duration,
                hash = hash,
                peaks = spectralPeaks,
                energyProfile = normalizedProfile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting fingerprint: ${e.message}")
            null
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release extractor: ${e.message}")
            }
        }
    }

    private fun extractSamples(buffer: java.nio.ByteBuffer, size: Int, channelCount: Int): DoubleArray {
        val sampleCount = size / (2 * channelCount)
        val samples = DoubleArray(sampleCount)
        
        for (i in 0 until sampleCount) {
            var sum = 0.0
            for (ch in 0 until channelCount) {
                val offset = (i * channelCount + ch) * 2
                if (offset + 1 < size) {
                    val low = buffer[offset].toInt() and 0xFF
                    val high = buffer[offset + 1].toInt()
                    val sample = (high shl 8) or low
                    sum += sample.toDouble()
                }
            }
            samples[i] = sum / channelCount
        }
        
        return samples
    }

    private fun calculateEnergy(samples: DoubleArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in samples) {
            sum += sample * sample
        }
        return sqrt(sum / samples.size)
    }

    private fun generateHash(duration: Long, energyProfile: List<Double>, peaks: List<Int>): String {
        val sb = StringBuilder()
        
        sb.append(duration.toString(16).padStart(6, '0'))
        
        if (energyProfile.size >= 8) {
            for (i in 0 until 8) {
                val start = i * energyProfile.size / 8
                val end = (i + 1) * energyProfile.size / 8
                val segment = energyProfile.subList(start, end)
                val avg = if (segment.isNotEmpty()) segment.average() else 0.0
                val value = (avg * 15).toInt().coerceIn(0, 15)
                sb.append(value.toString(16))
            }
        }
        
        if (peaks.isNotEmpty()) {
            val peakHash = peaks.take(4).fold(0) { acc, peak -> acc * 31 + peak }
            sb.append(abs(peakHash).toString(16).take(4))
        }
        
        return sb.toString()
    }

    fun calculateSimilarity(fp1: Fingerprint, fp2: Fingerprint): Double {
        val durationDiff = abs(fp1.duration - fp2.duration).toDouble()
        val durationScore = if (fp1.duration > 0 && fp2.duration > 0) {
            1.0 - (durationDiff / maxOf(fp1.duration, fp2.duration)).coerceIn(0.0, 1.0)
        } else {
            0.5
        }
        
        val energyScore = calculateEnergySimilarity(fp1.energyProfile, fp2.energyProfile)
        
        val peakScore = calculatePeakSimilarity(fp1.peaks, fp2.peaks)
        
        return durationScore * 0.3 + energyScore * 0.5 + peakScore * 0.2
    }

    private fun calculateEnergySimilarity(profile1: List<Double>, profile2: List<Double>): Double {
        if (profile1.isEmpty() || profile2.isEmpty()) return 0.0
        
        val minSize = minOf(profile1.size, profile2.size)
        val p1 = profile1.take(minSize)
        val p2 = profile2.take(minSize)
        
        var sumDiff = 0.0
        for (i in p1.indices) {
            sumDiff += abs(p1[i] - p2[i])
        }
        
        val avgDiff = sumDiff / minSize
        return (1.0 - avgDiff).coerceIn(0.0, 1.0)
    }

    private fun calculatePeakSimilarity(peaks1: List<Int>, peaks2: List<Int>): Double {
        if (peaks1.isEmpty() && peaks2.isEmpty()) return 1.0
        if (peaks1.isEmpty() || peaks2.isEmpty()) return 0.0
        
        val tolerance = 3
        var matches = 0
        
        for (p1 in peaks1) {
            for (p2 in peaks2) {
                if (abs(p1 - p2) <= tolerance) {
                    matches++
                    break
                }
            }
        }
        
        return matches.toDouble() / maxOf(peaks1.size, peaks2.size)
    }

    companion object {
        private const val TAG = "AudioFingerprinter"
    }
}
