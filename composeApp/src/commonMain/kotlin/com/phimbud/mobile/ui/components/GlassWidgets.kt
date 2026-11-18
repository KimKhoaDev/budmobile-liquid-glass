package com.phimbud.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.ui.settings.LocalGlassSettings
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

private val PillShape = RoundedCornerShape(50)

private val RimBrush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.05f))
)

/**
 * Glass segmented control with a sliding, spring-driven selection pill.
 * Only two glass layers in total (track + pill), regardless of how many options there are.
 */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalGlassSettings.current.accent.color
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .liquidGlass(PillShape, fillAlpha = 0.08f)
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 1000f),
            label = "segmentOffset"
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .liquidGlass(PillShape, tint = accent, fillAlpha = 0.2f)
        )
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) Color.White else NetflixTextSecondary,
                    animationSpec = tween(110),
                    label = "segmentText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PillShape)
                        .clickable(interactionSource = null, indication = null) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Lightweight glass chip for long scrolling rows: no per-chip backdrop layer, just a translucent
 * fill + gradient rim, so dozens of chips scroll at full frame rate.
 */
@Composable
fun GlassFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalGlassSettings.current.accent.color
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(110),
        label = "chipBackground"
    )
    Text(
        text = label,
        color = if (selected) Color.White else NetflixTextPrimary.copy(alpha = 0.85f),
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .liquidPress(1.05f)
            .clip(PillShape)
            .background(background)
            .border(0.75.dp, RimBrush, PillShape)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp)
    )
}
