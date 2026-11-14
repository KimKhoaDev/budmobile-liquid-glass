package com.phimbud.mobile.player

import com.phimbud.mobile.core.platform.logError
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight loopback (127.0.0.1) HLS proxy for Windows Desktop FFmpeg player:
 * - Rewrites master and media .m3u8 manifests via [HlsManifestRewriter] to decode ROT-6 + Base64
 *   obfuscated .ts/.m4s segment and #EXT-X-KEY AES-128 key filenames.
 * - Routes all upstream .m3u8, .key, and .ts/.m4s requests through [HttpClient] carrying
 *   full Chrome 120 Client Hints, Origin, Referer, and session cookies.
 * - Caches static VOD playlists (#EXT-X-ENDLIST), AES-128 keys, and recent/prefetched .ts/.m4s
 *   segments in a bounded RAM LRU cache so +10s / -10s seeking responds instantaneously from RAM.
 */
class DesktopHlsProxyServer(
    private val httpClient: HttpClient
) {
    companion object {
        private const val MAX_CACHED_SEGMENTS = 24
        private const val MAX_CACHED_BYTES = 64L * 1024L * 1024L
        private const val MAX_SINGLE_SEGMENT_BYTES = 12 * 1024 * 1024
        private const val LOOKAHEAD_PREFETCH_COUNT = 2
    }

    private class CachedSegment(
        val bytes: ByteArray,
        val contentType: ContentType
    )

    private val mutex = Mutex()
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var baseUrl: String? = null

    private val vodPlaylistCache = ConcurrentHashMap<String, String>()
    private val keyBytesCache = ConcurrentHashMap<String, ByteArray>()
    private val segmentLookaheadMap = ConcurrentHashMap<String, List<String>>()

    private val segmentCacheLock = Any()
    private val segmentLruCache = java.util.LinkedHashMap<String, CachedSegment>(32, 0.75f, true)
    private var cachedSegmentBytesTotal: Long = 0L

    private val activeUpstreamStreams = AtomicInteger(0)
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var prefetchJob: Job? = null

    private fun getCachedSegment(url: String): CachedSegment? = synchronized(segmentCacheLock) {
        segmentLruCache[url]
    }

    private fun putCachedSegment(url: String, segment: CachedSegment) {
        if (segment.bytes.isEmpty() || segment.bytes.size > MAX_SINGLE_SEGMENT_BYTES) return
        synchronized(segmentCacheLock) {
            val prev = segmentLruCache.put(url, segment)
            if (prev != null) {
                cachedSegmentBytesTotal -= prev.bytes.size.toLong()
            }
            cachedSegmentBytesTotal += segment.bytes.size.toLong()

            val iterator = segmentLruCache.entries.iterator()
            while (iterator.hasNext() &&
                (segmentLruCache.size > MAX_CACHED_SEGMENTS || cachedSegmentBytesTotal > MAX_CACHED_BYTES)
            ) {
                val eldest = iterator.next()
                cachedSegmentBytesTotal -= eldest.value.bytes.size.toLong()
                iterator.remove()
            }
            if (cachedSegmentBytesTotal < 0L) {
                cachedSegmentBytesTotal = 0L
            }
        }
    }

    private fun indexPlaylistSegments(playlistUrl: String, rawContent: String) {
        val lines = rawContent.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val orderedSegments = ArrayList<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val resolved = HlsManifestRewriter.resolveUrl(playlistUrl, trimmed)
            val pathOnly = resolved.substringBefore('?').substringBefore('#')
            if (!pathOnly.endsWith(".m3u8", ignoreCase = true)) {
                orderedSegments.add(HlsManifestRewriter.decodeObfuscatedAssetUrl(resolved))
            }
        }
        if (orderedSegments.size < 2) return
        if (segmentLookaheadMap.size > 4000) {
            segmentLookaheadMap.clear()
        }
        for (i in orderedSegments.indices) {
            val nextEnd = minOf(orderedSegments.size, i + 1 + LOOKAHEAD_PREFETCH_COUNT)
            if (i + 1 < nextEnd) {
                segmentLookaheadMap[orderedSegments[i]] = orderedSegments.subList(i + 1, nextEnd).toList()
            }
        }
    }

    private fun resolveMediaContentType(segmentUrl: String, upstreamContentType: String?): ContentType {
        val pathOnly = segmentUrl.substringBefore('?').lowercase()
        val upstreamCt = upstreamContentType.orEmpty().lowercase()
        return if (
            pathOnly.endsWith(".m4s") ||
            pathOnly.endsWith(".mp4") ||
            upstreamCt.contains("video/mp4")
        ) {
            ContentType.Video.MP4
        } else {
            ContentType("video", "mp2t")
        }
    }

    private fun scheduleIdlePrefetch(currentSegmentUrl: String) {
        val nextUrls = segmentLookaheadMap[currentSegmentUrl] ?: return
        if (nextUrls.all { getCachedSegment(it) != null }) return

        prefetchJob?.cancel()
        prefetchJob = prefetchScope.launch {
            // Brief settle delay so active FFmpeg demuxing always has 100% network priority
            delay(180L)
            for (nextUrl in nextUrls) {
                if (!isActive || activeUpstreamStreams.get() > 0) break
                if (getCachedSegment(nextUrl) != null) continue

                runCatching {
                    httpClient.prepareGet(nextUrl) {
                        header("Referer", "https://phimbud.com/")
                    }.execute { upstream ->
                        if (!upstream.status.isSuccess()) return@execute
                        val mediaContentType = resolveMediaContentType(
                            segmentUrl = nextUrl,
                            upstreamContentType = upstream.headers[HttpHeaders.ContentType]
                        )
                        val channel = upstream.bodyAsChannel()
                        val baos = ByteArrayOutputStream(256 * 1024)
                        val buf = ByteArray(16 * 1024)
                        var aborted = false
                        while (isActive && activeUpstreamStreams.get() == 0) {
                            val read = channel.readAvailable(buf, 0, buf.size)
                            if (read < 0) break
                            if (read > 0) {
                                if (baos.size() + read <= MAX_SINGLE_SEGMENT_BYTES) {
                                    baos.write(buf, 0, read)
                                } else {
                                    aborted = true
                                    break
                                }
                            }
                        }
                        if (!aborted && isActive && activeUpstreamStreams.get() == 0 && channel.isClosedForRead) {
                            val bytes = baos.toByteArray()
                            if (bytes.isNotEmpty()) {
                                putCachedSegment(nextUrl, CachedSegment(bytes, mediaContentType))
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun ensureStarted(): String = mutex.withLock {
        baseUrl?.let { return it }

        val created = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                get("/playlist.m3u8") {
                    val encoded = call.request.queryParameters["u"]
                    if (encoded.isNullOrBlank()) {
                        call.respondText("Missing u", status = HttpStatusCode.BadRequest)
                        return@get
                    }
                    runCatching {
                        val upstreamUrl = HlsManifestRewriter.decodeProxyTarget(encoded)
                        val cachedRewritten = vodPlaylistCache[upstreamUrl]
                        if (cachedRewritten != null) {
                            call.response.header(HttpHeaders.CacheControl, "no-cache")
                            call.respondText(
                                text = cachedRewritten,
                                contentType = ContentType("application", "vnd.apple.mpegurl"),
                                status = HttpStatusCode.OK
                            )
                            return@runCatching
                        }

                        val res = httpClient.get(upstreamUrl) {
                            header("Referer", "https://phimbud.com/")
                        }
                        if (!res.status.isSuccess()) {
                            call.respondText("Upstream HTTP ${res.status.value}", status = res.status)
                            return@runCatching
                        }
                        val rawBody = res.bodyAsText()
                        indexPlaylistSegments(upstreamUrl, rawBody)
                        val proxyBase = baseUrl ?: "http://127.0.0.1:${call.request.local.serverPort}"
                        val rewritten = HlsManifestRewriter.rewritePlaylist(
                            playlistUrl = upstreamUrl,
                            playlistContent = rawBody,
                            proxyBaseUrl = proxyBase
                        )
                        if (rawBody.contains("#EXT-X-ENDLIST") || rawBody.contains("#EXT-X-STREAM-INF")) {
                            if (vodPlaylistCache.size > 32) {
                                vodPlaylistCache.clear()
                            }
                            vodPlaylistCache[upstreamUrl] = rewritten
                        }
                        call.response.header(HttpHeaders.CacheControl, "no-cache")
                        call.respondText(
                            text = rewritten,
                            contentType = ContentType("application", "vnd.apple.mpegurl"),
                            status = HttpStatusCode.OK
                        )
                    }.onFailure { err ->
                        logError("DesktopHlsProxyServer", "Failed to proxy playlist.m3u8", err)
                        call.respondText("Playlist proxy error", status = HttpStatusCode.BadGateway)
                    }
                }

                get("/key.bin") {
                    val encoded = call.request.queryParameters["u"]
                    if (encoded.isNullOrBlank()) {
                        call.respondText("Missing u", status = HttpStatusCode.BadRequest)
                        return@get
                    }
                    runCatching {
                        val keyUrl = HlsManifestRewriter.decodeProxyTarget(encoded)
                        val cachedKey = keyBytesCache[keyUrl]
                        if (cachedKey != null) {
                            call.respondBytes(
                                bytes = cachedKey,
                                contentType = ContentType.Application.OctetStream,
                                status = HttpStatusCode.OK
                            )
                            return@runCatching
                        }

                        val res = httpClient.get(keyUrl) {
                            header("Referer", "https://phimbud.com/")
                        }
                        val bytes = res.bodyAsBytes()
                        if (res.status.isSuccess() && bytes.isNotEmpty()) {
                            if (keyBytesCache.size > 64) {
                                keyBytesCache.clear()
                            }
                            keyBytesCache[keyUrl] = bytes
                        }
                        call.respondBytes(
                            bytes = bytes,
                            contentType = ContentType.Application.OctetStream,
                            status = res.status
                        )
                    }.onFailure { err ->
                        logError("DesktopHlsProxyServer", "Failed to proxy key.bin", err)
                        call.respondText("Key proxy error", status = HttpStatusCode.BadGateway)
                    }
                }

                get("/segment.ts") {
                    val encoded = call.request.queryParameters["u"]
                    if (encoded.isNullOrBlank()) {
                        call.respondText("Missing u", status = HttpStatusCode.BadRequest)
                        return@get
                    }
                    runCatching {
                        val segmentUrl = HlsManifestRewriter.decodeProxyTarget(encoded)
                        val rangeHeader = call.request.headers[HttpHeaders.Range]
                        val isFullSegmentRequest =
                            rangeHeader.isNullOrBlank() || rangeHeader.trim() == "bytes=0-"

                        if (isFullSegmentRequest) {
                            val cached = getCachedSegment(segmentUrl)
                            if (cached != null) {
                                scheduleIdlePrefetch(segmentUrl)
                                call.respondBytes(
                                    bytes = cached.bytes,
                                    contentType = cached.contentType,
                                    status = HttpStatusCode.OK
                                )
                                return@runCatching
                            }
                        }

                        prefetchJob?.cancel()
                        activeUpstreamStreams.incrementAndGet()
                        try {
                            httpClient.prepareGet(segmentUrl) {
                                header("Referer", "https://phimbud.com/")
                                if (!rangeHeader.isNullOrBlank()) {
                                    header(HttpHeaders.Range, rangeHeader)
                                }
                            }.execute { upstream ->
                                upstream.headers[HttpHeaders.ContentRange]?.let { cr ->
                                    call.response.header(HttpHeaders.ContentRange, cr)
                                }
                                upstream.headers[HttpHeaders.AcceptRanges]?.let { ar ->
                                    call.response.header(HttpHeaders.AcceptRanges, ar)
                                }
                                val mediaContentType = resolveMediaContentType(
                                    segmentUrl = segmentUrl,
                                    upstreamContentType = upstream.headers[HttpHeaders.ContentType]
                                )
                                val canCacheResponse = isFullSegmentRequest &&
                                    upstream.status == HttpStatusCode.OK

                                call.respondBytesWriter(
                                    contentType = mediaContentType,
                                    status = upstream.status
                                ) {
                                    val channel = upstream.bodyAsChannel()
                                    val teeBuffer = if (canCacheResponse) {
                                        ByteArrayOutputStream(256 * 1024)
                                    } else {
                                        null
                                    }
                                    var teeOverflow = false
                                    val chunk = ByteArray(16 * 1024)
                                    while (true) {
                                        val read = channel.readAvailable(chunk, 0, chunk.size)
                                        if (read < 0) break
                                        if (read > 0) {
                                            writeFully(chunk, 0, read)
                                            flush()
                                            if (teeBuffer != null && !teeOverflow) {
                                                if (teeBuffer.size() + read <= MAX_SINGLE_SEGMENT_BYTES) {
                                                    teeBuffer.write(chunk, 0, read)
                                                } else {
                                                    teeOverflow = true
                                                }
                                            }
                                        }
                                    }
                                    if (teeBuffer != null && !teeOverflow && channel.isClosedForRead) {
                                        val fullBytes = teeBuffer.toByteArray()
                                        if (fullBytes.isNotEmpty()) {
                                            putCachedSegment(
                                                segmentUrl,
                                                CachedSegment(fullBytes, mediaContentType)
                                            )
                                        }
                                    }
                                }
                            }
                        } finally {
                            activeUpstreamStreams.decrementAndGet()
                            scheduleIdlePrefetch(segmentUrl)
                        }
                    }.onFailure { err ->
                        logError("DesktopHlsProxyServer", "Failed to proxy segment.ts", err)
                    }
                }
            }
        }

        created.start(wait = false)
        val port = created.engine.resolvedConnectors().first().port
        val resolvedBase = "http://127.0.0.1:$port"
        server = created
        baseUrl = resolvedBase
        resolvedBase
    }

    fun clearSessionCache() {
        prefetchJob?.cancel()
        vodPlaylistCache.clear()
        keyBytesCache.clear()
        segmentLookaheadMap.clear()
        synchronized(segmentCacheLock) {
            segmentLruCache.clear()
            cachedSegmentBytesTotal = 0L
        }
    }

    fun stop() {
        clearSessionCache()
        prefetchScope.cancel()
        runCatching {
            server?.stop(gracePeriodMillis = 50, timeoutMillis = 200)
        }
        server = null
        baseUrl = null
    }
}
