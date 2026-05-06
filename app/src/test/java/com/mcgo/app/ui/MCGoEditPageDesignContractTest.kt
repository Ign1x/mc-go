package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoEditPageDesignContractTest {
    private val source: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")))

    @Test
    fun editPages_shareFullScreenFluidScaffoldWithCurrentVisualTokens() {
        val editDialog = source.substringBetween(
            start = "private fun EditPaperServerDialog(",
            end = "private fun PaperServerPropertiesEditorDialog(",
        )
        val propertiesDialog = source.substringBetween(
            start = "private fun PaperServerPropertiesEditorDialog(",
            end = "private fun EditSettingsSectionCard(",
        )

        assertThat(editDialog).contains("EditFullScreenScaffold(")
        assertThat(propertiesDialog).contains("EditFullScreenScaffold(")
        assertThat(source).contains("private fun EditFullScreenScaffold(")
        assertThat(source).contains("FluidGradientBackground(")
        assertThat(source).contains("LocalMcGoVisualTokens.current")
        assertThat(source).contains("animate = dynamicBackground")
        assertThat(source).contains("dynamicBackground = appearancePreferences.dynamicBackground")
    }

    @Test
    fun editPageComponents_areDarkModeAwareAndAvoidLightOnlySurfaces() {
        val editSupportSource = source.substringBetween(
            start = "private fun EditPaperServerDialog(",
            end = "private fun PaperGameMode.displayLabel()",
        )

        assertThat(editSupportSource).contains("editPageColors()")
        assertThat(editSupportSource).contains("MaterialTheme.colorScheme")
        assertThat(editSupportSource).contains("navigationBarsPadding()")
        assertThat(editSupportSource).contains("border = BorderStroke")
        listOf(
            "Color.White",
            "Color(0xFFF5F5F7)",
            "Color(0xFFE9E9EE)",
            "Color(0xFFF2F2F6)",
        ).forEach { lightOnlyColor ->
            assertThat(editSupportSource).doesNotContain(lightOnlyColor)
        }
    }

    @Test
    fun editDialogs_requestEdgeToEdgeSystemBarsForImmersiveBackground() {
        val dialogPropertiesCalls = Regex("DialogProperties\\([^)]*usePlatformDefaultWidth = false[^)]*\\)")
            .findAll(source)
            .map { it.value }
            .toList()

        assertThat(dialogPropertiesCalls).hasSize(2)
        dialogPropertiesCalls.forEach { call ->
            assertThat(call).contains("decorFitsSystemWindows = false")
        }
    }

    @Test
    fun editDialogs_makeTheirOwnWindowSystemBarsTransparent() {
        val scaffold = source.substringBetween(
            start = "private fun EditFullScreenScaffold(",
            end = "private fun EditSettingsInfoCard(",
        )
        val systemBarHelper = source.substringBetween(
            start = "private fun EditDialogImmersiveSystemBars(",
            end = "@Composable\nprivate fun EditFullScreenScaffold(",
        )

        assertThat(scaffold).contains("EditDialogImmersiveSystemBars()")
        assertThat(systemBarHelper).contains("DialogWindowProvider")
        assertThat(systemBarHelper).contains("WindowCompat.setDecorFitsSystemWindows(window, false)")
        assertThat(systemBarHelper).contains("statusBarColor = android.graphics.Color.TRANSPARENT")
        assertThat(systemBarHelper).contains("navigationBarColor = android.graphics.Color.TRANSPARENT")
        assertThat(systemBarHelper).contains("isNavigationBarContrastEnforced = false")
        assertThat(systemBarHelper).contains("isStatusBarContrastEnforced = false")
        assertThat(systemBarHelper).contains("isAppearanceLightStatusBars")
        assertThat(systemBarHelper).contains("isAppearanceLightNavigationBars")
    }

    private fun String.substringBetween(start: String, end: String): String {
        val startIndex = indexOf(start)
        val endIndex = indexOf(end, startIndex.coerceAtLeast(0))
        require(startIndex >= 0) { "Missing start marker: $start" }
        require(endIndex > startIndex) { "Missing end marker after $start: $end" }
        return substring(startIndex, endIndex)
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
