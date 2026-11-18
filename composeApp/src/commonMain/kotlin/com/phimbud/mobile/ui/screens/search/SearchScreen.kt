package com.phimbud.mobile.ui.screens.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.ui.components.GlassFilterChip
import com.phimbud.mobile.ui.components.LocalBottomOverlayInset
import com.phimbud.mobile.ui.components.PosterTile
import com.phimbud.mobile.ui.components.ProgressiveTopBlur
import com.phimbud.mobile.ui.components.glassBackdropSource
import com.phimbud.mobile.ui.components.liquidGlass
import com.phimbud.mobile.ui.components.rememberGlassBackdropState
import com.phimbud.mobile.ui.settings.LocalGlassSettings
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

private val TitleHeight = 60.dp
private val FieldHeight = 50.dp
private val FieldGap = 6.dp

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onItemClick: (String) -> Unit,
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val recent by viewModel.recent.collectAsState()

    val gridState = rememberLazyGridState()
    val backdrop = rememberGlassBackdropState()
    val focusManager = LocalFocusManager.current

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val horizontal = if (isWideScreen) 44.dp else 16.dp
    val gridSpacing = if (isWideScreen) 16.dp else 12.dp
    val headerHeight = TitleHeight + FieldHeight + FieldGap

    // Scrolling the results dismisses the keyboard.
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) focusManager.clearFocus()
        }
    }

    val pulse = rememberInfiniteTransition(label = "searchSkeleton").animateFloat(
        initialValue = 0.05f,
        targetValue = 0.13f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "searchSkeletonPulse"
    )

    val openItem: (String) -> Unit = { fid ->
        viewModel.saveCurrentQuery()
        onItemClick(fid)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (isWideScreen) 165.dp else 104.dp),
            state = gridState,
            contentPadding = PaddingValues(
                start = horizontal,
                end = horizontal,
                top = topInset + headerHeight + 12.dp,
                bottom = 24.dp + LocalBottomOverlayInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop)
        ) {
            when {
                isLoading -> {
                    items(count = 12, key = { "sk_$it" }, contentType = { "skeleton" }) {
                        SkeletonTile(pulse, isWideScreen)
                    }
                }

                query.isBlank() -> {
                    if (recent.isNotEmpty()) {
                        item(key = "recent", span = { GridItemSpan(maxLineSpan) }, contentType = "recent") {
                            RecentSection(
                                recent = recent,
                                onPick = { viewModel.onQueryChange(it) },
                                onClear = { viewModel.clearRecent() }
                            )
                        }
                    }
                    if (results.isNotEmpty()) {
                        item(key = "suggested_title", span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
                            SectionTitle("Đề xuất cho bạn")
                        }
                        items(items = results, key = { it.fid }, contentType = { "poster" }) { item ->
                            PosterTile(
                                posterUrl = item.posterUrl,
                                title = item.displayTitle,
                                year = item.year,
                                rating = null,
                                onClick = { openItem(item.fid) },
                                isWideScreen = isWideScreen
                            )
                        }
                    }
                }

                results.isEmpty() -> {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                        EmptyResult(query)
                    }
                }

                else -> {
                    item(key = "count", span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
                        SectionTitle("${results.size} kết quả")
                    }
                    items(items = results, key = { it.fid }, contentType = { "poster" }) { item ->
                        PosterTile(
                            posterUrl = item.posterUrl,
                            title = item.displayTitle,
                            year = item.year,
                            rating = null,
                            onClick = { openItem(item.fid) },
                            isWideScreen = isWideScreen
                        )
                    }
                }
            }
        }

        ProgressiveTopBlur(
            state = backdrop,
            height = topInset + headerHeight + 18.dp,
            modifier = Modifier.align(Alignment.TopStart)
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = topInset)
                .padding(horizontal = horizontal)
                .fillMaxWidth()
        ) {
            Box(Modifier.height(TitleHeight), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = "Tìm kiếm",
                    color = NetflixTextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
            SearchField(
                query = query,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSubmit = {
                    viewModel.saveCurrentQuery()
                    focusManager.clearFocus()
                },
                modifier = Modifier
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalGlassSettings.current.accent.color
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    val ring by animateColorAsState(
        targetValue = if (focused) accent.copy(alpha = 0.85f) else Color.Transparent,
        animationSpec = tween(180),
        label = "searchRing"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .liquidGlass(shape, fillAlpha = 0.1f)
            .border(1.5.dp, ring, shape)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (focused) accent else NetflixTextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = "Tên phim, diễn viên hoặc link IMDb",
                    color = NetflixTextSecondary,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = null, indication = null) { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Xóa",
                    tint = NetflixTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentSection(
    recent: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = NetflixTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Tìm gần đây",
                color = NetflixTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Xóa tất cả",
                color = LocalGlassSettings.current.accent.color,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = recent, key = { it }, contentType = { "recent_chip" }) { q ->
                GlassFilterChip(label = q, selected = false, onClick = { onPick(q) })
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = NetflixTextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    )
}

@Composable
private fun EmptyResult(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = NetflixTextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Không tìm thấy phim phù hợp với \"$query\"",
            color = NetflixTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Thử tên khác, bỏ dấu hoặc dán link IMDb của phim.",
            color = NetflixTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SkeletonTile(pulse: State<Float>, isWideScreen: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(if (isWideScreen) 12.dp else 16.dp))
            .drawBehind { drawRect(Color.White.copy(alpha = pulse.value)) }
    )
}
