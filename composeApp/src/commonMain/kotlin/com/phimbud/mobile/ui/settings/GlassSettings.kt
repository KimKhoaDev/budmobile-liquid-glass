package com.phimbud.mobile.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.phimbud.mobile.core.platform.KeyValueStore
import com.phimbud.mobile.core.platform.PlatformContext
import com.phimbud.mobile.ui.theme.NetflixRed

enum class GlassAccent(val label: String, val color: Color) {
    RED("Đỏ", NetflixRed),
    BLUE("Xanh dương", Color(0xFF0A84FF)),
    PURPLE("Tím", Color(0xFFBF5AF2)),
    GREEN("Xanh lá", Color(0xFF30D158)),
    GOLD("Vàng", Color(0xFFFFD60A)),
    PINK("Hồng", Color(0xFFFF375F))
}

/** User-customisable Liquid Glass appearance. */
@Immutable
data class GlassSettings(
    /** Master switch. Off = flat translucent surfaces, no rim/shadow/refraction. */
    val enabled: Boolean = true,
    /** Multiplies the opacity of glass fills (0.5 = subtle, 1.6 = frosted). */
    val intensity: Float = DEFAULT_INTENSITY,
    /** Colour used wherever the brand red used to tint glass (and the tab bar accent). */
    val accent: GlassAccent = GlassAccent.RED,
    /** Spring "inflate" feedback when pressing glass controls. */
    val pressPhysics: Boolean = true,
    /** Lens refraction on the bottom bar / back button (Android only). */
    val refraction: Boolean = true
) {
    fun normalized(): GlassSettings = copy(intensity = intensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY))

    companion object {
        const val DEFAULT_INTENSITY = 1.0f
        const val MIN_INTENSITY = 0.5f
        const val MAX_INTENSITY = 1.6f
    }
}

/** Persists [GlassSettings] and exposes them as Compose state. */
class GlassSettingsController(platformContext: PlatformContext) {
    private val store = KeyValueStore(platformContext, STORE_NAME)

    var settings: GlassSettings by mutableStateOf(load())
        private set

    fun update(transform: (GlassSettings) -> GlassSettings) {
        val next = transform(settings).normalized()
        settings = next
        save(next)
    }

    fun reset() = update { GlassSettings() }

    private fun load(): GlassSettings {
        val defaults = GlassSettings()
        return GlassSettings(
            enabled = store.getString(KEY_ENABLED)?.toBooleanStrictOrNull() ?: defaults.enabled,
            intensity = store.getString(KEY_INTENSITY)?.toFloatOrNull() ?: defaults.intensity,
            accent = store.getString(KEY_ACCENT)
                ?.let { name -> GlassAccent.entries.firstOrNull { it.name == name } }
                ?: defaults.accent,
            pressPhysics = store.getString(KEY_PRESS)?.toBooleanStrictOrNull() ?: defaults.pressPhysics,
            refraction = store.getString(KEY_REFRACTION)?.toBooleanStrictOrNull() ?: defaults.refraction
        ).normalized()
    }

    private fun save(value: GlassSettings) {
        store.putString(KEY_ENABLED, value.enabled.toString())
        store.putString(KEY_INTENSITY, value.intensity.toString())
        store.putString(KEY_ACCENT, value.accent.name)
        store.putString(KEY_PRESS, value.pressPhysics.toString())
        store.putString(KEY_REFRACTION, value.refraction.toString())
    }

    private companion object {
        const val STORE_NAME = "phimbud_glass_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_INTENSITY = "intensity"
        const val KEY_ACCENT = "accent"
        const val KEY_PRESS = "press_physics"
        const val KEY_REFRACTION = "refraction"
    }
}

val LocalGlassSettings = staticCompositionLocalOf { GlassSettings() }
val LocalGlassSettingsController = staticCompositionLocalOf<GlassSettingsController?> { null }
