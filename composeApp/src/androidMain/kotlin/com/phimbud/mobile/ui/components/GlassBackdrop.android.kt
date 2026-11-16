package com.phimbud.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.phimbud.mobile.ui.components.glass.InteractiveHighlight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.phimbud.mobile.ui.components.glass.LiquidBottomTab
import com.phimbud.mobile.ui.components.glass.LiquidBottomTabs
import com.phimbud.mobile.ui.settings.LocalGlassSettings
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

@Stable
actual class GlassBackdropState internal constructor(internal val backdrop: LayerBackdrop)

@Composable
actual fun rememberGlassBackdropState(): GlassBackdropState {
    val backdrop = rememberLayerBackdrop()
    return remember(backdrop) { GlassBackdropState(backdrop) }
}

actual fun Modifier.glassBackdropSource(state: GlassBackdropState): Modifier =
    this.layerBackdrop(state.backdrop)

@Composable
actual fun GlassBottomNavBar(
    state: GlassBackdropState,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier
) {
    val glass = LocalGlassSettings.current
    val items = NavItem.entries
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    // Stable lambdas: LiquidBottomTabs keys its internal state on the selectedTabIndex lambda.
    val selectedIndexState = rememberUpdatedState(selectedIndex)
    val currentRouteState = rememberUpdatedState(currentRoute)
    val onNavigateState = rememberUpdatedState(onNavigate)
    val selectedTabIndex = remember { { selectedIndexState.value } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, bottom = 12.dp)
    ) {
        LiquidBottomTabs(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { index ->
                val route = items[index].route
                // Avoid re-firing navigation for programmatic selection changes.
                if (route != currentRouteState.value) onNavigateState.value(route)
            },
            backdrop = state.backdrop,
            tabsCount = items.size,
            accentColor = glass.accent.color,
            refraction = glass.refraction && glass.enabled,
            intensity = if (glass.enabled) glass.intensity else 2.2f
        ) {
            items.forEach { item ->
                LiquidBottomTab(onClick = { onNavigateState.value(item.route) }) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = NetflixTextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = item.title,
                        color = NetflixTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

actual fun Modifier.platformLiquidGlass(
    shape: Shape,
    tint: Color,
    fillAlpha: Float,
    interactive: Boolean
): Modifier =
    if (!interactive) {
        this.glassBody(shape, tint, fillAlpha, null)
    } else {
        // Same physics as the library demo / our back button: the glass inflates, drifts a little
        // towards the finger and a soft light follows the touch (clipped to the shape by drawBackdrop).
        this.composed {
            val scope = rememberCoroutineScope()
            val highlight = remember(scope) { InteractiveHighlight(scope) }
            Modifier
                .glassBody(shape, tint, fillAlpha) {
                    val progress = highlight.pressProgress
                    if (progress > 0f) {
                        val grow = 6f.dp.toPx() * progress
                        scaleX = 1f + grow / size.width
                        scaleY = 1f + grow / size.height
                        translationX = highlight.offset.x.coerceIn(-size.width / 2f, size.width / 2f) * 0.05f
                        translationY = highlight.offset.y.coerceIn(-size.height / 2f, size.height / 2f) * 0.05f
                    }
                }
                .then(highlight.modifier)
                .then(highlight.gestureModifier)
        }
    }

private fun Modifier.glassBody(
    shape: Shape,
    tint: Color,
    fillAlpha: Float,
    layerBlock: (GraphicsLayerScope.() -> Unit)?
): Modifier =
    this.drawBackdrop(
        backdrop = emptyBackdrop(),
        shape = { shape },
        effects = {},
        highlight = { Highlight.Default.copy(width = 1f.dp, alpha = 1f) },
        shadow = { Shadow(radius = 14.dp, color = Color.Black.copy(alpha = 0.4f)) },
        innerShadow = { InnerShadow(radius = 10.dp, color = Color.White.copy(alpha = 0.16f)) },
        layerBlock = layerBlock,
        onDrawSurface = {
            if (tint.isSpecified) {
                drawRect(tint.copy(alpha = (fillAlpha * 4f).coerceAtMost(0.92f)))
            } else {
                drawRect(Color.White.copy(alpha = fillAlpha))
            }
            // Top sheen: a soft vertical light falloff gives the surface its "wet glass" depth.
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (tint.isSpecified) 0.12f else 0.16f), Color.Transparent)
                )
            )
        }
    )

@Composable
actual fun LiquidGlassIconButton(
    state: GlassBackdropState,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val glass = LocalGlassSettings.current
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope = animationScope) }
    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = state.backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    if (glass.refraction && glass.enabled) lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                layerBlock = {
                    // Inflate while pressed, following the finger slightly like the library demo.
                    val progress = interactiveHighlight.pressProgress
                    val scale = lerp(1f, 1f + 6f.dp.toPx() / size.height, progress)
                    scaleX = scale
                    scaleY = scale
                    translationX = interactiveHighlight.offset.x * 0.05f
                    translationY = interactiveHighlight.offset.y * 0.05f
                },
                onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.18f)) }
            )
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .size(44.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
actual fun ProgressiveTopBlur(
    state: GlassBackdropState,
    height: Dp,
    modifier: Modifier
) {
    val glass = LocalGlassSettings.current
    Box(modifier.fillMaxWidth().height(height)) {
        // Legibility scrim underneath the blur.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent
                    )
                )
        )
        if (glass.enabled) {
            // Three blurs of decreasing strength, each masked to a shorter-reaching fade, add up
            // to a smooth progressive ramp (strongest at the top edge).
            BlurStrip(state, radius = 22.dp, fadeEnd = 0.50f)
            BlurStrip(state, radius = 10.dp, fadeEnd = 0.78f)
            BlurStrip(state, radius = 3.dp, fadeEnd = 1.00f)
        }
    }
}

@Composable
private fun BlurStrip(state: GlassBackdropState, radius: Dp, fadeEnd: Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        fadeEnd to Color.Transparent,
                        1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            .drawBackdrop(
                backdrop = state.backdrop,
                shape = { RectangleShape },
                effects = { blur(radius.toPx()) },
                highlight = null,
                shadow = null
            )
    )
}
