package com.phimbud.mobile.player

import com.phimbud.mobile.core.platform.KeyValueStore
import com.phimbud.mobile.core.platform.PlatformContext

/**
 * Manages persistent user preferences for the video player:
 * - Preferred streaming server (e.g., "1", "2", "3")
 * - Subtitle state (enabled / disabled)
 * - Preferred subtitle language (e.g., "vi", "en")
 */
class PlayerPreferencesStore(platformContext: PlatformContext) {
    private val store = KeyValueStore(platformContext, STORE_NAME)

    var preferredServer: String
        get() = store.getString(KEY_PREFERRED_SERVER)?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER
        set(value) {
            store.putString(KEY_PREFERRED_SERVER, value.ifBlank { DEFAULT_SERVER })
        }

    var isSubtitleEnabled: Boolean
        get() = store.getString(KEY_SUBTITLE_ENABLED)?.toBooleanStrictOrNull() ?: true
        set(value) {
            store.putString(KEY_SUBTITLE_ENABLED, value.toString())
        }

    var preferredSubtitleLanguage: String
        get() = store.getString(KEY_SUBTITLE_LANGUAGE)?.takeIf { it.isNotBlank() } ?: DEFAULT_LANGUAGE
        set(value) {
            store.putString(KEY_SUBTITLE_LANGUAGE, value.ifBlank { DEFAULT_LANGUAGE })
        }

    companion object {
        private const val STORE_NAME = "phimbud_player_prefs"
        private const val KEY_PREFERRED_SERVER = "preferred_server"
        private const val KEY_SUBTITLE_ENABLED = "subtitle_enabled"
        private const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"

        const val DEFAULT_SERVER = "1"
        const val DEFAULT_LANGUAGE = "vi"
    }
}
