package com.phimbud.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp

@Stable
actual class GlassBackdropState

@Composable
actual fun rememberGlassBackdropState(): GlassBackdropState = remember { GlassBackdropState() }

actual fun Modifier.glassBackdropSource(state: GlassBackdropState): Modifier = this

@Composable
actual fun GlassBottomNavBar(
    state: GlassBackdropState,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier
) {
    BottomNavBar(currentRoute = currentRoute, onNavigate = onNavigate)
}

/** Non-Android fallback: translucent fill with a soft white rim, approximating the glass look. */
actual fun Modifier.platformLiquidGlass(
    shape: Shape,
    tint: Color,
    fillAlpha: Float,
    ambient: GlassBackdropState?,
    interactive: Boolean
): Modifier =
    this
        .then(if (interactive) Modifier.liquidPress() else Modifier)
        .clip(shape)
        .background(
            if (tint.isSpecified) tint.copy(alpha = (fillAlpha * 4f).coerceAtMost(0.85f))
            else Color.White.copy(alpha = fillAlpha)
        )
        .border(
            width = 0.75.dp,
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.3f))
            ),
            shape = shape
        )

@Composable
actual fun LiquidGlassIconButton(
    state: GlassBackdropState,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .liquidPress()
            .liquidGlass(CircleShape, fillAlpha = 0.16f)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
actual fun ProgressiveTopBlur(
    state: GlassBackdropState,
    height: Dp,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.92f),
                    0.55f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Transparent
                )
            )
    )
}

@Composable
actual fun PlatformLiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color,
    ambient: GlassBackdropState?,
    modifier: Modifier
) = FallbackLiquidToggle(checked, onCheckedChange, modifier, accent)

@Composable
actual fun PlatformLiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: Color,
    ambient: GlassBackdropState?,
    modifier: Modifier
) = FallbackLiquidSlider(value, onValueChange, valueRange, accent, modifier)
