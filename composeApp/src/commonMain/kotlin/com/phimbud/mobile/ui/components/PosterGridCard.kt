package com.phimbud.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.platform.formatOneDecimal
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixGold
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

private val PosterRimBrush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.04f))
)

/**
 * Poster tile shared by Khám phá and Của tôi. Built for grid performance: no backdrop layers, no
 * per-item animation state on touch devices, stable lambdas and a single static rim.
 */
@Composable
fun PosterGridCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWideScreen: Boolean = false
) = PosterTile(
    posterUrl = movie.posterUrl,
    title = movie.displayTitle,
    year = movie.year,
    rating = movie.imdbRating,
    onClick = onClick,
    modifier = modifier,
    isWideScreen = isWideScreen
)

/** Generic poster tile (any data source): see [PosterGridCard] for the performance notes. */
@Composable
fun PosterTile(
    posterUrl: String?,
    title: String,
    year: Int,
    rating: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWideScreen: Boolean = false
) {
    val shape = RoundedCornerShape(if (isWideScreen) 12.dp else 16.dp)

    // Hover scale only exists on pointer devices; on phones the card carries no animation state.
    val hoverSource = if (isWideScreen) remember { MutableInteractionSource() } else null
    val hovered by (hoverSource?.collectIsHoveredAsState()
        ?: remember { androidx.compose.runtime.mutableStateOf(false) })
    val hoverScale = if (isWideScreen) {
        animateFloatAsState(
            targetValue = if (hovered) 1.04f else 1f,
            animationSpec = spring(stiffness = 300f),
            label = "posterHover"
        ).value
    } else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isWideScreen) Modifier.graphicsLayer { scaleX = hoverScale; scaleY = hoverScale } else Modifier.liquidPress(1.035f))
            .then(if (hoverSource != null) Modifier.hoverable(hoverSource) else Modifier)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(NetflixCard)
                .border(0.75.dp, PosterRimBrush, shape)
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = title,
                    color = NetflixTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.Center)
                )
            }

            if (rating != null && rating > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = NetflixGold,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = formatOneDecimal(rating),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = NetflixTextPrimary,
            fontSize = if (isWideScreen) 14.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (year > 0) {
            Text(
                text = year.toString(),
                color = NetflixTextSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}
