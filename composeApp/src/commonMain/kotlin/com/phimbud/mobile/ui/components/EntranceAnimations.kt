package com.phimbud.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * One-shot staggered entrance: fades in while sliding up, delayed by [index] steps. The "played"
 * flag is saved per item, so lazy-list items that scroll out and back in do not replay it.
 */
fun Modifier.entrance(index: Int, travel: Dp = 28.dp, stepMillis: Long = 70L): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(index * stepMillis)
            progress.animateTo(1f, tween(460, easing = EaseOutCubic))
            played = true
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * travel.toPx()
    }
}

/** Hero image settles from a slight zoom, like a camera pulling back as the film opens. */
fun Modifier.heroZoom(from: Float = 1.14f): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            progress.animateTo(1f, tween(900, easing = EaseOutCubic))
            played = true
        }
    }
    graphicsLayer {
        val scale = from + (1f - from) * progress.value
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0.5f, 0.4f)
    }
}
