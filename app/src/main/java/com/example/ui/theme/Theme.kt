package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SquadPrimary,
    secondary = SquadGreen,
    tertiary = SquadRed,
    background = SquadBackground,
    surface = SquadSurfaceLayer,
    onPrimary = SquadTextPrimary,
    onSecondary = SquadTextPrimary,
    onTertiary = SquadTextPrimary,
    onBackground = SquadTextPrimary,
    onSurface = SquadTextPrimary,
    onSurfaceVariant = SquadTextSecondary,
    surfaceVariant = SquadHover
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
