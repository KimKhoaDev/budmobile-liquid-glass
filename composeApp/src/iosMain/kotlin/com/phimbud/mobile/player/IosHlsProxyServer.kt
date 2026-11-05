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
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lightweight loopback (127.0.0.1) HLS proxy for iOS AVPlayer:
 * - Rewrites master and media .m3u8 manifests via [HlsManifestRewriter] to decode ROT-6 + Base64
 *   obfuscated .ts segment and #EXT-X-KEY AES-128 key filenames.
 * - Fetches all upstream .m3u8, .key, and .ts resources through our Ktor Darwin [HttpClient]
 *   with 100% Chrome 120 Client Hints, Origin, Referer, and session cookies.
 * - Normalizes media segment Content-Type to video/mp2t (or video/mp4 for fMP4) so AVPlayer
 *   plays seamlessly even when CDNs disguise MPEG-TS segments with image/jpeg or text/html headers.
 */
class IosHlsProxyServer(
    private val httpClient: HttpClient
) {
    private val mutex = Mutex()
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var baseUrl: String? = null

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
                        val res = httpClient.get(upstreamUrl) {
                            header("Referer", "https://phimbud.com/")
                        }
                        if (!res.status.isSuccess()) {
                            call.respondText("Upstream HTTP ${res.status.value}", status = res.status)
                            return@runCatching
                        }
                        val proxyBase = baseUrl ?: "http://127.0.0.1:${call.request.local.serverPort}"
                        val rewritten = HlsManifestRewriter.rewritePlaylist(
                            playlistUrl = upstreamUrl,
                            playlistContent = res.bodyAsText(),
                            proxyBaseUrl = proxyBase
                        )
                        call.response.header(HttpHeaders.CacheControl, "no-cache")
                        call.respondText(
                            text = rewritten,
                            contentType = ContentType("application", "vnd.apple.mpegurl"),
                            status = HttpStatusCode.OK
                        )
                    }.onFailure { err ->
                        logError("IosHlsProxyServer", "Failed to proxy playlist.m3u8", err)
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
                        val res = httpClient.get(keyUrl) {
                            header("Referer", "https://phimbud.com/")
                        }
                        val bytes = res.bodyAsBytes()
                        call.respondBytes(
                            bytes = bytes,
                            contentType = ContentType.Application.OctetStream,
                            status = res.status
                        )
                    }.onFailure { err ->
                        logError("IosHlsProxyServer", "Failed to proxy key.bin", err)
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
                            val pathOnly = segmentUrl.substringBefore('?').lowercase()
                            val upstreamCt = upstream.headers[HttpHeaders.ContentType].orEmpty().lowercase()
                            val mediaContentType = if (
                                pathOnly.endsWith(".m4s") ||
                                pathOnly.endsWith(".mp4") ||
                                upstreamCt.contains("video/mp4")
                            ) {
                                ContentType.Video.MP4
                            } else {
                                ContentType("video", "mp2t")
                            }

                            call.respondBytesWriter(
                                contentType = mediaContentType,
                                status = upstream.status
                            ) {
                                upstream.bodyAsChannel().copyTo(this)
                            }
                        }
                    }.onFailure { err ->
                        logError("IosHlsProxyServer", "Failed to proxy segment.ts", err)
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

    fun stop() {
        runCatching {
            server?.stop(gracePeriodMillis = 50, timeoutMillis = 200)
        }
        server = null
        baseUrl = null
    }
}
