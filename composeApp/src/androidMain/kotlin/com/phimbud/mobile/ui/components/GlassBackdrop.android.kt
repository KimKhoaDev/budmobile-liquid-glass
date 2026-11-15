package com.phimbud.mobile.ui.components

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.phimbud.mobile.ui.components.glass.LiquidBottomTab
import com.phimbud.mobile.ui.components.glass.LiquidBottomTabs
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
            accentColor = NetflixRed
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

actual fun Modifier.liquidGlass(shape: Shape, tint: Color, fillAlpha: Float): Modifier =
    this.drawBackdrop(
        backdrop = emptyBackdrop(),
        shape = { shape },
        effects = {},
        highlight = { Highlight.Default.copy(alpha = 0.9f) },
        shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.35f)) },
        innerShadow = { InnerShadow(radius = 6.dp, color = Color.White.copy(alpha = 0.18f)) },
        onDrawSurface = {
            if (tint.isSpecified) {
                drawRect(tint.copy(alpha = (fillAlpha * 4f).coerceAtMost(0.85f)))
            } else {
                drawRect(Color.White.copy(alpha = fillAlpha))
            }
        }
    )
