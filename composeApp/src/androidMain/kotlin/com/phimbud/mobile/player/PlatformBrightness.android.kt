package com.phimbud.mobile.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

internal class AndroidBrightnessController(
    private val activity: Activity?,
    initialBrightness: Float
) : PlatformBrightnessController {

    override var currentBrightness by mutableFloatStateOf(initialBrightness)
        private set

    override val isHardwareControlled: Boolean = true

    override fun setBrightness(value: Float) {
        val clamped = value.coerceIn(0.01f, 1.0f)
        currentBrightness = clamped
        val window = activity?.window ?: return
        val lp = window.attributes
        lp.screenBrightness = clamped
        window.attributes = lp
    }

    override fun restoreDefault() {
        val window = activity?.window ?: return
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }
}

@Composable
actual fun rememberPlatformBrightnessController(): PlatformBrightnessController {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val initialBrightness = remember(context, activity) {
        val currentWindowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        if (currentWindowBrightness in 0.01f..1.0f) {
            currentWindowBrightness
        } else {
            try {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (_: Throwable) {
                0.5f
            }.coerceIn(0.01f, 1.0f)
        }
    }

    val controller = remember(activity) {
        AndroidBrightnessController(activity, initialBrightness)
    }

    DisposableEffect(controller) {
        onDispose {
            controller.restoreDefault()
        }
    }

    return controller
}
