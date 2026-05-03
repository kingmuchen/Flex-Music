package com.kingmc.flexmusic.feature.player.lyrics

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null
)

data class LyricDocument(
    val lines: List<LyricLine>,
    val source: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val author: String? = null,
    val length: Long? = null,
    val offset: Long = 0L,
    val translations: Map<Long, String> = emptyMap()
) {
    fun isEmpty(): Boolean = lines.isEmpty()
    
    fun findLineIndexAt(positionMs: Long, offset: Long = 0L): Int {
        val adjustedTime = positionMs + offset
        var low = 0
        var high = lines.size - 1
        var result = 0
        
        while (low <= high) {
            val mid = (low + high) / 2
            val line = lines[mid]
            if (line.startMs <= adjustedTime) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        
        return result.coerceAtLeast(0)
    }
    
    fun findLineAt(positionMs: Long, offset: Long = 0L): LyricLine? {
        val index = findLineIndexAt(positionMs, offset)
        return lines.getOrNull(index)
    }
    
    fun findLineIndexAtWithTolerance(positionMs: Long, offset: Long = 0L, toleranceMs: Long = 80L): Int {
        val adjustedTime = positionMs + offset
        val index = findLineIndexAt(positionMs, offset)
        
        if (index < lines.size - 1) {
            val nextLine = lines[index + 1]
            if (kotlin.math.abs(nextLine.startMs - adjustedTime) <= toleranceMs) {
                return index + 1
            }
        }
        
        return index
    }
}
