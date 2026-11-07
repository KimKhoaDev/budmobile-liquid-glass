package com.phimbud.mobile.core.crypto

import androidx.compose.runtime.Immutable

@Immutable
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Parses SubRip (.srt) and WebVTT (.vtt) subtitle files into sorted [SubtitleCue] lists
 * and provides O(log N) lookup for real-time subtitle overlay rendering on iOS.
 */
object SrtParser {

    private val INLINE_TAG_REGEX = Regex("</?[biu][^>]*>", RegexOption.IGNORE_CASE)

    fun parse(content: String): List<SubtitleCue> {
        if (content.isBlank()) return emptyList()
        val normalized = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')

        val cues = ArrayList<SubtitleCue>(lines.size / 3 + 1)
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("WEBVTT", ignoreCase = true) || line.startsWith("NOTE")) {
                i++
                continue
            }

            val arrowIndex = line.indexOf("-->")
            val timingLine: String
            if (arrowIndex != -1) {
                timingLine = line
                i++
            } else if (i + 1 < lines.size && lines[i + 1].contains("-->")) {
                timingLine = lines[i + 1].trim()
                i += 2
            } else {
                i++
                continue
            }

            val parts = timingLine.split("-->", limit = 2)
            val startMs = parseTimestampMs(parts[0].trim())
            val endRaw = parts.getOrNull(1)?.trim()?.split(' ', '\t')?.firstOrNull().orEmpty()
            val endMs = parseTimestampMs(endRaw)

            val textBuilder = StringBuilder()
            while (i < lines.size && lines[i].trim().isNotEmpty()) {
                val cleanedLine = INLINE_TAG_REGEX.replace(lines[i].trim(), "").trim()
                if (cleanedLine.isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) textBuilder.append('\n')
                    textBuilder.append(cleanedLine)
                }
                i++
            }

            if (startMs >= 0L && endMs > startMs && textBuilder.isNotEmpty()) {
                cues.add(SubtitleCue(startMs = startMs, endMs = endMs, text = textBuilder.toString()))
            }
        }

        cues.sortBy { it.startMs }
        return cues
    }

    fun findActiveText(cues: List<SubtitleCue>, positionMs: Long): String? {
        if (cues.isEmpty() || positionMs < 0L) return null

        // Binary search for last cue with startMs <= positionMs
        var low = 0
        var high = cues.size - 1
        var candidate = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs <= positionMs) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (candidate == -1) return null

        // Check candidate and at most 2 preceding cues in case of overlapping dialogue lines
        var firstMatch: String? = null
        var extraBuilder: StringBuilder? = null
        val minIdx = (candidate - 2).coerceAtLeast(0)
        for (idx in minIdx..candidate) {
            val cue = cues[idx]
            if (positionMs in cue.startMs..cue.endMs) {
                if (firstMatch == null) {
                    firstMatch = cue.text
                } else {
                    val sb = extraBuilder ?: StringBuilder(firstMatch).also { extraBuilder = it }
                    sb.append('\n').append(cue.text)
                }
            }
        }
        return extraBuilder?.toString() ?: firstMatch
    }

    internal fun parseTimestampMs(raw: String): Long {
        if (raw.isEmpty()) return -1L
        val normalized = raw.replace(',', '.')
        val dotParts = normalized.split('.', limit = 2)
        val hmsParts = dotParts[0].split(':')
        val millisPart = dotParts.getOrNull(1).orEmpty().take(3).padEnd(3, '0')
        val millis = millisPart.toLongOrNull() ?: return -1L

        return when (hmsParts.size) {
            3 -> {
                val h = hmsParts[0].toLongOrNull() ?: return -1L
                val m = hmsParts[1].toLongOrNull() ?: return -1L
                val s = hmsParts[2].toLongOrNull() ?: return -1L
                ((h * 3600L) + (m * 60L) + s) * 1000L + millis
            }
            2 -> {
                val m = hmsParts[0].toLongOrNull() ?: return -1L
                val s = hmsParts[1].toLongOrNull() ?: return -1L
                ((m * 60L) + s) * 1000L + millis
            }
            else -> -1L
        }
    }
}
