package com.shashsam.boop.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Extended design tokens ──────────────────────────────────────────────────

/**
 * Non-M3 design tokens for the Boop "Solid Geometric" design system.
 * Accessible via [LocalBoopTokens] from any composable.
 *
 * Each token maps to a concrete color so screens never need to branch on
 * dark/light — the tokens carry the correct value for the active theme.
 */
data class BoopExtendedTokens(
    val accent: Color,
    val glowColor: Color,
    val glassBg: Color,
    val glassBorder: Color,
    val cardBackground: Color,
    val elevatedBackground: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    // Pill mode-selector
    val pillContainer: Color,
    val pillActive: Color,
    // Concentric-circle CTA
    val concentricDashed: Color,
    val concentricOuter: Color,
    val concentricInner: Color,
    // Bottom navigation
    val navBarContainer: Color,
    val navIndicator: Color,
    // Dialogs & bottom sheets
    val dialogSurface: Color,
)

val LocalBoopTokens = staticCompositionLocalOf {
    BoopExtendedTokens(
        accent = BoopAccentYellow,
        glowColor = BoopGlowYellow,
        glassBg = GlassWhiteBg,
        glassBorder = GlassWhiteBorder,
        cardBackground = BoopDarkCard,
        elevatedBackground = BoopDarkElevated,
        textSecondary = BoopOnDarkSecondary,
        textTertiary = BoopOnDarkTertiary,
        pillContainer = Color(0xFF1A1A1A),
        pillActive = Color(0xFF2A2A2A),
        concentricDashed = Color(0xFF333333),
        concentricOuter = Color(0xFF222222),
        concentricInner = Color(0xFF1A1A1A),
        navBarContainer = BoopBlack,
        navIndicator = Color(0xFF2A2A2A),
        dialogSurface = BoopDarkSurface,
    )
}

// ─── Color schemes ───────────────────────────────────────────────────────────

private val BoopDarkColorScheme = darkColorScheme(
    primary = BoopBrandPurple,
    onPrimary = BoopOnDark,
    primaryContainer = BoopBrandPurpleDark,
    onPrimaryContainer = BoopOnDark,
    secondary = BoopBrandPurpleLight,
    onSecondary = BoopBlack,
    secondaryContainer = BoopDarkSurfaceVariant,
    onSecondaryContainer = BoopOnDark,
    tertiary = BoopAccentYellow,
    onTertiary = BoopBlack,
    tertiaryContainer = BoopAccentYellowDark,
    onTertiaryContainer = BoopBlack,
    background = BoopBlack,
    onBackground = BoopOnDark,
    surface = BoopDarkSurface,
    onSurface = BoopOnDark,
    surfaceVariant = BoopDarkSurfaceVariant,
    onSurfaceVariant = BoopOnDarkSecondary,
    outline = BoopOnDarkTertiary,
    outlineVariant = Color(0xFF333333),
    error = ErrorRed,
    onError = BoopOnDark,
    errorContainer = Color(0xFF3B1F1F),
    onErrorContainer = ErrorRed
)

/**
 * Purple-dominant light scheme — purple background, yellow + white accents.
 */
private val BoopLightColorScheme = lightColorScheme(
    primary = Color.White,
    onPrimary = BoopBrandPurpleDark,
    primaryContainer = Color(0xFFEEEEFF),
    onPrimaryContainer = BoopBrandPurpleDark,
    secondary = BoopAccentYellow,
    onSecondary = Color(0xFF1A1A00),
    secondaryContainer = Color(0x33F8FFA3),
    onSecondaryContainer = Color(0xFF1A1A00),
    tertiary = BoopAccentYellow,
    onTertiary = Color(0xFF1A1A00),
    tertiaryContainer = BoopAccentYellowDark,
    onTertiaryContainer = Color(0xFF1A1A00),
    background = BoopBrandPurple,
    onBackground = Color.White,
    surface = BoopPurpleSurface,
    onSurface = Color.White,
    surfaceVariant = BoopPurpleSurfaceVariant,
    onSurfaceVariant = Color(0xFFD0CDFF),
    outline = Color(0xFFADA9E0),
    outlineVariant = Color(0xFF4D47B8),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFF4D2030),
    onErrorContainer = ErrorRed
)

private val DarkExtendedTokens = BoopExtendedTokens(
    accent = BoopAccentYellow,
    glowColor = BoopGlowYellow,
    glassBg = GlassWhiteBg,
    glassBorder = GlassWhiteBorder,
    cardBackground = BoopDarkCard,
    elevatedBackground = BoopDarkElevated,
    textSecondary = BoopOnDarkSecondary,
    textTertiary = BoopOnDarkTertiary,
    pillContainer = Color(0xFF1A1A1A),
    pillActive = Color(0xFF2A2A2A),
    concentricDashed = Color(0xFF333333),
    concentricOuter = Color(0xFF222222),
    concentricInner = Color(0xFF1A1A1A),
    navBarContainer = BoopBlack,
    navIndicator = Color(0xFF2A2A2A),
    dialogSurface = BoopDarkSurface,
)

private val LightExtendedTokens = BoopExtendedTokens(
    accent = Color.White,
    glowColor = Color(0x26FFFFFF),  // 15% white glow
    glassBg = Color(0x26FFFFFF),
    glassBorder = Color(0x33FFFFFF),
    cardBackground = Color(0x1AFFFFFF),
    elevatedBackground = Color(0x26FFFFFF),
    textSecondary = BoopOnPurpleSecondary,
    textTertiary = BoopOnPurpleTertiary,
    pillContainer = Color(0xFF5D57D0),
    pillActive = Color(0xFF8A85F5),
    concentricDashed = Color(0xFF9590F0),
    concentricOuter = Color(0xFF6560D8),
    concentricInner = Color(0xFF5D57D0),
    navBarContainer = BoopPurpleSurfaceVariant,
    navIndicator = Color(0xFF5550C0),
    dialogSurface = BoopLightDialogSurface,
)

// ─── Theme composable ────────────────────────────────────────────────────────

/**
 * Boop Material 3 theme with "Solid Geometric" design system.
 *
 * Brand colors (purple primary, yellow accent) are always used —
 * dynamic color is not supported to maintain the design identity.
 */
@Composable
fun BoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BoopDarkColorScheme else BoopLightColorScheme
    val extendedTokens = if (darkTheme) DarkExtendedTokens else LightExtendedTokens

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            // Both themes have dark backgrounds — light status bar icons when bg is dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                colorScheme.background.luminance() > 0.5f
        }
    }

    CompositionLocalProvider(LocalBoopTokens provides extendedTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BoopTypography,
            content = content
        )
    }
}
