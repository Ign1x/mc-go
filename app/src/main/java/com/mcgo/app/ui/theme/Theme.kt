package com.mcgo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mcgo.app.ui.model.AccentPreset
import com.mcgo.app.ui.model.AppearancePreferences

@Immutable
data class McGoVisualTokens(
    val cardContainerColor: Color,
    val cardStrokeColor: Color,
    val backgroundGradient: List<Color>,
    val backgroundAuraColors: List<Color>,
)

val LocalMcGoVisualTokens = staticCompositionLocalOf {
    McGoVisualTokens(
        cardContainerColor = FrostSurface,
        cardStrokeColor = FrostStroke,
        backgroundGradient = listOf(MistBackground, CloudBackground, SurfaceSoftAlt),
        backgroundAuraColors = listOf(Color.Transparent, Color.Transparent, Color.Transparent),
    )
}

@Composable
fun McGoTheme(
    appearancePreferences: AppearancePreferences = AppearancePreferences(),
    content: @Composable () -> Unit,
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appearancePreferences.themeMode.resolvesToDark(systemIsDark = systemDarkTheme)
    val accentColors = remember(appearancePreferences.accentPreset) { accentColors(appearancePreferences.accentPreset) }
    val colorScheme = remember(darkTheme, accentColors) {
        mcGoColorScheme(
            darkTheme = darkTheme,
            primary = accentColors.primary,
            secondary = accentColors.secondary,
            tertiary = accentColors.tertiary,
        )
    }
    val typography = remember(appearancePreferences.effectiveTypographyScale()) {
        mcGoTypography(scale = appearancePreferences.effectiveTypographyScale())
    }
    val visualTokens = remember(appearancePreferences, darkTheme, accentColors) {
        val cardAlpha = appearancePreferences.cardContainerAlpha()
        val cardSurface = if (darkTheme) {
            Color(0xFF182033).copy(alpha = cardAlpha.coerceIn(0.72f, 1f))
        } else {
            Color.White.copy(alpha = cardAlpha)
        }
        val cardStroke = if (darkTheme) {
            Color.White.copy(alpha = 0.12f)
        } else {
            FrostStroke.copy(alpha = if (appearancePreferences.transparentCards) 0.7f else 1f)
        }
        val backgroundGradient = if (darkTheme) {
            listOf(Color(0xFF0D1423), Color(0xFF111B2F), Color(0xFF18243A))
        } else {
            listOf(MistBackground, CloudBackground, SurfaceSoftAlt)
        }
        val auraAlpha = appearancePreferences.backgroundAuraAlpha()
        McGoVisualTokens(
            cardContainerColor = cardSurface,
            cardStrokeColor = cardStroke,
            backgroundGradient = backgroundGradient,
            backgroundAuraColors = listOf(
                accentColors.primary.copy(alpha = auraAlpha),
                accentColors.secondary.copy(alpha = auraAlpha * 0.72f),
                Color.Transparent,
            ),
        )
    }

    CompositionLocalProvider(LocalMcGoVisualTokens provides visualTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

private data class AccentColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

private fun accentColors(preset: AccentPreset): AccentColors = when (preset) {
    AccentPreset.Ocean -> AccentColors(
        primary = Blue500,
        secondary = Green500,
        tertiary = Violet500,
    )
    AccentPreset.Forest -> AccentColors(
        primary = Green500,
        secondary = Blue500,
        tertiary = Violet500,
    )
    AccentPreset.Amethyst -> AccentColors(
        primary = Violet500,
        secondary = Blue500,
        tertiary = Green500,
    )
    AccentPreset.Sunset -> AccentColors(
        primary = Gold500,
        secondary = Red500,
        tertiary = Blue500,
    )
}

private fun mcGoColorScheme(
    darkTheme: Boolean,
    primary: Color,
    secondary: Color,
    tertiary: Color,
): ColorScheme = if (darkTheme) {
    darkColorScheme(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        background = Color(0xFF0D1423),
        surface = Color(0xFF141F33),
        surfaceVariant = Color(0xFF1D2940),
        onPrimary = readableOnColor(primary),
        onSecondary = readableOnColor(secondary),
        onTertiary = readableOnColor(tertiary),
        onBackground = Color(0xFFF5F7FD),
        onSurface = Color(0xFFF5F7FD),
        onSurfaceVariant = Color(0xFFB8C5DD),
    )
} else {
    lightColorScheme(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        background = MistBackground,
        surface = FrostSurface,
        surfaceVariant = SurfaceSoft,
        onPrimary = readableOnColor(primary),
        onSecondary = readableOnColor(secondary),
        onTertiary = readableOnColor(tertiary),
        onBackground = Ink900,
        onSurface = Ink900,
        onSurfaceVariant = Ink600,
    )
}

private fun readableOnColor(color: Color): Color = if (color.luminance() > 0.5f) Ink900 else Color.White
