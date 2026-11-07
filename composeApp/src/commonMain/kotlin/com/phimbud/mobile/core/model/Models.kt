package com.phimbud.mobile.core.model

import androidx.compose.runtime.Immutable
import com.phimbud.mobile.core.platform.utcYearFromEpochMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

object SearchNormalizer {

    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length)
        var pendingSpace = false
        for (rawChar in input) {
            val ch = rawChar.lowercaseChar()
            val code = ch.code
            if (code <= 32) {
                if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                    if (sb.isNotEmpty()) {
                        pendingSpace = true
                    }
                }
            } else if (code < 128) {
                if (ch in 'a'..'z' || ch in '0'..'9') {
                    if (pendingSpace) {
                        sb.append(' ')
                        pendingSpace = false
                    }
                    sb.append(ch)
                }
            } else if (isCombiningMark(code)) {
                // Skip Unicode combining diacritical marks (handles NFD & mixed Vietnamese input)
                continue
            } else {
                val folded = foldToAsciiLower(ch)
                if (folded != '\u0000') {
                    if (pendingSpace) {
                        sb.append(' ')
                        pendingSpace = false
                    }
                    sb.append(folded)
                }
            }
        }
        return sb.toString()
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
    @Transient
    val displayTitle: String = nameVi.ifBlank { nameEn }

    @Transient
    val posterUrl: String? = tmdbPoster?.let { "https://image.tmdb.org/t/p/w342$it" }

    @Transient
    val posterLargeUrl: String? = tmdbPoster?.let { "https://image.tmdb.org/t/p/w500$it" }

    @Transient
    val backdropUrl: String? = tmdbBackdrop?.let { "https://image.tmdb.org/t/p/w780$it" } ?: posterLargeUrl
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
    @Transient
    val thumbnailUrl: String? = tmdbPoster?.let { "https://image.tmdb.org/t/p/w300$it" }
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
    val displayShowTitle: String = showNameVi.ifBlank { showNameEn }
}

@Immutable
@Serializable
data class GenreTag(
    val name: String,
    val slug: String
)

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
    val genreTags: List<GenreTag> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val relatedTitles: List<Movie> = emptyList(),
    val year: Int = 0
) {
    val displayTitle: String = nameVi.ifBlank { nameEn }

    val posterUrl: String? = tmdbPoster?.let { "https://image.tmdb.org/t/p/w500$it" }

    val backdropUrl: String? = tmdbBackdrop?.let { "https://image.tmdb.org/t/p/w780$it" } ?: posterUrl
}

@Immutable
data class BrowsePageResult(
    val titles: List<Movie> = emptyList(),
    val nextPage: Int? = null
)

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
    val displayTitle: String = nameVi.ifBlank { nameEn }

    val posterUrl: String? = tmdbPoster?.let { "https://image.tmdb.org/t/p/w342$it" }
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

@Immutable
@Serializable
data class BrowseFilter(
    val type: String? = null,
    val genre: String? = null,
    val country: String? = null,
    val year: String? = null,
    val duration: String? = null,
    val sort: String? = null
) {
    val isActive: Boolean
        get() = !type.isNullOrBlank() ||
            !genre.isNullOrBlank() ||
            !country.isNullOrBlank() ||
            !year.isNullOrBlank() ||
            !duration.isNullOrBlank() ||
            !sort.isNullOrBlank()

    val activeCount: Int
        get() {
            var count = 0
            if (!type.isNullOrBlank()) count++
            if (!genre.isNullOrBlank()) count++
            if (!country.isNullOrBlank()) count++
            if (!year.isNullOrBlank()) count++
            if (!duration.isNullOrBlank()) count++
            if (!sort.isNullOrBlank()) count++
            return count
        }
}

data class FilterOption(
    val label: String,
    val value: String?
)

object BrowseFilterOptions {

    val TYPE_OPTIONS = listOf(
        FilterOption("- Tất cả -", null),
        FilterOption("Phim Lẻ", "movie"),
        FilterOption("Phim Bộ", "show")
    )

    val GENRE_OPTIONS = listOf(
        FilterOption("- Tất cả -", null),
        FilterOption("Âm nhạc", "am-nhac"),
        FilterOption("Bí ẩn", "bi-an"),
        FilterOption("Chiến tranh", "chien-tranh"),
        FilterOption("Chiến tranh & Chính trị", "chien-tranh-chinh-tri"),
        FilterOption("Chính kịch", "chinh-kich"),
        FilterOption("Gia đình", "gia-dinh"),
        FilterOption("Giật gân", "giat-gan"),
        FilterOption("Hài", "hai"),
        FilterOption("Hành động", "hanh-dong"),
        FilterOption("Hành động & Phiêu lưu", "hanh-dong-phieu-luu"),
        FilterOption("Hoạt hình", "hoat-hinh"),
        FilterOption("Kinh dị", "kinh-di"),
        FilterOption("Kỳ ảo", "ky-ao"),
        FilterOption("Lãng mạn", "lang-man"),
        FilterOption("Lịch sử", "lich-su"),
        FilterOption("Phiêu lưu", "phieu-luu"),
        FilterOption("Tài liệu", "tai-lieu"),
        FilterOption("Thực tế", "thuc-te"),
        FilterOption("Tội phạm", "toi-pham"),
        FilterOption("Trẻ em", "tre-em"),
        FilterOption("Truyền hình", "truyen-hinh"),
        FilterOption("Viễn Tây", "vien-tay"),
        FilterOption("Viễn tưởng", "vien-tuong"),
        FilterOption("Viễn tưởng & Thần thoại", "vien-tuong-than-thoai")
    )

    val COUNTRY_OPTIONS = listOf(
        FilterOption("- Tất cả -", null),
        FilterOption("Mỹ", "US"),
        FilterOption("Hàn Quốc", "KR"),
        FilterOption("Trung Quốc", "CN"),
        FilterOption("Nhật Bản", "JP"),
        FilterOption("Thái Lan", "TH"),
        FilterOption("Hồng Kông", "HK"),
        FilterOption("Anh", "GB"),
        FilterOption("Pháp", "FR"),
        FilterOption("Canada", "CA"),
        FilterOption("Đài Loan", "TW"),
        FilterOption("Ấn Độ", "IN"),
        FilterOption("Úc", "AU"),
        FilterOption("Việt Nam", "VN"),
        FilterOption("Đức", "DE"),
        FilterOption("Thụy Điển", "SE"),
        FilterOption("Ý", "IT"),
        FilterOption("Hungary", "HU"),
        FilterOption("Ai-len", "IE"),
        FilterOption("Malta", "MT"),
        FilterOption("New Zealand", "NZ"),
        FilterOption("Nga", "RU"),
        FilterOption("Iceland", "IS"),
        FilterOption("Phần Lan", "FI"),
        FilterOption("Ma-la-uy", "MW"),
        FilterOption("Colombia", "CO"),
        FilterOption("Đan Mạch", "DK"),
        FilterOption("Bỉ", "BE"),
        FilterOption("Tây Ban Nha", "ES"),
        FilterOption("Argentina", "AR"),
        FilterOption("Hà Lan", "NL"),
        FilterOption("Na Uy", "NO"),
        FilterOption("Singapore", "SG"),
        FilterOption("Ba Lan", "PL"),
        FilterOption("Malaysia", "MY"),
        FilterOption("Indonesia", "ID"),
        FilterOption("Iran", "IR"),
        FilterOption("Puerto Rico", "PR"),
        FilterOption("Nepal", "NP"),
        FilterOption("Bulgaria", "BG"),
        FilterOption("Campuchia", "KH"),
        FilterOption("Philippines", "PH"),
        FilterOption("Thổ Nhĩ Kỳ", "TR"),
        FilterOption("Morocco", "MA"),
        FilterOption("Brazil", "BR"),
        FilterOption("Mexico", "MX"),
        FilterOption("Séc", "CZ"),
        FilterOption("Rumani", "RO"),
        FilterOption("Palestine", "PS"),
        FilterOption("Kazakhstan", "KZ"),
        FilterOption("Nam Phi", "ZA"),
        FilterOption("Áo", "AT"),
        FilterOption("Thụy Sĩ", "CH"),
        FilterOption("Ả Rập Xê-út", "SA")
    )

    fun getYearOptions(currentYear: Int = utcYearFromEpochMillis()): List<FilterOption> {
        val list = mutableListOf(FilterOption("- Tất cả -", null))
        for (y in currentYear downTo (currentYear - 10)) {
            list.add(FilterOption(y.toString(), y.toString()))
        }
        val pastYear = currentYear - 10
        list.add(FilterOption("Trước $pastYear", "-$pastYear"))
        return list
    }

    val DURATION_OPTIONS = listOf(
        FilterOption("- Tất cả -", null),
        FilterOption("Dưới 30 phút", "0-30"),
        FilterOption("30' - 1 tiếng", "30-60"),
        FilterOption("1 - 1.5 tiếng", "60-90"),
        FilterOption("1.5 - 2 tiếng", "90-120"),
        FilterOption("2 - 2.5 tiếng", "120-150"),
        FilterOption("2.5 - 3 tiếng", "150-180"),
        FilterOption("Trên 3 tiếng", "180-0")
    )

    val SORT_OPTIONS = listOf(
        FilterOption("Ngày cập nhật", null),
        FilterOption("Ngày phát hành", "publishDate"),
        FilterOption("Điểm đánh giá", "rating")
    )

    fun resolveGenreSlug(genreNameOrSlug: String): String? {
        val trimmed = genreNameOrSlug.trim()
        if (trimmed.isEmpty()) return null

        GENRE_OPTIONS.firstOrNull {
            it.value != null && it.value.equals(trimmed, ignoreCase = true)
        }?.value?.let { return it }

        GENRE_OPTIONS.firstOrNull {
            it.value != null && it.label.equals(trimmed, ignoreCase = true)
        }?.value?.let { return it }

        val normalizedInput = SearchNormalizer.normalize(trimmed)
        if (normalizedInput.isEmpty()) return null

        GENRE_OPTIONS.firstOrNull {
            it.value != null && SearchNormalizer.normalize(it.label) == normalizedInput
        }?.value?.let { return it }

        return normalizedInput.replace(' ', '-')
    }

    fun resolveYearFilterValue(year: Int, currentYear: Int = utcYearFromEpochMillis()): String? {
        if (year <= 0) return null
        val minIndividualYear = currentYear - 10
        return if (year < minIndividualYear) {
            "-$minIndividualYear"
        } else {
            year.toString()
        }
    }

    fun findLabel(options: List<FilterOption>, value: String?): String? {
        if (value.isNullOrBlank()) return null
        return options.firstOrNull { it.value == value }?.label ?: value
    }
}
