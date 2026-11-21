package com.phimbud.mobile.ui.screens.detail

import com.phimbud.mobile.ui.aurora.AuroraFromPoster
import com.phimbud.mobile.ui.components.heroZoom
import com.phimbud.mobile.ui.components.entrance
import com.phimbud.mobile.ui.components.LiquidGlassIconButton
import com.phimbud.mobile.ui.components.glassBackdropSource
import com.phimbud.mobile.ui.components.rememberGlassBackdropState
import com.phimbud.mobile.ui.components.liquidPress
import com.phimbud.mobile.ui.components.liquidGlassPress
import com.phimbud.mobile.ui.components.liquidGlass
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.phimbud.mobile.core.model.BrowseFilter
import com.phimbud.mobile.core.model.BrowseFilterOptions
import com.phimbud.mobile.core.model.Episode
import com.phimbud.mobile.core.model.GenreTag
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.platform.PlatformBackHandler
import com.phimbud.mobile.core.platform.formatOneDecimal
import com.phimbud.mobile.core.platform.platformHorizontalDragScroll
import com.phimbud.mobile.ui.components.MovieRow
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixDarkGray
import com.phimbud.mobile.ui.theme.NetflixGold
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

private val DetailBackdropGradient = Brush.verticalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.5f),
        Color.Transparent,
        NetflixBlack
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBackClick: () -> Unit,
    onPlayClick: (String) -> Unit,
    onRelatedMovieClick: (Movie) -> Unit,
    onFilterClick: ((BrowseFilter) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    PlatformBackHandler(enabled = true) {
        onBackClick()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
    ) {
        val isWideScreen = maxWidth >= 720.dp
        val horizontalMargin = if (isWideScreen) 44.dp else 16.dp

        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NetflixRed)
                }
            }
            is DetailUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = state.message, color = NetflixTextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.loadDetail() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier.liquidGlassPress(RoundedCornerShape(50), tint = NetflixRed, fillAlpha = 0.22f)
                    ) {
                        Text("Thử lại")
                    }
                }
            }
            is DetailUiState.Success -> {
                val detail = state.detail
                AuroraFromPoster(detail.posterUrl ?: detail.backdropUrl)
                val genreItems = remember(detail.genreTags, detail.genres) {
                    if (detail.genreTags.isNotEmpty()) {
                        detail.genreTags
                    } else {
                        detail.genres.mapNotNull { name ->
                            val slug = BrowseFilterOptions.resolveGenreSlug(name) ?: return@mapNotNull null
                            GenreTag(name = name, slug = slug)
                        }
                    }
                }

                val glassState = rememberGlassBackdropState()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding().glassBackdropSource(glassState)
                ) {
                    item(key = "detail_backdrop", contentType = "backdrop") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isWideScreen) 400.dp else 260.dp)
                        ) {
                            AsyncImage(
                                model = detail.backdropUrl ?: detail.posterUrl,
                                contentDescription = detail.displayTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().heroZoom()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(DetailBackdropGradient)
                            )

                        }
                    }

                    item(key = "detail_metadata", contentType = "metadata") {
                        Column(modifier = Modifier.entrance(1).padding(horizontal = horizontalMargin)) {
                            Text(
                                text = detail.displayTitle,
                                color = NetflixTextPrimary,
                                fontSize = if (isWideScreen) 32.sp else 24.sp,
                                lineHeight = if (isWideScreen) 38.sp else 30.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (detail.nameEn.isNotBlank() &&
                                detail.nameVi.isNotBlank() &&
                                !detail.nameEn.trim().equals(detail.nameVi.trim(), ignoreCase = true)
                            ) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = detail.nameEn,
                                    color = NetflixTextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 1: Core Metadata Badges (Rating, Release Year, Type, Season Count)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (detail.imdbRating != null && detail.imdbRating > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .liquidGlass(RoundedCornerShape(50))
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Rating",
                                            tint = NetflixGold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = formatOneDecimal(detail.imdbRating),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                if (detail.year > 0) {
                                    val yearFilterVal = remember(detail.year) {
                                        BrowseFilterOptions.resolveYearFilterValue(detail.year)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .liquidGlassPress(RoundedCornerShape(50), fillAlpha = 0.12f)
                                            .clip(RoundedCornerShape(50))
                                            .then(
                                                if (onFilterClick != null && yearFilterVal != null) {
                                                    Modifier.clickable {
                                                        onFilterClick(BrowseFilter(year = yearFilterVal))
                                                    }
                                                } else Modifier
                                            )
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = detail.year.toString(),
                                            color = NetflixTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                val isShow = detail.type == "show"
                                val typeLabel = if (isShow) "Phim Bộ" else "Phim Lẻ"
                                val typeFilterVal = if (isShow) "show" else "movie"

                                Box(
                                    modifier = Modifier
                                        .liquidGlassPress(RoundedCornerShape(50), fillAlpha = 0.12f)
                                        .clip(RoundedCornerShape(50))
                                        .then(
                                            if (onFilterClick != null) {
                                                Modifier.clickable {
                                                    onFilterClick(BrowseFilter(type = typeFilterVal))
                                                }
                                            } else Modifier
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        color = NetflixTextPrimary.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                if (isShow && detail.seasons.size > 1) {
                                    Box(
                                        modifier = Modifier
                                            .liquidGlass(RoundedCornerShape(50))
                                            .padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${detail.seasons.size} Mùa",
                                            color = NetflixTextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // Row 2: Interactive Genre Tags (FlowRow wraps whole chips cleanly, never stretches words)
                            if (genreItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    genreItems.forEach { genreTag ->
                                        Surface(
                                            onClick = {
                                                onFilterClick?.invoke(BrowseFilter(genre = genreTag.slug))
                                            },
                                            enabled = onFilterClick != null,
                                            color = Color.Transparent,
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.liquidGlassPress(RoundedCornerShape(50), fillAlpha = 0.12f)
                                        ) {
                                            Text(
                                                text = genreTag.name,
                                                color = NetflixTextPrimary.copy(alpha = 0.92f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (isWideScreen) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.playFast(onPlayClick) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                                        modifier = Modifier.height(46.dp)
                                            .liquidGlassPress(RoundedCornerShape(50), tint = Color.White, fillAlpha = 0.23f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Phát",
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Phát",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.toggleWishlist() },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        ),
                                        border = null,
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                                        modifier = Modifier.height(46.dp)
                                            .liquidGlassPress(RoundedCornerShape(50))
                                    ) {
                                        Icon(
                                            imageVector = if (state.isInWishlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = "Danh sách",
                                            tint = if (state.isInWishlist) NetflixRed else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (state.isInWishlist) "Đã thêm vào Danh sách" else "Thêm vào Danh sách",
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.playFast(onPlayClick) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .liquidGlassPress(RoundedCornerShape(50), tint = Color.White, fillAlpha = 0.23f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Phát",
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Phát",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { viewModel.toggleWishlist() },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .liquidGlassPress(RoundedCornerShape(50))
                                ) {
                                    Icon(
                                        imageVector = if (state.isInWishlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Danh sách",
                                        tint = if (state.isInWishlist) NetflixRed else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (state.isInWishlist) "Đã thêm vào Danh sách" else "Thêm vào Danh sách",
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (detail.intro.isNotBlank()) {
                                Text(
                                    text = detail.intro,
                                    color = NetflixTextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }

                    if (detail.seasons.isNotEmpty()) {
                        item(key = "detail_seasons_header", contentType = "seasons_header") {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalMargin)) {
                                Text(
                                    text = "Các tập phim",
                                    color = NetflixTextPrimary,
                                    fontSize = if (isWideScreen) 20.sp else 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                if (detail.seasons.size > 1) {
                                    val seasonsRowState = rememberLazyListState()
                                    LazyRow(
                                        state = seasonsRowState,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.platformHorizontalDragScroll(seasonsRowState)
                                    ) {
                                        items(
                                            items = detail.seasons,
                                            key = { it.fid },
                                            contentType = { "season_chip" }
                                        ) { season ->
                                            val isSelected = state.selectedSeason?.fid == season.fid
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { viewModel.selectSeason(season) },
                                                label = {
                                                    Text(
                                                        text = "Mùa ${season.number}",
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                },
                                                shape = RoundedCornerShape(50),
                                                border = null,
                                                modifier = Modifier.liquidGlassPress(
                                                    RoundedCornerShape(50),
                                                    tint = if (isSelected) NetflixRed else Color.Unspecified,
                                                    fillAlpha = if (isSelected) 0.22f else 0.12f
                                                ),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color.Transparent,
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color.Transparent,
                                                    labelColor = NetflixTextSecondary
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }

                        if (state.isEpisodesLoading && state.episodes.isEmpty()) {
                            item(key = "detail_episodes_loading", contentType = "episodes_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = NetflixRed,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }
                            }
                        } else {
                            items(
                                items = state.episodes,
                                key = { it.fid },
                                contentType = { "episode_item" }
                            ) { episode ->
                                EpisodeItem(
                                    episode = episode,
                                    onClick = { onPlayClick(episode.fid) },
                                    modifier = Modifier.padding(horizontal = horizontalMargin, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (detail.relatedTitles.isNotEmpty()) {
                        item(key = "detail_related", contentType = "related_row") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(Modifier.entrance(3)) {
                                MovieRow(
                                    title = "Nội dung tương tự",
                                    movies = detail.relatedTitles,
                                    onMovieClick = onRelatedMovieClick,
                                    isWideScreen = isWideScreen
                                )
                            }
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }

                // Pinned over the scrolling content so it genuinely refracts the backdrop/poster.
                LiquidGlassIconButton(
                    state = glassState,
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(horizontal = horizontalMargin, vertical = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodeItem(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassPress(RoundedCornerShape(18.dp), fillAlpha = 0.08f)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(NetflixCard)
        ) {
            if (!episode.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = episode.thumbnailUrl,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Phát",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.number}. ${episode.name}",
                color = NetflixTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!episode.airDate.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = episode.airDate,
                    color = NetflixTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
