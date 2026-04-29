package com.mcgo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.mcgo.app.ui.model.AccentPreset
import com.mcgo.app.ui.model.AppearancePreferences

@Immutable
data class McGoVisualTokens(
    val cardContainerColor: Color,
    val cardStrokeColor: Color,
    val fluidBackgroundSpec: FluidGradientSpec,
)

val LocalMcGoVisualTokens = staticCompositionLocalOf {
    McGoVisualTokens(
        cardContainerColor = FrostSurface,
        cardStrokeColor = FrostStroke,
        fluidBackgroundSpec = fluidGradientSpec(darkTheme = false),
    )
}

@Composable
fun McGoTheme(
    appearancePreferences: AppearancePreferences = AppearancePreferences(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appearancePreferences.themeMode.resolvesToDark(systemIsDark = systemDarkTheme)
    val colorScheme = remember(context, darkTheme, appearancePreferences.accentPreset) {
        when {
            appearancePreferences.accentPreset == AccentPreset.System && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> {
                val accentColors = accentColors(appearancePreferences.accentPreset)
                mcGoColorScheme(
                    darkTheme = darkTheme,
                    primary = accentColors.primary,
                    secondary = accentColors.secondary,
                    tertiary = accentColors.tertiary,
                )
            }
        }
    }
    val typography = remember(appearancePreferences.effectiveTypographyScale()) {
        mcGoTypography(scale = appearancePreferences.effectiveTypographyScale())
    }
    val visualTokens = remember(appearancePreferences, colorScheme, darkTheme) {
        val cardAlpha = appearancePreferences.cardContainerAlpha()
        val cardContainer = if (appearancePreferences.transparentCards) {
            colorScheme.surface.copy(alpha = if (darkTheme) cardAlpha.coerceIn(0.74f, 0.98f) else cardAlpha)
        } else {
            colorScheme.surface
        }
        val cardStroke = if (darkTheme) {
            colorScheme.outline.copy(alpha = if (appearancePreferences.transparentCards) 0.68f else 0.92f)
        } else {
            FrostStroke.copy(alpha = if (appearancePreferences.transparentCards) 0.7f else 1f)
        }
        McGoVisualTokens(
            cardContainerColor = cardContainer,
            cardStrokeColor = cardStroke,
            fluidBackgroundSpec = fluidGradientSpec(darkTheme = darkTheme),
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
    AccentPreset.System -> AccentColors(
        primary = Color(AccentPreset.System.primaryHex),
        secondary = Color(AccentPreset.System.secondaryHex),
        tertiary = Color(AccentPreset.System.tertiaryHex),
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
        background = Color(0xFF0B1020),
        surface = Color(0xFF172238),
        surfaceVariant = Color(0xFF22304A),
        outline = Color(0xFF51627F),
        error = Color(0xFFFF8B8B),
        onPrimary = readableOnColor(primary),
        onSecondary = readableOnColor(secondary),
        onTertiary = readableOnColor(tertiary),
        onBackground = Color(0xFFF5F7FD),
        onSurface = Color(0xFFF5F7FD),
        onSurfaceVariant = Color(0xFFD5DDF0),
        onError = Ink900,
    )
} else {
    lightColorScheme(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        background = MistBackground,
        surface = FrostSurface,
        surfaceVariant = SurfaceSoft,
        outline = Color(0xFFBDD0F4),
        error = Red500,
        onPrimary = readableOnColor(primary),
        onSecondary = readableOnColor(secondary),
        onTertiary = readableOnColor(tertiary),
        onBackground = Ink900,
        onSurface = Ink900,
        onSurfaceVariant = Ink600,
        onError = Color.White,
    )
}

private fun readableOnColor(color: Color): Color = if (color.luminance() > 0.5f) Ink900 else Color.White
