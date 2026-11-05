package com.phimbud.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (heroMovie != null) {
                        item(key = "hero_banner", contentType = "hero") {
                            HeroBanner(
                                movie = heroMovie,
                                onPlayClick = {
                                    viewModel.playMovie(heroMovie, onPlayClick)
                                },
                                onInfoClick = { onMovieClick(heroMovie) }
                            )
                        }
                    }

                    if (feed.watching.isNotEmpty()) {
                        item(key = "row_watching", contentType = "movie_row") {
                            MovieRow(
                                title = "Tiếp tục xem",
                                movies = feed.watching,
                                onMovieClick = onMovieClick
                            )
                        }
                    }

                    if (feed.trending.isNotEmpty()) {
                        item(key = "row_trending", contentType = "movie_row") {
                            MovieRow(
                                title = "Xu hướng thịnh hành",
                                movies = feed.trending,
                                onMovieClick = onMovieClick
                            )
                        }
                    }

                    if (feed.updated.isNotEmpty()) {
                        item(key = "row_updated", contentType = "movie_row") {
                            MovieRow(
                                title = "Mới cập nhật",
                                movies = feed.updated,
                                onMovieClick = onMovieClick
                            )
                        }
                    }

                    if (feed.shows.isNotEmpty()) {
                        item(key = "row_shows", contentType = "movie_row") {
                            MovieRow(
                                title = "Phim bộ chọn lọc",
                                movies = feed.shows,
                                onMovieClick = onMovieClick
                            )
                        }
                    }

                    if (feed.movies.isNotEmpty()) {
                        item(key = "row_movies", contentType = "movie_row") {
                            MovieRow(
                                title = "Phim lẻ đặc sắc",
                                movies = feed.movies,
                                onMovieClick = onMovieClick
                            )
                        }
                    }

                    if (feed.wishlist.isNotEmpty()) {
                        item(key = "row_wishlist", contentType = "movie_row") {
                            MovieRow(
                                title = "Danh sách của tôi",
                                movies = feed.wishlist,
                                onMovieClick = onMovieClick
                            )
                        }
                    }

                    item(key = "bottom_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }

                // Top Header Overlay (Logo + Search Button, respects iOS notch/Dynamic Island)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
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
