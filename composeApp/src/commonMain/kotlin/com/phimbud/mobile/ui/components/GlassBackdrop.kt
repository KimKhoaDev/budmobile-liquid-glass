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
 * inner glow. It does not sample what is behind it, so it is safe to use anywhere (inside scrolling
 * content, above video, inside dialogs) without any backdrop wiring.
 *
 * @param tint optional accent colour mixed into the fill (e.g. brand red for primary actions).
 * @param fillAlpha opacity of the white/tint fill.
 */
expect fun Modifier.platformLiquidGlass(
    shape: Shape,
    tint: Color = Color.Unspecified,
    fillAlpha: Float = 0.14f,
    /** Adds press physics: inflate, finger-following drift and a touch-tracking light (Android). */
    interactive: Boolean = false
): Modifier

/**
 * Round icon button that refracts whatever [state]'s backdrop source draws behind it (Android),
 * with spring press physics. Must be placed OUTSIDE the node marked with [glassBackdropSource].
 * Other platforms render a regular glass circle.
 */
@Composable
expect fun LiquidGlassIconButton(
    state: GlassBackdropState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
)

/**
 * Progressive blur + scrim for the top of a scrolling screen. Content scrolling under it is
 * blurred strongly at the very top and fades to sharp towards the bottom edge (Android renders
 * three stacked real backdrop blurs; other platforms fall back to a gradient scrim).
 * Place it as a sibling ABOVE the node marked with [glassBackdropSource] and BELOW the header.
 */
@Composable
expect fun ProgressiveTopBlur(
    state: GlassBackdropState,
    height: Dp,
    modifier: Modifier = Modifier
)
