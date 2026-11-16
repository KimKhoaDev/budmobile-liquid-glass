package com.phimbud.mobile.ui.screens.browse

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import com.phimbud.mobile.ui.components.liquidPress
import com.phimbud.mobile.ui.components.liquidGlassPress
import com.phimbud.mobile.ui.components.liquidGlass
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.phimbud.mobile.ui.components.LocalBottomOverlayInset
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixDarkGray
import com.phimbud.mobile.ui.theme.NetflixGold
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.ui.draw.drawBehind
import com.phimbud.mobile.ui.components.GlassFilterChip
import com.phimbud.mobile.ui.components.GlassSegmentedControl
import com.phimbud.mobile.ui.components.LiquidGlassIconButton
import com.phimbud.mobile.ui.components.PosterGridCard
import com.phimbud.mobile.ui.components.ProgressiveTopBlur
import com.phimbud.mobile.ui.components.glassBackdropSource
import com.phimbud.mobile.ui.components.rememberGlassBackdropState
import com.phimbud.mobile.ui.components.GlassBackdropState
import com.phimbud.mobile.ui.settings.LocalGlassSettings

private val HeaderHeight = 64.dp

@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onMovieClick: (Movie) -> Unit,
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val filter by viewModel.filter.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val scrollResetTrigger by viewModel.scrollResetTrigger.collectAsState()

    val gridState = rememberLazyGridState()
    val backdrop = rememberGlassBackdropState()
    val yearOptions = remember { BrowseFilterOptions.getYearOptions() }
    var isPanelExpanded by rememberSaveable { mutableStateOf(false) }

    val isNearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 1 && lastVisible >= total - 8
        }
    }

    LaunchedEffect(scrollResetTrigger) {
        if (scrollResetTrigger > 0) gridState.scrollToItem(0)
    }
    LaunchedEffect(isNearEnd, movies.size) {
        if (isNearEnd && movies.isNotEmpty()) viewModel.loadNextPage()
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val horizontal = if (isWideScreen) 44.dp else 16.dp
    val gridSpacing = if (isWideScreen) 16.dp else 12.dp

    // One shared pulse for every skeleton tile (read in the draw phase -> zero recomposition).
    val pulse = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.05f,
        targetValue = 0.13f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonPulse"
    )

    Box(modifier = modifier.fillMaxSize().background(NetflixBlack)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (isWideScreen) 165.dp else 104.dp),
            state = gridState,
            contentPadding = PaddingValues(
                start = horizontal,
                end = horizontal,
                top = topInset + HeaderHeight,
                bottom = 24.dp + LocalBottomOverlayInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop)
        ) {
            item(
                key = "browse_controls",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "controls"
            ) {
                BrowseControls(
                    filter = filter,
                    yearOptions = yearOptions,
                    isPanelExpanded = isPanelExpanded,
                    viewModel = viewModel
                )
            }

            when {
                isLoading && movies.isEmpty() -> {
                    items(
                        count = 12,
                        key = { "skeleton_$it" },
                        contentType = { "skeleton" }
                    ) { SkeletonPoster(pulse, isWideScreen) }
                }

                errorMessage != null && movies.isEmpty() -> {
                    item(key = "browse_error", span = { GridItemSpan(maxLineSpan) }, contentType = "status") {
                        StatusMessage(
                            message = errorMessage ?: "Không thể tải danh sách phim",
                            actionLabel = "Thử lại",
                            onAction = { viewModel.retry() }
                        )
                    }
                }

                !isLoading && movies.isEmpty() -> {
                    item(key = "browse_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "status") {
                        StatusMessage(
                            message = "Không tìm thấy phim phù hợp với bộ lọc đã chọn",
                            actionLabel = if (filter.isActive) "Đặt lại bộ lọc" else null,
                            onAction = { viewModel.resetFilter() }
                        )
                    }
                }

                else -> {
                    items(
                        items = movies,
                        key = { it.fid },
                        contentType = { "poster" }
                    ) { movie ->
                        PosterGridCard(
                            movie = movie,
                            onClick = { onMovieClick(movie) },
                            isWideScreen = isWideScreen
                        )
                    }
                    if (isLoading) {
                        item(key = "browse_page_loading", span = { GridItemSpan(maxLineSpan) }, contentType = "loading_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = LocalGlassSettings.current.accent.color, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }

        // Progressive blur: content scrolling under the header melts into the status bar.
        ProgressiveTopBlur(
            state = backdrop,
            height = topInset + HeaderHeight + 22.dp,
            modifier = Modifier.align(Alignment.TopStart)
        )

        BrowseHeader(
            filter = filter,
            isPanelExpanded = isPanelExpanded,
            backdrop = backdrop,
            onTogglePanel = { isPanelExpanded = !isPanelExpanded },
            onReset = { viewModel.resetFilter() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = topInset)
                .padding(horizontal = horizontal)
        )
    }
}

@Composable
private fun BrowseHeader(
    filter: BrowseFilter,
    isPanelExpanded: Boolean,
    backdrop: GlassBackdropState,
    onTogglePanel: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalGlassSettings.current.accent.color
    Row(
        modifier = modifier.fillMaxWidth().height(HeaderHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Khám phá",
            color = NetflixTextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (filter.isActive) {
            Text(
                text = "Đặt lại",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onReset)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Box {
            LiquidGlassIconButton(state = backdrop, onClick = onTogglePanel) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Bộ lọc",
                    tint = if (isPanelExpanded || filter.isActive) accent else Color.White
                )
            }
            if (filter.activeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${filter.activeCount}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseControls(
    filter: BrowseFilter,
    yearOptions: List<FilterOption>,
    isPanelExpanded: Boolean,
    viewModel: BrowseViewModel
) {
    val typeOptions = BrowseFilterOptions.TYPE_OPTIONS
    val typeLabels = remember { typeOptions.map { it.label.trim('-', ' ') } }
    val typeIndex = typeOptions.indexOfFirst { it.value == filter.type }.coerceAtLeast(0)

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassSegmentedControl(
            options = typeLabels,
            selectedIndex = typeIndex,
            onSelect = { viewModel.updateType(typeOptions[it].value) }
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = BrowseFilterOptions.GENRE_OPTIONS,
                key = { it.label },
                contentType = { "genre_chip" }
            ) { option ->
                GlassFilterChip(
                    label = option.label.trim('-', ' '),
                    selected = if (option.value == null) filter.genre.isNullOrBlank() else option.value == filter.genre,
                    onClick = { viewModel.updateGenre(option.value) }
                )
            }
        }

        AnimatedVisibility(
            visible = isPanelExpanded,
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

        AnimatedVisibility(
            visible = filter.isActive && !isPanelExpanded,
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
    }
}

@Composable
private fun SkeletonPoster(pulse: State<Float>, isWideScreen: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(if (isWideScreen) 12.dp else 16.dp))
            .drawBehind { drawRect(Color.White.copy(alpha = pulse.value)) }
    )
}

@Composable
private fun StatusMessage(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = NetflixTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(50),
                modifier = Modifier.liquidGlassPress(RoundedCornerShape(50), tint = NetflixRed, fillAlpha = 0.22f)
            ) {
                Text(actionLabel, maxLines = 1, softWrap = false)
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
            .liquidGlass(RoundedCornerShape(24.dp), fillAlpha = 0.09f)
            .clip(RoundedCornerShape(24.dp))
            .padding(14.dp),
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
        color = Color.Transparent,
        shape = RoundedCornerShape(50),
        modifier = Modifier.liquidGlassPress(RoundedCornerShape(50), tint = NetflixRed, fillAlpha = 0.16f)
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
                color = Color.Transparent,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .liquidGlassPress(
                        RoundedCornerShape(14.dp),
                        tint = if (enabled && !isDefault) NetflixRed else Color.Unspecified,
                        fillAlpha = if (!enabled) 0.05f else if (!isDefault) 0.16f else 0.11f
                    )
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
