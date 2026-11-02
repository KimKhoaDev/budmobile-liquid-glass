package com.phimbud.mobile.core.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class BrowserHeadersInterceptorTest {

    private fun interceptRequest(request: Request): Request {
        var captured: Request? = null
        val fakeChain = object : Interceptor.Chain {
            override fun request(): Request = request
            override fun proceed(request: Request): Response {
                captured = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_2)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis(): Int = 10000
            override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
            override fun readTimeoutMillis(): Int = 10000
            override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
            override fun writeTimeoutMillis(): Int = 10000
            override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
        }
        BrowserHeadersInterceptor().intercept(fakeChain)
        return captured!!
    }

    @Test
    fun `same-origin GET sets Chrome 120 client hints and same-origin without Origin`() {
        val req = Request.Builder()
            .url("https://phimbud.com/__data.json")
            .build()

        val out = interceptRequest(req)
        assertEquals(BrowserHeadersInterceptor.USER_AGENT, out.header("User-Agent"))
        assertEquals(BrowserHeadersInterceptor.SEC_CH_UA, out.header("sec-ch-ua"))
        assertEquals("?0", out.header("sec-ch-ua-mobile"))
        assertEquals("\"Windows\"", out.header("sec-ch-ua-platform"))
        assertEquals("same-origin", out.header("Sec-Fetch-Site"))
        assertEquals("cors", out.header("Sec-Fetch-Mode"))
        assertEquals("empty", out.header("Sec-Fetch-Dest"))
        assertEquals("https://phimbud.com/", out.header("Referer"))
        assertNull(out.header("Origin"))
    }

    @Test
    fun `same-origin POST includes Origin and preserves custom Referer`() {
        val req = Request.Builder()
            .url("https://phimbud.com/_app/remote/3w1j30/setPlaytime")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .header("Referer", "https://phimbud.com/watch/ep123")
            .build()

        val out = interceptRequest(req)
        assertEquals("same-origin", out.header("Sec-Fetch-Site"))
        assertEquals("https://phimbud.com", out.header("Origin"))
        assertEquals("https://phimbud.com/watch/ep123", out.header("Referer"))
    }

    @Test
    fun `cross-origin HLS segment GET sets cross-site and Origin for CORS parity`() {
        val req = Request.Builder()
            .url("https://cdn-edge.example.net/hls/segment001.ts")
            .build()

        val out = interceptRequest(req)
        assertEquals("cross-site", out.header("Sec-Fetch-Site"))
        assertEquals("https://phimbud.com", out.header("Origin"))
        assertEquals("https://phimbud.com/", out.header("Referer"))
    }
}
