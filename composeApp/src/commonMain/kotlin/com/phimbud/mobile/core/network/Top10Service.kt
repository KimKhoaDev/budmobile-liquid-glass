package com.phimbud.mobile.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RawTop10Charts(
    val vnMovies: List<String> = emptyList(),
    val vnSeries: List<String> = emptyList(),
    val globalMoviesEn: List<String> = emptyList(),
    val globalMoviesNonEn: List<String> = emptyList(),
    val globalSeriesEn: List<String> = emptyList(),
    val globalSeriesNonEn: List<String> = emptyList(),
    val itunesVnMovies: List<String> = emptyList(),
    val itunesGlobalMovies: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = vnMovies.isEmpty() &&
            vnSeries.isEmpty() &&
            globalMoviesEn.isEmpty() &&
            globalMoviesNonEn.isEmpty() &&
            globalSeriesEn.isEmpty() &&
            globalSeriesNonEn.isEmpty() &&
            itunesVnMovies.isEmpty() &&
            itunesGlobalMovies.isEmpty()
}

/**
 * Fetches official public Top 10 rankings for Vietnam and Worldwide using an isolated, cookie-free
 * [publicHttpClient] so phimbud.com headers/cookies are never exposed to external servers.
 */
class Top10Service(private val publicHttpClient: HttpClient) {

    companion object {
        private const val TUDUM_VN_FILMS = "https://www.netflix.com/tudum/top10/vietnam"
        private const val TUDUM_VN_TV = "https://www.netflix.com/tudum/top10/vietnam/tv"
        private const val TUDUM_GLOBAL_FILMS_EN = "https://www.netflix.com/tudum/top10"
        private const val TUDUM_GLOBAL_FILMS_NON_EN = "https://www.netflix.com/tudum/top10/films-non-english"
        private const val TUDUM_GLOBAL_TV_EN = "https://www.netflix.com/tudum/top10/tv"
        private const val TUDUM_GLOBAL_TV_NON_EN = "https://www.netflix.com/tudum/top10/tv-non-english"

        private const val ITUNES_VN_MOVIES = "https://itunes.apple.com/vn/rss/topmovies/limit=25/json"
        private const val ITUNES_US_MOVIES = "https://itunes.apple.com/us/rss/topmovies/limit=25/json"

        private const val PER_SOURCE_TIMEOUT_MS = 9_000L

        private const val TUDUM_MARKER = "top10-table-row-title"
        private val htmlTagStripRegex = Regex("""<[^>]+>""")
        private val hexEntityRegex = Regex("""&#x([0-9a-fA-F]+);""")
        private val decEntityRegex = Regex("""&#(\d+);""")

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parseTudumHtml(html: String): List<String> {
            if (html.isBlank()) return emptyList()
            val results = ArrayList<String>(10)
            var searchFrom = 0
            val len = html.length

            while (searchFrom < len) {
                val markerIdx = html.indexOf(TUDUM_MARKER, searchFrom, ignoreCase = true)
                if (markerIdx < 0) break

                val cellCloseIdx = html.indexOf("</td>", markerIdx, ignoreCase = true)
                val windowEnd = if (cellCloseIdx != -1) {
                    minOf(cellCloseIdx, markerIdx + 4096)
                } else {
                    minOf(len, markerIdx + 2048)
                }

                val btnOpenIdx = html.indexOf("<button", markerIdx, ignoreCase = true)
                if (btnOpenIdx in markerIdx until windowEnd) {
                    val btnTagEnd = html.indexOf('>', btnOpenIdx + 7)
                    if (btnTagEnd in (btnOpenIdx + 7) until windowEnd) {
                        val btnCloseIdx = html.indexOf("</button>", btnTagEnd + 1, ignoreCase = true)
                        if (btnCloseIdx in (btnTagEnd + 1)..windowEnd) {
                            val rawInner = html.substring(btnTagEnd + 1, btnCloseIdx)
                            val stripped = if (rawInner.indexOf('<') >= 0) {
                                htmlTagStripRegex.replace(rawInner, "")
                            } else {
                                rawInner
                            }
                            val clean = unescapeHtmlEntities(stripped).trim()
                            if (clean.isNotBlank()) {
                                results.add(clean)
                            }
                            searchFrom = btnCloseIdx + 9
                            continue
                        }
                    }
                }
                searchFrom = markerIdx + TUDUM_MARKER.length
            }
            return results
        }

        fun parseItunesRssJson(jsonText: String): List<String> {
            if (jsonText.isBlank()) return emptyList()
            return runCatching {
                val root = json.parseToJsonElement(jsonText).jsonObject
                val entries = root["feed"]?.jsonObject?.get("entry")?.jsonArray ?: return emptyList()
                val out = ArrayList<String>(entries.size)
                for (entry in entries) {
                    val label = entry.jsonObject["im:name"]
                        ?.jsonObject
                        ?.get("label")
                        ?.jsonPrimitive
                        ?.content
                        ?.trim()
                    if (!label.isNullOrBlank()) {
                        out.add(unescapeHtmlEntities(label))
                    }
                }
                out
            }.getOrDefault(emptyList())
        }

        fun unescapeHtmlEntities(input: String): String {
            if (!input.contains('&')) return input
            var s = input
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")

            s = hexEntityRegex.replace(s) { m ->
                val code = m.groupValues[1].toIntOrNull(16)
                if (code != null && code in 1..0xFFFF) code.toChar().toString() else m.value
            }
            s = decEntityRegex.replace(s) { m ->
                val code = m.groupValues[1].toIntOrNull(10)
                if (code != null && code in 1..0xFFFF) code.toChar().toString() else m.value
            }
            return s
        }
    }

    suspend fun fetchRawCharts(): RawTop10Charts = withContext(Dispatchers.IO) {
        coroutineScope {
            val vnMoviesDef = async { fetchTudumSource(TUDUM_VN_FILMS) }
            val vnSeriesDef = async { fetchTudumSource(TUDUM_VN_TV) }
            val globalMoviesEnDef = async { fetchTudumSource(TUDUM_GLOBAL_FILMS_EN) }
            val globalMoviesNonEnDef = async { fetchTudumSource(TUDUM_GLOBAL_FILMS_NON_EN) }
            val globalSeriesEnDef = async { fetchTudumSource(TUDUM_GLOBAL_TV_EN) }
            val globalSeriesNonEnDef = async { fetchTudumSource(TUDUM_GLOBAL_TV_NON_EN) }
            val itunesVnDef = async { fetchItunesSource(ITUNES_VN_MOVIES) }
            val itunesUsDef = async { fetchItunesSource(ITUNES_US_MOVIES) }

            RawTop10Charts(
                vnMovies = vnMoviesDef.await(),
                vnSeries = vnSeriesDef.await(),
                globalMoviesEn = globalMoviesEnDef.await(),
                globalMoviesNonEn = globalMoviesNonEnDef.await(),
                globalSeriesEn = globalSeriesEnDef.await(),
                globalSeriesNonEn = globalSeriesNonEnDef.await(),
                itunesVnMovies = itunesVnDef.await(),
                itunesGlobalMovies = itunesUsDef.await()
            )
        }
    }

    private suspend fun fetchTudumSource(url: String): List<String> {
        val html = fetchTextSafely(url) ?: return emptyList()
        return withContext(Dispatchers.Default) {
            parseTudumHtml(html)
        }
    }

    private suspend fun fetchItunesSource(url: String): List<String> {
        val text = fetchTextSafely(url) ?: return emptyList()
        return withContext(Dispatchers.Default) {
            parseItunesRssJson(text)
        }
    }

    private suspend fun fetchTextSafely(url: String): String? {
        return withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
            runCatching {
                val res = publicHttpClient.get(url)
                if (res.status.isSuccess()) {
                    res.bodyAsText()
                } else {
                    null
                }
            }.getOrNull()
        }
    }
}
