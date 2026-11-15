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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
