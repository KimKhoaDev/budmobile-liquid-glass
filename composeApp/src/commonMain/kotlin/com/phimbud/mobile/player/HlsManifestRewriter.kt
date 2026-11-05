package com.phimbud.mobile.player

import com.phimbud.mobile.core.crypto.UrlCipher

/**
 * Rewrites HLS master and media playlists (.m3u8) for iOS AVPlayer:
 * 1. Decodes ROT-6 + Base64 obfuscated filenames on `#EXT-X-KEY`, `#EXT-X-MAP`, and media segment URIs.
 * 2. Routes sub-playlists (`/playlist.m3u8?u=...`), AES-128 keys (`/key.bin?u=...`), and media segments
 *    (`/segment.ts?u=...`) through the local loopback proxy so every upstream CDN request carries
 *    Chrome 120 Client Hints, Origin, Referer, and normalized MPEG-TS Content-Type headers.
 */
object HlsManifestRewriter {

    private val URI_ATTR_REGEX = Regex("""URI="([^"]+)"""")

    fun encodeProxyTarget(url: String): String {
        return UrlCipher.base64UrlEncode(url.encodeToByteArray())
    }

    fun decodeProxyTarget(encoded: String): String {
        return UrlCipher.base64Decode(encoded).decodeToString()
    }

    fun buildLocalPlaylistUrl(proxyBaseUrl: String, upstreamM3u8Url: String): String {
        val base = proxyBaseUrl.trimEnd('/')
        return "$base/playlist.m3u8?u=${encodeProxyTarget(upstreamM3u8Url)}"
    }

    fun rewritePlaylist(
        playlistUrl: String,
        playlistContent: String,
        proxyBaseUrl: String
    ): String {
        val base = proxyBaseUrl.trimEnd('/')
        val lines = playlistContent.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = StringBuilder(playlistContent.length + 512)

        for (index in lines.indices) {
            val rawLine = lines[index]
            val trimmed = rawLine.trim()
            val rewritten = when {
                trimmed.isEmpty() -> rawLine
                trimmed.startsWith("#") -> {
                    if (trimmed.contains("URI=\"")) {
                        URI_ATTR_REGEX.replace(rawLine) { match ->
                            val rawUri = match.groupValues[1]
                            val resolved = resolveUrl(playlistUrl, rawUri)
                            val proxyUri = if (isM3u8Url(resolved)) {
                                "$base/playlist.m3u8?u=${encodeProxyTarget(resolved)}"
                            } else {
                                val decodedUrl = decodeObfuscatedAssetUrl(resolved)
                                "$base/key.bin?u=${encodeProxyTarget(decodedUrl)}"
                            }
                            "URI=\"$proxyUri\""
                        }
                    } else {
                        rawLine
                    }
                }
                else -> {
                    val resolved = resolveUrl(playlistUrl, trimmed)
                    if (isM3u8Url(resolved)) {
                        "$base/playlist.m3u8?u=${encodeProxyTarget(resolved)}"
                    } else {
                        val decodedUrl = decodeObfuscatedAssetUrl(resolved)
                        "$base/segment.ts?u=${encodeProxyTarget(decodedUrl)}"
                    }
                }
            }
            out.append(rewritten)
            if (index < lines.lastIndex) {
                out.append('\n')
            }
        }
        return out.toString()
    }

    fun decodeObfuscatedAssetUrl(url: String): String {
        val qIdx = url.indexOf('?')
        val pathPart = if (qIdx >= 0) url.substring(0, qIdx) else url
        val querySuffix = if (qIdx >= 0) url.substring(qIdx) else ""

        if (pathPart.endsWith(".m3u8", ignoreCase = true)) {
            return url
        }

        val lastSlash = pathPart.lastIndexOf('/')
        if (lastSlash < 0 || lastSlash == pathPart.lastIndex) {
            return url
        }

        val prefix = pathPart.substring(0, lastSlash + 1)
        val lastSegment = pathPart.substring(lastSlash + 1)
        val dotIndex = lastSegment.lastIndexOf('.')
        if (dotIndex <= 0) {
            return url
        }

        val name = lastSegment.substring(0, dotIndex)
        val ext = lastSegment.substring(dotIndex + 1)
        val decodedName = UrlCipher.decodeSegmentFilename(name) ?: return url
        return "$prefix$decodedName.$ext$querySuffix"
    }

    fun resolveUrl(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://", ignoreCase = true) || ref.startsWith("https://", ignoreCase = true)) {
            return ref
        }
        val baseWithoutQuery = baseUrl.substringBefore('?').substringBefore('#')
        val schemeEnd = baseWithoutQuery.indexOf("://")
        val scheme = if (schemeEnd > 0) baseWithoutQuery.substring(0, schemeEnd) else "https"
        if (ref.startsWith("//")) {
            return "$scheme:$ref"
        }

        val authorityStart = if (schemeEnd > 0) schemeEnd + 3 else 0
        val firstPathSlash = baseWithoutQuery.indexOf('/', authorityStart)
        val origin = if (firstPathSlash >= 0) {
            baseWithoutQuery.substring(0, firstPathSlash)
        } else {
            baseWithoutQuery
        }

        if (ref.startsWith("/")) {
            return "$origin$ref"
        }

        val baseDirPath = if (firstPathSlash >= 0) {
            val path = baseWithoutQuery.substring(firstPathSlash)
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash >= 0) path.substring(0, lastSlash + 1) else "/"
        } else {
            "/"
        }

        return origin + normalizePath(baseDirPath + ref)
    }

    private fun normalizePath(rawPath: String): String {
        val qIdx = rawPath.indexOf('?')
        val pathOnly = if (qIdx >= 0) rawPath.substring(0, qIdx) else rawPath
        val querySuffix = if (qIdx >= 0) rawPath.substring(qIdx) else ""

        val stack = ArrayList<String>()
        for (seg in pathOnly.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack.add(seg)
            }
        }
        val trailingSlash = if (pathOnly.endsWith("/") && stack.isNotEmpty()) "/" else ""
        return "/" + stack.joinToString("/") + trailingSlash + querySuffix
    }

    private fun isM3u8Url(url: String): Boolean {
        val pathOnly = url.substringBefore('?').substringBefore('#')
        return pathOnly.endsWith(".m3u8", ignoreCase = true)
    }
}
