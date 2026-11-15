package com.phimbud.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Extra space that screens should leave at the bottom of scrollable content so the last items
 * are not hidden behind the floating glass bottom bar. 0.dp when there is no floating bar.
 */
val LocalBottomOverlayInset = compositionLocalOf<Dp> { 0.dp }

/** Space reserved for [GlassBottomNavBar] (64dp bar + 12dp bottom margin + 8dp breathing room). */
val GlassBottomBarInset: Dp = 84.dp

/** Holds whatever the platform needs to render "behind-the-glass" content. */
@Stable
expect class GlassBackdropState

@Composable
expect fun rememberGlassBackdropState(): GlassBackdropState

/** Marks the content that should be visible (blurred / refracted) through the glass bar. */
expect fun Modifier.glassBackdropSource(state: GlassBackdropState): Modifier

/**
 * Bottom navigation bar. Android renders a Liquid Glass capsule floating over the content,
 * other platforms render the regular [BottomNavBar].
 */
@Composable
expect fun GlassBottomNavBar(
    state: GlassBackdropState,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
)

/**
 * Decorative Liquid Glass surface: tinted translucent fill, specular rim highlight, soft shadow and
 * inner glow. Safe to use anywhere (scrolling content, above video, dialogs) without backdrop wiring.
 *
 * @param shape the clip / outline shape.
 * @param tint optional accent colour mixed into the fill (e.g. brand red for primary actions).
 * @param fillAlpha opacity of the white/tint fill.
 */
expect fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = Color.Unspecified,
    fillAlpha: Float = 0.14f
): Modifier
