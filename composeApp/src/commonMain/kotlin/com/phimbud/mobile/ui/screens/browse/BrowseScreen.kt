package com.phimbud.mobile.ui.screens.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.phimbud.mobile.core.model.BrowseFilter
import com.phimbud.mobile.core.model.BrowseFilterOptions
import com.phimbud.mobile.core.model.FilterOption
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.core.platform.formatOneDecimal
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixDarkGray
import com.phimbud.mobile.ui.theme.NetflixGold
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary
import kotlinx.coroutines.launch

@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val filter by viewModel.filter.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val scrollResetTrigger by viewModel.scrollResetTrigger.collectAsState()

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val yearOptions = remember { BrowseFilterOptions.getYearOptions() }

    var isFilterPanelExpanded by rememberSaveable { mutableStateOf(true) }

    val isScrolledPastFilter by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0
        }
    }

    val isNearEnd by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 1 && lastVisible >= total - 6
        }
    }

    LaunchedEffect(scrollResetTrigger) {
        if (scrollResetTrigger > 0) {
            gridState.scrollToItem(0)
        }
    }

    LaunchedEffect(isNearEnd, movies.size) {
        if (isNearEnd && movies.isNotEmpty()) {
            viewModel.loadNextPage()
        }
    }

    val showActiveFilterStrip = filter.isActive && (!isFilterPanelExpanded || isScrolledPastFilter)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .statusBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        // Pinned Header Row: Title + Active Count + Reset + Filter Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "Khám phá",
                    color = NetflixTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (filter.activeCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(NetflixRed, RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${filter.activeCount}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (filter.isActive) {
                    Text(
                        text = "Đặt lại",
                        color = NetflixRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { viewModel.resetFilter() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                val isPanelCurrentlyVisible = isFilterPanelExpanded && !isScrolledPastFilter
                Surface(
                    onClick = {
                        if (isFilterPanelExpanded && isScrolledPastFilter) {
                            coroutineScope.launch {
                                gridState.animateScrollToItem(0)
                            }
                        } else {
                            val nextExpanded = !isFilterPanelExpanded
                            isFilterPanelExpanded = nextExpanded
                            if (nextExpanded && isScrolledPastFilter) {
                                coroutineScope.launch {
                                    gridState.animateScrollToItem(0)
                                }
                            }
                        }
                    },
                    color = if (isPanelCurrentlyVisible) NetflixCard else NetflixDarkGray,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (filter.isActive) NetflixRed.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Bộ lọc",
                            tint = if (filter.isActive) NetflixRed else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Bộ lọc",
                            color = NetflixTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (isPanelCurrentlyVisible) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = NetflixTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        // Compact Active Filter Chips Strip when panel is collapsed or scrolled out of view
        AnimatedVisibility(
            visible = showActiveFilterStrip,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ActiveFilterChipsRow(
                filter = filter,
                yearOptions = yearOptions,
                onClearType = { viewModel.updateType(null) },
                onClearGenre = { viewModel.updateGenre(null) },
                onClearCountry = { viewModel.updateCountry(null) },
                onClearYear = { viewModel.updateYear(null) },
                onClearDuration = { viewModel.updateDuration(null) },
                onClearSort = { viewModel.updateSort(null) }
            )
        }

        // Main Scrollable Grid (Filter Panel at top scrolls away naturally with posters)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 115.dp),
            state = gridState,
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(
                key = "browse_filter_panel",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "filter_panel"
            ) {
                AnimatedVisibility(
                    visible = isFilterPanelExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    BrowseFilterCard(
                        filter = filter,
                        yearOptions = yearOptions,
                        onTypeSelected = { viewModel.updateType(it) },
                        onGenreSelected = { viewModel.updateGenre(it) },
                        onCountrySelected = { viewModel.updateCountry(it) },
                        onYearSelected = { viewModel.updateYear(it) },
                        onDurationSelected = { viewModel.updateDuration(it) },
                        onSortSelected = { viewModel.updateSort(it) }
                    )
                }
            }

            if (isLoading && movies.isEmpty()) {
                item(
                    key = "browse_initial_loading",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "status"
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NetflixRed)
                    }
                }
            } else if (errorMessage != null && movies.isEmpty()) {
                item(
                    key = "browse_error",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "status"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "Không thể tải danh sách phim",
                            color = NetflixTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.retry() },
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Thử lại", maxLines = 1, softWrap = false)
                        }
                    }
                }
            } else if (!isLoading && movies.isEmpty()) {
                item(
                    key = "browse_empty",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "status"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Không tìm thấy phim phù hợp với bộ lọc đã chọn",
                            color = NetflixTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        if (filter.isActive) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.resetFilter() },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Đặt lại bộ lọc", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            } else {
                items(
                    items = movies,
                    key = { it.fid },
                    contentType = { "browse_movie" }
                ) { movie ->
                    BrowseMovieGridCard(
                        movie = movie,
                        onClick = { onMovieClick(movie) }
                    )
                }

                if (isLoading) {
                    item(
                        key = "browse_page_loading",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "loading_more"
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = NetflixRed,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseFilterCard(
    filter: BrowseFilter,
    yearOptions: List<FilterOption>,
    onTypeSelected: (String?) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onCountrySelected: (String?) -> Unit,
    onYearSelected: (String?) -> Unit,
    onDurationSelected: (String?) -> Unit,
    onSortSelected: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NetflixDarkGray)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Loại phim & Thể loại
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                FilterDropdown(
                    label = "Loại phim:",
                    options = BrowseFilterOptions.TYPE_OPTIONS,
                    selectedValue = filter.type,
                    onOptionSelected = onTypeSelected
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FilterDropdown(
                    label = "Thể loại:",
                    options = BrowseFilterOptions.GENRE_OPTIONS,
                    selectedValue = filter.genre,
                    onOptionSelected = onGenreSelected
                )
            }
        }

        // Row 2: Quốc gia & Năm
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                FilterDropdown(
                    label = "Quốc gia:",
                    options = BrowseFilterOptions.COUNTRY_OPTIONS,
                    selectedValue = filter.country,
                    onOptionSelected = onCountrySelected
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FilterDropdown(
                    label = "Năm:",
                    options = yearOptions,
                    selectedValue = filter.year,
                    onOptionSelected = onYearSelected
                )
            }
        }

        // Row 3: Thời lượng & Sắp xếp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                FilterDropdown(
                    label = "Thời lượng:",
                    options = BrowseFilterOptions.DURATION_OPTIONS,
                    selectedValue = filter.duration,
                    enabled = filter.type != "show",
                    onOptionSelected = onDurationSelected
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FilterDropdown(
                    label = "Sắp xếp:",
                    options = BrowseFilterOptions.SORT_OPTIONS,
                    selectedValue = filter.sort,
                    onOptionSelected = onSortSelected
                )
            }
        }
    }
}

@Composable
private fun ActiveFilterChipsRow(
    filter: BrowseFilter,
    yearOptions: List<FilterOption>,
    onClearType: () -> Unit,
    onClearGenre: () -> Unit,
    onClearCountry: () -> Unit,
    onClearYear: () -> Unit,
    onClearDuration: () -> Unit,
    onClearSort: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrowseFilterOptions.findLabel(BrowseFilterOptions.TYPE_OPTIONS, filter.type)?.let { label ->
            RemovableFilterChip(label = label, onRemove = onClearType)
        }
        BrowseFilterOptions.findLabel(BrowseFilterOptions.GENRE_OPTIONS, filter.genre)?.let { label ->
            RemovableFilterChip(label = label, onRemove = onClearGenre)
        }
        BrowseFilterOptions.findLabel(BrowseFilterOptions.COUNTRY_OPTIONS, filter.country)?.let { label ->
            RemovableFilterChip(label = label, onRemove = onClearCountry)
        }
        BrowseFilterOptions.findLabel(yearOptions, filter.year)?.let { label ->
            RemovableFilterChip(label = label, onRemove = onClearYear)
        }
        BrowseFilterOptions.findLabel(BrowseFilterOptions.DURATION_OPTIONS, filter.duration)?.let { label ->
            RemovableFilterChip(label = label, onRemove = onClearDuration)
        }
        BrowseFilterOptions.findLabel(BrowseFilterOptions.SORT_OPTIONS, filter.sort)?.let { label ->
            RemovableFilterChip(label = label, onRemove = onClearSort)
        }
    }
}

@Composable
private fun RemovableFilterChip(
    label: String,
    onRemove: () -> Unit
) {
    Surface(
        onClick = onRemove,
        color = NetflixRed.copy(alpha = 0.18f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.65f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                color = NetflixTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Xoá bộ lọc $label",
                tint = NetflixRed,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun BrowseMovieGridCard(
    movie: Movie,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
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
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.Center)
                )
            }

            if (movie.imdbRating != null && movie.imdbRating > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = NetflixGold,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = formatOneDecimal(movie.imdbRating),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (movie.year > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = movie.year.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = movie.displayTitle,
            color = NetflixTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    options: List<FilterOption>,
    selectedValue: String?,
    enabled: Boolean = true,
    onOptionSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val currentOption = options.firstOrNull { it.value == selectedValue }
        ?: selectedValue?.takeIf { it.isNotBlank() }?.let { FilterOption(it, it) }
        ?: options.first()
    val isDefault = selectedValue.isNullOrBlank()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = if (enabled) NetflixTextSecondary else NetflixTextSecondary.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { if (enabled) expanded = true },
                enabled = enabled,
                color = if (enabled) NetflixCard else NetflixCard.copy(alpha = 0.45f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = when {
                        !enabled -> Color.White.copy(alpha = 0.08f)
                        !isDefault -> NetflixRed.copy(alpha = 0.8f)
                        else -> Color.White.copy(alpha = 0.14f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentOption.label,
                        color = when {
                            !enabled -> NetflixTextSecondary.copy(alpha = 0.4f)
                            !isDefault -> NetflixTextPrimary
                            else -> NetflixTextSecondary
                        },
                        fontSize = 13.sp,
                        fontWeight = if (!isDefault) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = when {
                            !enabled -> NetflixTextSecondary.copy(alpha = 0.35f)
                            !isDefault -> NetflixRed
                            else -> NetflixTextSecondary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color(0xFF222222))
                    .heightIn(max = 300.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option.value == selectedValue
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                color = if (isSelected) NetflixRed else NetflixTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            expanded = false
                            onOptionSelected(option.value)
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = NetflixTextPrimary
                        )
                    )
                }
            }
        }
    }
}
