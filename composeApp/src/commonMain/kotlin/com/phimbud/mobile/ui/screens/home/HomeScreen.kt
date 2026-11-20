package com.phimbud.mobile.ui.screens.home

import com.phimbud.mobile.ui.components.liquidPress
import com.phimbud.mobile.ui.components.liquidGlassPress
import com.phimbud.mobile.ui.components.liquidGlass
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
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.phimbud.mobile.ui.components.GlassBackdropState
import com.phimbud.mobile.ui.components.GlassFilterChip
import com.phimbud.mobile.ui.components.LiquidGlassIconButton
import com.phimbud.mobile.ui.components.ProgressiveTopBlur
import com.phimbud.mobile.ui.components.glassBackdropSource
import com.phimbud.mobile.ui.components.rememberGlassBackdropState
import com.phimbud.mobile.ui.settings.LocalGlassSettings

private val BrandRowHeight = 56.dp
private val PillsRowHeight = 48.dp
private val SubRowHeight = 44.dp

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

    val backdrop = rememberGlassBackdropState()
    val accent = LocalGlassSettings.current.accent.color
    val showBrand = showBrandHeader && !isWideScreen
    val showSubRow = filterMode != HomeFilterMode.ALL

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = (if (showBrand) BrandRowHeight else 0.dp) +
        PillsRowHeight +
        (if (showSubRow) SubRowHeight else 0.dp) +
        (if (isWideScreen) 14.dp else 0.dp)
    val contentTop = topInset + headerHeight
    val blurHeight by animateDpAsState(
        targetValue = contentTop + 20.dp,
        animationSpec = spring(stiffness = 500f),
        label = "homeBlurHeight"
    )

    PlatformBackHandler(enabled = filterMode != HomeFilterMode.ALL) {
        viewModel.selectFilterMode(HomeFilterMode.ALL)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is HomeUiState.Loading -> HomeSkeleton(isWideScreen = isWideScreen, contentTop = contentTop)

            is HomeUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = contentTop).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        color = NetflixTextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    GlassActionButton(label = "Thử lại", onClick = { viewModel.loadHomeFeed() })
                }
            }

            is HomeUiState.Success -> {
                val feed = state.feed

                if (filterMode == HomeFilterMode.ALL) {
                    val heroMovie = feed.trending.firstOrNull() ?: feed.movies.firstOrNull()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop),
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
                                Spacer(modifier = Modifier.height(contentTop))
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
                            modifier = Modifier.fillMaxSize().padding(top = contentTop).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = accent)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Đang cập nhật Bảng xếp hạng Top 10 $regionLabel...",
                                color = NetflixTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else if (activeList.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(top = contentTop).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Chưa thể tải dữ liệu Top 10 $regionLabel$categorySuffix",
                                color = NetflixTextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            GlassActionButton(label = "Tải lại", onClick = { viewModel.loadTop10(forceRefresh = true) })
                        }
                    } else {
                        val top1Movie = activeList.first()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop),
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
                                            GlassFilterChip(
                                                label = "Tất cả",
                                                selected = top10Category == Top10CategoryFilter.ALL,
                                                onClick = { viewModel.selectTop10Category(Top10CategoryFilter.ALL) }
                                            )
                                            GlassFilterChip(
                                                label = "Phim Bộ",
                                                selected = top10Category == Top10CategoryFilter.SERIES,
                                                onClick = { viewModel.selectTop10Category(Top10CategoryFilter.SERIES) }
                                            )
                                            GlassFilterChip(
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
            }
        }

        // Progressive blur: content (hero art, rows) melts into the status bar and header.
        ProgressiveTopBlur(
            state = backdrop,
            height = blurHeight,
            modifier = Modifier.align(Alignment.TopStart)
        )

        HomeHeader(
            showBrand = showBrand,
            isWideScreen = isWideScreen,
            filterMode = filterMode,
            top10Category = top10Category,
            showSubRow = showSubRow,
            accent = accent,
            backdrop = backdrop,
            onSearchClick = onSearchClick,
            onSelectMode = { viewModel.selectFilterMode(it) },
            onSelectCategory = { viewModel.selectTop10Category(it) },
            onNavigateToBrowse = onNavigateToBrowse,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

@Composable
private fun HomeHeader(
    showBrand: Boolean,
    isWideScreen: Boolean,
    filterMode: HomeFilterMode,
    top10Category: Top10CategoryFilter,
    showSubRow: Boolean,
    accent: Color,
    backdrop: GlassBackdropState,
    onSearchClick: () -> Unit,
    onSelectMode: (HomeFilterMode) -> Unit,
    onSelectCategory: (Top10CategoryFilter) -> Unit,
    onNavigateToBrowse: (BrowseFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontal = if (isWideScreen) 44.dp else 16.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = if (isWideScreen) 14.dp else 0.dp)
    ) {
        if (showBrand) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BrandRowHeight)
                    .padding(horizontal = horizontal),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PHIMBUD",
                    color = accent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassIconButton(state = backdrop, onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Tìm kiếm",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        val pillsScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PillsRowHeight)
                .horizontalScroll(pillsScroll)
                .platformHorizontalDragScroll(pillsScroll)
                .padding(horizontal = horizontal),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassFilterChip("Tất cả", filterMode == HomeFilterMode.ALL, { onSelectMode(HomeFilterMode.ALL) })
            GlassFilterChip("🇻🇳 Top 10 Việt Nam", filterMode == HomeFilterMode.TOP10_VIETNAM, { onSelectMode(HomeFilterMode.TOP10_VIETNAM) })
            GlassFilterChip("🌍 Top 10 Thế Giới", filterMode == HomeFilterMode.TOP10_GLOBAL, { onSelectMode(HomeFilterMode.TOP10_GLOBAL) })
            if (!isWideScreen) {
                GlassFilterChip("Phim Bộ", false, { onNavigateToBrowse(BrowseFilter(type = "show")) })
                GlassFilterChip("Phim Lẻ", false, { onNavigateToBrowse(BrowseFilter(type = "movie")) })
            }
        }

        AnimatedVisibility(
            visible = showSubRow,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val subScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SubRowHeight)
                    .horizontalScroll(subScroll)
                    .platformHorizontalDragScroll(subScroll)
                    .padding(horizontal = horizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassFilterChip("Tất cả", top10Category == Top10CategoryFilter.ALL, { onSelectCategory(Top10CategoryFilter.ALL) })
                GlassFilterChip("Phim Bộ", top10Category == Top10CategoryFilter.SERIES, { onSelectCategory(Top10CategoryFilter.SERIES) })
                GlassFilterChip("Phim Lẻ", top10Category == Top10CategoryFilter.MOVIES, { onSelectCategory(Top10CategoryFilter.MOVIES) })
            }
        }
    }
}

@Composable
private fun GlassActionButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .liquidGlassPress(RoundedCornerShape(50), tint = NetflixRed, fillAlpha = 0.22f)
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp)
    )
}

/** Skeleton that mirrors the real layout (hero + rows) so the screen never flashes a bare spinner. */
@Composable
private fun HomeSkeleton(isWideScreen: Boolean, contentTop: Dp) {
    val pulse = rememberInfiniteTransition(label = "homeSkeleton").animateFloat(
        initialValue = 0.05f,
        targetValue = 0.13f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "homeSkeletonPulse"
    )
    val horizontal = if (isWideScreen) 44.dp else 16.dp
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (isWideScreen) 540.dp else 450.dp)
                .drawBehind { drawRect(Color.White.copy(alpha = pulse.value * 0.7f)) }
        )
        Spacer(Modifier.height(18.dp))
        repeat(2) {
            Box(
                Modifier
                    .padding(horizontal = horizontal)
                    .width(150.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(50))
                    .drawBehind { drawRect(Color.White.copy(alpha = pulse.value)) }
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.padding(horizontal = horizontal),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(if (isWideScreen) 7 else 3) {
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .drawBehind { drawRect(Color.White.copy(alpha = pulse.value)) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
