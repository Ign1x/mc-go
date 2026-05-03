package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class AppearancePreferencesTest {

    @Test
    fun defaults_matchCompactSystemGlassStyleUserWants() {
        val defaults = AppearancePreferences()

        assertThat(defaults.themeMode).isEqualTo(ThemeModePreference.FollowSystem)
        assertThat(defaults.accentPreset).isEqualTo(AccentPreset.Forest)
        assertThat(defaults.fontScale).isEqualTo(FontScalePreference.Compact)
        assertThat(defaults.cardTransparencyPercent).isEqualTo(82)
        assertThat(defaults.transparentCards).isTrue()
        assertThat(defaults.dynamicBackground).isTrue()
        assertThat(defaults.backgroundAuraAlpha()).isEqualTo(0.24f)
        assertThat(defaults.backgroundMotionScale()).isEqualTo(1.18f)
    }


    @Test
    fun themeModeCycle_usesFollowSystemLightDarkOrder() {
        assertThat(ThemeModePreference.FollowSystem.next()).isEqualTo(ThemeModePreference.Light)
        assertThat(ThemeModePreference.Light.next()).isEqualTo(ThemeModePreference.Dark)
        assertThat(ThemeModePreference.Dark.next()).isEqualTo(ThemeModePreference.FollowSystem)
    }

    @Test
    fun themeModePreference_resolvesFollowSystemAgainstRuntimeTheme() {
        assertThat(ThemeModePreference.FollowSystem.resolvesToDark(systemIsDark = true)).isTrue()
        assertThat(ThemeModePreference.FollowSystem.resolvesToDark(systemIsDark = false)).isFalse()
        assertThat(ThemeModePreference.Dark.resolvesToDark(systemIsDark = false)).isTrue()
    }

    @Test
    fun appearancePreferences_computeActualVisualEffectsAndKeepCompactTypography() {
        val preferences = AppearancePreferences(
            themeMode = ThemeModePreference.Dark,
            accentPreset = AccentPreset.Sunset,
            fontScale = FontScalePreference.Wide,
            cardTransparencyPercent = 64,
            transparentCards = true,
            dynamicBackground = false,
        )

        assertThat(preferences.effectiveTypographyScale()).isEqualTo(FontScalePreference.Compact.multiplier)
        assertThat(preferences.cardContainerAlpha()).isEqualTo(0.64f)
        assertThat(preferences.backgroundAuraAlpha()).isEqualTo(0f)
        assertThat(preferences.backgroundMotionScale()).isEqualTo(1f)
        assertThat(preferences.accentPreset.label).isEqualTo("暖阳橙")
    }

    @Test
    fun transparency_supportsFullZeroToHundredRangeAndOpaqueCardsIgnoreSlider() {
        val transparentZero = AppearancePreferences(cardTransparencyPercent = 0, transparentCards = true)
        val transparentFull = AppearancePreferences(cardTransparencyPercent = 100, transparentCards = true)
        val opaque = AppearancePreferences(cardTransparencyPercent = 35, transparentCards = false)

        assertThat(transparentZero.cardContainerAlpha()).isEqualTo(0f)
        assertThat(transparentFull.cardContainerAlpha()).isEqualTo(1f)
        assertThat(opaque.cardContainerAlpha()).isEqualTo(1f)
    }

    @Test
    fun options_includeSystemColorAndWideDensityPreset() {
        assertThat(AccentPreset.System.label).isEqualTo("系统颜色")
        assertThat(FontScalePreference.Wide.label).isEqualTo("宽松")
    }

    @Test
    fun saver_restoresLegacyPayloadFromPreviousSchema() {
        val legacy = listOf(
            ThemeModePreference.FollowSystem.name,
            AccentPreset.Sunset.name,
            "Comfortable",
            "Standard",
            64,
            true,
            false,
            false,
        )

        val restored = restoreAppearancePreferences(legacy)

        assertThat(restored.themeMode).isEqualTo(ThemeModePreference.FollowSystem)
        assertThat(restored.accentPreset).isEqualTo(AccentPreset.Sunset)
        assertThat(restored.fontScale).isEqualTo(FontScalePreference.Wide)
        assertThat(restored.cardTransparencyPercent).isEqualTo(64)
        assertThat(restored.transparentCards).isTrue()
        assertThat(restored.dynamicBackground).isFalse()
    }
}
