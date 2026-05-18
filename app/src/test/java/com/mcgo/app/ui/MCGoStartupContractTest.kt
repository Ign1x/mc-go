package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoStartupContractTest {
    private val source: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")))
    private val startupUiSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/StartupUi.kt")))
    private val manifestSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/AndroidManifest.xml")))

    @Test
    fun startup_initializesPersistedStateAsynchronouslyAndShowsLoadingUi() {
        val topLevelApp = source.substringBetween(
            start = "fun MCGoApp() {",
            end = "@Composable\nprivate fun MCGoAppScaffold(",
        )

        assertThat(topLevelApp).contains("produceState<StartupUiState>(initialValue = StartupUiState.Loading")
        assertThat(topLevelApp).contains("withContext(Dispatchers.IO)")
        assertThat(topLevelApp).contains("StartupUiState.Ready(")
        assertThat(topLevelApp).doesNotContain("val persistedServers = remember(serverStore, persistedServerDirectoryUri) {")
        assertThat(topLevelApp).contains("MCGoStartupLoadingScreen(")
        assertThat(source).doesNotContain("private sealed interface StartupUiState")
        assertThat(source).doesNotContain("private fun MCGoStartupLoadingScreen(")
        assertThat(source).doesNotContain("CircularProgressIndicator(")
        assertThat(source).doesNotContain("startup_loading_body")
        assertThat(startupUiSource).contains("internal sealed interface StartupUiState")
        assertThat(startupUiSource).contains("internal fun MCGoStartupLoadingScreen(")
        assertThat(startupUiSource).contains("McGoTheme(appearancePreferences = appearancePreferences)")
        assertThat(startupUiSource).contains("FluidGradientBackground(")
        assertThat(startupUiSource).contains("CircularProgressIndicator(")
        assertThat(startupUiSource).contains("Text(stringResource(R.string.startup_loading_title)")
    }

    @Test
    fun appManifest_usesDedicatedAppThemeInsteadOfPlatformLightTheme() {
        assertThat(manifestSource).contains("android:theme=\"@style/Theme.McGo\"")
        assertThat(manifestSource).doesNotContain("@android:style/Theme.DeviceDefault.Light.NoActionBar")
    }

    private fun String.substringBetween(start: String, end: String): String =
        substringAfter(start).substringBefore(end)

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
