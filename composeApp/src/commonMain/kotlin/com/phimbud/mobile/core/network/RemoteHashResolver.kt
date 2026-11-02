package com.phimbud.mobile.core.network

import com.phimbud.mobile.core.platform.KeyValueStore
import com.phimbud.mobile.core.platform.PlatformContext

/**
 * Resolves SvelteKit remote RPC method hashes across Android and iOS.
 * Defaults to current hashes with dynamic caching and self-healing.
 */
class RemoteHashResolver(context: PlatformContext) {

    private val store = KeyValueStore(context, "remote_hashes")

    private val defaultHashes = mapOf(
        "getVideoSrc" to "3w1j30",
        "getEpisodes" to "3w1j30",
        "setPlaytime" to "3w1j30",
        "updateCollection" to "3w1j30",
        "isWatchingElsewhere" to "3w1j30",
        "getSubtitles" to "1odrich",
        "addToCollection" to "zawfwv",
        "getCollectionState" to "zawfwv"
    )

    fun getHash(functionName: String): String {
        val fallback = defaultHashes[functionName] ?: "3w1j30"
        return store.getString(functionName, fallback) ?: fallback
    }

    fun updateHash(functionName: String, newHash: String) {
        store.putString(functionName, newHash)
    }

    fun getRemotePath(functionName: String): String {
        val hash = getHash(functionName)
        return "/_app/remote/$hash/$functionName"
    }
}
