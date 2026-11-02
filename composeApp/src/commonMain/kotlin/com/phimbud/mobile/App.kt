package com.phimbud.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.phimbud.mobile.core.platform.PlatformBackHandler
import com.phimbud.mobile.core.repository.PhimBudRepository
import com.phimbud.mobile.ui.components.BottomNavBar
import com.phimbud.mobile.ui.screens.browse.BrowseScreen
import com.phimbud.mobile.ui.screens.browse.BrowseViewModel
import com.phimbud.mobile.ui.screens.detail.DetailScreen
import com.phimbud.mobile.ui.screens.detail.DetailViewModel
import com.phimbud.mobile.ui.screens.home.HomeScreen
import com.phimbud.mobile.ui.screens.home.HomeViewModel
import com.phimbud.mobile.ui.screens.login.LoginScreen
import com.phimbud.mobile.ui.screens.login.LoginViewModel
import com.phimbud.mobile.ui.screens.mylist.MyListScreen
import com.phimbud.mobile.ui.screens.mylist.MyListViewModel
import com.phimbud.mobile.ui.screens.player.PlayerScreen
import com.phimbud.mobile.ui.screens.player.PlayerViewModel
import com.phimbud.mobile.ui.screens.search.SearchScreen
import com.phimbud.mobile.ui.screens.search.SearchViewModel
import com.phimbud.mobile.ui.theme.NetflixBlack
import com.phimbud.mobile.ui.theme.PhimBudTheme
import okio.FileSystem

@Composable
fun App(
    repository: PhimBudRepository,
    onPlayerScreenActiveChanged: (Boolean) -> Unit = {}
) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .fileSystem(FileSystem.SYSTEM)
                    .directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "phimbud_image_cache")
                    .maxSizeBytes(100L * 1024L * 1024L) // 100 MB
                    .build()
            }
            .crossfade(150)
            .build()
    }

    PhimBudTheme {
        val navController = rememberNavController()
        val isLoggedIn by repository.isLoggedIn.collectAsState()
        val startDestination = remember { if (repository.isLoggedIn.value) "main" else "login" }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize().background(NetflixBlack)
        ) {
            composable("login") {
                val loginVm = viewModel { LoginViewModel(repository) }
                LoginScreen(
                    viewModel = loginVm,
                    onLoginSuccess = {
                        navController.navigate("main") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("main") {
                var currentTab by rememberSaveable { mutableStateOf("home") }
                val saveableStateHolder = rememberSaveableStateHolder()

                PlatformBackHandler(enabled = currentTab != "home") {
                    currentTab = "home"
                }

                Scaffold(
                    bottomBar = {
                        BottomNavBar(
                            currentRoute = currentTab,
                            onNavigate = { currentTab = it }
                        )
                    },
                    containerColor = NetflixBlack
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        saveableStateHolder.SaveableStateProvider(currentTab) {
                            when (currentTab) {
                                "home" -> {
                                    val homeVm = viewModel { HomeViewModel(repository) }
                                    HomeScreen(
                                        viewModel = homeVm,
                                        onMovieClick = { movie ->
                                            navController.navigate("detail/${movie.fid}") {
                                                launchSingleTop = true
                                            }
                                        },
                                        onPlayClick = { watchFid ->
                                            navController.navigate("player/$watchFid") {
                                                launchSingleTop = true
                                            }
                                        },
                                        onSearchClick = {
                                            currentTab = "search"
                                        }
                                    )
                                }
                                "browse" -> {
                                    val browseVm = viewModel { BrowseViewModel(repository) }
                                    BrowseScreen(
                                        viewModel = browseVm,
                                        onMovieClick = { movie ->
                                            navController.navigate("detail/${movie.fid}") {
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }
                                "search" -> {
                                    val searchVm = viewModel { SearchViewModel(repository) }
                                    SearchScreen(
                                        viewModel = searchVm,
                                        onItemClick = { fid ->
                                            navController.navigate("detail/$fid") {
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }
                                "mylist" -> {
                                    val myListVm = viewModel { MyListViewModel(repository) }
                                    MyListScreen(
                                        viewModel = myListVm,
                                        onMovieClick = { movie ->
                                            navController.navigate("detail/${movie.fid}") {
                                                launchSingleTop = true
                                            }
                                        },
                                        onLogoutClick = {
                                            navController.navigate("login") {
                                                popUpTo("main") { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            composable(
                route = "detail/{fid}",
                arguments = listOf(navArgument("fid") { type = NavType.StringType })
            ) { backStackEntry ->
                val fid = backStackEntry.arguments?.getString("fid").orEmpty()
                val detailVm = viewModel(key = fid) { DetailViewModel(repository, fid) }

                DetailScreen(
                    viewModel = detailVm,
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = { watchFid ->
                        navController.navigate("player/$watchFid") {
                            launchSingleTop = true
                        }
                    },
                    onRelatedMovieClick = { movie ->
                        navController.navigate("detail/${movie.fid}") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "player/{watchFid}",
                arguments = listOf(navArgument("watchFid") { type = NavType.StringType })
            ) { backStackEntry ->
                DisposableEffect(Unit) {
                    onPlayerScreenActiveChanged(true)
                    onDispose {
                        onPlayerScreenActiveChanged(false)
                    }
                }
                val watchFid = backStackEntry.arguments?.getString("watchFid").orEmpty()
                val playerVm = viewModel(key = watchFid) { PlayerViewModel(repository, watchFid) }

                PlayerScreen(
                    viewModel = playerVm,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        LaunchedEffect(isLoggedIn) {
            val currentRoute = navController.currentDestination?.route
            if (!isLoggedIn && currentRoute != null && currentRoute != "login") {
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }
}
