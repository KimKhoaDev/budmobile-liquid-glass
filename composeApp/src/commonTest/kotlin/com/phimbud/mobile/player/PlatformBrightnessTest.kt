package com.phimbud.mobile.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import com.phimbud.mobile.ui.screens.player.brightnessIconForLevel
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformBrightnessTest {

    @Test
    fun brightnessIconAdaptsSmoothlyToLevel() {
        assertEquals(Icons.Default.BrightnessLow, brightnessIconForLevel(0.01f))
        assertEquals(Icons.Default.BrightnessLow, brightnessIconForLevel(0.20f))
        assertEquals(Icons.Default.BrightnessLow, brightnessIconForLevel(0.34f))

        assertEquals(Icons.Default.BrightnessMedium, brightnessIconForLevel(0.35f))
        assertEquals(Icons.Default.BrightnessMedium, brightnessIconForLevel(0.50f))
        assertEquals(Icons.Default.BrightnessMedium, brightnessIconForLevel(0.69f))

        assertEquals(Icons.Default.BrightnessHigh, brightnessIconForLevel(0.70f))
        assertEquals(Icons.Default.BrightnessHigh, brightnessIconForLevel(0.85f))
        assertEquals(Icons.Default.BrightnessHigh, brightnessIconForLevel(1.0f))
    }

    @Test
    fun brightnessClampingAndPercentageCalculationsAreExact() {
        val clampMin = (-0.5f).coerceIn(0.01f, 1.0f)
        assertEquals(0.01f, clampMin)
        assertEquals("1%", "${(clampMin * 100).roundToInt()}%")

        val mid = 0.556f.coerceIn(0.01f, 1.0f)
        assertEquals("56%", "${(mid * 100).roundToInt()}%")

        val clampMax = 1.5f.coerceIn(0.01f, 1.0f)
        assertEquals(1.0f, clampMax)
        assertEquals("100%", "${(clampMax * 100).roundToInt()}%")
    }
}
