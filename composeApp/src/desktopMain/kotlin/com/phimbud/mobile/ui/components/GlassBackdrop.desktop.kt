package com.phimbud.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

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
