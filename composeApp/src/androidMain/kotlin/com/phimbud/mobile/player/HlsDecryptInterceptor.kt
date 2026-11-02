package com.phimbud.mobile.player

import com.phimbud.mobile.core.crypto.UrlCipher
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Intercepts media segment and AES-128 key requests made by ExoPlayer on Android.
 * Replaces ROT-6 + Base64 obfuscated filenames with decoded filenames on the fly.
 */
class HlsDecryptInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalReq = chain.request()
        val url = originalReq.url
        val path = url.encodedPath

        if (path.endsWith(".m3u8", ignoreCase = true)) {
            val req = originalReq.newBuilder()
                .header("Referer", "https://phimbud.com/")
                .build()
            return chain.proceed(req)
        }

        val segments = url.pathSegments
        if (segments.isEmpty()) {
            return chain.proceed(originalReq)
        }

        val lastSegment = segments.last()
        val dotIndex = lastSegment.lastIndexOf('.')

        if (dotIndex != -1) {
            val name = lastSegment.substring(0, dotIndex)
            val ext = lastSegment.substring(dotIndex + 1)

            val decodedName = UrlCipher.decodeSegmentFilename(name)
            if (decodedName != null) {
                val newLastSegment = "$decodedName.$ext"
                val newUrlBuilder = url.newBuilder()

                val segmentCount = url.pathSegments.size
                for (i in 0 until segmentCount) {
                    newUrlBuilder.setPathSegment(
                        i,
                        if (i == segmentCount - 1) newLastSegment else url.pathSegments[i]
                    )
                }

                val rewrittenUrl = newUrlBuilder.build()
                val newReq = originalReq.newBuilder()
                    .url(rewrittenUrl)
                    .header("Referer", "https://phimbud.com/")
                    .build()

                return chain.proceed(newReq)
            }
        }

        val fallbackReq = originalReq.newBuilder()
            .header("Referer", "https://phimbud.com/")
            .build()
        return chain.proceed(fallbackReq)
    }
}
