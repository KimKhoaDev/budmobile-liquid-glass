package com.phimbud.mobile.core.model

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
}
