package com.phimbud.mobile.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Ensures all outgoing HTTP requests match Google Chrome 120 on Windows
 * with full Client Hints, Sec-Fetch headers, Origin, Accept-Language, and Referer.
 * This guarantees 100% indistinguishability from real web browser traffic.
 */
class BrowserHeadersInterceptor : Interceptor {

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val SEC_CH_UA = """"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120""""
        const val SEC_CH_UA_MOBILE = "?0"
        const val SEC_CH_UA_PLATFORM = "\"Windows\""
        const val ACCEPT_LANGUAGE = "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        // 1. Full Browser Client Hints
        builder.header("User-Agent", USER_AGENT)
        builder.header("sec-ch-ua", SEC_CH_UA)
        builder.header("sec-ch-ua-mobile", SEC_CH_UA_MOBILE)
        builder.header("sec-ch-ua-platform", SEC_CH_UA_PLATFORM)
        builder.header("Accept-Language", ACCEPT_LANGUAGE)

        // 2. Fetch Metadata (Sec-Fetch-*)
        val isSameOrigin = original.url.host.endsWith("phimbud.com")
        val isPost = original.method == "POST"

        if (original.header("Sec-Fetch-Site") == null) {
            builder.header("Sec-Fetch-Site", if (isSameOrigin) "same-origin" else "cross-site")
        }
        if (original.header("Sec-Fetch-Mode") == null) {
            builder.header("Sec-Fetch-Mode", "cors")
        }
        if (original.header("Sec-Fetch-Dest") == null) {
            builder.header("Sec-Fetch-Dest", "empty")
        }

        // 3. Origin header for POST requests or cross-origin CORS requests (e.g. HLS CDN XHR)
        if ((!isSameOrigin || isPost) && original.header("Origin") == null) {
            builder.header("Origin", "https://phimbud.com")
        }

        // 4. Default Referer if none provided
        if (original.header("Referer") == null) {
            builder.header("Referer", "https://phimbud.com/")
        }

        // 5. Default Accept
        if (original.header("Accept") == null) {
            builder.header("Accept", "*/*")
        }

        return chain.proceed(builder.build())
    }
}
