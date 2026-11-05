package com.phimbud.mobile.core.model

import com.phimbud.mobile.core.platform.currentTimeMillis
import kotlin.concurrent.Volatile

/**
 * Lock-free copy-on-write bounded TTL cache safe across Android and Kotlin/Native threads.
 */
class TtlLruCache<K, V>(
    private val maxSize: Int,
    private val ttlMs: Long
) {
    private data class Entry<V>(val value: V, val timestampMs: Long)

    @Volatile
    private var snapshot: Map<K, Entry<V>> = emptyMap()

    fun get(key: K): V? {
        val entry = snapshot[key] ?: return null
        if (currentTimeMillis() - entry.timestampMs > ttlMs) {
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V) {
        val next = LinkedHashMap<K, Entry<V>>(snapshot.size + 1)
        for ((k, v) in snapshot) {
            if (k != key) {
                next[k] = v
            }
        }
        next[key] = Entry(value, currentTimeMillis())
        while (next.size > maxSize) {
            val eldestKey = next.keys.firstOrNull() ?: break
            next.remove(eldestKey)
        }
        snapshot = next
    }

    fun evictAll() {
        snapshot = emptyMap()
    }
}
