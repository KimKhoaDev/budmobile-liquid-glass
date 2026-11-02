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
        val editor = prefs.edit()
        for (cookie in cookies) {
            val key = "${cookie.domain}|${cookie.name}"
            memoryCookies[key] = cookie
            editor.putString(key, "${cookie.name}=${cookie.value}; domain=${cookie.domain}; path=${cookie.path}")
        }
        editor.apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        return memoryCookies.values.filter { cookie ->
            host == cookie.domain || host.endsWith("." + cookie.domain)
        }
    }

    @Synchronized
    fun clear() {
        memoryCookies.clear()
        prefs.edit().clear().apply()
    }

    @Synchronized
    fun hasSession(): Boolean {
        return memoryCookies.values.any { it.name == "session" && it.value.isNotBlank() }
    }

    private fun loadFromPrefs() {
        val all = prefs.all
        for ((key, raw) in all) {
            val str = raw as? String ?: continue
            val parts = str.split("; ")
            val nameVal = parts.getOrNull(0)?.split("=") ?: continue
            val name = nameVal.getOrNull(0) ?: continue
            val value = nameVal.getOrNull(1) ?: continue
            var domain = "phimbud.com"
            var path = "/"

            for (p in parts) {
                if (p.startsWith("domain=")) domain = p.removePrefix("domain=")
                if (p.startsWith("path=")) path = p.removePrefix("path=")
            }

            val cookie = Cookie.Builder()
                .name(name)
                .value(value)
                .domain(domain)
                .path(path)
                .build()

            memoryCookies[key] = cookie
        }
    }
}
