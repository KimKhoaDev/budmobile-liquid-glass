package com.phimbud.mobile.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNormalizerTest {

    @Test
    fun `normalize strips Vietnamese diacritics and special letter d`() {
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
    fun `normalize handles ASCII fast-path and strips punctuation`() {
        assertEquals(
            "spiderman across the spiderverse 2023",
            SearchNormalizer.normalize("  Spider-Man: Across the Spider-Verse (2023) ")
        )
    }

    @Test
    fun `normalize returns empty string for blank or symbol-only input`() {
        assertEquals("", SearchNormalizer.normalize(""))
        assertEquals("", SearchNormalizer.normalize("   "))
        assertEquals("", SearchNormalizer.normalize("?!@#$%^&*()"))
    }

    @Test
    fun `SearchItem precomputes normalized Vietnamese and English titles`() {
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
