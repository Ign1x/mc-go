package com.mcgo.app.ui.theme

import android.content.Context
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max
import kotlin.math.min
import com.mcgo.app.ui.model.AccentPreset
import com.mcgo.app.ui.model.AppearancePreferences

@Immutable
data class McGoVisualTokens(
    val cardContainerColor: Color,
    val cardStrokeColor: Color,
    val cardContentColor: Color,
    val primaryTextColor: Color,
    val secondaryTextColor: Color,
    val disabledTextColor: Color,
    val fluidBackgroundSpec: FluidGradientSpec,
)

@Immutable
data class ScreenTextColors(
    val primary: Color,
    val secondary: Color,
    val disabled: Color,
)

internal fun screenTextColors(tokens: McGoVisualTokens): ScreenTextColors = ScreenTextColors(
    primary = tokens.primaryTextColor,
    secondary = tokens.secondaryTextColor,
    disabled = tokens.disabledTextColor,
)

val LocalMcGoVisualTokens = staticCompositionLocalOf {
    McGoVisualTokens(
        cardContainerColor = FrostSurface,
        cardStrokeColor = FrostStroke,
        cardContentColor = Ink900,
        primaryTextColor = Ink900,
        secondaryTextColor = Ink600,
        disabledTextColor = Ink600.copy(alpha = 0.38f),
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
    val accentColors = remember(context, darkTheme, appearancePreferences.accentPreset) {
        resolveAccentColors(
            context = context,
            preset = appearancePreferences.accentPreset,
            darkTheme = darkTheme,
        )
    }
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
    val visualTokens = remember(appearancePreferences, colorScheme, darkTheme) {
        buildMcGoVisualTokens(
            appearancePreferences = appearancePreferences,
            colorScheme = colorScheme,
            darkTheme = darkTheme,
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

@Immutable
internal data class AccentColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

internal fun buildMcGoVisualTokens(
    appearancePreferences: AppearancePreferences,
    colorScheme: ColorScheme,
    darkTheme: Boolean,
): McGoVisualTokens {
    val primaryText = if (darkTheme) DarkTextPrimary else colorScheme.onSurface
    val secondaryText = if (darkTheme) DarkTextSecondary else colorScheme.onSurfaceVariant
    val disabledText = if (darkTheme) DarkTextDisabled else colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val cardAlpha = appearancePreferences.cardContainerAlpha()
    val effectiveCardAlpha = if (darkTheme && appearancePreferences.transparentCards) {
        cardAlpha.coerceAtLeast(0.30f)
    } else {
        cardAlpha
    }
    val cardContainer = if (appearancePreferences.transparentCards) {
        colorScheme.surface.copy(alpha = effectiveCardAlpha)
    } else {
        colorScheme.surface
    }
    val cardStroke = if (darkTheme) {
        DarkDivider
    } else if (appearancePreferences.transparentCards) {
        FrostStroke.copy(alpha = 0.70f)
    } else {
        colorScheme.outline.copy(alpha = 0.24f)
    }
    return McGoVisualTokens(
        cardContainerColor = cardContainer,
        cardStrokeColor = cardStroke,
        cardContentColor = primaryText,
        primaryTextColor = primaryText,
        secondaryTextColor = secondaryText,
        disabledTextColor = disabledText,
        fluidBackgroundSpec = fluidGradientSpec(darkTheme = darkTheme),
    )
}

internal fun resolveAccentColors(
    context: Context,
    preset: AccentPreset,
    darkTheme: Boolean,
): AccentColors {
    if (preset == AccentPreset.System && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return AccentColors(
            primary = if (darkTheme) softenAccentForDark(scheme.primary) else scheme.primary,
            secondary = if (darkTheme) softenAccentForDark(scheme.secondary) else scheme.secondary,
            tertiary = if (darkTheme) softenAccentForDark(scheme.tertiary) else scheme.tertiary,
        )
    }
    return accentColors(preset = preset, darkTheme = darkTheme)
}

internal fun accentColors(
    preset: AccentPreset,
    darkTheme: Boolean,
): AccentColors = when (preset) {
    AccentPreset.Ocean -> AccentColors(
        primary = if (darkTheme) OceanDarkPrimary else Blue500,
        secondary = if (darkTheme) OceanDarkSecondary else Green500,
        tertiary = if (darkTheme) OceanDarkTertiary else Violet500,
    )
    AccentPreset.Forest -> AccentColors(
        primary = if (darkTheme) ForestDarkPrimary else Green500,
        secondary = if (darkTheme) ForestDarkSecondary else Blue500,
        tertiary = if (darkTheme) ForestDarkTertiary else Violet500,
    )
    AccentPreset.Amethyst -> AccentColors(
        primary = if (darkTheme) AmethystDarkPrimary else Violet500,
        secondary = if (darkTheme) AmethystDarkSecondary else Blue500,
        tertiary = if (darkTheme) AmethystDarkTertiary else Green500,
    )
    AccentPreset.Sunset -> AccentColors(
        primary = if (darkTheme) SunsetDarkPrimary else Gold500,
        secondary = if (darkTheme) SunsetDarkSecondary else Red500,
        tertiary = if (darkTheme) SunsetDarkTertiary else Blue500,
    )
    AccentPreset.System -> AccentColors(
        primary = if (darkTheme) softenAccentForDark(Color(AccentPreset.System.primaryHex)) else Color(AccentPreset.System.primaryHex),
        secondary = if (darkTheme) softenAccentForDark(Color(AccentPreset.System.secondaryHex)) else Color(AccentPreset.System.secondaryHex),
        tertiary = if (darkTheme) softenAccentForDark(Color(AccentPreset.System.tertiaryHex)) else Color(AccentPreset.System.tertiaryHex),
    )
}

internal fun mcGoColorScheme(
    darkTheme: Boolean,
    primary: Color,
    secondary: Color,
    tertiary: Color,
): ColorScheme = if (darkTheme) {
    darkColorScheme(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        background = DarkBackground,
        surface = DarkSurface,
        surfaceVariant = DarkSurfaceHigh,
        outline = DarkDivider,
        error = DarkError,
        onPrimary = readableOnAccent(primary, darkTheme = true),
        onSecondary = readableOnAccent(secondary, darkTheme = true),
        onTertiary = readableOnAccent(tertiary, darkTheme = true),
        onBackground = DarkTextPrimary,
        onSurface = DarkTextPrimary,
        onSurfaceVariant = DarkTextSecondary,
        onError = readableOnAccent(DarkError, darkTheme = true),
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
        onPrimary = readableOnAccent(primary, darkTheme = false),
        onSecondary = readableOnAccent(secondary, darkTheme = false),
        onTertiary = readableOnAccent(tertiary, darkTheme = false),
        onBackground = Ink900,
        onSurface = Ink900,
        onSurfaceVariant = Ink600,
        onError = Color.White,
    )
}

private fun softenAccentForDark(color: Color): Color = lerp(color, DarkAccentNeutral, 0.28f)

private fun readableOnAccent(color: Color, darkTheme: Boolean): Color {
    val darkCandidate = if (darkTheme) DarkBackground else Ink900
    val lightCandidate = if (darkTheme) DarkTextPrimary else Color.White
    return if (contrastRatio(color, darkCandidate) >= contrastRatio(color, lightCandidate)) {
        darkCandidate
    } else {
        lightCandidate
    }
}

private fun contrastRatio(background: Color, foreground: Color): Float {
    val backgroundLuminance = compositeOver(background, Color.Black).luminance() + 0.05f
    val foregroundLuminance = compositeOver(foreground, background).luminance() + 0.05f
    return max(backgroundLuminance, foregroundLuminance) / min(backgroundLuminance, foregroundLuminance)
}

private fun compositeOver(foreground: Color, background: Color): Color {
    val alpha = foreground.alpha + background.alpha * (1f - foreground.alpha)
    if (alpha == 0f) return Color.Transparent
    val red = ((foreground.red * foreground.alpha) + (background.red * background.alpha * (1f - foreground.alpha))) / alpha
    val green = ((foreground.green * foreground.alpha) + (background.green * background.alpha * (1f - foreground.alpha))) / alpha
    val blue = ((foreground.blue * foreground.alpha) + (background.blue * background.alpha * (1f - foreground.alpha))) / alpha
    return Color(red, green, blue, alpha)
}
