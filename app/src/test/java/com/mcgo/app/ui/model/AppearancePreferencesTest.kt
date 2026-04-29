package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class AppearancePreferencesTest {

    @Test
    fun defaults_matchCompactLightGlassStyleUserWants() {
        val defaults = AppearancePreferences()

        assertThat(defaults.themeMode).isEqualTo(ThemeModePreference.Light)
        assertThat(defaults.accentPreset).isEqualTo(AccentPreset.Forest)
        assertThat(defaults.fontScale).isEqualTo(FontScalePreference.Compact)
        assertThat(defaults.motionPreference).isEqualTo(MotionPreference.Standard)
        assertThat(defaults.cardTransparencyPercent).isEqualTo(82)
        assertThat(defaults.transparentCards).isTrue()
        assertThat(defaults.dynamicBackground).isTrue()
        assertThat(defaults.compactTypography).isTrue()
    }

    @Test
    fun themeModePreference_resolvesFollowSystemAgainstRuntimeTheme() {
        assertThat(ThemeModePreference.FollowSystem.resolvesToDark(systemIsDark = true)).isTrue()
        assertThat(ThemeModePreference.FollowSystem.resolvesToDark(systemIsDark = false)).isFalse()
        assertThat(ThemeModePreference.Dark.resolvesToDark(systemIsDark = false)).isTrue()
    }

    @Test
    fun appearancePreferences_computeActualVisualEffects() {
        val preferences = AppearancePreferences(
            themeMode = ThemeModePreference.Dark,
            accentPreset = AccentPreset.Sunset,
            fontScale = FontScalePreference.Comfortable,
            motionPreference = MotionPreference.Expressive,
            cardTransparencyPercent = 64,
            transparentCards = true,
            dynamicBackground = false,
            compactTypography = false,
        )

        assertThat(preferences.effectiveTypographyScale()).isEqualTo(1.08f)
        assertThat(preferences.cardContainerAlpha()).isEqualTo(0.64f)
        assertThat(preferences.backgroundAuraAlpha()).isEqualTo(0f)
        assertThat(preferences.accentPreset.label).isEqualTo("暖阳橙")
    }

    @Test
    fun compactTypography_toggleWinsOverComfortablePreset() {
        val preferences = AppearancePreferences(
            fontScale = FontScalePreference.Comfortable,
            compactTypography = true,
            transparentCards = false,
            cardTransparencyPercent = 55,
        )

        assertThat(preferences.effectiveTypographyScale()).isEqualTo(0.92f)
        assertThat(preferences.cardContainerAlpha()).isEqualTo(1f)
    }
}
