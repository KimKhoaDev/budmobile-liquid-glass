package com.phimbud.mobile.ui.screens.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.ui.components.GlassSegmentedControl
import com.phimbud.mobile.ui.components.LiquidGlassIconButton
import com.phimbud.mobile.ui.components.LocalBottomOverlayInset
import com.phimbud.mobile.ui.components.PosterGridCard
import com.phimbud.mobile.ui.components.ProgressiveTopBlur
import com.phimbud.mobile.ui.components.glassBackdropSource
import com.phimbud.mobile.ui.components.liquidGlass
import com.phimbud.mobile.ui.components.rememberGlassBackdropState
import com.phimbud.mobile.ui.settings.LocalGlassSettings
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixGold
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

private val HeaderHeight = 64.dp

@Composable
fun MyListScreen(
    viewModel: MyListViewModel,
    onMovieClick: (Movie) -> Unit,
    onLogoutClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val userSession by viewModel.userSession.collectAsState()
    val homeFeed by viewModel.homeFeed.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    val wishlist = homeFeed.wishlist
    val watching = homeFeed.watching
    val displayList = if (selectedTab == 0) wishlist else watching

    val gridState = rememberLazyGridState()
    val backdrop = rememberGlassBackdropState()
    val accent = LocalGlassSettings.current.accent.color

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val horizontal = if (isWideScreen) 44.dp else 16.dp
    val gridSpacing = if (isWideScreen) 16.dp else 12.dp

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
            item(key = "profile", span = { GridItemSpan(maxLineSpan) }, contentType = "profile") {
                ProfileCard(
                    name = userSession.userName,
                    email = userSession.userEmail,
                    isVip = userSession.isVip,
                    wishlistCount = wishlist.size,
                    watchingCount = watching.size,
                    accent = accent
                )
            }

            item(key = "tabs", span = { GridItemSpan(maxLineSpan) }, contentType = "tabs") {
                GlassSegmentedControl(
                    options = listOf(
                        "Danh sách (${wishlist.size})",
                        "Đang xem (${watching.size})"
                    ),
                    selectedIndex = selectedTab,
                    onSelect = { viewModel.selectTab(it) }
                )
            }

            if (displayList.isEmpty()) {
                item(key = "empty_$selectedTab", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                    EmptyState(isWishlist = selectedTab == 0)
                }
            } else {
                items(
                    items = displayList,
                    key = { it.fid },
                    contentType = { "poster" }
                ) { movie ->
                    PosterGridCard(
                        movie = movie,
                        onClick = { onMovieClick(movie) },
                        isWideScreen = isWideScreen
                    )
                }
            }
        }

        ProgressiveTopBlur(
            state = backdrop,
            height = topInset + HeaderHeight + 22.dp,
            modifier = Modifier.align(Alignment.TopStart)
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = topInset)
                .padding(horizontal = horizontal)
                .fillMaxWidth()
                .height(HeaderHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Của tôi",
                color = NetflixTextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            LiquidGlassIconButton(state = backdrop, onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Cài đặt giao diện",
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(10.dp))
            LiquidGlassIconButton(
                state = backdrop,
                onClick = {
                    viewModel.logout()
                    onLogoutClick()
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Đăng xuất",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    name: String,
    email: String,
    isVip: Boolean,
    wishlistCount: Int,
    watchingCount: Int,
    accent: Color
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape, fillAlpha = 0.09f)
            .clip(shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .liquidGlass(CircleShape, tint = accent, fillAlpha = 0.24f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name.ifBlank { "Người dùng" },
                        color = NetflixTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isVip) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "VIP",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .liquidGlass(RoundedCornerShape(50), tint = NetflixGold, fillAlpha = 0.24f)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = email.ifBlank { "Đã kết nối" },
                    color = NetflixTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                icon = {
                    Icon(Icons.Default.Bookmark, null, tint = accent, modifier = Modifier.size(18.dp))
                },
                value = wishlistCount,
                label = "Trong danh sách",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                icon = {
                    Icon(Icons.Default.PlayCircle, null, tint = accent, modifier = Modifier.size(18.dp))
                },
                value = watchingCount,
                label = "Đang xem",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatTile(
    icon: @Composable () -> Unit,
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.07f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = value.toString(),
                color = NetflixTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = label, color = NetflixTextSecondary, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyState(isWishlist: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isWishlist) Icons.Default.Bookmark else Icons.Default.PlayCircle,
            contentDescription = null,
            tint = NetflixTextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = if (isWishlist) "Chưa có phim trong Danh sách" else "Chưa có phim nào đang xem",
            color = NetflixTextSecondary,
            fontSize = 14.sp
        )
    }
}
