package com.phimbud.mobile.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
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
@OptIn(ExperimentalMaterial3Api::class)
fun PhimBudTheme(content: @Composable () -> Unit) {
    PlatformSystemBarsEffect()

    MaterialTheme(
        colorScheme = DarkColorScheme
    ) {
        // No Material ripple anywhere: press feedback comes from the Liquid Glass physics instead.
        CompositionLocalProvider(
            LocalRippleConfiguration provides null,
            content = content
        )
    }
}
