package com.phimbud.mobile.core.model

import kotlinx.serialization.Serializable

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
        get() = tmdbPoster?.let { "https://image.tmdb.org/t/p/w500$it" }

    val backdropUrl: String?
        get() = tmdbBackdrop?.let { "https://image.tmdb.org/t/p/w780$it" } ?: posterUrl
}

@Serializable
data class Season(
    val fid: String,
    val number: Int,
    val seasonFull: Boolean = false,
    val episodeCount: String = "0"
)

@Serializable
data class Episode(
    val fid: String,
    val number: Int,
    val name: String = "Tập $number",
    val airDate: String? = null,
    val tmdbPoster: String? = null
) {
    val thumbnailUrl: String?
        get() = tmdbPoster?.let { "https://image.tmdb.org/t/p/w500$it" }
}

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

data class HomeFeed(
    val trending: List<Movie> = emptyList(),
    val watching: List<Movie> = emptyList(),
    val updated: List<Movie> = emptyList(),
    val shows: List<Movie> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val wishlist: List<Movie> = emptyList()
)

data class SearchItem(
    val fid: String,
    val nameEn: String,
    val nameVi: String,
    val tmdbPoster: String?,
    val translation: String,
    val imdbId: String?,
    val year: Int
) {
    val displayTitle: String
        get() = nameVi.ifBlank { nameEn }

    val posterUrl: String?
        get() = tmdbPoster?.let { "https://image.tmdb.org/t/p/w342$it" }
}

data class UserSession(
    val userId: Long = 0,
    val userName: String = "",
    val userEmail: String = "",
    val canWatch: Boolean = true,
    val isAdmin: Boolean = false,
    val isVip: Boolean = true
)
