package com.shashsam.boop.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- Static fallback color schemes (Android 11 and below) ---

private val BoopDarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Purple20,
    primaryContainer = Purple30,
    onPrimaryContainer = Purple90,
    secondary = Teal80,
    onSecondary = Teal20,
    secondaryContainer = Teal40,
    onSecondaryContainer = Teal90,
    tertiary = Rose80,
    onTertiary = Rose40,
    tertiaryContainer = Rose40,
    onTertiaryContainer = Rose90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral90,
    error = ErrorRed
)

private val BoopLightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Neutral99,
    primaryContainer = Purple90,
    onPrimaryContainer = Purple10,
    secondary = Teal40,
    onSecondary = Neutral99,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,
    tertiary = Rose40,
    onTertiary = Neutral99,
    tertiaryContainer = Rose90,
    onTertiaryContainer = Rose40,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Neutral20,
    error = ErrorRed
)

/**
 * Boop Material 3 theme.
 *
 * Dynamic color is enabled on Android 12+ (API 31) devices, where the system
 * generates a color scheme from the user's wallpaper. On older devices the
 * static [BoopLightColorScheme] / [BoopDarkColorScheme] are used instead.
 */
@Composable
fun BoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BoopDarkColorScheme
        else -> BoopLightColorScheme
    }

    // Set the system status bar to match the theme background color.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BoopTypography,
        content = content
    )
}
