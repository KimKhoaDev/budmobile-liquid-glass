package com.phimbud.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@Stable
interface PlatformBrightnessController {
    val currentBrightness: Float // Normalized 0.01f to 1.0f
    val isHardwareControlled: Boolean

    fun setBrightness(value: Float)
    fun restoreDefault()
}

@Composable
expect fun rememberPlatformBrightnessController(): PlatformBrightnessController
