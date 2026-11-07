package com.phimbud.mobile.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

object SearchNormalizer {

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder(input.length)
        for (rawChar in input) {
            val ch = rawChar.lowercaseChar()
            val code = ch.code
            if (code < 128) {
                if (ch in 'a'..'z' || ch in '0'..'9' || ch == ' ') {
                    sb.append(ch)
                }
            } else if (isCombiningMark(code)) {
                // Skip Unicode combining diacritical marks (handles NFD & mixed Vietnamese input)
                continue
            } else {
                val folded = foldToAsciiLower(ch)
                if (folded != '\u0000') {
                    sb.append(folded)
                }
            }
        }
        return sb.toString().trim()
    }

    private fun isCombiningMark(code: Int): Boolean {
        return code in 0x0300..0x036F ||
            code in 0x1AB0..0x1AFF ||
            code in 0x1DC0..0x1DFF ||
            code in 0x20D0..0x20FF ||
            code in 0xFE20..0xFE2F
    }

    private fun foldToAsciiLower(ch: Char): Char = when (ch) {
        'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą',
        'ạ', 'ả', 'ấ', 'ầ', 'ẩ', 'ẫ', 'ậ', 'ắ', 'ằ', 'ẳ', 'ẵ', 'ặ' -> 'a'
        'ç', 'ć', 'č' -> 'c'
        'đ', 'ď', 'ð' -> 'd'
        'è', 'é', 'ê', 'ë', 'ē', 'ę', 'ě',
        'ẹ', 'ẻ', 'ẽ', 'ế', 'ề', 'ể', 'ễ', 'ệ' -> 'e'
        'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ỉ', 'ị' -> 'i'
        'ł' -> 'l'
        'ñ', 'ń', 'ň' -> 'n'
        'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō', 'ơ',
        'ọ', 'ỏ', 'ố', 'ồ', 'ổ', 'ỗ', 'ộ', 'ớ', 'ờ', 'ở', 'ỡ', 'ợ' -> 'o'
        'ř' -> 'r'
        'ś', 'š', 'ş' -> 's'
        'ť', 'ţ' -> 't'
        'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ů', 'ư',
        'ụ', 'ủ', 'ứ', 'ừ', 'ử', 'ữ', 'ự' -> 'u'
        'ý', 'ÿ', 'ỳ', 'ỵ', 'ỷ', 'ỹ' -> 'y'
        'ź', 'ż', 'ž' -> 'z'
        else -> '\u0000'
    }
}

@Immutable
@Serializable
data class Movie(
    val fid: String,
    val nameEn: String = "",
    val nameVi: String = "",
    val intro: String = "",
    val tmdbPoster: String? = null,
    val tmdbBackdrop: String? = null,
    val translation: String = "subtitle",
    val year: Int = 0,
    val imdbRating: Double? = null,
    val genres: List<String> = emptyList()
) {
    val displayTitle: String
        get() = nameVi.ifBlank { nameEn }

    val posterUrl: String?
        get() = tmdbPoster?.let { "https://image.tmdb.org/t/p/w342$it" }

    val posterLargeUrl: String?
        get() = tmdbPoster?.let { "https://image.tmdb.org/t/p/w500$it" }

    val backdropUrl: String?
        get() = tmdbBackdrop?.let { "https://image.tmdb.org/t/p/w780$it" } ?: posterLargeUrl
}

@Immutable
@Serializable
data class Season(
    val fid: String,
    val number: Int,
    val seasonFull: Boolean = false,
    val episodeCount: String = "0"
)

@Immutable
@Serializable
data class Episode(
    val fid: String,
    val number: Int,
    val name: String = "Tập $number",
    val airDate: String? = null,
    val tmdbPoster: String? = null
) {
    val thumbnailUrl: String?
        get() = tmdbPoster?.let { "https://image.tmdb.org/t/p/w300$it" }
}

@Immutable
@Serializable
data class Subtitle(
    val id: Long,
    val subsceneId: Long,
    val language: String,
    val fileName: String,
    val releaseName: String = "",
    val isDefault: Boolean = false,
    val label: String = ""
)

@Immutable
data class WatchInfo(
    val fid: String,
    val showFid: String = "",
    val showNameEn: String = "",
    val showNameVi: String = "",
    val seasonFid: String = "",
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val nextTitleFid: String? = null,
    val prevTitleFid: String? = null,
    val isVip: Boolean = false,
    val lastCurrentTime: Int? = null,
    val durationSeconds: Double = 0.0
) {
    val displayShowTitle: String
        get() = showNameVi.ifBlank { showNameEn }
}

@Immutable
data class TitleDetail(
    val fid: String,
    val nameEn: String,
    val nameVi: String,
    val type: String = "show", // "show" or "movie"
    val intro: String = "",
    val tmdbPoster: String? = null,
    val tmdbBackdrop: String? = null,
    val imdbRating: Double? = null,
    val genres: List<String> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val relatedTitles: List<Movie> = emptyList()
) {
    val displayTitle: String
        get() = nameVi.ifBlank { nameEn }

    val posterUrl: String?
        get() = tmdbPoster?.let { "https://image.tmdb.org/t/p/w500$it" }

    val backdropUrl: String?
        get() = tmdbBackdrop?.let { "https://image.tmdb.org/t/p/w780$it" } ?: posterUrl
}

@Immutable
data class HomeFeed(
    val trending: List<Movie> = emptyList(),
    val watching: List<Movie> = emptyList(),
    val updated: List<Movie> = emptyList(),
    val shows: List<Movie> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val wishlist: List<Movie> = emptyList()
)

@Immutable
data class SearchItem(
    val fid: String,
    val nameEn: String,
    val nameVi: String,
    val tmdbPoster: String?,
    val translation: String,
    val imdbId: String?,
    val year: Int,
    val normalizedNameVi: String = SearchNormalizer.normalize(nameVi),
    val normalizedNameEn: String = SearchNormalizer.normalize(nameEn)
) {
    val displayTitle: String
        get() = nameVi.ifBlank { nameEn }

    val posterUrl: String?
        get() = tmdbPoster?.let { "https://image.tmdb.org/t/p/w342$it" }
}

@Immutable
data class UserSession(
    val userId: Long = 0,
    val userName: String = "",
    val userEmail: String = "",
    val canWatch: Boolean = true,
    val isAdmin: Boolean = false,
    val isVip: Boolean = true
)


enum class HomeFilterMode {
    ALL,
    TOP10_VIETNAM,
    TOP10_GLOBAL
}

enum class Top10CategoryFilter {
    ALL,
    SERIES,
    MOVIES
}

@Immutable
@Serializable
data class Top10Lists(
    val vnAll: List<Movie> = emptyList(),
    val vnSeries: List<Movie> = emptyList(),
    val vnMovies: List<Movie> = emptyList(),
    val globalAll: List<Movie> = emptyList(),
    val globalSeries: List<Movie> = emptyList(),
    val globalMovies: List<Movie> = emptyList(),
    val updatedAtEpochMs: Long = 0L
) {
    val hasAnyData: Boolean
        get() = vnAll.isNotEmpty() || globalAll.isNotEmpty()

    fun getList(mode: HomeFilterMode, category: Top10CategoryFilter): List<Movie> {
        return when (mode) {
            HomeFilterMode.TOP10_VIETNAM -> when (category) {
                Top10CategoryFilter.ALL -> vnAll
                Top10CategoryFilter.SERIES -> vnSeries
                Top10CategoryFilter.MOVIES -> vnMovies
            }
            HomeFilterMode.TOP10_GLOBAL -> when (category) {
                Top10CategoryFilter.ALL -> globalAll
                Top10CategoryFilter.SERIES -> globalSeries
                Top10CategoryFilter.MOVIES -> globalMovies
            }
            HomeFilterMode.ALL -> emptyList()
        }
    }
}
