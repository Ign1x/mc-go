package com.mcgo.app.ui.storage

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.AccentPreset
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.FontScalePreference
import com.mcgo.app.ui.model.ThemeModePreference
import java.nio.file.Files
import kotlin.test.Test

class AppearancePreferencesStoreTest {

    @Test
    fun saveAndLoad_roundTripsAllAppearancePreferences() {
        val storeFile = Files.createTempFile("mcgo-appearance", ".properties")
        val store = AppearancePreferencesStore(storeFile)
        val preferences = AppearancePreferences(
            themeMode = ThemeModePreference.Dark,
            accentPreset = AccentPreset.Ocean,
            fontScale = FontScalePreference.Wide,
            cardTransparencyPercent = 64,
            transparentCards = false,
            dynamicBackground = false,
        )

        store.save(preferences)
        val loaded = store.load()

        assertThat(loaded).isEqualTo(preferences)
    }
}
