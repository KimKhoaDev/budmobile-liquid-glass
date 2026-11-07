package com.phimbud.mobile.core.model

import com.phimbud.mobile.core.network.RawTop10Charts
import com.phimbud.mobile.core.network.Top10Service
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Top10ParserAndMatcherTest {

    @Test
    fun parseTudumHtmlExtractsTitlesAndUnescapesEntities() {
        val sampleHtml = """
            <table>
              <tbody>
                <tr>
                  <td class="title" data-uia="top10-table-row-title"><span class="rank">01</span><img class="desktop-only" src="https://dnm.nflximg.net/a.jpg" alt="" aria-hidden="true"/><button>Surviving Hurricane Otis</button></td>
                  <td data-uia="top10-table-row-weeks">2</td>
                </tr>
                <tr>
                  <td class="title" data-uia="top10-table-row-title"><span class="rank">02</span><img class="desktop-only" src="https://dnm.nflximg.net/b.jpg" alt="" aria-hidden="true"/><button>Hot Wheels Let&#x27;s Race: Season 2</button></td>
                  <td data-uia="top10-table-row-weeks">5</td>
                </tr>
                <tr>
                  <td class="title" data-uia="top10-table-row-title"><span class="rank">03</span><button>Fast &amp; Furious &#39;9&#39;</button></td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val titles = Top10Service.parseTudumHtml(sampleHtml)
        assertEquals(
            listOf(
                "Surviving Hurricane Otis",
                "Hot Wheels Let's Race: Season 2",
                "Fast & Furious '9'"
            ),
            titles
        )
    }

    @Test
    fun parseItunesRssJsonExtractsMovieLabels() {
        val sampleJson = """
            {
              "feed": {
                "entry": [
                  { "im:name": { "label": "Spider-Man: Brand New Day" } },
                  { "im:name": { "label": "Michael (2026)" } },
                  { "im:name": { "label": "Final Destination: Bloodlines" } }
                ]
              }
            }
        """.trimIndent()

        val titles = Top10Service.parseItunesRssJson(sampleJson)
        assertEquals(
            listOf(
                "Spider-Man: Brand New Day",
                "Michael (2026)",
                "Final Destination: Bloodlines"
            ),
            titles
        )
    }

    @Test
    fun matcherStripsSeasonsHandlesNumberedColonsAndPrefersMatchingYear() {
        val catalog = listOf(
            SearchItem(
                fid = "mai_1999",
                nameEn = "Mai Old",
                nameVi = "Mai",
                tmdbPoster = "/old.jpg",
                translation = "subtitle",
                imdbId = "tt0001",
                year = 1999
            ),
            SearchItem(
                fid = "mai_2024",
                nameEn = "Mai",
                nameVi = "Mai",
                tmdbPoster = "/mai2024.jpg",
                translation = "subtitle",
                imdbId = "tt2024",
                year = 2024
            ),
            SearchItem(
                fid = "lupin_show",
                nameEn = "Lupin",
                nameVi = "Siêu Trộm Lupin",
                tmdbPoster = "/lupin.jpg",
                translation = "subtitle",
                imdbId = "tt2531336",
                year = 2021
            ),
            SearchItem(
                fid = "doctor_x",
                nameEn = "Doctor X: Mafia in White",
                nameVi = "Bác Sĩ X",
                tmdbPoster = "/drx.jpg",
                translation = "subtitle",
                imdbId = "tt9999",
                year = 2026
            ),
            SearchItem(
                fid = "ice_age_5",
                nameEn = "Ice Age: Collision Course",
                nameVi = "Kỷ Băng Hà: Trời Sập",
                tmdbPoster = "/ice5.jpg",
                translation = "dub",
                imdbId = "tt3416828",
                year = 2016
            ),
            SearchItem(
                fid = "michael_1996",
                nameEn = "Michael",
                nameVi = "Thiên Thần Michael",
                tmdbPoster = "/m96.jpg",
                translation = "subtitle",
                imdbId = "tt0117038",
                year = 1996
            ),
            SearchItem(
                fid = "michael_2026",
                nameEn = "Michael",
                nameVi = "Ông Hoàng Nhạc Pop",
                tmdbPoster = "/m26.jpg",
                translation = "subtitle",
                imdbId = "tt11378946",
                year = 2026
            )
        )

        val rawCharts = RawTop10Charts(
            vnMovies = listOf("Mai", "Unknown Documentary"),
            vnSeries = listOf("Doctor X: Mafia in White: Limited Series", "Lupin: Part 4"),
            itunesVnMovies = listOf("Michael (2026)"),
            globalMoviesEn = listOf("Ice Age 5: Collision Course"),
            globalSeriesNonEn = listOf("Lupin: Part 4")
        )

        val lists = Top10CatalogMatcher.buildTop10Lists(
            raw = rawCharts,
            catalog = catalog,
            updatedAtEpochMs = 1000L
        )

        assertEquals(listOf("mai_2024", "michael_2026"), lists.vnMovies.map { it.fid })
        assertEquals(listOf("doctor_x", "lupin_show"), lists.vnSeries.map { it.fid })
        assertEquals(
            listOf("doctor_x", "mai_2024", "lupin_show", "michael_2026"),
            lists.vnAll.map { it.fid }
        )
        assertEquals(listOf("ice_age_5"), lists.globalMovies.map { it.fid })
        assertTrue(lists.hasAnyData)
        assertEquals(
            lists.vnSeries,
            lists.getList(HomeFilterMode.TOP10_VIETNAM, Top10CategoryFilter.SERIES)
        )
    }
}
