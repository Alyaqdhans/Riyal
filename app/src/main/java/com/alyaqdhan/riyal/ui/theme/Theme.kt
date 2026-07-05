@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Color the M3 Expressive way: on Android 12+ the whole palette is dynamic, derived
 * from the user's own wallpaper, the most personal theme a phone can offer. The
 * schemes below are only the fallback for older devices: warm gold pulled straight
 * from the coin mascot (fill 0xFFFFE082, ring 0xFFF9A825), so brand character comes
 * from the imperfect shapes and the coin, not from one loud accent color.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDF9E),
    onPrimaryContainer = Color(0xFF261A00),
    secondary = Color(0xFF6C5D3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6E1BB),
    onSecondaryContainer = Color(0xFF251A04),
    tertiary = Color(0xFF4B6546),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDEBC3),
    onTertiaryContainer = Color(0xFF092008),
    background = Color(0xFFFFF8F0),
    onBackground = Color(0xFF1E1B13),
    surface = Color(0xFFFFF8F0),
    onSurface = Color(0xFF1E1B13),
    surfaceVariant = Color(0xFFECE1CF),
    onSurfaceVariant = Color(0xFF4C4639),
    outline = Color(0xFF7E7667),
    outlineVariant = Color(0xFFCFC5B4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF2E5),
    surfaceContainer = Color(0xFFF4ECDF),
    surfaceContainerHigh = Color(0xFFEFE6D9),
    surfaceContainerHighest = Color(0xFFE9E1D4),
    // Snackbars are drawn with the inverse colors (M3 flips the theme so they pop);
    // without these they'd fall back to the neutral defaults and look off-brand.
    inverseSurface = Color(0xFF333027),
    inverseOnSurface = Color(0xFFF7F0E2),
    inversePrimary = Color(0xFFF4BE48),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF4BE48),
    onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF5C4300),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFD9C5A0),
    onSecondary = Color(0xFF3B2F15),
    secondaryContainer = Color(0xFF53452A),
    onSecondaryContainer = Color(0xFFF6E1BB),
    tertiary = Color(0xFFB1CFA9),
    onTertiary = Color(0xFF1D361B),
    tertiaryContainer = Color(0xFF334D30),
    onTertiaryContainer = Color(0xFFCDEBC3),
    background = Color(0xFF16130B),
    onBackground = Color(0xFFE9E1D4),
    surface = Color(0xFF16130B),
    onSurface = Color(0xFFE9E1D4),
    surfaceVariant = Color(0xFF4C4639),
    onSurfaceVariant = Color(0xFFCFC5B4),
    outline = Color(0xFF989080),
    outlineVariant = Color(0xFF4C4639),
    surfaceContainerLowest = Color(0xFF100E07),
    surfaceContainerLow = Color(0xFF1E1B13),
    surfaceContainer = Color(0xFF221F17),
    surfaceContainerHigh = Color(0xFF2D2A21),
    surfaceContainerHighest = Color(0xFF38342B),
    inverseSurface = Color(0xFFE9E1D4),
    inverseOnSurface = Color(0xFF333027),
    inversePrimary = Color(0xFF7A5900),
)

/**
 * Money semantics the M3 scheme doesn't have: green = money in / success, paired
 * with the scheme's error red for money out. Basic UX beats decorative color, so
 * these are used wherever an amount has a direction.
 */
@Composable
fun successColor() = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

@Composable
fun successContainer() = if (isSystemInDarkTheme()) Color(0xFF1B4322) else Color(0xFFC8E6C9)

@Composable
fun onSuccessContainer() = if (isSystemInDarkTheme()) Color(0xFFC8E6C9) else Color(0xFF0B2E13)

/** Softer, rounder shape scale than stock M3: friendlier and less machine-perfect. */
private val RiyalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun RiyalTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = RiyalShapes,
        content = content,
    )
}
