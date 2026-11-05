package com.phimbud.mobile.player

import androidx.compose.runtime.Immutable
import com.phimbud.mobile.core.model.SearchNormalizer
import java.io.File

enum class SkipSegmentType {
    RECAP,
    INTRO
}

@Immutable
data class SkipSegment(
    val type: SkipSegmentType,
    val startMs: Long,
    val endMs: Long,
    val label: String
)

/**
 * Analyzes decrypted .srt subtitle cues to automatically detect "Skip Recap" (Bỏ qua Tóm tắt)
 * and "Skip Intro" (Bỏ qua Giới thiệu) timestamp ranges for TV episodes and movies.
 */
object IntroRecapDetector {

    internal data class SrtCue(
        val startMs: Long,
        val endMs: Long,
        val rawText: String,
        val isMusicOrSoundEffect: Boolean,
        val isCreditOrTitleCard: Boolean
    ) {
        val isSpokenDialogue: Boolean
            get() = !isMusicOrSoundEffect && !isCreditOrTitleCard
    }

    private val TIMECODE_REGEX = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )
    private val HTML_TAG_REGEX = Regex("""<[^>]*>""")
    private val ASS_TAG_REGEX = Regex("""\{[^}]*\}""")
    private val EPISODE_TITLE_CARD_REGEX = Regex(
        """^(?:tap|episode|ep|mua|season|phan|chuong)\s+\d+\b.*"""
    )

    private val RECAP_NORMALIZED_PREFIXES = listOf(
        "o cac tap truoc",
        "o tap truoc",
        "trong cac tap truoc",
        "trong tap truoc",
        "tom tat tap truoc",
        "nhung tap truoc",
        "previously on",
        "last time on",
        "last season on"
    )

    private val CREDIT_AND_STUDIO_KEYWORDS = listOf(
        "phu de duoc thuc hien",
        "phu de thuc hien boi",
        "bien dich",
        "dich phu de",
        "dong bo phu de",
        "subteam",
        "subscene",
        "phimbud",
        "chuc cac ban xem phim",
        "translated by",
        "synched by",
        "synced by",
        "subtitles by",
        "encoded by",
        "netflix",
        "hbo",
        "disney",
        "apple tv",
        "amazon original",
        "prime video",
        "paramount",
        "studio dragon",
        "tvn",
        "jtbc",
        "marvel studios",
        "lucasfilm",
        "warner bros",
        "dao dien",
        "bien kich",
        "kich ban",
        "san xuat",
        "chuyen the tu",
        "dua tren",
        "directed by",
        "written by",
        "created by",
        "produced by",
        "executive producer",
        "based on",
        "inspired by true",
        "hoan toan hu cau",
        "la hu cau",
        "su trung hop",
        "canh bao",
        "bo phim co chua",
        "khong phu hop",
        "viewer discretion",
        "fictitious",
        "coincidental"
    )

    private const val MAX_ANALYSIS_WINDOW_MS = 7 * 60 * 1000L // First 7 minutes
    private const val MIN_INSTRUMENTAL_INTRO_MS = 35_000L    // 35s minimum pure gap
    private const val MIN_TAGGED_INTRO_MS = 22_000L          // 22s minimum when music/credit markers exist
    private const val MAX_INTRO_DURATION_MS = 135_000L       // 2m15s max intro length
    private const val PRE_DIALOGUE_BUFFER_MS = 500L          // Land 0.5s before first dialogue line
    private const val EARLY_START_SNAP_MS = 25_000L          // Snap intros/recaps starting in first 25s to 00:00
    private const val PRE_INTRO_LEAD_IN_MS = 3_000L          // Show Skip Intro 3s early as cold-open ends

    fun detectFromFile(srtFile: File?, isEpisode: Boolean): List<SkipSegment> {
        if (srtFile == null || !srtFile.exists() || srtFile.length() <= 0L) return emptyList()
        return runCatching {
            val content = srtFile.readText(Charsets.UTF_8)
            detectFromSrt(content, isEpisode)
        }.getOrDefault(emptyList())
    }

    fun detectFromSrt(srtContent: String, isEpisode: Boolean = true): List<SkipSegment> {
        if (srtContent.isBlank()) return emptyList()
        val cues = parseCues(srtContent)
        if (cues.isEmpty()) return emptyList()

        val result = mutableListOf<SkipSegment>()

        // 1. Detect "Skip Recap" (Tóm tắt tập trước) in the first 45 seconds
        val recapSegment = detectRecap(cues)
        if (recapSegment != null) {
            result.add(recapSegment)
        }

        // 2. Detect "Skip Intro" (Giới thiệu phim / Nhạc mở đầu) in the first 7 minutes
        val introStartSearchMs = recapSegment?.endMs ?: 0L
        val introSegment = detectIntro(cues, introStartSearchMs, isEpisode, hasRecap = recapSegment != null)
        if (introSegment != null) {
            result.add(introSegment)
        }

        return result
    }

    private fun detectRecap(cues: List<SrtCue>): SkipSegment? {
        val earlyCues = cues.filter { it.startMs <= 45_000L }.take(8)
        val triggerCue = earlyCues.firstOrNull { cue ->
            val norm = SearchNormalizer.normalize(cue.rawText)
            RECAP_NORMALIZED_PREFIXES.any { prefix -> norm.contains(prefix) }
        } ?: return null

        val triggerIndex = cues.indexOf(triggerCue)
        if (triggerIndex < 0) return null

        // Scan forward through spoken dialogue cues to find the scene transition pause after the recap
        val spokenFromTrigger = cues.subList(triggerIndex, cues.size).filter {
            it.isSpokenDialogue || it == triggerCue
        }
        if (spokenFromTrigger.size < 2) return null

        var recapEndMs: Long? = null
        for (i in 0 until spokenFromTrigger.size - 1) {
            val current = spokenFromTrigger[i]
            val next = spokenFromTrigger[i + 1]
            if (current.endMs > 180_000L) break

            val gapMs = next.startMs - current.endMs
            val elapsedRecapMs = current.endMs - triggerCue.startMs
            if ((i == 0 && gapMs >= 10_000L) || (elapsedRecapMs >= 10_000L && gapMs >= 4_200L)) {
                recapEndMs = if (gapMs >= MIN_INSTRUMENTAL_INTRO_MS) {
                    (current.endMs + 1_000L).coerceAtMost(next.startMs - PRE_DIALOGUE_BUFFER_MS)
                } else {
                    (next.startMs - PRE_DIALOGUE_BUFFER_MS).coerceAtLeast(current.endMs)
                }
                break
            }
        }

        val finalEndMs = recapEndMs ?: return null
        val startMs = if (triggerCue.startMs <= EARLY_START_SNAP_MS) 0L else triggerCue.startMs
        if (finalEndMs - startMs < 10_000L) return null

        return SkipSegment(
            type = SkipSegmentType.RECAP,
            startMs = startMs,
            endMs = finalEndMs,
            label = "Bỏ qua Tóm tắt"
        )
    }

    private fun detectIntro(
        cues: List<SrtCue>,
        minStartMs: Long,
        isEpisode: Boolean,
        hasRecap: Boolean
    ): SkipSegment? {
        val spokenCues = cues.filter { it.isSpokenDialogue && it.endMs > minStartMs }
        if (spokenCues.isEmpty()) return null

        // Case A: Episode starts directly with Opening Intro (or immediately after Recap) before first spoken dialogue
        val firstSpoken = spokenCues.first()
        val initialGapMs = firstSpoken.startMs - minStartMs
        val hasEarlyMusicOrCredits = cues.any {
            it.startMs >= minStartMs && it.endMs <= firstSpoken.startMs && !it.isSpokenDialogue
        }
        val minInitialIntroMs = if (hasEarlyMusicOrCredits) MIN_TAGGED_INTRO_MS else MIN_INSTRUMENTAL_INTRO_MS

        if ((isEpisode || hasEarlyMusicOrCredits) &&
            minStartMs <= 120_000L &&
            initialGapMs in minInitialIntroMs..MAX_INTRO_DURATION_MS
        ) {
            val snappedStart = if (!hasRecap && minStartMs <= EARLY_START_SNAP_MS) 0L else minStartMs
            val targetEnd = (firstSpoken.startMs - PRE_DIALOGUE_BUFFER_MS).coerceAtLeast(snappedStart + 10_000L)
            return SkipSegment(
                type = SkipSegmentType.INTRO,
                startMs = snappedStart,
                endMs = targetEnd,
                label = "Bỏ qua Giới thiệu"
            )
        }

        // Case B: Cold open followed by Opening Credits gap (with or without music/subteam/studio credit cues inside)
        var bestCandidate: SkipSegment? = null
        var bestScore = 0L

        for (i in 0 until spokenCues.size - 1) {
            val prevSpoken = spokenCues[i]
            val nextSpoken = spokenCues[i + 1]

            val rawGapStartMs = prevSpoken.endMs.coerceAtLeast(minStartMs)
            if (rawGapStartMs > MAX_ANALYSIS_WINDOW_MS) break

            val gapEndMs = nextSpoken.startMs
            val gapDurationMs = gapEndMs - rawGapStartMs
            if (gapDurationMs > MAX_INTRO_DURATION_MS) continue

            val nonSpokenInGap = cues.filter {
                !it.isSpokenDialogue && it.startMs >= rawGapStartMs - 1_000L && it.endMs <= gapEndMs + 1_000L
            }
            val hasMusicOrCreditsInGap = nonSpokenInGap.isNotEmpty()
            val minRequiredMs = if (hasMusicOrCreditsInGap) MIN_TAGGED_INTRO_MS else MIN_INSTRUMENTAL_INTRO_MS

            if (gapDurationMs >= minRequiredMs) {
                val bonus = if (hasMusicOrCreditsInGap) 30_000L else 0L
                val score = gapDurationMs + bonus
                if (score > bestScore) {
                    bestScore = score
                    // Snap to 0ms if the intro starts within the first 25s (e.g. after a 10s disclaimer,
                    // studio logo, or 1-2 line hook), otherwise trigger 3s before prevSpoken.endMs
                    // so the Skip button appears immediately as the intro music begins!
                    val effectiveStartMs = if (!hasRecap && (rawGapStartMs <= EARLY_START_SNAP_MS || i <= 1 && rawGapStartMs <= 30_000L)) {
                        0L
                    } else {
                        (rawGapStartMs - PRE_INTRO_LEAD_IN_MS).coerceAtLeast(minStartMs)
                    }
                    val targetEnd = (gapEndMs - PRE_DIALOGUE_BUFFER_MS).coerceAtLeast(effectiveStartMs + 10_000L)
                    bestCandidate = SkipSegment(
                        type = SkipSegmentType.INTRO,
                        startMs = effectiveStartMs,
                        endMs = targetEnd,
                        label = "Bỏ qua Giới thiệu"
                    )
                }
            }
        }

        return bestCandidate
    }

    internal fun parseCues(srtContent: String): List<SrtCue> {
        val blocks = srtContent
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split(Regex("\n{2,}"))

        val cues = ArrayList<SrtCue>(blocks.size.coerceAtMost(400))
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) continue

            val timeLineIndex = if (TIMECODE_REGEX.containsMatchIn(lines[0])) 0 else 1
            if (timeLineIndex >= lines.size) continue

            val match = TIMECODE_REGEX.find(lines[timeLineIndex]) ?: continue
            val startMs = toMillis(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3],
                match.groupValues[4]
            )
            if (startMs > MAX_ANALYSIS_WINDOW_MS + 60_000L) break

            val endMs = toMillis(
                match.groupValues[5],
                match.groupValues[6],
                match.groupValues[7],
                match.groupValues[8]
            )
            if (endMs <= startMs) continue

            val rawText = lines.subList(timeLineIndex + 1, lines.size)
                .joinToString("\n")
                .let { HTML_TAG_REGEX.replace(it, "") }
                .let { ASS_TAG_REGEX.replace(it, "") }
                .trim()

            if (rawText.isEmpty()) continue

            val normalized = SearchNormalizer.normalize(rawText)
            val isMusic = isMusicOrSoundCue(rawText)
            val isCredit = isCreditOrTitleCardCue(rawText, normalized, startMs)

            cues.add(
                SrtCue(
                    startMs = startMs,
                    endMs = endMs,
                    rawText = rawText,
                    isMusicOrSoundEffect = isMusic,
                    isCreditOrTitleCard = isCredit
                )
            )
        }
        return cues
    }

    private fun isMusicOrSoundCue(rawText: String): Boolean {
        if (rawText.contains('♪') || rawText.contains('♫') || rawText.contains('♬') || rawText.contains('#')) {
            return true
        }
        val compact = rawText.replace("\n", " ").trim()
        // Any SDH cue enclosed in [...] or (...) such as [Nhạc mở đầu], (Tiếng nhạc), [Theme music]
        if ((compact.startsWith("[") && compact.endsWith("]")) ||
            (compact.startsWith("(") && compact.endsWith(")"))
        ) {
            return true
        }
        return false
    }

    private fun isCreditOrTitleCardCue(rawText: String, normalized: String, startMs: Long): Boolean {
        if (normalized.isEmpty()) return true
        if (CREDIT_AND_STUDIO_KEYWORDS.any { normalized.contains(it) }) {
            return true
        }
        // Episode/Season title cards in the first 3 minutes (e.g. "TẬP 1: KHỞI ĐẦU", "EPISODE 2")
        if (startMs <= 180_000L && rawText.length <= 48 && EPISODE_TITLE_CARD_REGEX.matches(normalized)) {
            return true
        }
        return false
    }

    private fun toMillis(h: String, m: String, s: String, ms: String): Long {
        val hours = h.toLongOrNull() ?: 0L
        val minutes = m.toLongOrNull() ?: 0L
        val seconds = s.toLongOrNull() ?: 0L
        val millis = ms.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return (((hours * 60 + minutes) * 60) + seconds) * 1000L + millis
    }
}
