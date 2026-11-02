package com.phimbud.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.ui.components.HeroBanner
import com.phimbud.mobile.ui.components.MovieRow
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMovieClick: (Movie) -> Unit,
    onPlayClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NetflixRed)
                }
            }
            is HomeUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = state.message, color = NetflixTextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.loadHomeFeed() },
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                    ) {
                        Text("Thử lại")
                    }
                }
            }
            is HomeUiState.Success -> {
                val feed = state.feed
                val heroMovie = feed.trending.firstOrNull() ?: feed.movies.firstOrNull()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // Hero Banner
                    if (heroMovie != null) {
                        HeroBanner(
                            movie = heroMovie,
                            onPlayClick = {
                                viewModel.playMovie(heroMovie, onPlayClick)
                            },
                            onInfoClick = { onMovieClick(heroMovie) }
                        )
                    }

                    // Content Rows
                    if (feed.watching.isNotEmpty()) {
                        MovieRow(
                            title = "Tiếp tục xem",
                            movies = feed.watching,
                            onMovieClick = onMovieClick
                        )
                    }

                    if (feed.trending.isNotEmpty()) {
                        MovieRow(
                            title = "Xu hướng thịnh hành",
                            movies = feed.trending,
                            onMovieClick = onMovieClick
                        )
                    }

                    if (feed.updated.isNotEmpty()) {
                        MovieRow(
                            title = "Mới cập nhật",
                            movies = feed.updated,
                            onMovieClick = onMovieClick
                        )
                    }

                    if (feed.shows.isNotEmpty()) {
                        MovieRow(
                            title = "Phim bộ chọn lọc",
                            movies = feed.shows,
                            onMovieClick = onMovieClick
                        )
                    }

                    if (feed.movies.isNotEmpty()) {
                        MovieRow(
                            title = "Phim lẻ đặc sắc",
                            movies = feed.movies,
                            onMovieClick = onMovieClick
                        )
                    }

                    if (feed.wishlist.isNotEmpty()) {
                        MovieRow(
                            title = "Danh sách của tôi",
                            movies = feed.wishlist,
                            onMovieClick = onMovieClick
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }

                // Top Header Overlay (Logo + Search Button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PHIMBUD",
                        color = NetflixRed,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Tìm kiếm",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}
