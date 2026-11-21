package com.phimbud.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.phimbud.mobile.ui.settings.LocalGlassSettings

/** A light "tick" (toggles, tab changes, chips). Does nothing when haptics are turned off. */
@Composable
fun rememberHapticTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalGlassSettings.current.haptics
    return remember(haptic, enabled) {
        { if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}
