package com.phimbud.mobile.core.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Persists session cookies across app restarts using SharedPreferences.
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences = context.getSharedPreferences("phimbud_cookies", Context.MODE_PRIVATE)
    private val memoryCookies = mutableMapOf<String, Cookie>()

    init {
        loadFromPrefs()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        for (cookie in cookies) {
            val key = "${cookie.domain}|${cookie.name}"
            if (cookie.value.isEmpty() || cookie.expiresAt < now) {
                memoryCookies.remove(key)
                editor.remove(key)
            } else {
                memoryCookies[key] = cookie
                editor.putString(
                    key,
                    "${cookie.name}=${cookie.value}; domain=${cookie.domain}; path=${cookie.path}; expiresAt=${cookie.expiresAt}"
                )
            }
        }
        editor.apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val host = url.host
        return memoryCookies.values.filter { cookie ->
            cookie.expiresAt >= now && (host == cookie.domain || host.endsWith("." + cookie.domain))
        }
    }

    @Synchronized
    fun clear() {
        memoryCookies.clear()
        prefs.edit().clear().apply()
    }

    @Synchronized
    fun hasSession(): Boolean {
        val now = System.currentTimeMillis()
        return memoryCookies.values.any { it.name == "session" && it.value.isNotBlank() && it.expiresAt >= now }
    }

    private fun loadFromPrefs() {
        val now = System.currentTimeMillis()
        val all = prefs.all
        for ((key, raw) in all) {
            runCatching {
                val str = raw as? String ?: return@runCatching
                val parts = str.split("; ")
                val nameVal = parts.getOrNull(0)?.split("=", limit = 2) ?: return@runCatching
                val name = nameVal.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@runCatching
                val value = nameVal.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return@runCatching
                var domain = "phimbud.com"
                var path = "/"
                var expiresAt: Long? = null

                for (p in parts) {
                    when {
                        p.startsWith("domain=") -> domain = p.removePrefix("domain=").trimStart('.')
                        p.startsWith("path=") -> path = p.removePrefix("path=")
                        p.startsWith("expiresAt=") -> expiresAt = p.removePrefix("expiresAt=").toLongOrNull()
                    }
                }

                if (expiresAt != null && expiresAt < now) {
                    return@runCatching
                }

                val builder = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(domain)
                    .path(path)
                if (expiresAt != null) {
                    builder.expiresAt(expiresAt)
                }

                memoryCookies[key] = builder.build()
            }
        }
    }
}
