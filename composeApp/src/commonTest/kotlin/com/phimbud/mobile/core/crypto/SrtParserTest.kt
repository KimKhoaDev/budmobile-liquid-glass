package com.phimbud.mobile.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals(SubtitlePlacement.BOTTOM, cues[0].placement)
    }

    @Test
    fun testAssTopTagPlacementAndSimultaneousSplitCues() {
        val srt = """
            1
            00:00:10,000 --> 00:00:14,000
            {\an8}<font color="#FFFF00">Thủ đô Seoul, Mùa đông năm 2025</font>

            2
            00:00:10,500 --> 00:00:13,800
            {\pos(960,980)}Chúng ta đã đến nơi an toàn rồi.
        """.trimIndent()

        val cues = SrtParser.parse(srt)
        assertEquals(2, cues.size)
        assertEquals(SubtitlePlacement.TOP, cues[0].placement)
        assertEquals("Thủ đô Seoul, Mùa đông năm 2025", cues[0].text)
        assertEquals(SubtitlePlacement.BOTTOM, cues[1].placement)
        assertEquals("Chúng ta đã đến nơi an toàn rồi.", cues[1].text)

        val stateAt12s = SrtParser.findActiveState(cues, 12_000L)
        assertEquals("Thủ đô Seoul, Mùa đông năm 2025", stateAt12s.topText)
        assertEquals("Chúng ta đã đến nơi an toàn rồi.", stateAt12s.bottomText)
    }

    @Test
    fun testSmartLineBalancingForLongSingleLineDialogue() {
        val longLine = "Chúng ta không còn nhiều thời gian nữa đâu, mau rời khỏi tòa nhà này ngay lập tức!"
        val balanced = SrtParser.balanceSubtitleLines(longLine, maxCharsPerLine = 44)
        val lines = balanced.split('\n')
        assertEquals(2, lines.size)
        assertEquals("Chúng ta không còn nhiều thời gian nữa đâu,", lines[0])
        assertEquals("mau rời khỏi tòa nhà này ngay lập tức!", lines[1])

        // Already multi-line dialogue from translator must remain untouched
        val twoSpeakers = "- Anh đi đâu đấy?\n- Tôi ra sân bay đón bạn."
        assertEquals(twoSpeakers, SrtParser.balanceSubtitleLines(twoSpeakers, maxCharsPerLine = 20))

        // Short line remains single-line
        val shortLine = "Chào buổi sáng mọi người!"
        assertTrue(!SrtParser.balanceSubtitleLines(shortLine, maxCharsPerLine = 44).contains('\n'))
    }
}
