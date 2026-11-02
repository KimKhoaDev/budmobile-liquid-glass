package com.phimbud.mobile.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.NetflixRed
import com.phimbud.mobile.ui.theme.NetflixTextSecondary

enum class NavItem(val route: String, val title: String, val icon: ImageVector) {
    HOME("home", "Trang chủ", Icons.Default.Home),
    BROWSE("browse", "Khám phá", Icons.Default.VideoLibrary),
    SEARCH("search", "Tìm kiếm", Icons.Default.Search),
    MY_LIST("mylist", "Của tôi", Icons.Default.Bookmark)
}

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = NetflixBlack,
        tonalElevation = 0.dp
    ) {
        NavItem.entries.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NetflixRed,
                    selectedTextColor = NetflixRed,
                    unselectedIconColor = NetflixTextSecondary,
                    unselectedTextColor = NetflixTextSecondary,
                    indicatorColor = NetflixBlack
                )
            )
        }
    }
}
