package com.mcgo.app.ui.screens

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class SettingsAppearanceScreenContractTest {
    private val settingsScreenSource: String = readSource("app/src/main/java/com/mcgo/app/ui/screens/SettingsScreen.kt")
    private val appearanceScreenSource: String = readSource("app/src/main/java/com/mcgo/app/ui/screens/AppearanceSettingsScreen.kt")
    private val settingsHeaderSource: String = readSource("app/src/main/java/com/mcgo/app/ui/screens/SettingsDetailHeader.kt")

    @Test
    fun appearanceDetailScreen_isExtractedOutOfSettingsScreen() {
        assertThat(settingsScreenSource).contains("SettingsDestination.Appearance -> AppearanceDetailScreen(")
        assertThat(settingsScreenSource).doesNotContain("private fun AppearanceDetailScreen(")
        assertThat(settingsScreenSource).doesNotContain("private fun AppearancePreviewCard(")
        assertThat(settingsScreenSource).doesNotContain("private fun ChoiceChipCard(")
        assertThat(settingsScreenSource).doesNotContain("private fun AccentChoiceCard(")
        assertThat(settingsScreenSource).doesNotContain("private fun TransparencySliderCard(")
        assertThat(settingsScreenSource).doesNotContain("private fun AppearanceTogglesCard(")
        assertThat(settingsScreenSource).doesNotContain("private fun themedSettingsChipColors(")
        assertThat(settingsScreenSource).doesNotContain("private fun accentColorForOption(")

        assertThat(appearanceScreenSource).contains("internal fun AppearanceDetailScreen(")
        assertThat(appearanceScreenSource).contains("SettingsDetailHeader(")
        assertThat(appearanceScreenSource).contains("ThemeModePreference.fromLabel")
        assertThat(appearanceScreenSource).contains("AccentPreset.fromLabel")
        assertThat(appearanceScreenSource).contains("isSystemInDarkTheme()")
        assertThat(appearanceScreenSource).contains("Brush.linearGradient")
        assertThat(appearanceScreenSource).contains("FilterChip(")
        assertThat(appearanceScreenSource).contains("Slider(")
        assertThat(appearanceScreenSource).contains("Switch(")
        assertThat(appearanceScreenSource).contains("resolveAccentColors(")
        assertThat(appearanceScreenSource).contains("text = \"主题色彩\"")
        assertThat(appearanceScreenSource).contains("text = \"卡片透明度\"")
        assertThat(appearanceScreenSource).contains("title = \"透明卡片\"")
        assertThat(appearanceScreenSource).contains("title = \"动态背景\"")
    }

    @Test
    fun settingsDetailHeader_hasGenericNameAndSharedBackAction() {
        assertThat(settingsScreenSource).contains("SettingsDetailHeader(")
        assertThat(settingsScreenSource).doesNotContain("AppearanceDetailHeader(")
        assertThat(settingsScreenSource).doesNotContain("private fun AppearanceDetailHeader(")

        assertThat(settingsHeaderSource).contains("internal fun SettingsDetailHeader(")
        assertThat(settingsHeaderSource).contains("SettingsBackActionPlacement.TopRight")
        assertThat(settingsHeaderSource).contains("chrome.usesCompactActionButton")
        assertThat(settingsHeaderSource).contains("Icons.AutoMirrored.Outlined.ArrowBack")
        assertThat(settingsHeaderSource).contains("contentDescription = \"返回上一级\"")
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
