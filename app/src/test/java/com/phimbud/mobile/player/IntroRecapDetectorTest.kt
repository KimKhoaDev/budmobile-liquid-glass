package com.phimbud.mobile.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroRecapDetectorTest {

    @Test
    fun `detectFromSrt detects Recap and lands 500ms before episode dialogue`() {
        val srt = """
            1
            00:00:02,000 --> 00:00:05,000
            <i>Ở các tập trước...</i>

            2
            00:00:06,000 --> 00:00:12,000
            Chúng ta không còn nhiều thời gian nữa!

            3
            00:00:13,000 --> 00:00:22,000
            Hãy mở cánh cổng ngay bây giờ!

            4
            00:00:30,000 --> 00:00:34,000
            Buổi sáng tốt lành, mọi người đã sẵn sàng chưa?
        """.trimIndent()

        val segments = IntroRecapDetector.detectFromSrt(srt, isEpisode = true)
        assertEquals(1, segments.size)
        val recap = segments.first()
        assertEquals(SkipSegmentType.RECAP, recap.type)
        assertEquals("Bỏ qua Tóm tắt", recap.label)
        assertEquals(0L, recap.startMs)
        assertEquals(29_500L, recap.endMs)
    }

    @Test
    fun `detectFromSrt snaps early intro with opening hook or disclaimer straight to 0ms`() {
        val srt = """
            1
            00:00:02,000 --> 00:00:10,000
            Các nhân vật và sự kiện trong phim hoàn toàn là hư cấu.

            2
            00:00:11,000 --> 00:00:15,000
            Chạy mau!

            3
            00:00:20,000 --> 00:00:35,000
            ♪ Nhạc mở đầu vang lên ♪

            4
            00:00:40,000 --> 00:00:55,000
            Phụ đề được thực hiện bởi PhimBud Subteam

            5
            00:01:30,000 --> 00:01:34,000
            Ba năm sau tại Seoul...
        """.trimIndent()

        val segments = IntroRecapDetector.detectFromSrt(srt, isEpisode = true)
        assertEquals(1, segments.size)
        val intro = segments.first()
        assertEquals(SkipSegmentType.INTRO, intro.type)
        assertEquals("Bỏ qua Giới thiệu", intro.label)
        // Snapped to 0ms so the user does not have to wait 10-15s for the button to appear!
        assertEquals(0L, intro.startMs)
        assertEquals(89_500L, intro.endMs)
    }

    @Test
    fun `detectFromSrt triggers 3s early after a multi-line Cold Open`() {
        val srt = """
            1
            00:00:05,000 --> 00:00:12,000
            Đội trưởng, chúng tôi đã tìm thấy dấu vết ở nhà kho cũ.

            2
            00:00:14,000 --> 00:00:25,000
            Phong tỏa toàn bộ khu vực, đừng để bất kỳ ai rời khỏi đó!

            3
            00:00:28,000 --> 00:00:42,000
            Đã rõ, đội đặc nhiệm đang tiến vào từ cổng sau.

            4
            00:00:50,000 --> 00:01:10,000
            [Nhạc mở đầu kịch tính]

            5
            00:01:55,000 --> 00:02:00,000
            Sáng hôm sau tại trụ sở cảnh sát...
        """.trimIndent()

        val segments = IntroRecapDetector.detectFromSrt(srt, isEpisode = true)
        assertEquals(1, segments.size)
        val intro = segments.first()
        assertEquals(SkipSegmentType.INTRO, intro.type)
        assertEquals(39_000L, intro.startMs) // 3s before 42_000ms
        assertEquals(114_500L, intro.endMs)
    }

    @Test
    fun `detectFromSrt detects both Recap and Intro in the same episode`() {
        val srt = """
            1
            00:00:01,500 --> 00:00:04,000
            Previously on The Last Kingdom...

            2
            00:00:05,000 --> 00:00:18,000
            Vương quốc sẽ thuộc về ai giữ được thành lũy này.

            3
            00:00:35,000 --> 00:00:50,000
            [Nhạc mở đầu]

            4
            00:01:25,000 --> 00:01:29,000
            Bẩm bệ hạ, quân đội đã tập hợp đông đủ.
        """.trimIndent()

        val segments = IntroRecapDetector.detectFromSrt(srt, isEpisode = true)
        assertEquals(2, segments.size)

        val recap = segments[0]
        assertEquals(SkipSegmentType.RECAP, recap.type)
        assertEquals(0L, recap.startMs)
        assertEquals(19_000L, recap.endMs)

        val intro = segments[1]
        assertEquals(SkipSegmentType.INTRO, intro.type)
        assertEquals(19_000L, intro.startMs)
        assertEquals(84_500L, intro.endMs)
    }

    @Test
    fun `detectFromSrt does not trigger false positive on normal dialogue pauses`() {
        val srt = """
            1
            00:00:04,000 --> 00:00:08,000
            Xin chào, hôm nay trời đẹp nhỉ.

            2
            00:00:18,000 --> 00:00:22,000
            Ừ, chúng ta đi dạo thôi.

            3
            00:00:35,000 --> 00:00:40,000
            Cẩn thận đường trơn đấy.
        """.trimIndent()

        val segments = IntroRecapDetector.detectFromSrt(srt, isEpisode = true)
        assertTrue(segments.isEmpty())
    }
}
