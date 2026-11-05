package com.phimbud.mobile.core.network

import com.phimbud.mobile.core.platform.KeyValueStore
import com.phimbud.mobile.core.platform.PlatformContext
import com.phimbud.mobile.core.platform.currentTimeMillis
import kotlin.concurrent.Volatile

data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expiresAt: Long = FAR_FUTURE_EXPIRES_AT
) {
    companion object {
        const val FAR_FUTURE_EXPIRES_AT = 253402300799999L // 9999-12-31T23:59:59Z
    }
}

/**
 * Cross-platform persistent cookie jar backed by [KeyValueStore] ("phimbud_cookies").
 * 100% compatible with existing Android SharedPreferences cookie storage format.
 */
class PersistentCookieStore(context: PlatformContext) {

    private val store = KeyValueStore(context, "phimbud_cookies")

    @Volatile
    private var memoryCookies: Map<String, StoredCookie> = emptyMap()

    init {
        loadFromStore()
    }

    fun saveCookie(cookie: StoredCookie) {
        val now = currentTimeMillis()
        val normalizedDomain = cookie.domain.trim().trimStart('.').lowercase()
        val key = "$normalizedDomain|${cookie.name}"
        val next = LinkedHashMap(memoryCookies)

        if (cookie.value.isEmpty() || cookie.expiresAt < now) {
            next.remove(key)
            store.remove(key)
        } else {
            val normalized = cookie.copy(domain = normalizedDomain)
            next[key] = normalized
            store.putString(
                key,
                "${normalized.name}=${normalized.value}; domain=${normalized.domain}; path=${normalized.path}; expiresAt=${normalized.expiresAt}"
            )
        }
        memoryCookies = next
    }

    fun saveFromSetCookieHeaders(requestHost: String, setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        for (header in setCookieHeaders) {
            val parsed = parseSetCookie(requestHost, header) ?: continue
            saveCookie(parsed)
        }
    }

    fun loadForHost(host: String): List<StoredCookie> {
        val now = currentTimeMillis()
        val lowerHost = host.lowercase()
        return memoryCookies.values.filter { cookie ->
            cookie.expiresAt >= now &&
                (lowerHost == cookie.domain || lowerHost.endsWith(".${cookie.domain}"))
        }
    }

    fun buildCookieHeader(host: String): String? {
        val matching = loadForHost(host)
        if (matching.isEmpty()) return null
        return matching.joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun clear() {
        memoryCookies = emptyMap()
        store.clear()
    }

    fun hasSession(): Boolean {
        val now = currentTimeMillis()
        return memoryCookies.values.any {
            it.name == "session" && it.value.isNotBlank() && it.expiresAt >= now
        }
    }

    private fun loadFromStore() {
        val now = currentTimeMillis()
        val all = store.getAllStrings()
        val loaded = LinkedHashMap<String, StoredCookie>(all.size)
        for ((key, str) in all) {
            runCatching {
                val parts = str.split("; ")
                val nameVal = parts.getOrNull(0)?.split("=", limit = 2) ?: return@runCatching
                val name = nameVal.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@runCatching
                val value = nameVal.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return@runCatching
                var domain = "phimbud.com"
                var path = "/"
                var expiresAt: Long = StoredCookie.FAR_FUTURE_EXPIRES_AT

                for (i in 1 until parts.size) {
                    val p = parts[i]
                    when {
                        p.startsWith("domain=") -> domain = p.removePrefix("domain=").trimStart('.').lowercase()
                        p.startsWith("path=") -> path = p.removePrefix("path=")
                        p.startsWith("expiresAt=") -> {
                            expiresAt = p.removePrefix("expiresAt=").toLongOrNull()
                                ?: StoredCookie.FAR_FUTURE_EXPIRES_AT
                        }
                    }
                }

                if (expiresAt < now) return@runCatching

                loaded[key] = StoredCookie(
                    name = name,
                    value = value,
                    domain = domain,
                    path = path,
                    expiresAt = expiresAt
                )
            }
        }
        memoryCookies = loaded
    }

    companion object {
        fun parseSetCookie(requestHost: String, rawSetCookie: String, nowMs: Long = currentTimeMillis()): StoredCookie? {
            val segments = rawSetCookie.split(';')
            if (segments.isEmpty()) return null

            val firstPair = segments[0].trim().split("=", limit = 2)
            val name = firstPair.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val value = firstPair.getOrNull(1)?.trim() ?: ""

            var domain = requestHost.trim().trimStart('.').lowercase()
            var path = "/"
            var maxAgeMs: Long? = null
            var expiresMs: Long? = null

            for (i in 1 until segments.size) {
                val attr = segments[i].trim()
                if (attr.isEmpty()) continue
                val kv = attr.split("=", limit = 2)
                val attrName = kv[0].trim().lowercase()
                val attrVal = kv.getOrNull(1)?.trim().orEmpty()
                when (attrName) {
                    "domain" -> if (attrVal.isNotEmpty()) {
                        domain = attrVal.trimStart('.').lowercase()
                    }
                    "path" -> if (attrVal.isNotEmpty()) {
                        path = attrVal
                    }
                    "max-age" -> {
                        val sec = attrVal.toLongOrNull()
                        if (sec != null) {
                            maxAgeMs = if (sec <= 0L) 0L else nowMs + sec * 1000L
                        }
                    }
                    "expires" -> {
                        expiresMs = parseHttpDateApproxMillis(attrVal)
                    }
                }
            }

            val finalExpiresAt = maxAgeMs ?: expiresMs ?: StoredCookie.FAR_FUTURE_EXPIRES_AT
            return StoredCookie(
                name = name,
                value = value,
                domain = domain,
                path = path,
                expiresAt = finalExpiresAt
            )
        }

        private val MONTHS = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
            "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
            "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
        )

        /**
         * Parses RFC-1123 / RFC-6265 Expires dates (e.g. "Wed, 02 Dec 2026 12:00:00 GMT" or "Thu, 01-Jan-1970 00:00:00 GMT")
         * into UTC epoch milliseconds without platform locale dependencies.
         */
        internal fun parseHttpDateApproxMillis(raw: String): Long? {
            if (raw.isBlank()) return null
            val cleaned = raw.replace('-', ' ').replace(',', ' ')
            val tokens = cleaned.split(' ').filter { it.isNotBlank() }
            if (tokens.size < 4) return null

            var day: Int? = null
            var month: Int? = null
            var year: Int? = null
            var hour = 0
            var minute = 0
            var second = 0

            for (token in tokens) {
                val lower = token.lowercase()
                if (month == null && MONTHS.containsKey(lower.take(3))) {
                    month = MONTHS[lower.take(3)]
                } else if (token.contains(':')) {
                    val hms = token.split(':')
                    hour = hms.getOrNull(0)?.toIntOrNull() ?: 0
                    minute = hms.getOrNull(1)?.toIntOrNull() ?: 0
                    second = hms.getOrNull(2)?.toIntOrNull() ?: 0
                } else {
                    val num = token.toIntOrNull()
                    if (num != null) {
                        if (num in 1..31 && day == null) {
                            day = num
                        } else if (num >= 70) {
                            year = when {
                                num < 100 -> 1900 + num
                                else -> num
                            }
                        } else if (num in 0..69 && day != null && year == null) {
                            year = 2000 + num
                        }
                    }
                }
            }

            val y = year ?: return null
            val m = month ?: return null
            val d = day ?: return null
            if (y <= 1970 && m == 1 && d == 1) return 0L

            // Civil-to-days (Howard Hinnant)
            val adjY = (if (m <= 2) y - 1 else y).toLong()
            val era = (if (adjY >= 0) adjY else adjY - 399L) / 400L
            val yoe = adjY - era * 400L
            val mp = (m + (if (m > 2) -3 else 9)).toLong()
            val doy = (153L * mp + 2L) / 5L + d - 1L
            val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
            val days = era * 146097L + doe - 719468L
            return ((days * 86_400L) + hour * 3600L + minute * 60L + second) * 1000L
        }
    }
}
