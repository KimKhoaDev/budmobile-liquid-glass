package com.phimbud.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Liquid "inflate" press physics: the element springs up while a finger is down and wobbles back
 * with an under-damped spring on release. Observes pointers in the Initial pass without consuming
 * anything, so the wrapped element's own click / drag handling is unaffected.
 * Place it BEFORE [liquidGlass] so the glass scales together with the content.
 */
fun Modifier.liquidPress(pressedScale: Float = 1.07f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(pressedScale) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { scale.animateTo(pressedScale, spring(dampingRatio = 0.55f, stiffness = 700f)) }
                val slop = viewConfiguration.touchSlop * 2f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if ((change.position - down.position).getDistance() > slop) break
                }
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f)) }
            }
        }
}

/** Glass slider thumb that balloons with a spring while it is pressed or dragged. */
@Composable
fun LiquidSliderThumb(
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier
) {
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (dragged || pressed) 1.5f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "liquidThumbScale"
    )
    Box(
        modifier
            .size(26.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlass(CircleShape, tint = Color.White, fillAlpha = 0.22f)
    )
}
