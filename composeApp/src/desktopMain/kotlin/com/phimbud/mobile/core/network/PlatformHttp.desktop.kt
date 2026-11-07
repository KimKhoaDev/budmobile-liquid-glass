package com.phimbud.mobile.core.network

import com.phimbud.mobile.core.platform.PlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

private class DesktopCookieJarAdapter(
    private val cookieStore: PersistentCookieStore
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) {
            cookieStore.saveCookie(
                StoredCookie(
                    name = c.name,
                    value = c.value,
                    domain = c.domain,
                    path = c.path,
                    expiresAt = c.expiresAt
                )
            )
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore.loadForHost(url.host).mapNotNull { stored ->
            runCatching {
                Cookie.Builder()
                    .name(stored.name)
                    .value(stored.value)
                    .domain(stored.domain)
                    .path(stored.path)
                    .expiresAt(stored.expiresAt)
                    .build()
            }.getOrNull()
        }
    }
}

private class DesktopBrowserHeadersInterceptor : Interceptor {
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

actual class PlatformHttpEngine actual constructor(
    context: PlatformContext,
    cookieStore: PersistentCookieStore
) {
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(DesktopCookieJarAdapter(cookieStore))
        .addInterceptor(DesktopBrowserHeadersInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cleanPublicOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    actual val httpClient: HttpClient = HttpClient(OkHttp) {
        followRedirects = true
        engine {
            preconfigured = okHttpClient
        }
    }.also { client ->
        installBrowserAndCookieInterceptor(client, cookieStore)
    }

    actual val noRedirectHttpClient: HttpClient = HttpClient(OkHttp) {
        followRedirects = false
        engine {
            preconfigured = okHttpClient.newBuilder().followRedirects(false).build()
        }
    }.also { client ->
        installBrowserAndCookieInterceptor(client, cookieStore)
    }

    actual val publicHttpClient: HttpClient = HttpClient(OkHttp) {
        followRedirects = true
        engine {
            preconfigured = cleanPublicOkHttpClient
        }
    }.also { client ->
        installCleanPublicInterceptor(client)
    }
}
