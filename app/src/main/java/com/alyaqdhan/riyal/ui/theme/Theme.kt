@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Riyal's own loud, warm palette (Talabat-style: orange first, with teal and pink
 * pulling their weight). Deliberately NOT dynamic color: the brand look wins over
 * whatever blue the system wallpaper suggests.
 *
 * primary   = sunset orange (buttons, selection)
 * secondary = teal (received money, calm accents)
 * tertiary  = punchy pink (halos, highlights, playful bits)
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFFEA5B0C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF380D00),
    secondary = Color(0xFF00897B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF9CF2E2),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFFC2185B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3E001D),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DED3),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD7C2B8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFCEAE0),
    surfaceContainerHigh = Color(0xFFF6E4D9),
    surfaceContainerHighest = Color(0xFFF0DED3),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB68E),
    onPrimary = Color(0xFF541F00),
    primaryContainer = Color(0xFF773008),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFF7FD6C6),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005045),
    onSecondaryContainer = Color(0xFF9CF2E2),
    tertiary = Color(0xFFFFB0C8),
    onTertiary = Color(0xFF650033),
    tertiaryContainer = Color(0xFF8E2A56),
    onTertiaryContainer = Color(0xFFFFD9E2),
    background = Color(0xFF1A120D),
    onBackground = Color(0xFFF0DED3),
    surface = Color(0xFF1A120D),
    onSurface = Color(0xFFF0DED3),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFFA08D83),
    outlineVariant = Color(0xFF52443C),
    surfaceContainerLowest = Color(0xFF140D09),
    surfaceContainerLow = Color(0xFF221A15),
    surfaceContainer = Color(0xFF271E18),
    surfaceContainerHigh = Color(0xFF322822),
    surfaceContainerHighest = Color(0xFF3D332C),
)

@Composable
fun RiyalTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
