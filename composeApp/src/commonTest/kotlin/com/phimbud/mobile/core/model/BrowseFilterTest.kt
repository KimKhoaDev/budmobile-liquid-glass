package com.phimbud.mobile.core.model

import com.phimbud.mobile.core.platform.utcYearFromEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowseFilterTest {

    @Test
    fun testTitleDetailIncludesYearAndGenreTags() {
        val detail = TitleDetail(
            fid = "test-fid",
            nameEn = "VisionQuest",
            nameVi = "VisionQuest",
            year = 2026,
            genres = listOf("Chính kịch", "Viễn tưởng & Thần thoại"),
            genreTags = listOf(
                GenreTag("Chính kịch", "chinh-kich"),
                GenreTag("Viễn tưởng & Thần thoại", "vien-tuong-than-thoai")
            )
        )
        assertEquals(2026, detail.year)
        assertEquals("VisionQuest", detail.displayTitle)
        assertEquals(2, detail.genreTags.size)
        assertEquals("vien-tuong-than-thoai", detail.genreTags[1].slug)
    }

    @Test
    fun testBrowseFilterIsActiveAndActiveCount() {
        val emptyFilter = BrowseFilter()
        assertFalse(emptyFilter.isActive)
        assertEquals(0, emptyFilter.activeCount)

        val typeFilter = BrowseFilter(type = "movie")
        assertTrue(typeFilter.isActive)
        assertEquals(1, typeFilter.activeCount)

        val multiFilter = BrowseFilter(
            type = "movie",
            genre = "hanh-dong",
            country = "US",
            year = "2024",
            duration = "90-120",
            sort = "rating"
        )
        assertTrue(multiFilter.isActive)
        assertEquals(6, multiFilter.activeCount)
    }

    @Test
    fun testBrowseFilterOptionsFullWebParity() {
        // Types
        val types = BrowseFilterOptions.TYPE_OPTIONS
        assertEquals(3, types.size)
        assertEquals(null, types[0].value)
        assertEquals("movie", types[1].value)
        assertEquals("show", types[2].value)

        // Genres (24 slugs + default null)
        val genres = BrowseFilterOptions.GENRE_OPTIONS
        assertEquals(25, genres.size)
        assertNotNull(genres.find { it.value == "hanh-dong" })
        assertNotNull(genres.find { it.value == "vien-tuong-than-thoai" })

        // Countries (53 ISO codes from phimbud web + default null)
        val countries = BrowseFilterOptions.COUNTRY_OPTIONS
        assertEquals(54, countries.size)
        assertNotNull(countries.find { it.value == "VN" })
        assertNotNull(countries.find { it.value == "US" })
        assertNotNull(countries.find { it.value == "KR" })
        assertNotNull(countries.find { it.value == "SA" })

        // Years
        val years = BrowseFilterOptions.getYearOptions(2026)
        assertEquals(13, years.size) // null, 2026..2016 (11 years), "-2016"
        assertEquals("- Tất cả -", years[0].label)
        assertEquals("2026", years[1].value)
        assertEquals("-2016", years.last().value)
        assertEquals("Trước 2016", years.last().label)

        // Dynamic year from epoch
        assertEquals(2026, utcYearFromEpochMillis(1778140800000L)) // 2026-05-07 UTC
        val dynamicYears = BrowseFilterOptions.getYearOptions()
        assertTrue(dynamicYears.size == 13)

        // Durations
        val durations = BrowseFilterOptions.DURATION_OPTIONS
        assertEquals(8, durations.size)

        // Sorts
        val sorts = BrowseFilterOptions.SORT_OPTIONS
        assertEquals(3, sorts.size)
        assertEquals(null, sorts[0].value)
        assertEquals("publishDate", sorts[1].value)
        assertEquals("rating", sorts[2].value)
    }

    @Test
    fun testResolveGenreSlugAndYearFilterValue() {
        assertEquals("chinh-kich", BrowseFilterOptions.resolveGenreSlug("Chính kịch"))
        assertEquals(
            "vien-tuong-than-thoai",
            BrowseFilterOptions.resolveGenreSlug("Viễn tưởng & Thần thoại")
        )
        assertEquals(
            "hanh-dong-phieu-luu",
            BrowseFilterOptions.resolveGenreSlug("Hành động & Phiêu lưu")
        )
        assertEquals("hoat-hinh", BrowseFilterOptions.resolveGenreSlug("hoat-hinh"))
        assertNull(BrowseFilterOptions.resolveGenreSlug("   "))

        assertEquals("2026", BrowseFilterOptions.resolveYearFilterValue(2026, currentYear = 2026))
        assertEquals("2016", BrowseFilterOptions.resolveYearFilterValue(2016, currentYear = 2026))
        assertEquals("-2016", BrowseFilterOptions.resolveYearFilterValue(2010, currentYear = 2026))
        assertNull(BrowseFilterOptions.resolveYearFilterValue(0, currentYear = 2026))

        val yearOpts = BrowseFilterOptions.getYearOptions(2026)
        assertEquals("Trước 2016", BrowseFilterOptions.findLabel(yearOpts, "-2016"))
        assertEquals("Phim Lẻ", BrowseFilterOptions.findLabel(BrowseFilterOptions.TYPE_OPTIONS, "movie"))
        assertNull(BrowseFilterOptions.findLabel(BrowseFilterOptions.TYPE_OPTIONS, null))
    }
}
