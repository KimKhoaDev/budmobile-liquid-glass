package com.phimbud.mobile

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {

    private var isInPlayerScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PhimBudApp
        requestHighestRefreshRate()

        setContent {
            App(
                repository = app.repository,
                onPlayerScreenActiveChanged = { active ->
                    isInPlayerScreen = active
                }
            )
        }
    }

    /**
     * Many phones keep third-party apps at 60Hz unless a faster display mode is requested.
     * Ask for the highest refresh rate available at the current resolution.
     */
    @Suppress("DEPRECATION")
    private fun requestHighestRefreshRate() {
        runCatching {
            val display = (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
                else windowManager.defaultDisplay
                ) ?: return
            val current = display.mode
            val best = display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
                ?: return
            window.attributes = window.attributes.also {
                it.preferredDisplayModeId = best.modeId
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isInPlayerScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }
}
