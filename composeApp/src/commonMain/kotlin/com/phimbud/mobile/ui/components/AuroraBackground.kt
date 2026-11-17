package com.phimbud.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.phimbud.mobile.ui.settings.LocalGlassSettings
import com.phimbud.mobile.ui.theme.NetflixBlack

/**
 * Soft accent-coloured glow on black. It is the "wallpaper" that glass controls refract (the same
 * role the photo plays in the Kyant0 demo), so it is also the node marked as the ambient backdrop.
 * Drawn once with plain gradients, so it costs almost nothing per frame.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val accent = LocalGlassSettings.current.accent.color
    val second = lerp(accent, Color(0xFF3A2BD9), 0.55f)
    val third = lerp(accent, Color(0xFFFF8A00), 0.45f)
    Box(
        modifier
            .background(NetflixBlack)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun glow(color: Color, alpha: Float, cx: Float, cy: Float, radius: Float) {
                    val center = Offset(w * cx, h * cy)
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                            center = center,
                            radius = radius
                        )
                    )
                }
                glow(accent, 0.42f, 0.08f, 0.02f, w * 1.0f)
                glow(second, 0.30f, 1.0f, 0.38f, w * 0.9f)
                glow(third, 0.22f, 0.0f, 0.82f, w * 0.85f)
            }
    )
}
