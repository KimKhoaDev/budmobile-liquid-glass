package com.phimbud.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.UIKit.UIScreen

internal class IosBrightnessController(
    private val originalBrightness: Float
) : PlatformBrightnessController {

    override var currentBrightness by mutableFloatStateOf(originalBrightness)
        private set

    override val isHardwareControlled: Boolean = true

    override fun setBrightness(value: Float) {
        val clamped = value.coerceIn(0.01f, 1.0f)
        currentBrightness = clamped
        UIScreen.mainScreen.brightness = clamped.toDouble()
    }

    override fun restoreDefault() {
        UIScreen.mainScreen.brightness = originalBrightness.toDouble()
    }
}

@Composable
actual fun rememberPlatformBrightnessController(): PlatformBrightnessController {
    val originalBrightness = remember {
        val screen = UIScreen.mainScreen
        screen.brightness.toFloat().coerceIn(0.01f, 1.0f)
    }

    val controller = remember(originalBrightness) {
        IosBrightnessController(originalBrightness)
    }

    DisposableEffect(controller) {
        onDispose {
            controller.restoreDefault()
        }
    }

    return controller
}
