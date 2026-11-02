package com.phimbud.mobile.core.network

import android.content.Context
import android.content.SharedPreferences

/**
 * Resolves SvelteKit remote RPC method hashes.
 * Defaults to current hashes with dynamic caching and self-healing.
 */
class RemoteHashResolver(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("remote_hashes", Context.MODE_PRIVATE)

    // Current known hashes from reverse engineering
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
        return prefs.getString(functionName, defaultHashes[functionName] ?: "3w1j30") ?: "3w1j30"
    }

    fun updateHash(functionName: String, newHash: String) {
        prefs.edit().putString(functionName, newHash).apply()
    }

    fun getRemotePath(functionName: String): String {
        val hash = getHash(functionName)
        return "/_app/remote/$hash/$functionName"
    }
}
