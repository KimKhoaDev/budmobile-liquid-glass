package com.phimbud.mobile.core.network

import com.phimbud.mobile.core.platform.PlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin

expect class PlatformHttpEngine(
    context: PlatformContext,
    cookieStore: PersistentCookieStore
) {
    val httpClient: HttpClient
    val noRedirectHttpClient: HttpClient
    val publicHttpClient: HttpClient
}

internal fun installBrowserAndCookieInterceptor(
    client: HttpClient,
    cookieStore: PersistentCookieStore
) {
    client.plugin(HttpSend).intercept { request ->
        val host = request.url.host
        val method = request.method.value
        val computed = BrowserHeaders.computeHeaders(
            urlHost = host,
            method = method,
            hasHeader = { name -> request.headers.contains(name) }
        )
        for ((k, v) in computed) {
            request.headers.remove(k)
            request.headers.append(k, v)
        }
        if (!request.headers.contains("Cookie")) {
            val cookieHeader = cookieStore.buildCookieHeader(host)
            if (!cookieHeader.isNullOrBlank()) {
                request.headers.append("Cookie", cookieHeader)
            }
        }

        val call = execute(request)
        val setCookies = call.response.headers.getAll("Set-Cookie")
        if (!setCookies.isNullOrEmpty()) {
            cookieStore.saveFromSetCookieHeaders(call.request.url.host, setCookies)
        }
        call
    }
}

/**
 * Installs clean, standalone browser headers for public third-party endpoints (e.g. Netflix Tudum / Apple RSS).
 * Strictly strips any Origin, Referer, or Cookie headers so phimbud.com identity/session is NEVER leaked.
 */
internal fun installCleanPublicInterceptor(client: HttpClient) {
    client.plugin(HttpSend).intercept { request ->
        request.headers.remove("Origin")
        request.headers.remove("Referer")
        request.headers.remove("Cookie")
        if (!request.headers.contains("User-Agent")) {
            request.headers.append("User-Agent", BrowserHeaders.USER_AGENT)
        }
        if (!request.headers.contains("Accept-Language")) {
            request.headers.append("Accept-Language", "en-US,en;q=0.9")
        }
        if (!request.headers.contains("Accept")) {
            request.headers.append("Accept", "text/html,application/json,*/*;q=0.8")
        }
        execute(request)
    }
}
