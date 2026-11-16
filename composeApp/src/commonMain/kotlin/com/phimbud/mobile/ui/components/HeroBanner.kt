package com.phimbud.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixDarkGray
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

private val HeroTopScrimBrush = Brush.verticalGradient(
    colors = listOf(
        NetflixBlack.copy(alpha = 0.85f),
        NetflixBlack.copy(alpha = 0.45f),
        Color.Transparent
    )
)

private val HeroBottomScrimBrush = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        NetflixBlack.copy(alpha = 0.25f),
        NetflixBlack.copy(alpha = 0.88f),
        NetflixBlack
    ),
    startY = 160f
)

private val HeroDesktopLeftScrimBrush = Brush.horizontalGradient(
    colors = listOf(
        NetflixBlack.copy(alpha = 0.96f),
        NetflixBlack.copy(alpha = 0.82f),
        NetflixBlack.copy(alpha = 0.45f),
        Color.Transparent
    ),
    startX = 0f,
    endX = 850f
)

@Composable
fun HeroBanner(
    movie: Movie,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit,
    rankBadgeText: String? = null,
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bannerHeight = if (isWideScreen) 540.dp else 450.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeight)
    ) {
        AsyncImage(
            model = movie.backdropUrl ?: movie.posterUrl,
            contentDescription = movie.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Top gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .align(Alignment.TopCenter)
                .background(HeroTopScrimBrush)
        )

        // Desktop horizontal left-to-right vignette so text stands out crisply
        if (isWideScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HeroDesktopLeftScrimBrush)
            )
        }

        // Bottom gradient scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HeroBottomScrimBrush)
        )

        if (isWideScreen) {
            // Widescreen / Desktop Layout (Cinematic left-aligned)
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(start = 44.dp, end = 44.dp, bottom = 44.dp)
            ) {
                if (!rankBadgeText.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.liquidGlass(RoundedCornerShape(4.dp), tint = NetflixRed, fillAlpha = 0.22f)
                        ) {
                            Text(
                                text = "TOP 10",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = rankBadgeText,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = movie.displayTitle,
                    color = NetflixTextPrimary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Start,
                    lineHeight = 46.sp,
                    maxLines = 2,
                    modifier = Modifier.widthIn(max = 720.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (movie.year > 0) {
                        Text(
                            text = movie.year.toString(),
                            color = NetflixTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(text = " • ", color = NetflixTextSecondary, fontSize = 14.sp)
                    }
                    Text(
                        text = if (movie.translation == "subtitle") "Phụ đề" else "Lồng tiếng",
                        color = NetflixRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.liquidGlass(RoundedCornerShape(3.dp))
                    ) {
                        Text(
                            text = "HD",
                            color = NetflixTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
                        modifier = Modifier.liquidPress().liquidGlass(RoundedCornerShape(50), tint = Color.White, fillAlpha = 0.23f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Phát",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Phát",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Button(
                        onClick = onInfoClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        modifier = Modifier.liquidPress().liquidGlass(RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Thông tin khác",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thông tin chi tiết",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        } else {
            // Mobile Layout (Centered vertical)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (!rankBadgeText.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.liquidGlass(RoundedCornerShape(4.dp), tint = NetflixRed, fillAlpha = 0.22f)
                        ) {
                            Text(
                                text = "TOP 10",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = rankBadgeText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = movie.displayTitle,
                    color = NetflixTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onPlayClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                            .liquidPress().liquidGlass(RoundedCornerShape(50), tint = Color.White, fillAlpha = 0.23f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Phát",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Phát",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Button(
                        onClick = onInfoClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                            .liquidPress().liquidGlass(RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Thông tin",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Thông tin",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
