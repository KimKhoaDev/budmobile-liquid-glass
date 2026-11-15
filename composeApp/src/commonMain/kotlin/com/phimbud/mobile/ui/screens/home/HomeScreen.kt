package com.phimbud.mobile.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.core.model.BrowseFilter
import com.phimbud.mobile.core.model.HomeFilterMode
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.model.Top10CategoryFilter
import com.phimbud.mobile.core.platform.PlatformBackHandler
import com.phimbud.mobile.core.platform.platformHorizontalDragScroll
import com.phimbud.mobile.ui.components.HeroBanner
import com.phimbud.mobile.ui.components.MovieRow
import com.phimbud.mobile.ui.components.Top10MovieRow
import com.phimbud.mobile.ui.components.Top10RankedListItem
import com.phimbud.mobile.ui.components.LocalBottomOverlayInset
import com.phimbud.mobile.ui.components.liquidGlass
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

private val HomeHeaderGradient = Brush.verticalGradient(
    colors = listOf(
        NetflixBlack.copy(alpha = 0.92f),
        NetflixBlack.copy(alpha = 0.68f),
        Color.Transparent
    )
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMovieClick: (Movie) -> Unit,
    onPlayClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onNavigateToBrowse: (BrowseFilter) -> Unit = {},
    onNavigateToMyList: (Int) -> Unit = {},
    isWideScreen: Boolean = false,
    showBrandHeader: Boolean = !isWideScreen,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val filterMode by viewModel.filterMode.collectAsState()
    val top10Category by viewModel.top10Category.collectAsState()
    val top10Lists by viewModel.top10Lists.collectAsState()
    val isTop10Loading by viewModel.isTop10Loading.collectAsState()

    PlatformBackHandler(enabled = filterMode != HomeFilterMode.ALL) {
        viewModel.selectFilterMode(HomeFilterMode.ALL)
    }

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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.liquidGlass(RoundedCornerShape(8.dp), tint = NetflixRed, fillAlpha = 0.35f)
                    ) {
                        Text("Thử lại")
                    }
                }
            }
            is HomeUiState.Success -> {
                val feed = state.feed

                if (filterMode == HomeFilterMode.ALL) {
                    val heroMovie = feed.trending.firstOrNull() ?: feed.movies.firstOrNull()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current)
                    ) {
                        if (heroMovie != null) {
                            item(key = "hero_banner", contentType = "hero") {
                                HeroBanner(
                                    movie = heroMovie,
                                    onPlayClick = {
                                        viewModel.playMovie(heroMovie, onPlayClick)
                                    },
                                    onInfoClick = { onMovieClick(heroMovie) },
                                    isWideScreen = isWideScreen
                                )
                            }
                        } else {
                            item(key = "top_pad") {
                                Spacer(modifier = Modifier.height(if (isWideScreen) 68.dp else 120.dp))
                            }
                        }

                        if (feed.watching.isNotEmpty()) {
                            item(key = "row_watching", contentType = "movie_row") {
                                MovieRow(
                                    title = "Tiếp tục xem",
                                    movies = feed.watching,
                                    onMovieClick = onMovieClick,
                                    onSeeAllClick = {
                                        onNavigateToMyList(1)
                                    },
                                    isWideScreen = isWideScreen
                                )
                            }
                        }

                        if (top10Lists.vnAll.isNotEmpty()) {
                            item(key = "row_top10_vn", contentType = "top10_row") {
                                Top10MovieRow(
                                    title = "Tại Việt Nam hôm nay",
                                    movies = top10Lists.vnAll,
                                    onMovieClick = onMovieClick,
                                    onSeeAllClick = {
                                        viewModel.selectFilterMode(HomeFilterMode.TOP10_VIETNAM)
                                    },
                                    isWideScreen = isWideScreen
                                )
                            }
                        }

                        if (top10Lists.globalAll.isNotEmpty()) {
                            item(key = "row_top10_global", contentType = "top10_row") {
                                Top10MovieRow(
                                    title = "Thế Giới tuần này",
                                    movies = top10Lists.globalAll,
                                    onMovieClick = onMovieClick,
                                    onSeeAllClick = {
                                        viewModel.selectFilterMode(HomeFilterMode.TOP10_GLOBAL)
                                    },
                                    isWideScreen = isWideScreen
                                )
                            }
                        }

                        if (feed.trending.isNotEmpty()) {
                            item(key = "row_trending", contentType = "movie_row") {
                                MovieRow(
                                    title = "Thịnh hành",
                                    movies = feed.trending,
                                    onMovieClick = onMovieClick,
                                    isWideScreen = isWideScreen
                                )
                            }
                        }

                        if (feed.movies.isNotEmpty()) {
                            item(key = "row_movies", contentType = "movie_row") {
                                MovieRow(
                                    title = "Phim lẻ mới",
                                    movies = feed.movies,
                                    onMovieClick = onMovieClick,
                                    onSeeAllClick = {
                                        onNavigateToBrowse(BrowseFilter(type = "movie"))
                                    },
                                    isWideScreen = isWideScreen
                                )
                            }
                        }

                        if (feed.shows.isNotEmpty()) {
                            item(key = "row_shows", contentType = "movie_row") {
                                MovieRow(
                                    title = "Phim bộ mới",
                                    movies = feed.shows,
                                    onMovieClick = onMovieClick,
                                    onSeeAllClick = {
                                        onNavigateToBrowse(BrowseFilter(type = "show"))
                                    },
                                    isWideScreen = isWideScreen
                                )
                            }
                        }

                        if (feed.updated.isNotEmpty()) {
                            item(key = "row_updated", contentType = "movie_row") {
                                MovieRow(
                                    title = "Cập nhật mới từ Danh sách",
                                    movies = feed.updated,
                                    onMovieClick = onMovieClick,
                                    isWideScreen = isWideScreen
                                )
                            }
                        }

                        if (feed.wishlist.isNotEmpty()) {
                            item(key = "row_wishlist", contentType = "movie_row") {
                                MovieRow(
                                    title = "Danh sách của tôi",
                                    movies = feed.wishlist,
                                    onMovieClick = onMovieClick,
                                    onSeeAllClick = {
                                        onNavigateToMyList(0)
                                    },
                                    isWideScreen = isWideScreen
                                )
                            }
                        }

                        item(key = "bottom_spacer", contentType = "spacer") {
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                    }
                } else {
                    // Dedicated Top 10 Filtered View (Vietnam or Global + Category)
                    val activeList = top10Lists.getList(filterMode, top10Category)
                    val regionLabel = if (filterMode == HomeFilterMode.TOP10_VIETNAM) "tại Việt Nam" else "Thế Giới"
                    val categorySuffix = when (top10Category) {
                        Top10CategoryFilter.ALL -> ""
                        Top10CategoryFilter.SERIES -> " (Phim Bộ)"
                        Top10CategoryFilter.MOVIES -> " (Phim Lẻ)"
                    }

                    if (isTop10Loading && activeList.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = NetflixRed)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Đang cập nhật Bảng xếp hạng Top 10 $regionLabel...",
                                color = NetflixTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else if (activeList.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Chưa thể tải dữ liệu Top 10 $regionLabel$categorySuffix",
                                color = NetflixTextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.loadTop10(forceRefresh = true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.liquidGlass(RoundedCornerShape(8.dp), tint = NetflixRed, fillAlpha = 0.35f)
                            ) {
                                Text("Tải lại")
                            }
                        }
                    } else {
                        val top1Movie = activeList.first()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current)
                        ) {
                            item(key = "top10_hero_${top1Movie.fid}", contentType = "hero") {
                                HeroBanner(
                                    movie = top1Movie,
                                    rankBadgeText = "#1 $regionLabel$categorySuffix",
                                    onPlayClick = {
                                        viewModel.playMovie(top1Movie, onPlayClick)
                                    },
                                    onInfoClick = { onMovieClick(top1Movie) },
                                    isWideScreen = isWideScreen
                                )
                            }

                            item(key = "top10_section_header") {
                                val hMargin = if (isWideScreen) 44.dp else 16.dp
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = hMargin, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Bảng xếp hạng Top 10 $regionLabel$categorySuffix",
                                            color = NetflixTextPrimary,
                                            fontSize = if (isWideScreen) 22.sp else 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (isWideScreen) {
                                            Text(
                                                text = "‹ Trở về Trang chủ",
                                                color = NetflixRed,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { viewModel.selectFilterMode(HomeFilterMode.ALL) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    if (isWideScreen) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            NetflixCategoryChip(
                                                label = "Tất cả",
                                                selected = top10Category == Top10CategoryFilter.ALL,
                                                onClick = { viewModel.selectTop10Category(Top10CategoryFilter.ALL) }
                                            )
                                            NetflixCategoryChip(
                                                label = "Phim Bộ",
                                                selected = top10Category == Top10CategoryFilter.SERIES,
                                                onClick = { viewModel.selectTop10Category(Top10CategoryFilter.SERIES) }
                                            )
                                            NetflixCategoryChip(
                                                label = "Phim Lẻ",
                                                selected = top10Category == Top10CategoryFilter.MOVIES,
                                                onClick = { viewModel.selectTop10Category(Top10CategoryFilter.MOVIES) }
                                            )
                                        }
                                    }
                                }
                            }

                            itemsIndexed(
                                items = activeList,
                                key = { _, m -> "ranked_${m.fid}" },
                                contentType = { _, _ -> "top10_ranked_item" }
                            ) { index, movie ->
                                Top10RankedListItem(
                                    rank = index + 1,
                                    movie = movie,
                                    onCardClick = { onMovieClick(movie) },
                                    onPlayClick = {
                                        viewModel.playMovie(movie, onPlayClick)
                                    },
                                    isWideScreen = isWideScreen,
                                    modifier = Modifier.padding(
                                        horizontal = if (isWideScreen) 44.dp else 16.dp,
                                        vertical = if (isWideScreen) 8.dp else 5.dp
                                    )
                                )
                            }

                            item(key = "top10_bottom_spacer") {
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }

                // Top Header Overlay (Logo + Search Button on Mobile, Netflix Filter Pills on all screens)
                val headerHorizontalPadding = if (isWideScreen) 44.dp else 16.dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HomeHeaderGradient)
                        .statusBarsPadding()
                        .padding(top = if (isWideScreen) 14.dp else 0.dp, bottom = 10.dp)
                ) {
                    if (showBrandHeader && !isWideScreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
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

                    // Primary Filter Chips: Tất cả | Top 10 Việt Nam | Top 10 Thế Giới (+ Phim Bộ | Phim Lẻ on mobile)
                    val primaryPillsScroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(primaryPillsScroll)
                            .platformHorizontalDragScroll(primaryPillsScroll)
                            .padding(horizontal = headerHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetflixFilterPill(
                            label = "Tất cả",
                            selected = filterMode == HomeFilterMode.ALL,
                            onClick = { viewModel.selectFilterMode(HomeFilterMode.ALL) }
                        )
                        NetflixFilterPill(
                            label = "🇻🇳 Top 10 Việt Nam",
                            selected = filterMode == HomeFilterMode.TOP10_VIETNAM,
                            onClick = { viewModel.selectFilterMode(HomeFilterMode.TOP10_VIETNAM) }
                        )
                        NetflixFilterPill(
                            label = "🌍 Top 10 Thế Giới",
                            selected = filterMode == HomeFilterMode.TOP10_GLOBAL,
                            onClick = { viewModel.selectFilterMode(HomeFilterMode.TOP10_GLOBAL) }
                        )
                        if (!isWideScreen) {
                            NetflixFilterPill(
                                label = "Phim Bộ",
                                selected = false,
                                onClick = { onNavigateToBrowse(BrowseFilter(type = "show")) }
                            )
                            NetflixFilterPill(
                                label = "Phim Lẻ",
                                selected = false,
                                onClick = { onNavigateToBrowse(BrowseFilter(type = "movie")) }
                            )
                        }
                    }

                    // Secondary Sub-Category Pills when viewing Top 10: Tất cả | Phim Bộ | Phim Lẻ
                    AnimatedVisibility(
                        visible = filterMode != HomeFilterMode.ALL,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val subCategoryScroll = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(subCategoryScroll)
                                .platformHorizontalDragScroll(subCategoryScroll)
                                .padding(start = headerHorizontalPadding, end = headerHorizontalPadding, top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NetflixCategoryChip(
                                label = "Tất cả",
                                selected = top10Category == Top10CategoryFilter.ALL,
                                onClick = { viewModel.selectTop10Category(Top10CategoryFilter.ALL) }
                            )
                            NetflixCategoryChip(
                                label = "Phim Bộ",
                                selected = top10Category == Top10CategoryFilter.SERIES,
                                onClick = { viewModel.selectTop10Category(Top10CategoryFilter.SERIES) }
                            )
                            NetflixCategoryChip(
                                label = "Phim Lẻ",
                                selected = top10Category == Top10CategoryFilter.MOVIES,
                                onClick = { viewModel.selectTop10Category(Top10CategoryFilter.MOVIES) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetflixFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .liquidGlass(
                shape = RoundedCornerShape(20.dp),
                tint = if (selected) NetflixRed else Color.Unspecified,
                fillAlpha = if (selected) 0.5f else 0.12f
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun NetflixCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                tint = if (selected) Color.White else Color.Unspecified,
                fillAlpha = if (selected) 0.4f else 0.1f
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else NetflixTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}
