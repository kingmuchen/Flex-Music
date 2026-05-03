package com.kingmc.flexmusic.feature.player.lyrics

private val lineTimeRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
private val enhancedWordTimeRegex = Regex("\\<(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?>([^<]*)")
private val karaokeWordRegex = Regex("\\((\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?,?(\\d*)\\)([^\\(]*)")
private val metadataRegex = Regex("^\\[([^\\[\\]:]+):([^\\[\\]]+)\\]$")
private val yrcLineRegex = Regex("^\\[(\\d+),(\\d+)\\](.*)$")
private val yrcWordRegex = Regex("\\((\\d+),(\\d+),(\\d+)\\)([^\\(]*)")
private val qrcWordRegex = Regex("\\((\\d+),(\\d+)\\)([^\\(]*)")
private val offsetRegex = Regex("\\[offset:(-?\\d+)\\]", RegexOption.IGNORE_CASE)

class LrcParser {

    fun parse(content: String, source: String): LyricDocument {
        val lrcFileOffset = extractLrcOffset(content)
        val normalized = normalizeLyricFormat(content)
        
        val parsed = mutableListOf<LyricLine>()
        val metadata = mutableMapOf<String, String>()
        val translations = mutableMapOf<Long, String>()

        normalized.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { raw ->
                val metaMatch = metadataRegex.find(raw)
                if (metaMatch != null) {
                    val key = metaMatch.groupValues[1].lowercase()
                    val value = metaMatch.groupValues[2].trim()
                    if (!key.matches(Regex("\\d+"))) {
                        metadata[key] = value
                    }
                    return@forEach
                }

                val yrcMatch = yrcLineRegex.find(raw)
                if (yrcMatch != null) {
                    val lineStartMs = yrcMatch.groupValues[1].toLongOrNull() ?: 0L
                    val lineDurationMs = yrcMatch.groupValues[2].toLongOrNull() ?: 0L
                    val textPart = yrcMatch.groupValues[3]
                    
                    val yrcWords = parseYrcWords(textPart, lineStartMs)
                    
                    val (finalText, words) = if (yrcWords.isNotEmpty()) {
                        yrcWords.joinToString("") { it.text } to yrcWords
                    } else {
                        val qrcWords = parseQrcWords(textPart, lineStartMs)
                        if (qrcWords.isNotEmpty()) {
                            qrcWords.joinToString("") { it.text } to qrcWords
                        } else {
                            val enhancedWords = parseEnhancedWords(textPart)
                            if (enhancedWords.isNotEmpty()) {
                                enhancedWords.joinToString("") { it.text } to enhancedWords
                            } else {
                                val karaokeWords = parseKaraokeWords(textPart)
                                if (karaokeWords.isNotEmpty()) {
                                    karaokeWords.joinToString("") { it.text } to karaokeWords
                                } else {
                                    textPart to emptyList()
                                }
                            }
                        }
                    }

                    parsed += LyricLine(
                        startMs = lineStartMs,
                        endMs = lineStartMs + lineDurationMs,
                        text = finalText,
                        words = words
                    )
                    return@forEach
                }

                val times = lineTimeRegex.findAll(raw).toList()
                if (times.isEmpty()) return@forEach

                val textPart = lineTimeRegex.replace(raw, "").trim()
                
                val enhancedWords = parseEnhancedWords(textPart)
                val karaokeWords = parseKaraokeWords(textPart)
                val qrcWords = parseQrcWords(textPart, 0L)
                
                val (finalText, words) = when {
                    qrcWords.isNotEmpty() -> {
                        qrcWords.joinToString("") { it.text } to qrcWords
                    }
                    enhancedWords.isNotEmpty() -> {
                        enhancedWords.joinToString("") { it.text } to enhancedWords
                    }
                    karaokeWords.isNotEmpty() -> {
                        karaokeWords.joinToString("") { it.text } to karaokeWords
                    }
                    else -> textPart to emptyList()
                }

                val firstTime = times.first()
                val lineStartMs = toMillis(
                    minute = firstTime.groupValues[1],
                    second = firstTime.groupValues[2],
                    fraction = firstTime.groupValues[3]
                )
                
                val processedWords = if (words.isNotEmpty()) {
                    words.mapIndexed { index, word ->
                        val nextWord = words.getOrNull(index + 1)
                        val wordEnd = nextWord?.startMs ?: (lineStartMs + 3000L)
                        word.copy(endMs = wordEnd)
                    }
                } else {
                    emptyList()
                }

                parsed += LyricLine(
                    startMs = lineStartMs,
                    endMs = lineStartMs + 3000L,
                    text = finalText,
                    words = processedWords
                )
            }

        val sorted = parsed.sortedBy { it.startMs }
        
        val lines = if (sorted.isEmpty()) {
            emptyList()
        } else {
            sorted.mapIndexed { index, line ->
                val nextLine = sorted.getOrNull(index + 1)
                val endMs = if (nextLine != null) {
                    nextLine.startMs
                } else {
                    val songDuration = metadata["length"]?.toLongOrNull() ?: 300000L
                    val remainingTime = songDuration - line.startMs
                    line.startMs + remainingTime.coerceIn(5000L, 15000L)
                }
                
                val words = if (line.words.isNotEmpty()) {
                    line.words.mapIndexed { wordIndex, word ->
                        val nextWord = line.words.getOrNull(wordIndex + 1)
                        val wordEnd = nextWord?.startMs ?: endMs
                        word.copy(endMs = wordEnd)
                    }
                } else {
                    generateWordTimings(line.text, line.startMs, endMs)
                }
                
                line.copy(endMs = endMs, words = words)
            }
        }

        val mergedLines = mergeTranslations(lines)

        val offsetAppliedLines = if (lrcFileOffset != 0L) {
            mergedLines.map { line ->
                line.copy(
                    startMs = line.startMs + lrcFileOffset,
                    endMs = line.endMs + lrcFileOffset,
                    words = line.words.map { word ->
                        word.copy(
                            startMs = word.startMs + lrcFileOffset,
                            endMs = word.endMs + lrcFileOffset
                        )
                    }
                )
            }
        } else {
            mergedLines
        }

        return LyricDocument(
            lines = offsetAppliedLines,
            source = source,
            title = metadata["ti"],
            artist = metadata["ar"],
            album = metadata["al"],
            author = metadata["au"],
            length = metadata["length"]?.toLongOrNull(),
            offset = 0L,
            translations = translations
        )
    }

    fun extractLrcOffset(content: String): Long {
        val match = offsetRegex.find(content)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun mergeTranslations(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines

        val grouped = lines.groupBy { it.startMs }
        val merged = mutableListOf<LyricLine>()

        grouped.toSortedMap().forEach { (_, bucket) ->
            if (bucket.size == 1) {
                merged += bucket.first()
                return@forEach
            }

            val chinese = bucket.firstOrNull { containsChinese(it.text) }
            val primary = bucket.firstOrNull { !containsChinese(it.text) } ?: bucket.first()
            val translation = when {
                chinese == null -> null
                chinese === primary -> null
                else -> chinese.text
            }

            merged += primary.copy(translation = translation)
        }

        return merged.sortedBy { it.startMs }
    }

    private fun containsChinese(text: String): Boolean {
        return text.any { ch -> ch.code in 0x4E00..0x9FFF }
    }

    private fun normalizeLyricFormat(content: String): String {
        return content.lineSequence()
            .map { line -> normalizeLine(line.trim()) }
            .joinToString("\n")
    }

    private fun normalizeLine(line: String): String {
        if (line.isBlank()) return line
        
        val yrcNewFormat = Regex("^\\[(\\d+),(\\d+)\\](.*)$").find(line)
        if (yrcNewFormat != null) {
            val startTimeMs = yrcNewFormat.groupValues[1].toLongOrNull() ?: return line
            val content = yrcNewFormat.groupValues[3]
            
            val hasYrcTimestamps = yrcWordRegex.containsMatchIn(content)
            if (hasYrcTimestamps) {
                return line
            }
            
            val hasOldTimestamps = Regex("\\<(\\d+),(\\d+),(\\d+)\\>").containsMatchIn(content)
            if (hasOldTimestamps) {
                val converted = content.replace(Regex("\\<(\\d+),(\\d+),(\\d+)\\>([^<]*)")) { match ->
                    val offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
                    val durationMs = match.groupValues[2].toLongOrNull() ?: 0L
                    val param = match.groupValues[3]
                    val text = match.groupValues[4]
                    "(${startTimeMs + offsetMs},${durationMs},${param})$text"
                }
                return "[$startTimeMs,${yrcNewFormat.groupValues[2]}]$converted"
            }
            
            return line
        }
        
        return line
    }

    private fun parseYrcWords(textPart: String, lineStartMs: Long): List<LyricWord> {
        val allMatches = yrcWordRegex.findAll(textPart).map { match ->
            val startMs = match.groupValues[1].toLongOrNull() ?: 0L
            val durationMs = match.groupValues[2].toLongOrNull() ?: 0L
            val text = match.groupValues[4]
            Triple(startMs, durationMs, text)
        }.filter { it.third.isNotBlank() }.toList()
        
        if (allMatches.isEmpty()) return emptyList()
        
        val firstStartMs = allMatches.first().first
        val isRelativeTime = firstStartMs < lineStartMs && firstStartMs < 10000
        
        return allMatches.map { (startMs, durationMs, text) ->
            val actualStartMs = if (isRelativeTime) {
                lineStartMs + startMs
            } else {
                startMs
            }
            LyricWord(
                startMs = actualStartMs,
                endMs = actualStartMs + durationMs,
                text = text
            )
        }
    }

    private fun parseQrcWords(textPart: String, lineStartMs: Long): List<LyricWord> {
        val allMatches = qrcWordRegex.findAll(textPart).map { match ->
            val startMs = match.groupValues[1].toLongOrNull() ?: 0L
            val durationMs = match.groupValues[2].toLongOrNull() ?: 0L
            val text = match.groupValues[3]
            Triple(startMs, durationMs, text)
        }.filter { it.third.isNotBlank() }.toList()
        
        if (allMatches.isEmpty()) return emptyList()
        
        val firstStartMs = allMatches.first().first
        val isRelativeTime = firstStartMs < lineStartMs && firstStartMs < 10000
        
        return allMatches.map { (startMs, durationMs, text) ->
            val actualStartMs = if (isRelativeTime) {
                lineStartMs + startMs
            } else {
                startMs
            }
            LyricWord(
                startMs = actualStartMs,
                endMs = actualStartMs + durationMs,
                text = text
            )
        }
    }

    private fun parseEnhancedWords(textPart: String): List<LyricWord> {
        return enhancedWordTimeRegex.findAll(textPart).map { match ->
            LyricWord(
                startMs = toMillis(
                    minute = match.groupValues[1],
                    second = match.groupValues[2],
                    fraction = match.groupValues[3]
                ),
                endMs = 0L,
                text = match.groupValues[4]
            )
        }.filter { it.text.isNotBlank() }.toList()
    }

    private fun parseKaraokeWords(textPart: String): List<LyricWord> {
        return karaokeWordRegex.findAll(textPart).map { match ->
            val startMs = toMillis(
                minute = match.groupValues[1],
                second = match.groupValues[2],
                fraction = match.groupValues[3]
            )
            val durationMs = match.groupValues[4].toLongOrNull() ?: 0L
            LyricWord(
                startMs = startMs,
                endMs = startMs + durationMs,
                text = match.groupValues[5]
            )
        }.filter { it.text.isNotBlank() }.toList()
    }

    private fun toMillis(minute: String, second: String, fraction: String): Long {
        val min = minute.toLongOrNull() ?: 0L
        val sec = second.toLongOrNull() ?: 0L
        val fracRaw = fraction.toLongOrNull() ?: 0L
        
        val ms = when (fraction.length) {
            0 -> 0L
            1 -> fracRaw * 100L
            2 -> fracRaw * 10L
            3 -> fracRaw
            else -> fracRaw
        }
        
        return min * 60_000L + sec * 1_000L + ms
    }
    
    private fun generateWordTimings(text: String, startMs: Long, endMs: Long): List<LyricWord> {
        if (text.isBlank()) return emptyList()
        
        val chars = text.toCharArray().filter { !it.isWhitespace() }
        if (chars.isEmpty()) return emptyList()
        
        val duration = endMs - startMs
        val charDuration = duration / chars.size
        
        val words = mutableListOf<LyricWord>()
        var currentTime = startMs
        
        text.forEach { char ->
            if (!char.isWhitespace()) {
                words.add(LyricWord(
                    startMs = currentTime,
                    endMs = currentTime + charDuration,
                    text = char.toString()
                ))
                currentTime += charDuration
            }
        }
        
        return words
    }
}
