package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// High-contrast monochrome OLED dark color scheme (Nothing OS Dark)
val NothingDarkColorScheme = darkColorScheme(
    primary = NothingPureWhite,
    onPrimary = NothingPureBlack,
    primaryContainer = NothingSurfaceVariant,
    onPrimaryContainer = NothingPureWhite,
    secondary = NothingSignatureRed,
    onSecondary = NothingPureWhite,
    secondaryContainer = NothingSignatureRed.copy(alpha = 0.2f),
    onSecondaryContainer = NothingPureWhite,
    tertiary = NothingPureWhite,
    onTertiary = NothingPureBlack,
    tertiaryContainer = NothingSurface,
    onTertiaryContainer = NothingPureWhite,
    background = NothingPureBlack,
    onBackground = NothingPureWhite,
    surface = NothingDarkGray,
    onSurface = NothingPureWhite,
    surfaceVariant = NothingSurface,
    onSurfaceVariant = NothingTextSecondary,
    surfaceContainerLowest = NothingPureBlack,
    surfaceContainerLow = NothingOledBlack,
    surfaceContainer = NothingCharcoal,
    surfaceContainerHigh = NothingSurface,
    surfaceContainerHighest = NothingSurfaceVariant,
    outline = NothingBorder,
    outlineVariant = NothingBorderSubtle,
    error = NothingSignatureRed,
    onError = NothingPureWhite
)

// High-contrast stark monochrome light color scheme (Nothing OS Light)
val NothingLightColorScheme = lightColorScheme(
    primary = NothingLightTextPrimary,
    onPrimary = NothingPureWhite,
    primaryContainer = NothingLightSurfaceVariant,
    onPrimaryContainer = NothingLightTextPrimary,
    secondary = NothingSignatureRed,
    onSecondary = NothingPureWhite,
    secondaryContainer = NothingSignatureRed.copy(alpha = 0.12f),
    onSecondaryContainer = NothingSignatureRed,
    tertiary = NothingLightTextPrimary,
    onTertiary = NothingPureWhite,
    tertiaryContainer = NothingLightSurface,
    onTertiaryContainer = NothingLightTextPrimary,
    background = NothingLightBackground,
    onBackground = NothingLightTextPrimary,
    surface = NothingLightSurface,
    onSurface = NothingLightTextPrimary,
    surfaceVariant = NothingLightSurfaceVariant,
    onSurfaceVariant = NothingLightTextSecondary,
    surfaceContainerLowest = NothingLightBackground,
    surfaceContainerLow = NothingLightSurface,
    surfaceContainer = NothingLightSurfaceVariant,
    surfaceContainerHigh = NothingLightSurfaceContainer,
    surfaceContainerHighest = Color(0xFFD6D6DE),
    outline = NothingLightBorder,
    outlineVariant = Color(0xFFE2E2E8),
    error = NothingSignatureRed,
    onError = NothingPureWhite
)

@Immutable
data class NothingPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceBorder: Color,
    val borderContrast: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val signatureRed: Color,
    val glyphAccent: Color,
    val statusOnline: Color,
    val dealAccent: Color
)

val LocalNothingPalette = staticCompositionLocalOf {
    NothingPalette(
        isDark = true,
        background = NothingPureBlack,
        surface = NothingDarkGray,
        surfaceVariant = NothingSurface,
        surfaceBorder = NothingBorder,
        borderContrast = NothingBorderContrast,
        textPrimary = NothingPureWhite,
        textSecondary = NothingTextSecondary,
        textMuted = NothingTextMuted,
        signatureRed = NothingSignatureRed,
        glyphAccent = NothingGlyphRed,
        statusOnline = NothingGreen,
        dealAccent = NothingDealOrange
    )
}

object NothingTheme {
    val palette: NothingPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalNothingPalette.current

    val typography = Typography
}

@Composable
fun NothingTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NothingDarkColorScheme else NothingLightColorScheme
    val palette = if (darkTheme) {
        NothingPalette(
            isDark = true,
            background = NothingPureBlack,
            surface = NothingDarkGray,
            surfaceVariant = NothingSurface,
            surfaceBorder = NothingBorder,
            borderContrast = NothingBorderContrast,
            textPrimary = NothingPureWhite,
            textSecondary = NothingTextSecondary,
            textMuted = NothingTextMuted,
            signatureRed = NothingSignatureRed,
            glyphAccent = NothingGlyphRed,
            statusOnline = NothingGreen,
            dealAccent = NothingDealOrange
        )
    } else {
        NothingPalette(
            isDark = false,
            background = NothingLightBackground,
            surface = NothingLightSurface,
            surfaceVariant = NothingLightSurfaceVariant,
            surfaceBorder = NothingLightBorder,
            borderContrast = NothingLightBorderContrast,
            textPrimary = NothingLightTextPrimary,
            textSecondary = NothingLightTextSecondary,
            textMuted = NothingLightTextMuted,
            signatureRed = NothingSignatureRed,
            glyphAccent = NothingGlyphRed,
            statusOnline = NothingGreen,
            dealAccent = NothingDealOrange
        )
    }

    CompositionLocalProvider(
        LocalNothingPalette provides palette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

