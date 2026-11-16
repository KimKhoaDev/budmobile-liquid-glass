package com.phimbud.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import com.phimbud.mobile.ui.settings.LocalGlassSettings
import com.phimbud.mobile.ui.theme.NetflixRed

/**
 * Liquid Glass surface that follows the user's [com.phimbud.mobile.ui.settings.GlassSettings]:
 * master on/off, intensity (fill opacity) and accent colour (any tint equal to the brand red is
 * swapped for the chosen accent). See [platformLiquidGlass] for the rendering itself.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = Color.Unspecified,
    fillAlpha: Float = 0.14f
): Modifier = settingsAwareGlass(shape, tint, fillAlpha, interactive = false)

/**
 * Same as [liquidGlass] plus press physics (inflate, finger-following drift and touch light).
 * Use it on anything tappable instead of combining [liquidPress] with [liquidGlass].
 */
fun Modifier.liquidGlassPress(
    shape: Shape,
    tint: Color = Color.Unspecified,
    fillAlpha: Float = 0.14f
): Modifier = settingsAwareGlass(shape, tint, fillAlpha, interactive = true)

private fun Modifier.settingsAwareGlass(
    shape: Shape,
    tint: Color,
    fillAlpha: Float,
    interactive: Boolean
): Modifier = composed {
    val settings = LocalGlassSettings.current
    val effectiveTint = if (tint == NetflixRed) settings.accent.color else tint
    val alpha = (fillAlpha * settings.intensity).coerceIn(0f, 0.5f)
    val enabled = settings.enabled
    val physics = settings.pressPhysics
    remember(shape, effectiveTint, alpha, enabled, interactive, physics) {
        if (enabled) {
            Modifier.platformLiquidGlass(shape, effectiveTint, alpha, interactive && physics)
        } else {
            // Flat fallback: plain translucent fill, no rim / shadow / glow.
            Modifier.background(
                if (effectiveTint.isSpecified) effectiveTint.copy(alpha = 0.9f)
                else Color.White.copy(alpha = 0.10f),
                shape
            )
        }
    }
}
