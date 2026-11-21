package com.phimbud.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.phimbud.mobile.ui.aurora.LocalAuroraController
import com.phimbud.mobile.ui.aurora.accentPalette
import com.phimbud.mobile.ui.settings.AuroraMode
import com.phimbud.mobile.ui.settings.LocalGlassSettings
import com.phimbud.mobile.ui.theme.NetflixBlack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app-wide "wallpaper": soft coloured glows on black that every glass control refracts (the same
 * role the photo plays in the Kyant0 demo), so it is also the node marked as the ambient backdrop.
 *
 * The colours follow the user's Aurora setting: the poster you are looking at (cross-faded),
 * the accent colour, or nothing. An optional drift animation moves the glows slowly.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val settings = LocalGlassSettings.current
    val controller = LocalAuroraController.current
    val accentColors = remember(settings.accent) { accentPalette(settings.accent.color) }
    val target = when (settings.auroraMode) {
        AuroraMode.POSTER -> controller?.palette ?: accentColors
        AuroraMode.ACCENT -> accentColors
        AuroraMode.OFF -> accentColors
    }

    val primary by animateColorAsState(target.primary, tween(800), label = "auroraPrimary")
    val secondary by animateColorAsState(target.secondary, tween(900), label = "auroraSecondary")
    val tertiary by animateColorAsState(target.tertiary, tween(1000), label = "auroraTertiary")

    val strength = settings.auroraStrength
    val visible = settings.auroraMode != AuroraMode.OFF

    // Read only in the draw phase, so animating it never recomposes anything.
    val phase: State<Float> = if (visible && settings.auroraDrift) {
        rememberInfiniteTransition(label = "auroraDrift").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Restart),
            label = "auroraPhase"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    val driftEnabled = settings.auroraDrift

    Box(
        modifier
            .background(NetflixBlack)
            .drawBehind {
                if (!visible) return@drawBehind
                val w = size.width
                val h = size.height
                val t = phase.value * 2f * PI.toFloat()
                val amp = if (driftEnabled) 0.09f else 0f

                fun glow(color: Color, alpha: Float, cx: Float, cy: Float, radius: Float, offset: Float) {
                    val center = Offset(
                        w * (cx + amp * sin(t + offset)),
                        h * (cy + amp * cos(t * 0.8f + offset))
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = (alpha * strength).coerceAtMost(0.9f)), Color.Transparent),
                            center = center,
                            radius = radius
                        )
                    )
                }
                glow(primary, 0.42f, 0.08f, 0.02f, w * 1.0f, 0f)
                glow(secondary, 0.30f, 1.0f, 0.38f, w * 0.9f, 2.1f)
                glow(tertiary, 0.22f, 0.0f, 0.82f, w * 0.85f, 4.2f)
            }
    )
}
