package com.phimbud.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.phimbud.mobile.ui.settings.LocalGlassSettings
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
    val physics = LocalGlassSettings.current.pressPhysics
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(pressedScale, physics) {
            if (!physics) return@pointerInput
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

/**
 * Liquid glass switch: the thumb stretches while pressed and slides with an under-damped spring,
 * the track fills with [accent] when on.
 */
@Composable
internal fun FallbackLiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalGlassSettings.current.accent.color
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val trackWidth = 58.dp
    val trackHeight = 34.dp
    val inset = 3.dp
    val thumbWidth by animateDpAsState(
        targetValue = if (pressed) 38.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "toggleThumbWidth"
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) trackWidth - inset - thumbWidth else inset,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f),
        label = "toggleThumbX"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.18f),
        label = "toggleTrackColor"
    )
    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .background(trackColor, RoundedCornerShape(50))
            .liquidGlass(RoundedCornerShape(50), fillAlpha = 0.06f)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(thumbWidth, trackHeight - inset * 2)
                .liquidGlass(RoundedCornerShape(50), tint = Color.White, fillAlpha = 0.24f)
        )
    }
}

/** Liquid switch. Android: the Kyant0 demo toggle (draggable, lens-refracting thumb). */
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalGlassSettings.current.accent.color
) {
    PlatformLiquidToggle(checked, onCheckedChange, accent, LocalAmbientBackdrop.current, modifier)
}

/** Liquid slider. Android: the Kyant0 demo slider (springy glass thumb that magnifies the track). */
@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    accent: Color = LocalGlassSettings.current.accent.color
) {
    PlatformLiquidSlider(value, onValueChange, valueRange, accent, LocalAmbientBackdrop.current, modifier)
}

@Composable
internal fun FallbackLiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = Color.Gray.copy(alpha = 0.4f)
        ),
        interactionSource = interaction,
        thumb = { LiquidSliderThumb(interaction) },
        modifier = modifier.fillMaxWidth()
    )
}
