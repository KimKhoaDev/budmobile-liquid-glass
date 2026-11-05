package com.phimbud.mobile.core.network

import com.phimbud.mobile.core.platform.PlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual class PlatformHttpEngine actual constructor(
    context: PlatformContext,
    cookieStore: PersistentCookieStore
) {
    actual val httpClient: HttpClient = HttpClient(Darwin) {
        followRedirects = true
        engine {
            configureSession {
                HTTPShouldSetCookies = false
            }
            configureRequest {
                setAllowsCellularAccess(true)
            }
        }
    }.also { client ->
        installBrowserAndCookieInterceptor(client, cookieStore)
    }

    actual val noRedirectHttpClient: HttpClient = HttpClient(Darwin) {
        followRedirects = false
        engine {
            configureSession {
                HTTPShouldSetCookies = false
            }
            configureRequest {
                setAllowsCellularAccess(true)
            }
        }
    }.also { client ->
        installBrowserAndCookieInterceptor(client, cookieStore)
    }
}
