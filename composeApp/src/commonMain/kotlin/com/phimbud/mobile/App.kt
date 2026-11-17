package com.phimbud.mobile

import com.phimbud.mobile.ui.components.LocalAmbientBackdrop
import com.phimbud.mobile.ui.components.AuroraBackground
import androidx.compose.ui.graphics.Color
import com.phimbud.mobile.ui.screens.settings.SettingsScreen
import com.phimbud.mobile.ui.settings.GlassSettingsController
import com.phimbud.mobile.ui.settings.LocalGlassSettings
import com.phimbud.mobile.ui.settings.LocalGlassSettingsController
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.phimbud.mobile.core.model.BrowseFilter
import com.phimbud.mobile.core.model.HomeFilterMode
import com.phimbud.mobile.core.platform.PlatformBackHandler
import com.phimbud.mobile.core.platform.configurePlatformImageLoader
import com.phimbud.mobile.core.platform.platformImageCacheDirectory
import com.phimbud.mobile.core.repository.PhimBudRepository
import com.phimbud.mobile.ui.components.GlassBottomBarInset
import com.phimbud.mobile.ui.components.GlassBottomNavBar
import com.phimbud.mobile.ui.components.LocalBottomOverlayInset
import com.phimbud.mobile.ui.components.glassBackdropSource
import com.phimbud.mobile.ui.components.rememberGlassBackdropState
import com.phimbud.mobile.ui.components.TopNavBar
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
    onPlayerScreenActiveChanged: (Boolean) -> Unit = {},
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {}
) {
    setSingletonImageLoaderFactory { context ->
        configurePlatformImageLoader(
            ImageLoader.Builder(context),
            repository.platformContext
        )
            .components {
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = { repository.apiClient.httpEngine.publicHttpClient }
                    )
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .fileSystem(FileSystem.SYSTEM)
                    .directory(platformImageCacheDirectory(repository.platformContext))
                    .maxSizeBytes(100L * 1024L * 1024L) // 100 MB
                    .build()
            }
            .crossfade(150)
            .build()
    }

    PhimBudTheme {
        val glassController = remember { GlassSettingsController(repository.platformContext) }
        val ambientBackdrop = rememberGlassBackdropState()
        val navController = rememberNavController()
        val isLoggedIn by repository.isLoggedIn.collectAsState()
        val startDestination = remember { if (repository.isLoggedIn.value) "main" else "login" }
        var pendingBrowseFilter by remember { mutableStateOf<BrowseFilter?>(null) }

        CompositionLocalProvider(
            LocalGlassSettings provides glassController.settings,
            LocalGlassSettingsController provides glassController,
            LocalAmbientBackdrop provides ambientBackdrop
        ) {
        Box(Modifier.fillMaxSize()) {
        // Wallpaper that every glass control refracts (and the visible app background).
        AuroraBackground(Modifier.fillMaxSize().glassBackdropSource(ambientBackdrop))
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            // The Navigation default is a 700ms cross-fade, which reads as lag. Keep it short.
            enterTransition = { fadeIn(tween(200, easing = EaseOutCubic)) },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(200, easing = EaseOutCubic)) },
            popExitTransition = { fadeOut(tween(140)) }
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
                val homeVm = viewModel { HomeViewModel(repository) }
                val browseVm = viewModel { BrowseViewModel(repository) }
                val myListVm = viewModel { MyListViewModel(repository) }

                LaunchedEffect(pendingBrowseFilter) {
                    pendingBrowseFilter?.let { filter ->
                        browseVm.updateFilter(filter)
                        currentTab = "browse"
                        pendingBrowseFilter = null
                    }
                }

                val browseFilter by browseVm.filter.collectAsState()

                val activeTopRoute = when (currentTab) {
                    "home" -> "home"
                    "search" -> "search"
                    "mylist" -> "mylist"
                    "browse" -> {
                        when (browseFilter.type) {
                            "show" -> "shows"
                            "movie" -> "movies"
                            else -> "browse"
                        }
                    }
                    else -> currentTab
                }

                PlatformBackHandler(enabled = currentTab != "home") {
                    currentTab = "home"
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideScreen = maxWidth >= 720.dp

                    val glassState = rememberGlassBackdropState()
                    CompositionLocalProvider(
                        LocalBottomOverlayInset provides (if (isWideScreen) 0.dp else GlassBottomBarInset)
                    ) {
                    Scaffold(
                        topBar = {
                            if (isWideScreen) {
                                TopNavBar(
                                    currentRoute = activeTopRoute,
                                    onNavigate = { route ->
                                        when (route) {
                                            "home" -> {
                                                homeVm.selectFilterMode(HomeFilterMode.ALL)
                                                currentTab = "home"
                                            }
                                            "shows" -> {
                                                browseVm.updateType("show")
                                                currentTab = "browse"
                                            }
                                            "movies" -> {
                                                browseVm.updateType("movie")
                                                currentTab = "browse"
                                            }
                                            "browse" -> {
                                                browseVm.updateType(null)
                                                currentTab = "browse"
                                            }
                                            else -> {
                                                currentTab = route
                                            }
                                        }
                                    },
                                    onLogoutClick = {
                                        myListVm.logout()
                                        navController.navigate("login") {
                                            popUpTo("main") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                        },
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().glassBackdropSource(glassState)) {
                            saveableStateHolder.SaveableStateProvider(currentTab) {
                                when (currentTab) {
                                    "home" -> {
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
                                            },
                                            onNavigateToBrowse = { filter ->
                                                browseVm.updateFilter(filter)
                                                currentTab = "browse"
                                            },
                                            onNavigateToMyList = { tabIndex ->
                                                myListVm.selectTab(tabIndex)
                                                currentTab = "mylist"
                                            },
                                            isWideScreen = isWideScreen
                                        )
                                    }
                                "browse" -> {
                                    LaunchedEffect(Unit) {
                                        browseVm.ensureInitialized()
                                    }
                                    BrowseScreen(
                                        viewModel = browseVm,
                                        onMovieClick = { movie ->
                                            navController.navigate("detail/${movie.fid}") {
                                                launchSingleTop = true
                                            }
                                        },
                                        isWideScreen = isWideScreen
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
                                        },
                                        isWideScreen = isWideScreen
                                    )
                                }
                                "mylist" -> {
                                    LaunchedEffect(Unit) {
                                        myListVm.refresh()
                                    }
                                    MyListScreen(
                                        viewModel = myListVm,
                                        onSettingsClick = {
                                            navController.navigate("settings") { launchSingleTop = true }
                                        },
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
                                        },
                                        isWideScreen = isWideScreen
                                    )
                                }
                            }
                        }
                    }
                }
                    }

                    if (!isWideScreen) {
                        GlassBottomNavBar(
                            state = glassState,
                            currentRoute = currentTab,
                            onNavigate = { route ->
                                        if (route == "home" && currentTab == "home") {
                                            homeVm.selectFilterMode(HomeFilterMode.ALL)
                                        }
                                        currentTab = route
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

            }
            }

            composable("settings") {
                SettingsScreen(onBackClick = { navController.popBackStack() })
            }

            composable(
                route = "detail/{fid}",
                arguments = listOf(navArgument("fid") { type = NavType.StringType }),
                // Opening a film: the page blooms in from slightly smaller, cinematic and quick.
                enterTransition = {
                    fadeIn(tween(340, easing = EaseOutCubic)) +
                        scaleIn(initialScale = 0.9f, animationSpec = tween(420, easing = EaseOutCubic))
                },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(240, easing = EaseOutCubic)) },
                popExitTransition = {
                    fadeOut(tween(240, easing = EaseInCubic)) +
                        scaleOut(targetScale = 0.92f, animationSpec = tween(280, easing = EaseInCubic))
                }
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
                    },
                    onFilterClick = { filter ->
                        pendingBrowseFilter = filter
                        navController.popBackStack("main", inclusive = false)
                    }
                )
            }

            composable(
                route = "player/{watchFid}",
                arguments = listOf(navArgument("watchFid") { type = NavType.StringType }),
                enterTransition = {
                    fadeIn(animationSpec = tween(280, easing = EaseOutCubic)) +
                        scaleIn(initialScale = 0.95f, animationSpec = tween(280, easing = EaseOutCubic))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200, easing = EaseInCubic)) +
                        scaleOut(targetScale = 0.95f, animationSpec = tween(200, easing = EaseInCubic))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(200, easing = EaseOutCubic))
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(220, easing = EaseInCubic)) +
                        scaleOut(targetScale = 0.95f, animationSpec = tween(220, easing = EaseInCubic))
                }
            ) { backStackEntry ->
                DisposableEffect(Unit) {
                    onPlayerScreenActiveChanged(true)
                    onDispose {
                        onPlayerScreenActiveChanged(false)
                    }
                }
                val watchFid = backStackEntry.arguments?.getString("watchFid").orEmpty()
                val playerVm = viewModel(key = watchFid) { PlayerViewModel(repository, watchFid) }

                // Over video there is nothing to refract, so glass falls back to decorative mode.
                CompositionLocalProvider(LocalAmbientBackdrop provides null) {
                    PlayerScreen(
                        viewModel = playerVm,
                        onBackClick = { navController.popBackStack() },
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = onToggleFullscreen
                    )
                }
            }
        }
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
