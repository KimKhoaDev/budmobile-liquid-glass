package com.phimbud.mobile.ui.screens.search

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.phimbud.mobile.core.model.SearchItem
import com.phimbud.mobile.ui.components.LocalBottomOverlayInset
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixCard
import com.phimbud.mobile.ui.theme.NetflixDarkGray
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextPrimary
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(
                horizontal = if (isWideScreen) 44.dp else 16.dp,
                vertical = if (isWideScreen) 18.dp else 12.dp
            )
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onQueryChange(it) },
            placeholder = {
                Text(
                    "Tìm tên phim, diễn viên hoặc link IMDb",
                    color = NetflixTextSecondary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Tìm kiếm",
                    tint = NetflixTextSecondary
                )
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Xóa",
                            tint = NetflixTextSecondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = NetflixRed.copy(alpha = 0.7f),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(50), fillAlpha = 0.1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NetflixRed)
            }
        } else if (results.isEmpty() && query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Không tìm thấy phim phù hợp với \"$query\"",
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
                    items = results,
                    key = { it.fid },
                    contentType = { "search_item" }
                ) { item ->
                    SearchItemCard(
                        item = item,
                        onClick = { onItemClick(item.fid) },
                        isWideScreen = isWideScreen
                    )
                }
            }
        }
    }
}

@Composable
fun SearchItemCard(
    item: SearchItem,
    onClick: () -> Unit,
    isWideScreen: Boolean = false
) {
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
            ) { onClick() }
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
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isWideScreen) 6.dp else 4.dp))

        Text(
            text = item.displayTitle,
            color = if (isWideScreen && isHovered) Color.White else NetflixTextPrimary,
            fontSize = if (isWideScreen) 14.sp else 12.sp,
            fontWeight = if (isWideScreen && isHovered) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = if (isWideScreen) 2 else 1,
            lineHeight = if (isWideScreen) 18.sp else 14.sp,
            overflow = TextOverflow.Ellipsis
        )
    }
}
