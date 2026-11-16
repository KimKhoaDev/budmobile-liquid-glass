package com.phimbud.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.platform.platformHorizontalDragScroll
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixDarkGray
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary
import kotlinx.coroutines.launch

private val Top10RankOutlineStroke = Stroke(
    width = 7f,
    join = StrokeJoin.Round
)

@Composable
fun Top10MovieRow(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) return
    val top10Items = remember(movies) { movies.take(10) }
    val horizontalMargin = if (isWideScreen) 44.dp else 16.dp
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isRowHovered by rowInteractionSource.collectIsHoveredAsState()
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth().padding(vertical = if (isWideScreen) 16.dp else 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalMargin, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.liquidGlass(RoundedCornerShape(4.dp), tint = NetflixRed, fillAlpha = 0.22f)
                ) {
                    Text(
                        text = "TOP 10",
                        color = Color.White,
                        fontSize = if (isWideScreen) 11.sp else 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = NetflixTextPrimary,
                    fontSize = if (isWideScreen) 22.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onSeeAllClick != null) {
                Text(
                    text = "Bộ lọc ›",
                    color = NetflixRed,
                    fontSize = if (isWideScreen) 14.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSeeAllClick() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(rowInteractionSource)
        ) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = horizontalMargin),
                horizontalArrangement = Arrangement.spacedBy(if (isWideScreen) 18.dp else 12.dp),
                modifier = Modifier.platformHorizontalDragScroll(rowState)
            ) {
                itemsIndexed(
                    items = top10Items,
                    key = { _, m -> m.fid },
                    contentType = { _, _ -> "top10_card" }
                ) { index, movie ->
                    Top10PosterCard(
                        rank = index + 1,
                        movie = movie,
                        onClick = { onMovieClick(movie) },
                        isWideScreen = isWideScreen
                    )
                }
            }

            // Desktop Left Chevron
            if (isWideScreen && isRowHovered && rowState.canScrollBackward) {
                IconButton(
                    onClick = {
                        scope.launch {
                            rowState.animateScrollBy(-680f)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(46.dp)
                        .liquidPress().liquidGlass(CircleShape, fillAlpha = 0.18f)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Cuộn sang trái",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).padding(start = 4.dp)
                    )
                }
            }

            // Desktop Right Chevron
            if (isWideScreen && isRowHovered && rowState.canScrollForward) {
                IconButton(
                    onClick = {
                        scope.launch {
                            rowState.animateScrollBy(680f)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(46.dp)
                        .liquidPress().liquidGlass(CircleShape, fillAlpha = 0.18f)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Cuộn sang phải",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun Top10PosterCard(
    rank: Int,
    movie: Movie,
    onClick: () -> Unit,
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardWidth = if (isWideScreen) {
        if (rank >= 10) 225.dp else 210.dp
    } else {
        if (rank >= 10) 154.dp else 142.dp
    }
    val boxHeight = if (isWideScreen) 235.dp else 162.dp
    val posterWidth = if (isWideScreen) 145.dp else 106.dp
    val posterHeight = if (isWideScreen) 230.dp else 156.dp

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isWideScreen && isHovered) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val borderColor by animateColorAsState(
        targetValue = if (isWideScreen && isHovered) Color.White.copy(alpha = 0.55f) else Color.Transparent
    )

    Column(
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(boxHeight)
        ) {
            // Giant Netflix-style Rank Number behind / beside the poster
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(
                        x = if (isWideScreen) (if (rank >= 10) (-6).dp else 4.dp) else (if (rank >= 10) (-4).dp else 2.dp),
                        y = if (isWideScreen) 14.dp else 10.dp
                    )
            ) {
                val fontSize = if (isWideScreen) {
                    if (rank >= 10) 120.sp else 140.sp
                } else {
                    if (rank >= 10) 92.sp else 108.sp
                }
                val letterSpacing = if (isWideScreen) (-8).sp else (-6).sp
                // Outer bright stroke
                Text(
                    text = rank.toString(),
                    style = TextStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Black,
                        letterSpacing = letterSpacing,
                        color = Color.White.copy(alpha = 0.72f),
                        drawStyle = Top10RankOutlineStroke
                    )
                )
                // Inner dark fill
                Text(
                    text = rank.toString(),
                    style = TextStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Black,
                        letterSpacing = letterSpacing,
                        color = Color(0xFF141414)
                    )
                )
            }

            // Movie Poster shifted to the right
            Box(
                modifier = Modifier
                    .width(posterWidth)
                    .height(posterHeight)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (isWideScreen && isHovered) 1.5.dp else 0.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(NetflixCard)
            ) {
                if (!movie.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = movie.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = movie.displayTitle,
                        color = NetflixTextPrimary,
                        fontSize = if (isWideScreen) 14.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.Center)
                    )
                }

                // Top-Right Red Ribbon Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            color = NetflixRed,
                            shape = RoundedCornerShape(bottomStart = 6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.5.dp)
                ) {
                    Text(
                        text = "TOP\n10",
                        color = Color.White,
                        fontSize = if (isWideScreen) 9.sp else 8.sp,
                        lineHeight = if (isWideScreen) 10.sp else 9.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = movie.displayTitle,
            color = if (isWideScreen && isHovered) Color.White else NetflixTextPrimary,
            fontSize = if (isWideScreen) 14.sp else 12.sp,
            fontWeight = if (isWideScreen && isHovered) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = if (isWideScreen) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = if (isWideScreen) 18.sp else 14.sp,
            modifier = Modifier.padding(start = if (isWideScreen) 48.dp else 28.dp)
        )
    }
}

@Composable
fun Top10RankedListItem(
    rank: Int,
    movie: Movie,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit,
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        targetValue = if (isWideScreen && isHovered) Color.White.copy(alpha = 0.35f) else Color.Transparent
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .liquidPress().liquidGlass(RoundedCornerShape(18.dp), fillAlpha = 0.08f)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isWideScreen && isHovered) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onCardClick() }
            .padding(horizontal = if (isWideScreen) 18.dp else 12.dp, vertical = if (isWideScreen) 14.dp else 10.dp)
    ) {
        // Rank Badge
        Box(
            modifier = Modifier.width(if (isWideScreen) 48.dp else 38.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                color = if (rank <= 3) NetflixRed else Color.White.copy(alpha = 0.85f),
                fontSize = if (rank >= 10) (if (isWideScreen) 28.sp else 24.sp) else (if (isWideScreen) 34.sp else 28.sp),
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Poster
        Box(
            modifier = Modifier
                .width(if (isWideScreen) 85.dp else 68.dp)
                .height(if (isWideScreen) 122.dp else 98.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(NetflixCard)
        ) {
            if (!movie.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Metadata
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movie.displayTitle,
                color = NetflixTextPrimary,
                fontSize = if (isWideScreen) 17.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (movie.nameEn.isNotBlank() && movie.nameVi.isNotBlank() && !movie.nameEn.equals(movie.nameVi, ignoreCase = true)) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = movie.nameEn,
                    color = NetflixTextSecondary,
                    fontSize = if (isWideScreen) 13.sp else 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (movie.year > 0) {
                    Text(
                        text = movie.year.toString(),
                        color = NetflixTextSecondary,
                        fontSize = 13.sp
                    )
                    Text(text = " • ", color = NetflixTextSecondary, fontSize = 13.sp)
                }
                Text(
                    text = if (movie.translation == "subtitle") "Phụ đề" else "Lồng tiếng",
                    color = NetflixRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Play Button
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier
                .size(if (isWideScreen) 46.dp else 40.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Phát phim",
                tint = Color.Black,
                modifier = Modifier.size(if (isWideScreen) 28.dp else 24.dp)
            )
        }
    }
}
