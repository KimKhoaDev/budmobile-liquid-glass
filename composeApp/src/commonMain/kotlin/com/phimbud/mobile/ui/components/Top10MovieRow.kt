package com.phimbud.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixDarkGray
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

@Composable
fun Top10MovieRow(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
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
                    color = NetflixRed,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "TOP 10",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = NetflixTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onSeeAllClick != null) {
                Text(
                    text = "Bộ lọc ›",
                    color = NetflixRed,
                    fontSize = 13.sp,
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

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = movies.take(10),
                key = { _, m -> m.fid },
                contentType = { _, _ -> "top10_card" }
            ) { index, movie ->
                Top10PosterCard(
                    rank = index + 1,
                    movie = movie,
                    onClick = { onMovieClick(movie) }
                )
            }
        }
    }
}

@Composable
fun Top10PosterCard(
    rank: Int,
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = if (rank >= 10) 154.dp else 142.dp

    Column(
        modifier = modifier
            .width(cardWidth)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(162.dp)
        ) {
            // Giant Netflix-style Rank Number behind / beside the poster
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = if (rank >= 10) (-4).dp else 2.dp, y = 10.dp)
            ) {
                val fontSize = if (rank >= 10) 92.sp else 108.sp
                val letterSpacing = (-6).sp
                // Outer bright stroke
                Text(
                    text = rank.toString(),
                    style = TextStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Black,
                        letterSpacing = letterSpacing,
                        color = Color.White.copy(alpha = 0.72f),
                        drawStyle = Stroke(
                            width = 7f,
                            join = StrokeJoin.Round
                        )
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
                    .width(106.dp)
                    .height(156.dp)
                    .align(Alignment.TopEnd)
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
                } else {
                    Text(
                        text = movie.displayTitle,
                        color = NetflixTextPrimary,
                        fontSize = 12.sp,
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
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "TOP\n10",
                        color = Color.White,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = movie.displayTitle,
            color = NetflixTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 28.dp)
        )
    }
}

@Composable
fun Top10RankedListItem(
    rank: Int,
    movie: Movie,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NetflixDarkGray)
            .clickable { onCardClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Rank Badge
        Box(
            modifier = Modifier.width(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                color = if (rank <= 3) NetflixRed else Color.White.copy(alpha = 0.85f),
                fontSize = if (rank >= 10) 24.sp else 28.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Poster
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(98.dp)
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

        Spacer(modifier = Modifier.width(14.dp))

        // Metadata
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movie.displayTitle,
                color = NetflixTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (movie.nameEn.isNotBlank() && movie.nameVi.isNotBlank() && !movie.nameEn.equals(movie.nameVi, ignoreCase = true)) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = movie.nameEn,
                    color = NetflixTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (movie.year > 0) {
                    Surface(
                        color = NetflixCard,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = movie.year.toString(),
                            color = NetflixTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    color = NetflixRed.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    val transLabel = when {
                        movie.translation.contains("dub", ignoreCase = true) ||
                            movie.translation.contains("voice", ignoreCase = true) -> "Thuyết minh"
                        else -> "Vietsub"
                    }
                    Text(
                        text = transLabel,
                        color = NetflixRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Quick Play Button
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.14f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Phát ngay",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
