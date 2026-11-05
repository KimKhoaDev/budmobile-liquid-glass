package com.phimbud.mobile.core.network

import com.phimbud.mobile.core.platform.utcDateHourKeyFromEpochMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserHeadersAndCookieTest {

    @Test
    fun testSameOriginGetHeaders() {
        val headers = BrowserHeaders.computeHeaders(
            urlHost = "phimbud.com",
            method = "GET",
            hasHeader = { false }
        )
        assertEquals(BrowserHeaders.USER_AGENT, headers["User-Agent"])
        assertEquals(BrowserHeaders.SEC_CH_UA, headers["sec-ch-ua"])
        assertEquals("?0", headers["sec-ch-ua-mobile"])
        assertEquals("\"Windows\"", headers["sec-ch-ua-platform"])
        assertEquals("same-origin", headers["Sec-Fetch-Site"])
        assertEquals("cors", headers["Sec-Fetch-Mode"])
        assertEquals("empty", headers["Sec-Fetch-Dest"])
        assertEquals("https://phimbud.com/", headers["Referer"])
        assertNull(headers["Origin"])
    }

    @Test
    fun testCrossOriginHlsGetIncludesOriginAndCrossSite() {
        val headers = BrowserHeaders.computeHeaders(
            urlHost = "cdn-edge.example.net",
            method = "GET",
            hasHeader = { false }
        )
        assertEquals("cross-site", headers["Sec-Fetch-Site"])
        assertEquals("https://phimbud.com", headers["Origin"])
        assertEquals("https://phimbud.com/", headers["Referer"])
    }

    @Test
    fun testParseSetCookiePreservesBase64PaddingAndMaxAge() {
        val nowMs = 1_700_000_000_000L
        val raw = "session=eyJhbGciOiJIUzI1NiJ9.payload==; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=86400"
        val parsed = PersistentCookieStore.parseSetCookie("phimbud.com", raw, nowMs)
        assertNotNull(parsed)
        assertEquals("session", parsed.name)
        assertEquals("eyJhbGciOiJIUzI1NiJ9.payload==", parsed.value)
        assertEquals("phimbud.com", parsed.domain)
        assertEquals("/", parsed.path)
        assertEquals(nowMs + 86_400_000L, parsed.expiresAt)
    }

    @Test
    fun testUtcDateHourKeyFormatting() {
        // 2024-02-29T07:15:00Z = 1709190900000 ms (leap year check)
        assertEquals("2024-02-29-7.js", utcDateHourKeyFromEpochMillis(1709190900000L))
    }
}
