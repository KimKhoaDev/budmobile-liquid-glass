package com.phimbud.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

data class TopBarItem(
    val route: String,
    val title: String
)

val DesktopNavItems = listOf(
    TopBarItem("home", "Trang chủ"),
    TopBarItem("shows", "Phim bộ"),
    TopBarItem("movies", "Phim lẻ"),
    TopBarItem("browse", "Khám phá"),
    TopBarItem("mylist", "Của tôi")
)

@Composable
fun TopNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onLogoutClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NetflixBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Logo
            Text(
                text = "PHIMBUD",
                color = NetflixRed,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onNavigate("home") }
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.width(36.dp))

            // Navigation Links
            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DesktopNavItems.forEach { item ->
                    val selected = currentRoute == item.route
                    val textColor by animateColorAsState(
                        targetValue = if (selected) Color.White else NetflixTextSecondary
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .then(
                                if (selected) Modifier.liquidGlassPress(RoundedCornerShape(50), fillAlpha = 0.14f)
                                else Modifier
                            )
                            .clip(RoundedCornerShape(50))
                            .clickable { onNavigate(item.route) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .height(2.5.dp)
                                    .width(22.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(NetflixRed)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(2.5.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Search Quick Button
            IconButton(
                onClick = { onNavigate("search") },
                modifier = Modifier.size(38.dp).liquidGlassPress(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Tìm kiếm",
                    tint = if (currentRoute == "search") Color.White else NetflixTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Logout Button
            if (onLogoutClick != null) {
                IconButton(
                    onClick = onLogoutClick,
                    modifier = Modifier.size(38.dp).liquidGlassPress(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Đăng xuất",
                        tint = NetflixTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Subtle bottom border
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 1.dp
        )
    }
}
