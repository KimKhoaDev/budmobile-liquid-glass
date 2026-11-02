package com.phimbud.mobile.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor delegating to shared [BrowserHeaders] so Android ExoPlayer & OkHttp
 * attach identical Chrome 120 Client Hints, Sec-Fetch-*, Origin, and Referer headers.
 */
class BrowserHeadersInterceptor : Interceptor {

    companion object {
        const val USER_AGENT = BrowserHeaders.USER_AGENT
        const val SEC_CH_UA = BrowserHeaders.SEC_CH_UA
        const val SEC_CH_UA_MOBILE = BrowserHeaders.SEC_CH_UA_MOBILE
        const val SEC_CH_UA_PLATFORM = BrowserHeaders.SEC_CH_UA_PLATFORM
        const val ACCEPT_LANGUAGE = BrowserHeaders.ACCEPT_LANGUAGE
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        val computed = BrowserHeaders.computeHeaders(
            urlHost = original.url.host,
            method = original.method,
            hasHeader = { name -> original.header(name) != null }
        )
        for ((k, v) in computed) {
            builder.header(k, v)
        }

        return chain.proceed(builder.build())
    }
}
