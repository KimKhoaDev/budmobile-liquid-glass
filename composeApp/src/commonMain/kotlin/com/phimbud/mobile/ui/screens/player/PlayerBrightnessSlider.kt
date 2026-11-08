package com.phimbud.mobile.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.ui.theme.NetflixDarkGray
import com.phimbud.mobile.ui.theme.NetflixRed
import kotlin.math.roundToInt

fun brightnessIconForLevel(brightness: Float): ImageVector {
    return when {
        brightness < 0.35f -> Icons.Default.BrightnessLow
        brightness < 0.70f -> Icons.Default.BrightnessMedium
        else -> Icons.Default.BrightnessHigh
    }
}

/**
 * Vertical Netflix-style Brightness Slider component.
 * Can be used as an interactive in-HUD slider or as an OSD popup indicator.
 */
@Composable
fun PlayerBrightnessBar(
    brightness: Float,
    onBrightnessChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentOnBrightnessChanged by rememberUpdatedState(onBrightnessChanged)
    val clampedBrightness = brightness.coerceIn(0.01f, 1.0f)
    val percentText = "${(clampedBrightness * 100).roundToInt()}%"
    val icon = brightnessIconForLevel(clampedBrightness)

    Surface(
        color = NetflixDarkGray.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .width(36.dp)
                .height(160.dp)
        ) {
            // Sun / Brightness Icon
            Icon(
                imageVector = icon,
                contentDescription = "Độ sáng $percentText",
                tint = NetflixRed,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Vertical Track and Fill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
                    .then(
                        if (currentOnBrightnessChanged != null) {
                            Modifier.pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        val delta = -dragAmount / size.height.toFloat()
                                        currentOnBrightnessChanged?.invoke(
                                            (clampedBrightness + delta).coerceIn(0.01f, 1.0f)
                                        )
                                    }
                                )
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(clampedBrightness)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Percentage Text
            Text(
                text = percentText,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Floating OSD overlay for Brightness adjustment when user drags vertically.
 */
@Composable
fun PlayerBrightnessOsd(
    visible: Boolean,
    brightness: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        PlayerBrightnessBar(
            brightness = brightness,
            onBrightnessChanged = null
        )
    }
}
