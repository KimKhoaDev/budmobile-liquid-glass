package com.phimbud.mobile.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HlsManifestRewriterTest {

    @Test
    fun testRewriteMasterPlaylistRoutesVariantsToLocalProxy() {
        val masterUrl = "https://cdn.example.net/hls/movie123/master.m3u8"
        val masterContent = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1920x1080
            1080p/index.m3u8
        """.trimIndent()

        val rewritten = HlsManifestRewriter.rewritePlaylist(
            playlistUrl = masterUrl,
            playlistContent = masterContent,
            proxyBaseUrl = "http://127.0.0.1:51234"
        )
        val lines = rewritten.lines()
        val variantLine = lines.last()
        assertTrue(variantLine.startsWith("http://127.0.0.1:51234/playlist.m3u8?u="))
        val encoded = variantLine.substringAfter("?u=")
        assertEquals(
            "https://cdn.example.net/hls/movie123/1080p/index.m3u8",
            HlsManifestRewriter.decodeProxyTarget(encoded)
        )
    }

    @Test
    fun testRewriteMediaPlaylistDecodesObfuscatedKeyAndSegmentFilenames() {
        val obfuscatedKey = "Hta3TgC2HAHbSgOsHgCtSdDcIAY1TdC5SQYtTgHbIXUnIXG2H2ZdHdfcHNG5HQHfIXU3HXUrTQHeG2DcINGqTQYqTgG1HdCsGAZdTADdTdHcSQO3TNU2INYtHgCrSdZfTdw2SNG5Hq=="
        val obfuscatedSeg = "HdKtHXGrHdKtIXStHdKtING4HdStGXGqHdCtGdGqGtKnTdwtHtPdHteqTXVcS2PcIXC1HAY4HgO4GdYqTdZcIXa1TQGqTNwqSNe5HXTgGdG1S2HfINO0G2PfGdVgTATfSdbeHAPbSdVbGAGtGgK2GdC1TXLeTgG5GdPbSdY1HXO1TNTdTAPcHNa5GtZeHte4TQLfIATeHgC1TNw0Sdw5TNYrIQS5HNHdINTfHdY0TNbb"

        val mediaUrl = "https://cdn.example.net/stream/1080p/index.m3u8"
        val mediaContent = """
            #EXTM3U
            #EXT-X-TARGETDURATION:4
            #EXT-X-KEY:METHOD=AES-128,URI="$obfuscatedKey.key",IV=0x00000000000000000000000000000001
            #EXTINF:4.000,
            $obfuscatedSeg.jpg
            #EXT-X-ENDLIST
        """.trimIndent()

        val rewritten = HlsManifestRewriter.rewritePlaylist(
            playlistUrl = mediaUrl,
            playlistContent = mediaContent,
            proxyBaseUrl = "http://127.0.0.1:51234"
        )

        val keyLine = rewritten.lines().first { it.startsWith("#EXT-X-KEY:") }
        val keyProxyUri = Regex("""URI="([^"]+)"""").find(keyLine)!!.groupValues[1]
        assertTrue(keyProxyUri.startsWith("http://127.0.0.1:51234/key.bin?u="))
        val decodedKeyTarget = HlsManifestRewriter.decodeProxyTarget(keyProxyUri.substringAfter("?u="))
        assertEquals(
            "https://cdn.example.net/stream/1080p/787fb64cabe26b3b2b8a5f29aa3fca80-8367ac69b5395ce807401ecd3bb930ea0fc56220acdbcf3bae7e069136b1b1ef76a397.key",
            decodedKeyTarget
        )

        val segLine = rewritten.lines().first { it.startsWith("http://127.0.0.1:51234/segment.ts?u=") }
        val decodedSegTarget = HlsManifestRewriter.decodeProxyTarget(segLine.substringAfter("?u="))
        assertEquals(
            "https://cdn.example.net/stream/1080p/64343164386364393866303062323034-f7375c790d0bceb8254a86e8210f1b885ec0e70a9946f235cce9543ee20fdfeb8d4eab0a0c32d6225d4dfc925ab15455e6cdeb58931d798ede8fd6b5e74b79e119f953c96e614e8a.jpg",
            decodedSegTarget
        )
    }
}
