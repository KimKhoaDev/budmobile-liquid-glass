package com.phimbud.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
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

private val GlassBarShape = RoundedCornerShape(percent = 50)
private val GlassPillShape = RoundedCornerShape(percent = 50)

@Composable
actual fun GlassBottomNavBar(
    state: GlassBackdropState,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier
) {
    val items = NavItem.entries
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .drawBackdrop(
                    backdrop = state.backdrop,
                    shape = { GlassBarShape },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(
                            refractionHeight = 16f.dp.toPx(),
                            refractionAmount = 32f.dp.toPx()
                        )
                    },
                    onDrawSurface = { drawRect(Color(0xFF141414).copy(alpha = 0.40f)) }
                )
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / items.size
            val pillOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                label = "glassPillOffset"
            )

            // Selection "liquid" pill
            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(GlassPillShape)
                    .background(Color.White.copy(alpha = 0.14f))
            )

            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val tint by animateColorAsState(
                        targetValue = if (selected) NetflixRed else NetflixTextSecondary,
                        label = "glassTabTint"
                    )
                    Column(
                        modifier = Modifier
                            .width(tabWidth)
                            .fillMaxHeight()
                            .clip(GlassPillShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onNavigate(item.route) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = tint,
                            modifier = Modifier.height(24.dp)
                        )
                        Text(
                            text = item.title,
                            color = tint,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
