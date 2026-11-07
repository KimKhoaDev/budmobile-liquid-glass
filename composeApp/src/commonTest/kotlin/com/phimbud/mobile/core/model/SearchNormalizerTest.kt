package com.phimbud.mobile.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchNormalizerTest {

    @Test
    fun testNormalizeStripsVietnameseDiacriticsAndSpecialLetterD() {
        assertEquals(
            "viec ma luong cao",
            SearchNormalizer.normalize("Việc ma, lương cao!")
        )
        assertEquals(
            "dao hai tac duong den dai hai trinh",
            SearchNormalizer.normalize("Đảo Hải Tặc: Đường Đến Đại Hải Trình")
        )
    }

    @Test
    fun testNormalizeHandlesDecomposedNfdVietnameseFromIosKeyboard() {
        // NFD decomposed "Đường Đến" ('Đ' + 'ư' + 'ơ' + '\u0300' + "ng " + 'Đ' + 'ê' + '\u0301' + 'n')
        val nfdInput = "Đu\u031Bo\u031B\u0300ng Đe\u0302\u0301n"
        assertEquals("duong den", SearchNormalizer.normalize(nfdInput))
    }

    @Test
    fun testNormalizeHandlesAsciiFastPathAndStripsPunctuation() {
        assertEquals(
            "spiderman across the spiderverse 2023",
            SearchNormalizer.normalize("  Spider-Man: Across the Spider-Verse (2023) ")
        )
    }

    @Test
    fun testNormalizeReturnsEmptyStringForBlankOrSymbolOnlyInput() {
        assertEquals("", SearchNormalizer.normalize(""))
        assertEquals("", SearchNormalizer.normalize("   "))
        assertEquals("", SearchNormalizer.normalize("?!@#$%^&*()"))
    }

    @Test
    fun testSearchItemPrecomputesNormalizedVietnameseAndEnglishTitles() {
        val item = SearchItem(
            fid = "abc123",
            nameEn = "Squid Game: Season 2",
            nameVi = "Trò Chơi Con Mực (Phần 2)",
            tmdbPoster = "/poster.jpg",
            translation = "subtitle",
            imdbId = "tt10919420",
            year = 2024
        )

        assertEquals("tro choi con muc phan 2", item.normalizedNameVi)
        assertEquals("squid game season 2", item.normalizedNameEn)
        assertTrue(item.normalizedNameVi.contains(SearchNormalizer.normalize("con mực")))
        assertTrue(item.normalizedNameVi.contains(SearchNormalizer.normalize("con muc")))
        assertTrue(item.normalizedNameEn.contains(SearchNormalizer.normalize("SQUID GAME")))
    }

    @Test
    fun testNormalizeCollapsesMultipleWhitespaceAndPunctuationGapsSinglePass() {
        assertEquals(
            "one piece dao hai tac 2024",
            SearchNormalizer.normalize("   One    Piece  :   Đảo   Hải\t\nTặc  ---  (2024)   ")
        )
    }

    @Test
    fun testMovieTransientComputedUrlsSurviveJsonSerializationRoundtrip() {
        val original = Movie(
            fid = "m1",
            nameEn = "Dune: Part Two",
            nameVi = "Hành Tinh Cát: Phần Hai",
            tmdbPoster = "/poster.jpg",
            tmdbBackdrop = "/backdrop.jpg",
            year = 2024
        )
        val wrapper = Top10Lists(
            updatedAtEpochMs = 123456L,
            vnMovies = listOf(original),
            vnAll = listOf(original)
        )
        val encoded = Json.encodeToString(wrapper)
        val decoded = Json.decodeFromString<Top10Lists>(encoded)
        val restored = decoded.vnMovies.single()

        assertEquals("Hành Tinh Cát: Phần Hai", restored.displayTitle)
        assertEquals("https://image.tmdb.org/t/p/w342/poster.jpg", restored.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", restored.posterLargeUrl)
        assertEquals("https://image.tmdb.org/t/p/w780/backdrop.jpg", restored.backdropUrl)
    }
}
