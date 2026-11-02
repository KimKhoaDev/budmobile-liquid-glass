package com.phimbud.mobile.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NetflixRed,
    onPrimary = NetflixTextPrimary,
    primaryContainer = NetflixDarkRed,
    onPrimaryContainer = NetflixTextPrimary,
    secondary = NetflixTextSecondary,
    onSecondary = NetflixTextPrimary,
    background = NetflixBlack,
    onBackground = NetflixTextPrimary,
    surface = NetflixDarkGray,
    onSurface = NetflixTextPrimary,
    surfaceVariant = NetflixCard,
    onSurfaceVariant = NetflixTextSecondary
)

@Composable
fun PhimBudTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = NetflixBlack.toArgb()
            window.navigationBarColor = NetflixBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
