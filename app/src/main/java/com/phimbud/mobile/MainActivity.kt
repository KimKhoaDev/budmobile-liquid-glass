package com.phimbud.mobile

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
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

class MainActivity : ComponentActivity() {

    private var isInPlayerScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PhimBudApp
        val repo = app.repository

        setContent {
            PhimBudTheme {
                val navController = rememberNavController()
                val isLoggedIn by repo.isLoggedIn.collectAsState()
                val startDestination = if (isLoggedIn) "main" else "login"

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize().background(NetflixBlack)
                ) {
                    // Login Screen
                    composable("login") {
                        val loginVm = viewModel { LoginViewModel(repo) }
                        LoginScreen(
                            viewModel = loginVm,
                            onLoginSuccess = {
                                navController.navigate("main") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }

                    // Main App Shell (with Bottom Nav: Home, Browse, Search, MyList)
                    composable("main") {
                        isInPlayerScreen = false
                        var currentTab by remember { mutableStateOf("home") }

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
                                when (currentTab) {
                                    "home" -> {
                                        val homeVm = viewModel { HomeViewModel(repo) }
                                        HomeScreen(
                                            viewModel = homeVm,
                                            onMovieClick = { movie ->
                                                navController.navigate("detail/${movie.fid}")
                                            },
                                            onPlayClick = { watchFid ->
                                                navController.navigate("player/$watchFid")
                                            },
                                            onSearchClick = {
                                                currentTab = "search"
                                            }
                                        )
                                    }
                                    "browse" -> {
                                        val browseVm = viewModel { BrowseViewModel(repo) }
                                        BrowseScreen(
                                            viewModel = browseVm,
                                            onMovieClick = { movie ->
                                                navController.navigate("detail/${movie.fid}")
                                            }
                                        )
                                    }
                                    "search" -> {
                                        val searchVm = viewModel { SearchViewModel(repo) }
                                        SearchScreen(
                                            viewModel = searchVm,
                                            onItemClick = { fid ->
                                                navController.navigate("detail/$fid")
                                            }
                                        )
                                    }
                                    "mylist" -> {
                                        val myListVm = viewModel { MyListViewModel(repo) }
                                        MyListScreen(
                                            viewModel = myListVm,
                                            onMovieClick = { movie ->
                                                navController.navigate("detail/${movie.fid}")
                                            },
                                            onLogoutClick = {
                                                navController.navigate("login") {
                                                    popUpTo("main") { inclusive = true }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Detail Screen
                    composable(
                        route = "detail/{fid}",
                        arguments = listOf(navArgument("fid") { type = NavType.StringType })
                    ) { backStackEntry ->
                        isInPlayerScreen = false
                        val fid = backStackEntry.arguments?.getString("fid").orEmpty()
                        val detailVm = remember(fid) { DetailViewModel(repo, fid) }

                        DetailScreen(
                            viewModel = detailVm,
                            onBackClick = { navController.popBackStack() },
                            onPlayClick = { watchFid ->
                                navController.navigate("player/$watchFid")
                            },
                            onRelatedMovieClick = { movie ->
                                navController.navigate("detail/${movie.fid}")
                            }
                        )
                    }

                    // Fullscreen Video Player
                    composable(
                        route = "player/{watchFid}",
                        arguments = listOf(navArgument("watchFid") { type = NavType.StringType })
                    ) { backStackEntry ->
                        isInPlayerScreen = true
                        val watchFid = backStackEntry.arguments?.getString("watchFid").orEmpty()
                        val playerVm = remember(watchFid) { PlayerViewModel(repo, watchFid) }

                        PlayerScreen(
                            viewModel = playerVm,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isInPlayerScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}
