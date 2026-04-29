package com.mcgo.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.AccentPreset
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.ThemeModePreference
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test

class DarkModeThemeTest {

    @Test
    fun darkScheme_uses_neutral_layers_and_opacity_based_text() {
        val scheme = mcGoColorScheme(
            darkTheme = true,
            primary = Color(0xFF7FA8A8),
            secondary = Color(0xFF8AA5C7),
            tertiary = Color(0xFFAA9BC5),
        )

        assertThat(scheme.background).isEqualTo(DarkBackground)
        assertThat(scheme.surface).isEqualTo(DarkSurface)
        assertThat(scheme.surfaceVariant).isEqualTo(DarkSurfaceHigh)
        assertThat(scheme.onBackground).isEqualTo(DarkTextPrimary)
        assertThat(scheme.onSurface).isEqualTo(DarkTextPrimary)
        assertThat(scheme.onSurfaceVariant).isEqualTo(DarkTextSecondary)
        assertThat(scheme.outline).isEqualTo(DarkDivider)
    }

    @Test
    fun transparentDarkCards_forceReadableTextColors() {
        val preferences = AppearancePreferences(
            themeMode = ThemeModePreference.Dark,
            accentPreset = AccentPreset.Forest,
            cardTransparencyPercent = 72,
            transparentCards = true,
            dynamicBackground = true,
        )
        val scheme = mcGoColorScheme(
            darkTheme = true,
            primary = Color(0xFF7FA8A8),
            secondary = Color(0xFF8AA5C7),
            tertiary = Color(0xFFAA9BC5),
        )

        val tokens = buildMcGoVisualTokens(
            appearancePreferences = preferences,
            colorScheme = scheme,
            darkTheme = true,
        )

        assertThat(tokens.cardContentColor).isEqualTo(DarkTextPrimary)
        assertThat(tokens.primaryTextColor).isEqualTo(DarkTextPrimary)
        assertThat(tokens.secondaryTextColor).isEqualTo(DarkTextSecondary)
        assertThat(tokens.disabledTextColor).isEqualTo(DarkTextDisabled)
        assertThat(tokens.cardContainerColor.alpha > 0.69f).isTrue()
    }

    @Test
    fun darkTransparentCards_keepMinimumReadableSurfaceAlpha() {
        val preferences = AppearancePreferences(
            themeMode = ThemeModePreference.Dark,
            accentPreset = AccentPreset.Forest,
            cardTransparencyPercent = 0,
            transparentCards = true,
            dynamicBackground = true,
        )
        val scheme = mcGoColorScheme(
            darkTheme = true,
            primary = Color(0xFF7FA8A8),
            secondary = Color(0xFF8AA5C7),
            tertiary = Color(0xFFAA9BC5),
        )

        val tokens = buildMcGoVisualTokens(
            appearancePreferences = preferences,
            colorScheme = scheme,
            darkTheme = true,
        )

        assertThat(tokens.cardContainerColor.alpha >= 0.28f).isTrue()
    }

    @Test
    fun darkScreenTextSpecs_useReadableTokensForTransparentHeadersAndMetricAreas() {
        val preferences = AppearancePreferences(
            themeMode = ThemeModePreference.Dark,
            accentPreset = AccentPreset.Forest,
            cardTransparencyPercent = 64,
            transparentCards = true,
            dynamicBackground = true,
        )
        val tokens = buildMcGoVisualTokens(
            appearancePreferences = preferences,
            colorScheme = mcGoColorScheme(
                darkTheme = true,
                primary = ForestDarkPrimary,
                secondary = ForestDarkSecondary,
                tertiary = ForestDarkTertiary,
            ),
            darkTheme = true,
        )

        val statusColors = screenTextColors(tokens)
        val settingsColors = screenTextColors(tokens)

        assertThat(statusColors.primary).isEqualTo(DarkTextPrimary)
        assertThat(statusColors.secondary).isEqualTo(DarkTextSecondary)
        assertThat(settingsColors.primary).isNotEqualTo(Ink900)
        assertThat(settingsColors.secondary).isNotEqualTo(Ink600)
    }

    @Test
    fun darkAccentPalette_isSofterThanLightPreset() {
        val lightAccent = accentColors(AccentPreset.Ocean, darkTheme = false)
        val darkAccent = accentColors(AccentPreset.Ocean, darkTheme = true)

        assertThat(darkAccent.primary).isEqualTo(OceanDarkPrimary)
        assertThat(darkAccent.secondary).isEqualTo(OceanDarkSecondary)
        assertThat(darkAccent.tertiary).isEqualTo(OceanDarkTertiary)
        assertThat(darkAccent.primary).isNotEqualTo(lightAccent.primary)
    }

    @Test
    fun darkAccents_keepReadableForegroundContrast() {
        AccentPreset.entries
            .filterNot { it == AccentPreset.System }
            .forEach { preset ->
                val accent = accentColors(preset, darkTheme = true)
                val scheme = mcGoColorScheme(
                    darkTheme = true,
                    primary = accent.primary,
                    secondary = accent.secondary,
                    tertiary = accent.tertiary,
                )

                assertThat(contrastRatio(scheme.primary, scheme.onPrimary) >= 4.5f).isTrue()
                assertThat(contrastRatio(scheme.secondary, scheme.onSecondary) >= 4.5f).isTrue()
                assertThat(contrastRatio(scheme.tertiary, scheme.onTertiary) >= 4.5f).isTrue()
            }
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
