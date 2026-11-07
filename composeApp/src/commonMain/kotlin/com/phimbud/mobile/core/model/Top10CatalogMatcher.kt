package com.phimbud.mobile.core.model

import com.phimbud.mobile.core.network.RawTop10Charts
import kotlin.math.abs

object Top10CatalogMatcher {

    private val trailingYearRegex = Regex("""\((\d{4})\)\s*$""")
    private val seasonSuffixRegexes = listOf(
        Regex("""\s*:\s*(?:Season|Part|Volume|Vol\.?|Book|Chapter|Series)\s+[0-9IVX]+.*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*:\s*(?:Limited Series|Miniseries|Mini-Series|The Complete Series|Complete Series|Collection).*$""", RegexOption.IGNORE_CASE),
        Regex("""\s+-\s*(?:Season|Part|Volume)\s+\d+.*$""", RegexOption.IGNORE_CASE)
    )
    private val numberedColonRegex = Regex("""\s+\d+\s*:""")

    data class CleanedTitle(
        val baseTitle: String,
        val yearHint: Int?
    )

    fun cleanChartTitle(rawTitle: String): CleanedTitle {
        var working = rawTitle.trim()
        var yearHint: Int? = null

        val yearMatch = trailingYearRegex.find(working)
        if (yearMatch != null) {
            yearHint = yearMatch.groupValues[1].toIntOrNull()
            working = working.substring(0, yearMatch.range.first).trim()
        }

        // Strip trailing season / limited series suffixes (up to 2 passes for nested labels)
        repeat(2) {
            for (rx in seasonSuffixRegexes) {
                working = rx.replace(working, "").trim()
            }
        }

        return CleanedTitle(baseTitle = working, yearHint = yearHint)
    }

    internal fun normalizeKey(input: String): String {
        return SearchNormalizer.normalize(input)
    }

    fun buildTop10Lists(
        raw: RawTop10Charts,
        catalog: List<SearchItem>,
        updatedAtEpochMs: Long
    ): Top10Lists {
        if (catalog.isEmpty() || raw.isEmpty) {
            return Top10Lists(updatedAtEpochMs = updatedAtEpochMs)
        }

        val byEn = HashMap<String, MutableList<SearchItem>>(catalog.size * 2)
        val byVi = HashMap<String, MutableList<SearchItem>>(catalog.size * 2)

        for (item in catalog) {
            val enKey = item.normalizedNameEn
            if (enKey.isNotEmpty()) {
                byEn.getOrPut(enKey) { ArrayList(2) }.add(item)
            }
            val viKey = item.normalizedNameVi
            if (viKey.isNotEmpty()) {
                byVi.getOrPut(viKey) { ArrayList(2) }.add(item)
            }
        }

        fun matchSingleTitle(rawTitle: String): Movie? {
            val cleaned = cleanChartTitle(rawTitle)
            if (cleaned.baseTitle.isBlank()) return null

            val candidateKeys = LinkedHashSet<String>(4)
            val primaryKey = normalizeKey(cleaned.baseTitle)
            if (primaryKey.isNotEmpty()) {
                candidateKeys.add(primaryKey)
            }

            if (numberedColonRegex.containsMatchIn(cleaned.baseTitle)) {
                val unnumbered = numberedColonRegex.replace(cleaned.baseTitle, ":")
                val altKey = normalizeKey(unnumbered)
                if (altKey.isNotEmpty()) {
                    candidateKeys.add(altKey)
                }
            }

            if (primaryKey.startsWith("the ") && primaryKey.length >= 10) {
                candidateKeys.add(primaryKey.removePrefix("the ").trim())
            }

            for (key in candidateKeys) {
                val enHits = byEn[key]
                if (!enHits.isNullOrEmpty()) {
                    return pickBestCandidate(enHits, cleaned.yearHint).toMovie()
                }
                val viHits = byVi[key]
                if (!viHits.isNullOrEmpty()) {
                    return pickBestCandidate(viHits, cleaned.yearHint).toMovie()
                }
            }
            return null
        }

        fun resolveTopN(rawTitles: List<String>, maxCount: Int = 10): List<Movie> {
            val out = ArrayList<Movie>(maxCount)
            val seenFids = HashSet<String>(maxCount * 2)
            for (title in rawTitles) {
                if (out.size >= maxCount) break
                val matched = matchSingleTitle(title) ?: continue
                if (seenFids.add(matched.fid)) {
                    out.add(matched)
                }
            }
            return out
        }

        val vnMovieCandidates = buildList {
            addAll(raw.vnMovies)
            addAll(raw.itunesVnMovies)
            addAll(raw.globalMoviesNonEn)
        }
        val vnSeriesCandidates = buildList {
            addAll(raw.vnSeries)
            addAll(raw.globalSeriesNonEn)
            addAll(raw.globalSeriesEn)
        }

        val globalMovieCandidates = buildList {
            addAll(interleaveStrings(raw.globalMoviesEn, raw.globalMoviesNonEn))
            addAll(raw.itunesGlobalMovies)
        }
        val globalSeriesCandidates = buildList {
            addAll(interleaveStrings(raw.globalSeriesEn, raw.globalSeriesNonEn))
            addAll(raw.vnSeries)
        }

        val vnMovies = resolveTopN(vnMovieCandidates, 10)
        val vnSeries = resolveTopN(vnSeriesCandidates, 10)
        val vnAll = interleaveMovies(vnSeries, vnMovies, 10)

        val globalMovies = resolveTopN(globalMovieCandidates, 10)
        val globalSeries = resolveTopN(globalSeriesCandidates, 10)
        val globalAll = interleaveMovies(globalSeries, globalMovies, 10)

        return Top10Lists(
            vnAll = vnAll,
            vnSeries = vnSeries,
            vnMovies = vnMovies,
            globalAll = globalAll,
            globalSeries = globalSeries,
            globalMovies = globalMovies,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    private fun pickBestCandidate(candidates: List<SearchItem>, yearHint: Int?): SearchItem {
        if (candidates.size == 1) return candidates[0]
        if (yearHint != null) {
            candidates.firstOrNull { it.year == yearHint }?.let { return it }
            candidates.minByOrNull { item ->
                if (item.year > 0) abs(item.year - yearHint) else Int.MAX_VALUE
            }?.takeIf { it.year > 0 && abs(it.year - yearHint) <= 1 }?.let { return it }
        }
        return candidates.maxByOrNull { it.year } ?: candidates[0]
    }

    private fun SearchItem.toMovie(): Movie {
        return Movie(
            fid = fid,
            nameEn = nameEn,
            nameVi = nameVi,
            tmdbPoster = tmdbPoster,
            translation = translation,
            year = year
        )
    }

    private fun interleaveStrings(first: List<String>, second: List<String>): List<String> {
        val maxLen = maxOf(first.size, second.size)
        val out = ArrayList<String>(first.size + second.size)
        for (i in 0 until maxLen) {
            if (i < first.size) out.add(first[i])
            if (i < second.size) out.add(second[i])
        }
        return out
    }

    private fun interleaveMovies(first: List<Movie>, second: List<Movie>, limit: Int): List<Movie> {
        val maxLen = maxOf(first.size, second.size)
        val out = ArrayList<Movie>(limit)
        val seen = HashSet<String>(limit * 2)
        for (i in 0 until maxLen) {
            if (i < first.size && seen.add(first[i].fid)) {
                out.add(first[i])
                if (out.size >= limit) break
            }
            if (i < second.size && seen.add(second[i].fid)) {
                out.add(second[i])
                if (out.size >= limit) break
            }
        }
        return out
    }
}
