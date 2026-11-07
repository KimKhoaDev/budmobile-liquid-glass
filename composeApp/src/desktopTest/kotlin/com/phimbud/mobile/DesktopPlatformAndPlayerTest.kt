package com.phimbud.mobile

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import com.phimbud.mobile.core.network.BrowserHeaders
import com.phimbud.mobile.core.network.PersistentCookieStore
import com.phimbud.mobile.core.network.PlatformHttpEngine
import com.phimbud.mobile.core.network.StoredCookie
import com.phimbud.mobile.core.platform.CatalogIndexCache
import com.phimbud.mobile.core.platform.DesktopKeyDispatcher
import com.phimbud.mobile.core.platform.DesktopPlayerKey
import com.phimbud.mobile.core.platform.KeyValueStore
import com.phimbud.mobile.core.platform.PlatformContext
import com.phimbud.mobile.core.platform.SubtitleCache
import com.phimbud.mobile.player.DesktopFfmpegPlayer
import com.phimbud.mobile.player.DesktopHlsProxyServer
import com.phimbud.mobile.player.HlsManifestRewriter
import com.phimbud.mobile.player.SmartSubtitleCalculator
import com.phimbud.mobile.player.SubtitleBgStyle
import com.phimbud.mobile.player.SubtitleColorPreset
import com.phimbud.mobile.player.SubtitlePreferences
import com.phimbud.mobile.player.SubtitlePreferencesStore
import com.phimbud.mobile.player.VideoResizeMode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import java.io.File
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPlatformAndPlayerTest {

    @Test
    fun desktopStoragePersistsAtomicallyAcrossReloads() {
        val tempRoot = Files.createTempDirectory("phimbud_desktop_test").toFile()
        try {
            val ctx = PlatformContext(tempRoot)
            val store1 = KeyValueStore(ctx, "auth_cookies")
            store1.putString("session_id", "abc_123")
            store1.putString("lang", "vi")

            // Reload from disk in a fresh KeyValueStore instance
            val store2 = KeyValueStore(ctx, "auth_cookies")
            assertEquals("abc_123", store2.getString("session_id"))
            assertEquals("vi", store2.getString("lang"))

            store2.remove("lang")
            val store3 = KeyValueStore(ctx, "auth_cookies")
            assertNull(store3.getString("lang"))
            assertEquals("abc_123", store3.getString("session_id"))

            val subCache = SubtitleCache(ctx)
            val cachedSub = subCache.write("ep1_vi.srt", "1\n00:00:01,000 --> 00:00:03,000\nXin chào Windows")
            assertTrue(cachedSub.content.contains("Xin chào Windows"))
            assertEquals(cachedSub.content, subCache.readIfCached("ep1_vi.srt")?.content)

            val indexCache = CatalogIndexCache(ctx)
            indexCache.write("2026-11-07-9.js", "[\"movie_1\",\"movie_2\"]")
            assertEquals("[\"movie_1\",\"movie_2\"]", indexCache.readExact("2026-11-07-9.js"))
            assertEquals("[\"movie_1\",\"movie_2\"]", indexCache.readLatestFallback())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun desktopHttpEngineInitializesStealthAndIsolatedPublicClients() {
        val tempRoot = Files.createTempDirectory("phimbud_http_test").toFile()
        try {
            val ctx = PlatformContext(tempRoot)
            val cookieStore = PersistentCookieStore(ctx)
            cookieStore.saveCookie(
                StoredCookie(
                    name = "pb_sess",
                    value = "secret_token",
                    domain = "phimbud.com",
                    path = "/",
                    expiresAt = System.currentTimeMillis() + 3_600_000L
                )
            )
            val engine = PlatformHttpEngine(ctx, cookieStore)
            assertNotNull(engine.httpClient)
            assertNotNull(engine.noRedirectHttpClient)
            assertNotNull(engine.publicHttpClient)

            val getHeaders = BrowserHeaders.computeHeaders(
                urlHost = "phimbud.com",
                method = "GET",
                hasHeader = { false }
            )
            assertEquals("\"Windows\"", getHeaders["sec-ch-ua-platform"])
            assertEquals("same-origin", getHeaders["Sec-Fetch-Site"])
            assertNull(getHeaders["Origin"])

            val postHeaders = BrowserHeaders.computeHeaders(
                urlHost = "phimbud.com",
                method = "POST",
                hasHeader = { false }
            )
            assertEquals("https://phimbud.com", postHeaders["Origin"])

            val cdnHeaders = BrowserHeaders.computeHeaders(
                urlHost = "cdn.stream-node.net",
                method = "GET",
                hasHeader = { false }
            )
            assertEquals("cross-site", cdnHeaders["Sec-Fetch-Site"])
            assertEquals("https://phimbud.com", cdnHeaders["Origin"])
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun desktopHlsProxyStartsOnLoopbackAndFfmpegNativesLoad() = runBlocking {
        // 1. Verify bundled FFmpeg 7.1 native libraries load cleanly on Windows x64
        val avUtilVersion = avutil.avutil_version()
        assertTrue(avUtilVersion > 0, "Expected FFmpeg avutil native library to load")

        // 2. Verify loopback proxy binds strictly to 127.0.0.1 ephemeral port
        val tempRoot = Files.createTempDirectory("phimbud_proxy_test").toFile()
        try {
            val ctx = PlatformContext(tempRoot)
            val engine = PlatformHttpEngine(ctx, PersistentCookieStore(ctx))
            val proxy = DesktopHlsProxyServer(engine.httpClient)
            try {
                val baseUrl = proxy.ensureStarted()
                assertTrue(baseUrl.startsWith("http://127.0.0.1:"), "Unexpected proxy base URL: $baseUrl")
                val localM3u8 = HlsManifestRewriter.buildLocalPlaylistUrl(
                    baseUrl,
                    "https://cdn.example.com/hls/master.m3u8"
                )
                assertTrue(localM3u8.startsWith("$baseUrl/playlist.m3u8?u="))
            } finally {
                proxy.stop()
            }
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun desktopFfmpegPlayerPlaysEncryptedObfuscatedHlsEndToEnd() = runBlocking {
        val tempRoot = Files.createTempDirectory("phimbud_e2e_hls_test").toFile()
        val rawTsFile = File(tempRoot, "sample.ts")

        // 1. Synthesize a valid 12-frame MPEG-TS H.264 clip (320x180 @ 15fps)
        val recorder = FFmpegFrameRecorder(rawTsFile, 320, 180, 0)
        recorder.format = "mpegts"
        recorder.videoCodec = avcodec.AV_CODEC_ID_MPEG2VIDEO
        recorder.frameRate = 15.0
        recorder.pixelFormat = avutil.AV_PIX_FMT_YUV420P
        recorder.start()
        val synthFrame = Frame(320, 180, Frame.DEPTH_UBYTE, 3)
        val imgBuf = synthFrame.image[0] as ByteBuffer
        for (i in 0 until imgBuf.capacity()) {
            imgBuf.put(i, ((i * 7) and 0xFF).toByte())
        }
        repeat(12) {
            recorder.record(synthFrame)
        }
        recorder.stop()
        recorder.release()

        val plainTsBytes = rawTsFile.readBytes()
        assertTrue(plainTsBytes.isNotEmpty(), "Expected synthesized MPEG-TS bytes")

        // 2. Encrypt MPEG-TS segment with AES-128-CBC (#EXT-X-KEY:METHOD=AES-128)
        val aesKey = ByteArray(16) { idx -> (0x21 + idx).toByte() }
        val aesIv = ByteArray(16).apply { this[15] = 1 }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(aesIv))
        val encryptedTsBytes = cipher.doFinal(plainTsBytes)

        // 3. Use real ROT-6 + Base64 obfuscated filenames & disguised image/jpeg Content-Type
        val obfuscatedKey = "Hta3TgC2HAHbSgOsHgCtSdDcIAY1TdC5SQYtTgHbIXUnIXG2H2ZdHdfcHNG5HQHfIXU3HXUrTQHeG2DcINGqTQYqTgG1HdCsGAZdTADdTdHcSQO3TNU2INYtHgCrSdZfTdw2SNG5Hq=="
        val decodedKeyName = "787fb64cabe26b3b2b8a5f29aa3fca80-8367ac69b5395ce807401ecd3bb930ea0fc56220acdbcf3bae7e069136b1b1ef76a397.key"
        val obfuscatedSeg = "HdKtHXGrHdKtIXStHdKtING4HdStGXGqHdCtGdGqGtKnTdwtHtPdHteqTXVcS2PcIXC1HAY4HgO4GdYqTdZcIXa1TQGqTNwqSNe5HXTgGdG1S2HfINO0G2PfGdVgTATfSdbeHAPbSdVbGAGtGgK2GdC1TXLeTgG5GdPbSdY1HXO1TNTdTAPcHNa5GtZeHte4TQLfIATeHgC1TNw0Sdw5TNYrIQS5HNHdINTfHdY0TNbb"
        val decodedSegName = "64343164386364393866303062323034-f7375c790d0bceb8254a86e8210f1b885ec0e70a9946f235cce9543ee20fdfeb8d4eab0a0c32d6225d4dfc925ab15455e6cdeb58931d798ede8fd6b5e74b79e119f953c96e614e8a.jpg"

        val upstreamServer = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                get("/stream/1080p/index.m3u8") {
                    val m3u8 = """
                        #EXTM3U
                        #EXT-X-VERSION:3
                        #EXT-X-TARGETDURATION:2
                        #EXT-X-KEY:METHOD=AES-128,URI="$obfuscatedKey.key",IV=0x00000000000000000000000000000001
                        #EXTINF:0.800,
                        $obfuscatedSeg.jpg
                        #EXT-X-ENDLIST
                    """.trimIndent()
                    call.respondText(
                        text = m3u8,
                        contentType = ContentType("application", "vnd.apple.mpegurl"),
                        status = HttpStatusCode.OK
                    )
                }
                get("/stream/1080p/$decodedKeyName") {
                    call.respondBytes(aesKey, ContentType.Application.OctetStream, HttpStatusCode.OK)
                }
                get("/stream/1080p/$decodedSegName") {
                    // Disguise MPEG-TS segment as image/jpeg just like real anti-leech CDNs
                    call.respondBytes(encryptedTsBytes, ContentType.Image.JPEG, HttpStatusCode.OK)
                }
            }
        }
        upstreamServer.start(wait = false)

        val ctx = PlatformContext(tempRoot)
        val engine = PlatformHttpEngine(ctx, PersistentCookieStore(ctx))
        val proxy = DesktopHlsProxyServer(engine.httpClient)

        val frameCount = AtomicInteger(0)
        val latestBitmap = AtomicReference<ImageBitmap?>(null)
        val playbackErrorRef = AtomicReference<String?>(null)
        val endedLatch = CountDownLatch(1)

        val player = DesktopFfmpegPlayer(
            onFrameRendered = { bmp ->
                latestBitmap.set(bmp)
                frameCount.incrementAndGet()
            },
            onStateChanged = { _, _ -> },
            onDurationKnown = { _ -> },
            onPositionUpdated = { _ -> },
            onPlaybackEnded = {
                endedLatch.countDown()
            },
            onPlaybackError = { err ->
                playbackErrorRef.set(err)
                endedLatch.countDown()
            }
        )

        try {
            val upstreamPort = upstreamServer.engine.resolvedConnectors().first().port
            val upstreamM3u8 = "http://127.0.0.1:$upstreamPort/stream/1080p/index.m3u8"
            val proxyBase = proxy.ensureStarted()
            val localPlaylistUrl = HlsManifestRewriter.buildLocalPlaylistUrl(proxyBase, upstreamM3u8)

            player.openStream(
                localPlaylistUrl = localPlaylistUrl,
                initialResumeMs = 0L,
                autoPlay = true
            )

            val finished = endedLatch.await(12, TimeUnit.SECONDS)
            assertNull(playbackErrorRef.get(), "Unexpected playback error: ${playbackErrorRef.get()}")
            assertTrue(finished, "Expected HLS playback to reach onPlaybackEnded within timeout")
            assertTrue(frameCount.get() > 0, "Expected decoded video frames > 0, got ${frameCount.get()}")
            val bmp = latestBitmap.get()
            assertNotNull(bmp)
            assertEquals(320, bmp.width)
            assertEquals(180, bmp.height)
        } finally {
            player.release()
            proxy.stop()
            upstreamServer.stop(50, 200)
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun desktopKeyDispatcherHandlesSpaceAndArrowsWithoutFocusLossOrRepeatFlicker() {
        val dispatcher = DesktopKeyDispatcher()
        val received = ArrayList<DesktopPlayerKey>()

        // When no player entry is active, Space & Arrow keys are not intercepted (e.g. SearchScreen TextField)
        assertTrue(!dispatcher.dispatchRawKey(Key.Spacebar, isKeyDown = true, nowMs = 1_000L))
        assertTrue(!dispatcher.dispatchRawKey(Key.DirectionRight, isKeyDown = true, nowMs = 1_000L))

        val entry = DesktopKeyDispatcher.Entry(enabled = true) { key ->
            received.add(key)
        }
        dispatcher.register(entry)

        // Holding Spacebar triggers SPACE once on initial KeyDown and ignores OS auto-repeats until KeyUp
        assertTrue(dispatcher.dispatchRawKey(Key.Spacebar, isKeyDown = true, nowMs = 1_100L))
        assertTrue(dispatcher.dispatchRawKey(Key.Spacebar, isKeyDown = true, nowMs = 1_140L))
        assertTrue(dispatcher.dispatchRawKey(Key.Spacebar, isKeyDown = true, nowMs = 1_180L))
        assertEquals(listOf(DesktopPlayerKey.SPACE), received)

        // KeyUp is also consumed so focused buttons underneath never misfire, then Space can trigger again
        assertTrue(dispatcher.dispatchRawKey(Key.Spacebar, isKeyDown = false, nowMs = 1_220L))
        assertTrue(dispatcher.dispatchRawKey(Key.Spacebar, isKeyDown = true, nowMs = 1_300L))
        assertTrue(dispatcher.dispatchRawKey(Key.Spacebar, isKeyDown = false, nowMs = 1_340L))
        assertEquals(listOf(DesktopPlayerKey.SPACE, DesktopPlayerKey.SPACE), received)

        // Arrow keys seek immediately and throttle ultra-fast OS key-repeat bursts cleanly
        received.clear()
        assertTrue(dispatcher.dispatchRawKey(Key.DirectionRight, isKeyDown = true, nowMs = 2_000L))
        assertTrue(dispatcher.dispatchRawKey(Key.DirectionRight, isKeyDown = true, nowMs = 2_030L)) // throttled
        assertTrue(dispatcher.dispatchRawKey(Key.DirectionRight, isKeyDown = true, nowMs = 2_090L)) // allowed (>=75ms)
        assertTrue(dispatcher.dispatchRawKey(Key.DirectionLeft, isKeyDown = true, nowMs = 2_200L))
        assertEquals(
            listOf(DesktopPlayerKey.ARROW_RIGHT, DesktopPlayerKey.ARROW_RIGHT, DesktopPlayerKey.ARROW_LEFT),
            received
        )

        // Modifier combinations (e.g. Ctrl+Left) are ignored
        assertTrue(!dispatcher.dispatchRawKey(Key.DirectionLeft, isKeyDown = true, hasModifier = true, nowMs = 2_500L))
        dispatcher.unregister(entry)
    }

    @Test
    fun smartSubtitleLayoutLuminanceAndPreferencesPersistCleanly() {
        val tempRoot = Files.createTempDirectory("phimbud_sub_prefs_test").toFile()
        try {
            val store = SubtitlePreferencesStore(PlatformContext(tempRoot))
            assertEquals(SubtitlePreferences(), store.load())

            val custom = SubtitlePreferences(
                fontScale = 1.2f,
                bottomMarginPercent = 18.5f,
                bgStyle = SubtitleBgStyle.NETFLIX_OUTLINE,
                colorPreset = SubtitleColorPreset.WARM_YELLOW
            )
            store.save(custom)
            assertEquals(custom, SubtitlePreferencesStore(PlatformContext(tempRoot)).load())
        } finally {
            tempRoot.deleteRecursively()
        }

        // Verify DPI-invariant physical font sizing: 1080p @ 100% (1920x1080dp) vs 1080p @ 125% (1536x864dp)
        val defaultPrefs = SubtitlePreferences()
        val m100 = SmartSubtitleCalculator.computeMetrics(
            containerWidthDp = 1920f,
            containerHeightDp = 1080f,
            videoWidthPx = 1920,
            videoHeightPx = 1080,
            resizeMode = VideoResizeMode.FIT,
            prefs = defaultPrefs,
            controlsVisible = false
        )
        val m125 = SmartSubtitleCalculator.computeMetrics(
            containerWidthDp = 1536f,
            containerHeightDp = 864f,
            videoWidthPx = 1920,
            videoHeightPx = 1080,
            resizeMode = VideoResizeMode.FIT,
            prefs = defaultPrefs,
            controlsVisible = false
        )
        assertTrue(m100.fontSizeSp > 36f, "Expected ~40sp on 1080p fullscreen, got ${m100.fontSizeSp}")
        assertEquals(m100.fontSizeSp, m125.fontSizeSp * 1.25f, 0.2f)
        assertTrue(m100.bottomPaddingDp > 110f, "Expected Cinema safe-zone (~119dp) instead of 30dp, got ${m100.bottomPaddingDp}")

        // Portrait floating window (430x860dp) hugs the rendered 16:9 video instead of dropping to bottom edge
        val portraitMetrics = SmartSubtitleCalculator.computeMetrics(
            containerWidthDp = 430f,
            containerHeightDp = 860f,
            videoWidthPx = 1920,
            videoHeightPx = 1080,
            resizeMode = VideoResizeMode.FIT,
            prefs = defaultPrefs,
            controlsVisible = false
        )
        assertTrue(portraitMetrics.bottomPaddingDp > 300f, "Expected subtitle anchored inside centered video frame")

        // Verify bottom-band luminance sampler on dark vs bright BGRA buffers
        val darkFrame = ByteArray(64 * 64 * 4) { idx -> if (idx % 4 == 3) 0xFF.toByte() else 12.toByte() }
        val brightFrame = ByteArray(64 * 64 * 4) { idx -> if (idx % 4 == 3) 0xFF.toByte() else 240.toByte() }
        val darkLum = DesktopFfmpegPlayer.sampleBottomBandLuminance(darkFrame, 64, 64)
        val brightLum = DesktopFfmpegPlayer.sampleBottomBandLuminance(brightFrame, 64, 64)
        assertTrue(darkLum < 0.10f)
        assertTrue(brightLum > 0.88f)
        assertTrue(
            SmartSubtitleCalculator.computeBackdropAlpha(SubtitleBgStyle.SMART_ADAPTIVE, brightLum) >
                SmartSubtitleCalculator.computeBackdropAlpha(SubtitleBgStyle.SMART_ADAPTIVE, darkLum) + 0.30f
        )
    }

    @Test
    fun desktopFfmpegPlayerNormalizesMpegTsStartTimeSoSubtitlesNeverLeadAudio() = runBlocking {
        val tempRoot = Files.createTempDirectory("phimbud_av_sync_test").toFile()
        val rawTsFile = File(tempRoot, "shifted_av.ts")

        // 1. Synthesize a 0.8s MPEG-TS A/V segment with a +6.5s initial PTS offset (just like HLS VOD packagers)
        val recorder = FFmpegFrameRecorder(rawTsFile, 320, 180, 2)
        recorder.format = "mpegts"
        recorder.setOption("output_ts_offset", "6.5")
        recorder.videoCodec = avcodec.AV_CODEC_ID_MPEG2VIDEO
        recorder.audioCodec = avcodec.AV_CODEC_ID_AAC
        recorder.frameRate = 15.0
        recorder.sampleRate = 48_000
        recorder.audioBitrate = 128_000
        recorder.pixelFormat = avutil.AV_PIX_FMT_YUV420P
        recorder.start()

        val synthFrame = Frame(320, 180, Frame.DEPTH_UBYTE, 3)
        val imgBuf = synthFrame.image[0] as ByteBuffer
        for (i in 0 until imgBuf.capacity()) {
            imgBuf.put(i, ((i * 5) and 0xFF).toByte())
        }
        // 48000 Hz / 15 fps = 3200 samples per video frame * 2 channels = 6400 shorts
        val pcmPerFrame = ShortArray(6400) { idx -> ((idx * 17) % 1200).toShort() }
        repeat(12) {
            recorder.record(synthFrame)
            recorder.recordSamples(48_000, 2, ShortBuffer.wrap(pcmPerFrame))
        }
        recorder.stop()
        recorder.release()

        val tsBytes = rawTsFile.readBytes()
        assertTrue(tsBytes.isNotEmpty(), "Expected synthesized A/V MPEG-TS segment")

        val upstreamServer = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                get("/vod/index.m3u8") {
                    val m3u8 = """
                        #EXTM3U
                        #EXT-X-VERSION:3
                        #EXT-X-TARGETDURATION:2
                        #EXTINF:0.800,
                        seg0.ts
                        #EXT-X-ENDLIST
                    """.trimIndent()
                    call.respondText(
                        text = m3u8,
                        contentType = ContentType("application", "vnd.apple.mpegurl"),
                        status = HttpStatusCode.OK
                    )
                }
                get("/vod/seg0.ts") {
                    call.respondBytes(tsBytes, ContentType.Video.MPEG, HttpStatusCode.OK)
                }
            }
        }
        upstreamServer.start(wait = false)

        val ctx = PlatformContext(tempRoot)
        val engine = PlatformHttpEngine(ctx, PersistentCookieStore(ctx))
        val proxy = DesktopHlsProxyServer(engine.httpClient)
        val reportedPositions = Collections.synchronizedList(ArrayList<Long>())
        val playbackErrorRef = AtomicReference<String?>(null)
        val endedLatch = CountDownLatch(1)

        val player = DesktopFfmpegPlayer(
            onFrameRendered = { _ -> },
            onStateChanged = { _, _ -> },
            onDurationKnown = { _ -> },
            onPositionUpdated = { posMs ->
                reportedPositions.add(posMs)
            },
            onPlaybackEnded = {
                endedLatch.countDown()
            },
            onPlaybackError = { err ->
                playbackErrorRef.set(err)
                endedLatch.countDown()
            }
        )

        try {
            val upstreamPort = upstreamServer.engine.resolvedConnectors().first().port
            val proxyBase = proxy.ensureStarted()
            val localPlaylistUrl = HlsManifestRewriter.buildLocalPlaylistUrl(
                proxyBase,
                "http://127.0.0.1:$upstreamPort/vod/index.m3u8"
            )

            // Prove raw FFmpegFrameGrabber reports > 6.5s un-normalized PTS and resolveStreamStartOffsetUs catches it
            val probe = FFmpegFrameGrabber(localPlaylistUrl)
            try {
                probe.start()
                val detectedOffsetUs = DesktopFfmpegPlayer.resolveStreamStartOffsetUs(probe)
                val firstRawFrame = probe.grab()
                assertNotNull(firstRawFrame)
                assertTrue(
                    firstRawFrame.timestamp >= 6_400_000L,
                    "Expected raw JavaCV frame.timestamp >= 6.4s, got ${firstRawFrame.timestamp}"
                )
                assertTrue(
                    detectedOffsetUs >= 6_400_000L,
                    "Expected resolveStreamStartOffsetUs >= 6.4s, got $detectedOffsetUs"
                )
            } finally {
                runCatching { probe.stop() }
                runCatching { probe.release() }
            }

            // Now verify DesktopFfmpegPlayer normalizes playback clock to 0ms..1300ms (never jumping ahead to 6500ms+)
            player.openStream(
                localPlaylistUrl = localPlaylistUrl,
                initialResumeMs = 0L,
                autoPlay = true
            )

            val finished = endedLatch.await(12, TimeUnit.SECONDS)
            assertNull(playbackErrorRef.get(), "Unexpected playback error: ${playbackErrorRef.get()}")
            assertTrue(finished, "Expected HLS A/V stream to finish cleanly")
            assertTrue(reportedPositions.isNotEmpty(), "Expected position updates during playback")
            val firstPosMs = reportedPositions.first()
            val maxPosMs = reportedPositions.maxOrNull() ?: 0L
            assertTrue(
                firstPosMs < 400L,
                "Expected normalized initial playback position < 400ms (near 0ms), but got ${firstPosMs}ms"
            )
            assertTrue(
                maxPosMs < 2_000L,
                "Expected normalized max position < 2000ms for 0.8s clip, but got ${maxPosMs}ms (all=$reportedPositions)"
            )
        } finally {
            player.release()
            proxy.stop()
            upstreamServer.stop(50, 200)
            tempRoot.deleteRecursively()
        }
    }
}
