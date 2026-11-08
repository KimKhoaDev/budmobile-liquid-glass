package com.phimbud.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal class DesktopBrightnessController : PlatformBrightnessController {
    override var currentBrightness by mutableFloatStateOf(1.0f)
        private set

    override val isHardwareControlled: Boolean = false

    override fun setBrightness(value: Float) {
        currentBrightness = value.coerceIn(0.10f, 1.0f)
    }

    override fun restoreDefault() {
        currentBrightness = 1.0f
    }
}

@Composable
actual fun rememberPlatformBrightnessController(): PlatformBrightnessController {
    val controller = remember { DesktopBrightnessController() }

    DisposableEffect(controller) {
        onDispose {
            controller.restoreDefault()
        }
    }

    return controller
}
