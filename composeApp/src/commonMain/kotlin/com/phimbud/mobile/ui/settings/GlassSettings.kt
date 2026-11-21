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

/** What drives the colours of the ambient background glow. */
enum class AuroraMode(val label: String) {
    POSTER("Theo poster"),
    ACCENT("Màu nhấn"),
    OFF("Tắt")
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
    val refraction: Boolean = true,
    /** Where the background glow gets its colours from. */
    val auroraMode: AuroraMode = AuroraMode.POSTER,
    /** Brightness of the background glow. */
    val auroraStrength: Float = DEFAULT_AURORA_STRENGTH,
    /** Slowly drifting glow (animated; costs a little more battery). */
    val auroraDrift: Boolean = false,
    /** Light haptic ticks on toggles, tabs and chips. */
    val haptics: Boolean = true
) {
    fun normalized(): GlassSettings = copy(
        intensity = intensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY),
        auroraStrength = auroraStrength.coerceIn(MIN_AURORA_STRENGTH, MAX_AURORA_STRENGTH)
    )

    companion object {
        const val DEFAULT_INTENSITY = 1.0f
        const val MIN_INTENSITY = 0.5f
        const val MAX_INTENSITY = 1.6f
        const val DEFAULT_AURORA_STRENGTH = 1.0f
        const val MIN_AURORA_STRENGTH = 0.3f
        const val MAX_AURORA_STRENGTH = 1.6f
    }
}

/** Persists [GlassSettings] and exposes them as Compose state. */
class GlassSettingsController(platformContext: PlatformContext) {
    private val store = KeyValueStore(platformContext, STORE_NAME)

    var settings: GlassSettings by mutableStateOf(load())
        private set

    fun update(transform: (GlassSettings) -> GlassSettings) {
        val previous = settings
        val next = transform(previous).normalized()
        settings = next
        save(previous, next)
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
            refraction = store.getString(KEY_REFRACTION)?.toBooleanStrictOrNull() ?: defaults.refraction,
            auroraMode = store.getString(KEY_AURORA_MODE)
                ?.let { name -> AuroraMode.entries.firstOrNull { it.name == name } }
                ?: defaults.auroraMode,
            auroraStrength = store.getString(KEY_AURORA_STRENGTH)?.toFloatOrNull() ?: defaults.auroraStrength,
            auroraDrift = store.getString(KEY_AURORA_DRIFT)?.toBooleanStrictOrNull() ?: defaults.auroraDrift,
            haptics = store.getString(KEY_HAPTICS)?.toBooleanStrictOrNull() ?: defaults.haptics
        ).normalized()
    }

    private fun save(previous: GlassSettings, value: GlassSettings) {
        if (value.enabled != previous.enabled) store.putString(KEY_ENABLED, value.enabled.toString())
        if (value.intensity != previous.intensity) store.putString(KEY_INTENSITY, value.intensity.toString())
        if (value.accent != previous.accent) store.putString(KEY_ACCENT, value.accent.name)
        if (value.pressPhysics != previous.pressPhysics) store.putString(KEY_PRESS, value.pressPhysics.toString())
        if (value.refraction != previous.refraction) store.putString(KEY_REFRACTION, value.refraction.toString())
        if (value.auroraMode != previous.auroraMode) store.putString(KEY_AURORA_MODE, value.auroraMode.name)
        if (value.auroraStrength != previous.auroraStrength) store.putString(KEY_AURORA_STRENGTH, value.auroraStrength.toString())
        if (value.auroraDrift != previous.auroraDrift) store.putString(KEY_AURORA_DRIFT, value.auroraDrift.toString())
        if (value.haptics != previous.haptics) store.putString(KEY_HAPTICS, value.haptics.toString())
    }

    private companion object {
        const val STORE_NAME = "phimbud_glass_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_INTENSITY = "intensity"
        const val KEY_ACCENT = "accent"
        const val KEY_PRESS = "press_physics"
        const val KEY_REFRACTION = "refraction"
        const val KEY_AURORA_MODE = "aurora_mode"
        const val KEY_AURORA_STRENGTH = "aurora_strength"
        const val KEY_AURORA_DRIFT = "aurora_drift"
        const val KEY_HAPTICS = "haptics"
    }
}

val LocalGlassSettings = staticCompositionLocalOf { GlassSettings() }
val LocalGlassSettingsController = staticCompositionLocalOf<GlassSettingsController?> { null }
