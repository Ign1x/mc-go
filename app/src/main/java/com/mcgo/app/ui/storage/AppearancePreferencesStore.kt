package com.mcgo.app.ui.storage

import com.mcgo.app.ui.model.AccentPreset
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.FontScalePreference
import com.mcgo.app.ui.model.ThemeModePreference
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class AppearancePreferencesStore(
    private val storePath: Path,
) {
    fun load(): AppearancePreferences {
        if (!Files.exists(storePath)) return AppearancePreferences()
        val properties = Properties()
        Files.newInputStream(storePath).use { input -> properties.load(input) }
        return AppearancePreferences(
            themeMode = enumValueOrDefault(properties.getProperty("themeMode"), ThemeModePreference.FollowSystem),
            accentPreset = enumValueOrDefault(properties.getProperty("accentPreset"), AccentPreset.Forest),
            fontScale = enumValueOrDefault(properties.getProperty("fontScale"), FontScalePreference.Compact),
            cardTransparencyPercent = properties.getProperty("cardTransparencyPercent")?.toIntOrNull() ?: 82,
            transparentCards = properties.getProperty("transparentCards")?.toBooleanStrictOrNull() ?: true,
            dynamicBackground = properties.getProperty("dynamicBackground")?.toBooleanStrictOrNull() ?: true,
        )
    }

    fun save(preferences: AppearancePreferences) {
        storePath.parent?.let { Files.createDirectories(it) }
        val properties = Properties()
        properties.setProperty("version", "1")
        properties.setProperty("themeMode", preferences.themeMode.name)
        properties.setProperty("accentPreset", preferences.accentPreset.name)
        properties.setProperty("fontScale", preferences.fontScale.name)
        properties.setProperty("cardTransparencyPercent", preferences.cardTransparencyPercent.toString())
        properties.setProperty("transparentCards", preferences.transparentCards.toString())
        properties.setProperty("dynamicBackground", preferences.dynamicBackground.toString())
        Files.newOutputStream(storePath).use { output -> properties.store(output, "MC-GO appearance preferences") }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback
