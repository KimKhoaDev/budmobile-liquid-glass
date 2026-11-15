package com.phimbud.mobile

import com.phimbud.mobile.core.network.PersistentCookieStore
import com.phimbud.mobile.core.network.PlatformHttpEngine
import com.phimbud.mobile.core.platform.PlatformContext
import com.phimbud.mobile.player.DesktopFfmpegPlayer
import com.phimbud.mobile.player.DesktopHlsProxyServer
import com.phimbud.mobile.player.HlsManifestRewriter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
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
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reproduces the Windows "flash back ~1s after +10s seek" stutter on a synthetic multi-segment HLS VOD
 * (1s GOP, 4s segments, A/V) served over a fast and a throttled (slow) upstream.
 */
class DesktopSeekStressTest {

    private sealed class Event {
        class Seek(val atNs: Long, val targetMs: Long) : Event()
        class Frame(val atNs: Long, val ptsUs: Long) : Event()
    }

    private class Harness(
        val root: File,
        val upstream: io.ktor.server.engine.EmbeddedServer<*, *>,
        val proxy: DesktopHlsProxyServer,
        val player: DesktopFfmpegPlayer,
        val events: MutableList<Event>,
        val error: AtomicReference<String?>,
        val upstreamSegmentRequests: java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>,
        val playlistUrl: String
    ) {
        fun close() {
            player.release()
            proxy.stop()
            upstream.stop(50, 200)
            root.deleteRecursively()
        }
    }

    private fun synthesizeHls(dir: File, seconds: Int) {
        val fps = 15
        val recorder = FFmpegFrameRecorder(File(dir, "index.m3u8"), 320, 180, 2)
        recorder.format = "hls"
        recorder.setOption("hls_time", "4")
        recorder.setOption("hls_list_size", "0")
        recorder.setOption("hls_playlist_type", "vod")
        recorder.setOption("hls_segment_filename", File(dir, "seg%03d.ts").absolutePath)
        recorder.videoCodec = avcodec.AV_CODEC_ID_MPEG2VIDEO
        recorder.audioCodec = avcodec.AV_CODEC_ID_AAC
        recorder.frameRate = fps.toDouble()
        recorder.gopSize = fps
        recorder.sampleRate = 48_000
        recorder.audioBitrate = 64_000
        recorder.videoBitrate = 200_000
        recorder.pixelFormat = avutil.AV_PIX_FMT_YUV420P
        recorder.start()
        val frame = Frame(320, 180, Frame.DEPTH_UBYTE, 3)
        val img = frame.image[0] as ByteBuffer
        val pcm = ShortArray(3200 * 2) { idx -> ((idx * 13) % 900).toShort() }
        repeat(seconds * fps) { n ->
            for (i in 0 until img.capacity()) img.put(i, ((i * 3 + n * 11) and 0xFF).toByte())
            recorder.record(frame)
            recorder.recordSamples(48_000, 2, ShortBuffer.wrap(pcm))
        }
        recorder.stop()
        recorder.release()
    }

    private fun createHarness(slow: Boolean, seconds: Int = 40): Harness {
        val root = Files.createTempDirectory("phimbud_seek_stress").toFile()
        synthesizeHls(root, seconds)
        println("SEEKTEST segments: " + root.listFiles { f -> f.name.endsWith(".ts") }!!.sortedBy { it.name }.joinToString { "${it.name}=${it.length() / 1024}KB" })
        val segmentRequests = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()

        val upstream = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                get("/vod/index.m3u8") {
                    call.respondText(
                        File(root, "index.m3u8").readText(),
                        ContentType("application", "vnd.apple.mpegurl"),
                        HttpStatusCode.OK
                    )
                }
                get("/vod/{name}") {
                    val name = call.parameters["name"].orEmpty()
                    val f = File(root, name)
                    if (!f.isFile) {
                        call.respondText("nf", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    segmentRequests.computeIfAbsent(name) { AtomicInteger(0) }.incrementAndGet()
                    val bytes = f.readBytes()
                    call.respondBytesWriter(ContentType.Video.MPEG, HttpStatusCode.OK, bytes.size.toLong()) {
                        if (!slow) {
                            writeFully(bytes, 0, bytes.size)
                        } else {
                            var off = 0
                            while (off < bytes.size) {
                                val n = minOf(8 * 1024, bytes.size - off)
                                writeFully(bytes, off, off + n)
                                off += n
                                flush()
                                delay(25L) // ~320 KB/s
                            }
                        }
                    }
                }
            }
        }
        upstream.start(wait = false)

        val ctx = PlatformContext(root)
        val engine = PlatformHttpEngine(ctx, PersistentCookieStore(ctx))
        val proxy = DesktopHlsProxyServer(engine.httpClient)
        val events = Collections.synchronizedList(ArrayList<Event>())
        val error = AtomicReference<String?>(null)
        val player = DesktopFfmpegPlayer(
            onFrameRendered = { },
            onStateChanged = { _, _ -> },
            onDurationKnown = { },
            onPositionUpdated = { },
            onPlaybackEnded = { },
            onPlaybackError = { error.set(it) },
            onFramePresented = { pts, isPreroll ->
                if (!isPreroll) {
                    events.add(Event.Frame(System.nanoTime(), pts))
                }
            }
        )
        if (slow && System.getenv("SEEKTEST_VERBOSE") != null) {
            avutil.av_log_set_level(avutil.AV_LOG_VERBOSE)
        }
        val port = runBlocking { upstream.engine.resolvedConnectors().first().port }
        val proxyBase = runBlocking { proxy.ensureStarted() }
        val url = HlsManifestRewriter.buildLocalPlaylistUrl(proxyBase, "http://127.0.0.1:$port/vod/index.m3u8")
        return Harness(root, upstream, proxy, player, events, error, segmentRequests, url)
    }

    private fun waitForFrames(h: Harness, minFrames: Int, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (h.events.count { it is Event.Frame } >= minFrames) return true
            Thread.sleep(20)
        }
        return false
    }

    private fun doSeek(h: Harness, targetMs: Long) {
        h.events.add(Event.Seek(System.nanoTime(), targetMs))
        h.player.seekTo(targetMs)
    }

    /** Returns violations: frames presented after a seek whose PTS is earlier than that seek's target. */
    private fun analyze(events: List<Event>, graceMs: Long = 40L, toleranceMs: Long = 70L): List<String> {
        val problems = ArrayList<String>()
        var target = -1L
        var seekNs = 0L
        var lastPts = -1L
        var firstAfterSeekNs = 0L
        var framesIn500ms = 0
        for (e in events.toList()) {
            when (e) {
                is Event.Seek -> {
                    target = e.targetMs; seekNs = e.atNs; lastPts = -1L; firstAfterSeekNs = 0L; framesIn500ms = 0
                }
                is Event.Frame -> {
                    if (target < 0) continue
                    val ptsMs = e.ptsUs / 1000L
                    if ((e.atNs - seekNs) / 1_000_000L < graceMs) continue
                    if (ptsMs < target - toleranceMs) {
                        problems.add("frame pts=${ptsMs}ms < seek target=${target}ms (+${(e.atNs - seekNs) / 1_000_000L}ms after seek)")
                    }
                    if (lastPts >= 0 && ptsMs < lastPts - 1) {
                        problems.add("non-monotonic pts $ptsMs after $lastPts")
                    }
                    lastPts = ptsMs
                    if (firstAfterSeekNs == 0L) firstAfterSeekNs = e.atNs
                    if ((e.atNs - firstAfterSeekNs) < 500_000_000L) {
                        framesIn500ms++
                        if (framesIn500ms > 14) problems.add("burst: >14 frames presented within 500ms after seek")
                    }
                }
            }
        }
        return problems.distinct()
    }

    private fun dump(label: String, events: List<Event>) {
        val sb = StringBuilder("SEEKTEST $label:\n")
        var t0 = 0L
        var frames = 0
        for (e in events.toList()) {
            when (e) {
                is Event.Seek -> { t0 = e.atNs; frames = 0; sb.append("  SEEK -> ${e.targetMs}ms\n") }
                is Event.Frame -> if (frames++ < 12) sb.append("    +${(e.atNs - t0) / 1_000_000}ms pts=${e.ptsUs / 1000}ms\n")
            }
        }
        println(sb)
    }

    private fun runScenario(slow: Boolean, storm: Boolean) {
        val h = createHarness(slow)
        try {
            h.player.openStream(h.playlistUrl, 0L, true)
            val got = waitForFrames(h, 20, 40_000)
            if (!got) dump("NO INITIAL FRAMES slow=$slow", h.events)
            assertTrue(got, "no initial frames (err=${h.error.get()}) count=${h.events.size}")
            Thread.sleep(500)
            if (!storm) {
                doSeek(h, 10_500L)
            } else {
                var t = 6_000L
                repeat(10) {
                    t += 10_000L
                    doSeek(h, t % 36_000L)
                    Thread.sleep(80)
                }
            }
            val before = h.events.size
            val deadline = System.currentTimeMillis() + if (slow) 20_000L else 8_000L
            while (System.currentTimeMillis() < deadline) {
                if (h.events.size - before >= 45) break
                Thread.sleep(50)
            }
            assertNull(h.error.get(), "playback error: ${h.error.get()}")
            assertTrue(h.events.size - before >= 20, "no frames after seek (slow=$slow storm=$storm)")
            val problems = analyze(h.events)
            dump("slow=$slow storm=$storm", h.events)
            assertTrue(problems.isEmpty(), "seek artifacts (slow=$slow storm=$storm): ${problems.take(8)}")
        } finally {
            h.close()
        }
    }

    @Test
    fun fastNetworkSingleSeekHasNoFlashBack() = runScenario(slow = false, storm = false)

    @Test
    fun fastNetworkSeekStormHasNoFlashBack() = runScenario(slow = false, storm = true)

    @Test
    fun slowNetworkSingleSeekHasNoFlashBack() = runScenario(slow = true, storm = false)

    @Test
    fun slowNetworkSeekStormHasNoFlashBack() = runScenario(slow = true, storm = true)

    @Test
    fun pausedSeekRendersTargetFrameImmediately() {
        val h = createHarness(slow = false)
        try {
            h.player.openStream(h.playlistUrl, 0L, true)
            assertTrue(waitForFrames(h, 5, 15_000), "Expected initial frames before pause")
            h.player.pause()
            Thread.sleep(150)

            val beforeCount = h.events.size
            val seekTargetMs = 12_000L
            doSeek(h, seekTargetMs)

            val deadline = System.currentTimeMillis() + 6_000L
            var pausedFramePtsMs = -1L
            while (System.currentTimeMillis() < deadline) {
                val newFrames = h.events.drop(beforeCount).filterIsInstance<Event.Frame>()
                if (newFrames.isNotEmpty()) {
                    pausedFramePtsMs = newFrames.last().ptsUs / 1000L
                    break
                }
                Thread.sleep(25)
            }

            assertNull(h.error.get(), "Unexpected error during paused seek: ${h.error.get()}")
            assertTrue(
                pausedFramePtsMs >= seekTargetMs - 70L,
                "Expected paused seek to render frame >= ${seekTargetMs - 70L}ms immediately while paused, got ${pausedFramePtsMs}ms"
            )
        } finally {
            h.close()
        }
    }

    @Test
    fun slowNetworkLookaheadPrefetchesDeeplyAndStreamsInFlightSegmentWithoutRedownload() {
        val h = createHarness(slow = true, seconds = 40)
        try {
            h.player.openStream(h.playlistUrl, 0L, true)
            assertTrue(waitForFrames(h, 5, 20_000), "Expected initial frames on slow network")
            h.player.pause()

            // Wait while paused so the proxy prefetches multiple segments (> 2) sequentially on slow network
            val waitEnd = System.currentTimeMillis() + 8_000L
            while (System.currentTimeMillis() < waitEnd) {
                if (h.upstreamSegmentRequests.size >= 4) break
                Thread.sleep(50)
            }
            assertTrue(
                h.upstreamSegmentRequests.size >= 4,
                "Expected lookahead prefetch to fetch >= 4 segments while paused, got ${h.upstreamSegmentRequests.keys}"
            )

            // Seek to 12.0s (seg003.ts, which is either already cached or currently in-flight prefetching)
            val beforeCount = h.events.size
            val seekStartMs = System.currentTimeMillis()
            doSeek(h, 12_000L)

            var renderedPtsMs = -1L
            val deadline = System.currentTimeMillis() + 6_000L
            while (System.currentTimeMillis() < deadline) {
                val postSeekFrames = h.events.drop(beforeCount).filterIsInstance<Event.Frame>()
                if (postSeekFrames.isNotEmpty()) {
                    renderedPtsMs = postSeekFrames.last().ptsUs / 1000L
                    break
                }
                Thread.sleep(20)
            }
            val elapsedSeekMs = System.currentTimeMillis() - seekStartMs
            assertNull(h.error.get(), "Unexpected playback error: ${h.error.get()}")
            assertTrue(renderedPtsMs >= 11_930L, "Expected post-seek frame >= 11930ms, got ${renderedPtsMs}ms")

            // Verify seg000..seg003 were each requested at most once (zero cancelled/wasted re-downloads!)
            for (segName in listOf("seg000.ts", "seg001.ts", "seg002.ts", "seg003.ts")) {
                val count = h.upstreamSegmentRequests[segName]?.get() ?: 0
                assertTrue(
                    count == 1,
                    "Expected $segName to be downloaded exactly once without cancellation/redownload, got $count (seek latency=${elapsedSeekMs}ms)"
                )
            }
        } finally {
            h.close()
        }
    }
}
