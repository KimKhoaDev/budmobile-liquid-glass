package com.phimbud.mobile.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UrlCipherTest {

    @Test
    fun testRot6() {
        val input = "Hello"
        val rotated = UrlCipher.rot(input, 6)
        assertEquals("Nkrru", rotated)
    }

    @Test
    fun testDecodeKeyFilename() {
        val obfuscatedKey = "Hta3TgC2HAHbSgOsHgCtSdDcIAY1TdC5SQYtTgHbIXUnIXG2H2ZdHdfcHNG5HQHfIXU3HXUrTQHeG2DcINGqTQYqTgG1HdCsGAZdTADdTdHcSQO3TNU2INYtHgCrSdZfTdw2SNG5Hq=="
        val decoded = UrlCipher.decodeSegmentFilename(obfuscatedKey)
        assertNotNull(decoded)
        assertEquals(
            "787fb64cabe26b3b2b8a5f29aa3fca80-8367ac69b5395ce807401ecd3bb930ea0fc56220acdbcf3bae7e069136b1b1ef76a397",
            decoded
        )
    }

    @Test
    fun testDecodeSegmentFilename() {
        val obfuscatedSeg = "HdKtHXGrHdKtIXStHdKtING4HdStGXGqHdCtGdGqGtKnTdwtHtPdHteqTXVcS2PcIXC1HAY4HgO4GdYqTdZcIXa1TQGqTNwqSNe5HXTgGdG1S2HfINO0G2PfGdVgTATfSdbeHAPbSdVbGAGtGgK2GdC1TXLeTgG5GdPbSdY1HXO1TNTdTAPcHNa5GtZeHte4TQLfIATeHgC1TNw0Sdw5TNYrIQS5HNHdINTfHdY0TNbb"
        val decoded = UrlCipher.decodeSegmentFilename(obfuscatedSeg)
        assertNotNull(decoded)
        assertEquals(
            "64343164386364393866303062323034-f7375c790d0bceb8254a86e8210f1b885ec0e70a9946f235cce9543ee20fdfeb8d4eab0a0c32d6225d4dfc925ab15455e6cdeb58931d798ede8fd6b5e74b79e119f953c96e614e8a",
            decoded
        )
    }

    @Test
    fun testSkipUnobfuscated() {
        val plain = "normal_video_file"
        val decoded = UrlCipher.decodeSegmentFilename(plain)
        assertNull(decoded)
    }

    @Test
    fun testBase64DecodeHandlesEmbeddedWhitespaceAndUnpaddedTail() {
        val wrapped = "  SGVs\r\nbG8g\nV29y\tbGQh  "
        assertEquals("Hello World!", UrlCipher.base64Decode(wrapped).decodeToString())
        assertEquals("PhimBud", UrlCipher.base64Decode("UGhpbUJ1ZA").decodeToString())
        assertEquals(0, UrlCipher.base64Decode("Invalid@Char==").size)
        assertNull(UrlCipher.decodeSegmentFilename("Invalid@Char=="))
    }
}
