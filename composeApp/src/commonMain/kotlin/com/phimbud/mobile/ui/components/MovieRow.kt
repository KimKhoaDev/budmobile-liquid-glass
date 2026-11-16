package com.phimbud.mobile.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.platform.platformHorizontalDragScroll
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary
import kotlinx.coroutines.launch

@Composable
fun MovieRow(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) return

    val horizontalMargin = if (isWideScreen) 44.dp else 16.dp
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isRowHovered by rowInteractionSource.collectIsHoveredAsState()
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth().padding(vertical = if (isWideScreen) 16.dp else 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onSeeAllClick != null) {
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onSeeAllClick() }
                    } else Modifier
                )
                .padding(horizontal = horizontalMargin, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                if (isWideScreen) {
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .width(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(NetflixRed)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Xem tất cả",
                        color = NetflixTextSecondary,
                        fontSize = if (isWideScreen) 13.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Xem tất cả",
                        tint = NetflixTextSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                }
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
                horizontalArrangement = Arrangement.spacedBy(if (isWideScreen) 16.dp else 10.dp),
                modifier = Modifier.platformHorizontalDragScroll(rowState)
            ) {
                items(
                    items = movies,
                    key = { it.fid },
                    contentType = { "movie_card" }
                ) { movie ->
                    MovieCard(
                        movie = movie,
                        onClick = { onMovieClick(movie) },
                        isWideScreen = isWideScreen
                    )
                }
            }

            // Desktop Left Chevron Button
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

            // Desktop Right Chevron Button
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
