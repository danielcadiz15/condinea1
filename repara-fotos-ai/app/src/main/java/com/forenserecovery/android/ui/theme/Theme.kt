package com.reparafotos.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = NeonBlue,
    onPrimary = DarkBackground,
    primaryContainer = ElectricPurple,
    onPrimaryContainer = LightSurface,
    secondary = MagentaPop,
    onSecondary = DarkBackground,
    tertiary = MintSignal,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = LightSurface,
    surface = DarkSurface,
    onSurface = LightSurface,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = LightSurfaceAlt,
    outline = DarkOutline
)

private val LightColors = lightColorScheme(
    primary = ElectricPurple,
    onPrimary = LightSurface,
    primaryContainer = NeonBlue,
    onPrimaryContainer = DarkBackground,
    secondary = MagentaPop,
    onSecondary = LightSurface,
    tertiary = MintSignal,
    onTertiary = DarkBackground,
    background = LightBackground,
    onBackground = DarkBackground,
    surface = LightSurface,
    onSurface = DarkBackground,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = DarkSurface,
    outline = LightOutline
)

@Composable
fun ReparaFotosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
