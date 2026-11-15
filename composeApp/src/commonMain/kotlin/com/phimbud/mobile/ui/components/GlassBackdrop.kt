package com.phimbud.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
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
