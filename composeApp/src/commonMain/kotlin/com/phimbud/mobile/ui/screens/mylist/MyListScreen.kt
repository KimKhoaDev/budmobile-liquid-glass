package com.phimbud.mobile.ui.screens.mylist

import com.phimbud.mobile.ui.components.liquidGlass
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.phimbud.mobile.core.model.Movie
import com.phimbud.mobile.ui.components.LocalBottomOverlayInset
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixDarkGray
import com.phimbud.mobile.ui.theme.NetflixGold
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

@Composable
fun MyListScreen(
    viewModel: MyListViewModel,
    onMovieClick: (Movie) -> Unit,
    onLogoutClick: () -> Unit,
    isWideScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val userSession by viewModel.userSession.collectAsState()
    val homeFeed by viewModel.homeFeed.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    val displayList = if (selectedTab == 0) homeFeed.wishlist else homeFeed.watching

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .statusBarsPadding()
            .padding(
                horizontal = if (isWideScreen) 44.dp else 16.dp,
                vertical = if (isWideScreen) 18.dp else 16.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(RoundedCornerShape(24.dp), fillAlpha = 0.09f)
                .clip(RoundedCornerShape(24.dp))
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .liquidGlass(CircleShape, tint = NetflixRed, fillAlpha = 0.24f)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userSession.userName.firstOrNull()?.uppercase() ?: "P",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = userSession.userName.ifBlank { "Tài khoản PhimBud" },
                        color = NetflixTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (userSession.isVip) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .liquidGlass(RoundedCornerShape(50), tint = NetflixGold, fillAlpha = 0.24f)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "VIP",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = userSession.userEmail.ifBlank { "Đã kết nối" },
                    color = NetflixTextSecondary,
                    fontSize = 13.sp
                )
            }

            IconButton(onClick = {
                viewModel.logout()
                onLogoutClick()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Đăng xuất",
                    tint = NetflixTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = NetflixBlack,
            contentColor = NetflixRed,
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { viewModel.selectTab(0) },
                text = {
                    Text(
                        text = "Danh sách của tôi (${homeFeed.wishlist.size})",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 0) Color.White else NetflixTextSecondary
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { viewModel.selectTab(1) },
                text = {
                    Text(
                        text = "Đang xem (${homeFeed.watching.size})",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 1) Color.White else NetflixTextSecondary
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (displayList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedTab == 0) "Chưa có phim trong Danh sách" else "Chưa có phim nào đang xem",
                    color = NetflixTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            val gridSpacing = if (isWideScreen) 16.dp else 10.dp
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (isWideScreen) 165.dp else 110.dp),
                contentPadding = PaddingValues(bottom = 24.dp + LocalBottomOverlayInset.current),
                verticalArrangement = Arrangement.spacedBy(gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = displayList,
                    key = { it.fid },
                    contentType = { "mylist_movie" }
                ) { movie ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isWideScreen && isHovered) 1.04f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isWideScreen && isHovered) Color.White.copy(alpha = 0.55f) else Color.Transparent
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .hoverable(interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onMovieClick(movie) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(if (isWideScreen) 8.dp else 6.dp))
                                .border(
                                    width = if (isWideScreen && isHovered) 1.5.dp else 0.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(if (isWideScreen) 8.dp else 6.dp)
                                )
                                .background(NetflixCard)
                        ) {
                            if (!movie.posterUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = movie.posterUrl,
                                    contentDescription = movie.displayTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(if (isWideScreen) 6.dp else 4.dp))

                        Text(
                            text = movie.displayTitle,
                            color = if (isWideScreen && isHovered) Color.White else NetflixTextPrimary,
                            fontSize = if (isWideScreen) 14.sp else 12.sp,
                            fontWeight = if (isWideScreen && isHovered) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = if (isWideScreen) 2 else 1,
                            lineHeight = if (isWideScreen) 18.sp else 14.sp,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
