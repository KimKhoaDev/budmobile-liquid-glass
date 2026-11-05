package com.phimbud.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.phimbud.mobile.core.platform.PlatformSystemBarsEffect

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
    PlatformSystemBarsEffect()

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
