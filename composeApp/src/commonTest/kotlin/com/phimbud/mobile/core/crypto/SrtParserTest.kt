package com.phimbud.mobile.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SrtParserTest {

    @Test
    fun testParseSrtWithVietnameseMultilineAndInlineTags() {
        val srt = "\uFEFF1\r\n" +
            "00:00:01,500 --> 00:00:04,200\r\n" +
            "<i>Xin chào các bạn!</i>\r\n" +
            "Chào mừng đến với <b>PhimBud iOS</b>.\r\n\r\n" +
            "2\r\n" +
            "00:01:10,000 --> 00:01:13,750\r\n" +
            "Tập phim tiếp theo bắt đầu ngay bây giờ.\r\n"

        val cues = SrtParser.parse(srt)
        assertEquals(2, cues.size)
        assertEquals(1500L, cues[0].startMs)
        assertEquals(4200L, cues[0].endMs)
        assertEquals("Xin chào các bạn!\nChào mừng đến với PhimBud iOS.", cues[0].text)

        assertNull(SrtParser.findActiveText(cues, 1499L))
        assertEquals(
            "Xin chào các bạn!\nChào mừng đến với PhimBud iOS.",
            SrtParser.findActiveText(cues, 2500L)
        )
        assertNull(SrtParser.findActiveText(cues, 5000L))
        assertEquals(
            "Tập phim tiếp theo bắt đầu ngay bây giờ.",
            SrtParser.findActiveText(cues, 71_000L)
        )
    }

    @Test
    fun testParseWebVttFormat() {
        val vtt = """
            WEBVTT

            00:05.000 --> 00:08.500 align:middle
            Phụ đề chuẩn WebVTT
        """.trimIndent()

        val cues = SrtParser.parse(vtt)
        assertEquals(1, cues.size)
        assertEquals(5000L, cues[0].startMs)
        assertEquals(8500L, cues[0].endMs)
        assertEquals("Phụ đề chuẩn WebVTT", cues[0].text)
    }
}
