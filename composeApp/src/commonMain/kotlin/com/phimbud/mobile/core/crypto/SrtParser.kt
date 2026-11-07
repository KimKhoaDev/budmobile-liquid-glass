package com.phimbud.mobile.core.crypto

import androidx.compose.runtime.Immutable

enum class SubtitlePlacement {
    BOTTOM,
    TOP
}

@Immutable
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val placement: SubtitlePlacement = SubtitlePlacement.BOTTOM
)

@Immutable
data class ActiveSubtitleState(
    val bottomText: String? = null,
    val topText: String? = null
) {
    val hasAnyText: Boolean
        get() = !bottomText.isNullOrBlank() || !topText.isNullOrBlank()
}

/**
 * Parses SubRip (.srt) and WebVTT (.vtt) subtitle files into sorted [SubtitleCue] lists
 * and provides O(log N) lookup and smart line balancing for real-time subtitle overlay rendering.
 */
object SrtParser {

    private val HTML_TAG_REGEX = Regex("</?[a-zA-Z][^>]*>")
    private val ASS_OVERRIDE_TAG_REGEX = Regex("\\{[^}]*\\}")
    private val ASS_TOP_ALIGN_REGEX = Regex("\\\\(?:an[789]|a[567])(?![0-9])", RegexOption.IGNORE_CASE)
    private val VTT_TOP_LINE_REGEX = Regex("(?:^|\\s)line:(?:0|1|2|[0-9]%|1[0-9]%|2[0-5]%)(?:\\s|$|,)", RegexOption.IGNORE_CASE)

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
            val rightSide = parts.getOrNull(1)?.trim().orEmpty()
            val endRaw = rightSide.split(' ', '\t').firstOrNull().orEmpty()
            val endMs = parseTimestampMs(endRaw)

            var isTopPlacement = VTT_TOP_LINE_REGEX.containsMatchIn(rightSide)
            val textBuilder = StringBuilder()
            while (i < lines.size && lines[i].trim().isNotEmpty()) {
                val rawCueLine = lines[i].trim()
                if (!isTopPlacement && ASS_TOP_ALIGN_REGEX.containsMatchIn(rawCueLine)) {
                    isTopPlacement = true
                }
                val withoutAss = ASS_OVERRIDE_TAG_REGEX.replace(rawCueLine, "")
                val cleanedLine = HTML_TAG_REGEX.replace(withoutAss, "").trim()
                if (cleanedLine.isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) textBuilder.append('\n')
                    textBuilder.append(cleanedLine)
                }
                i++
            }

            if (startMs >= 0L && endMs > startMs && textBuilder.isNotEmpty()) {
                cues.add(
                    SubtitleCue(
                        startMs = startMs,
                        endMs = endMs,
                        text = textBuilder.toString(),
                        placement = if (isTopPlacement) SubtitlePlacement.TOP else SubtitlePlacement.BOTTOM
                    )
                )
            }
        }

        cues.sortBy { it.startMs }
        return cues
    }

    fun findActiveText(cues: List<SubtitleCue>, positionMs: Long): String? {
        val state = findActiveState(cues, positionMs)
        return when {
            state.bottomText != null && state.topText != null -> "${state.topText}\n${state.bottomText}"
            state.bottomText != null -> state.bottomText
            else -> state.topText
        }
    }

    fun findActiveState(cues: List<SubtitleCue>, positionMs: Long): ActiveSubtitleState {
        if (cues.isEmpty() || positionMs < 0L) return ActiveSubtitleState()

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

        if (candidate == -1) return ActiveSubtitleState()

        // Check candidate and at most 3 preceding cues in case of simultaneous top note + bottom dialogue
        var firstBottom: String? = null
        var bottomBuilder: StringBuilder? = null
        var firstTop: String? = null
        var topBuilder: StringBuilder? = null

        val minIdx = (candidate - 3).coerceAtLeast(0)
        for (idx in minIdx..candidate) {
            val cue = cues[idx]
            if (positionMs in cue.startMs..cue.endMs) {
                if (cue.placement == SubtitlePlacement.TOP) {
                    if (firstTop == null) {
                        firstTop = cue.text
                    } else {
                        val sb = topBuilder ?: StringBuilder(firstTop).also { topBuilder = it }
                        sb.append('\n').append(cue.text)
                    }
                } else {
                    if (firstBottom == null) {
                        firstBottom = cue.text
                    } else {
                        val sb = bottomBuilder ?: StringBuilder(firstBottom).also { bottomBuilder = it }
                        sb.append('\n').append(cue.text)
                    }
                }
            }
        }

        return ActiveSubtitleState(
            bottomText = bottomBuilder?.toString() ?: firstBottom,
            topText = topBuilder?.toString() ?: firstTop
        )
    }

    /**
     * Balances overly long single-line dialogue into two visually even lines near natural clause/word boundaries
     * while preserving intentional multi-line dialogue written by translators.
     */
    fun balanceSubtitleLines(text: String, maxCharsPerLine: Int = 44): String {
        if (text.length <= maxCharsPerLine || text.contains('\n')) {
            return text
        }

        val len = text.length
        val mid = len / 2
        val minSplit = (len * 0.28f).toInt().coerceAtLeast(1)
        val maxSplit = (len * 0.72f).toInt().coerceAtMost(len - 1)

        // 1. Prefer splitting right after punctuation + space (", ", "; ", ". ", "? ", "! ", ": ") near midpoint
        var bestPunctSpaceIdx = -1
        var bestPunctDist = Int.MAX_VALUE
        for (idx in minSplit..maxSplit) {
            if (text[idx] == ' ' && idx > 0) {
                val prev = text[idx - 1]
                if (prev == ',' || prev == ';' || prev == '.' || prev == '?' || prev == '!' || prev == ':') {
                    val dist = kotlin.math.abs(idx - mid)
                    if (dist < bestPunctDist) {
                        bestPunctDist = dist
                        bestPunctSpaceIdx = idx
                    }
                }
            }
        }

        val splitSpaceIdx = if (bestPunctSpaceIdx != -1) {
            bestPunctSpaceIdx
        } else {
            // 2. Fallback to space character closest to midpoint
            var nearestSpace = -1
            var nearestDist = Int.MAX_VALUE
            for (idx in 1 until len - 1) {
                if (text[idx] == ' ') {
                    val dist = kotlin.math.abs(idx - mid)
                    if (dist < nearestDist) {
                        nearestDist = dist
                        nearestSpace = idx
                    }
                }
            }
            nearestSpace
        }

        if (splitSpaceIdx <= 0 || splitSpaceIdx >= len - 1) {
            return text
        }

        val firstLine = text.substring(0, splitSpaceIdx).trimEnd()
        val secondLine = text.substring(splitSpaceIdx + 1).trimStart()
        return if (firstLine.isNotEmpty() && secondLine.isNotEmpty()) {
            "$firstLine\n$secondLine"
        } else {
            text
        }
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
