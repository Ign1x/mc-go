package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoEditPageDesignContractTest {
    private val source: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")))
    private val settingsScreenSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/SettingsScreen.kt")))

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

    @Test
    fun editPagesAvoidImeAndBringFocusedInputsIntoView() {
        val editDialog = source.substringBetween(
            start = "private fun EditPaperServerDialog(",
            end = "private fun PaperServerPropertiesEditorDialog(",
        )
        val propertiesDialog = source.substringBetween(
            start = "private fun PaperServerPropertiesEditorDialog(",
            end = "private fun EditSettingsSectionCard(",
        )
        val scaffold = source.substringBetween(
            start = "private fun EditFullScreenScaffold(",
            end = "private fun EditSettingsInfoCard(",
        )
        val textRow = source.substringBetween(
            start = "private fun EditTextSettingRow(",
            end = "@Composable\nprivate fun EditSwitchSettingRow(",
        )

        assertThat(source).contains("import androidx.compose.foundation.relocation.BringIntoViewRequester")
        assertThat(source).contains("import androidx.compose.foundation.relocation.bringIntoViewRequester")
        assertThat(source).contains("import androidx.compose.foundation.layout.imePadding")
        assertThat(source).contains("import androidx.compose.ui.focus.onFocusEvent")
        assertThat(scaffold).contains("imePadding()")
        assertThat(editDialog).contains("layoutMode = EditFullScreenScaffoldLayoutMode.InlineChrome")
        assertThat(propertiesDialog).contains("rememberImeBringIntoViewRequester()")
        assertThat(propertiesDialog).contains("bringIntoViewRequester(propertiesBringIntoViewRequester)")
        assertThat(propertiesDialog).contains("onFocusEvent")
        assertThat(textRow).contains("rememberImeBringIntoViewRequester()")
        assertThat(textRow).contains("bringIntoViewRequester(bringIntoViewRequester)")
        assertThat(textRow).contains("onFocusEvent")
        assertThat(source).contains("private fun rememberImeBringIntoViewRequester(): Pair<BringIntoViewRequester, (Boolean) -> Unit>")
        assertThat(source).contains("bringIntoViewRequester.bringIntoView()")
    }

    @Test
    fun bottomNavigationUsesFloatingTranslucentGlassMenu() {
        val scaffold = source.substringBetween(
            start = "private fun MCGoAppScaffold(",
            end = "@Composable\nprivate fun RequestRuntimePermissions(",
        )
        val bottomMenu = source.substringBetween(
            start = "private fun FloatingGlassBottomMenu(",
            end = "private fun ServerDirectoryPermissionEffect(",
        )

        assertThat(source).contains("private fun FloatingGlassBottomMenu(")
        assertThat(settingsScreenSource).contains("settingsDestination: SettingsDestination = SettingsDestination.Overview")
        assertThat(settingsScreenSource).contains("onSettingsDestinationChange: (SettingsDestination) -> Unit = {}")
        assertThat(settingsScreenSource).doesNotContain("LaunchedEffect(navigationState.canNavigateBack)")
        assertThat(settingsScreenSource).doesNotContain("onBottomBarVisibilityChange(!navigationState.canNavigateBack)")
        assertThat(scaffold).contains("FloatingGlassBottomMenu(")
        assertThat(scaffold).contains("destination == McGoDestination.Settings && settingsDestination != SettingsDestination.Overview")
        assertThat(scaffold).contains("if (!(destination == McGoDestination.Settings && settingsDestination != SettingsDestination.Overview))")
        assertThat(scaffold).contains("settingsDestination = SettingsDestination.Overview")
        assertThat(scaffold).contains("settingsDestination = it")
        assertThat(scaffold).doesNotContain("NavigationBar(")
        assertThat(scaffold).doesNotContain("NavigationBarItem(")
        assertThat(scaffold).contains("containerColor = Color.Transparent")
        assertThat(scaffold).doesNotContain(".padding(innerPadding)")
        assertThat(scaffold).contains("innerPadding.calculateTopPadding()")
        assertThat(scaffold).contains("innerPadding.calculateStartPadding(layoutDirection)")
        assertThat(scaffold).contains("innerPadding.calculateEndPadding(layoutDirection)")
        assertThat(bottomMenu).contains("navigationBarsPadding()")
        assertThat(bottomMenu).doesNotContain("Brush.verticalGradient(")
        assertThat(bottomMenu).doesNotContain("containerGradient")
        assertThat(bottomMenu).doesNotContain("Modifier.matchParentSize()")
        assertThat(bottomMenu).doesNotContain("background(containerGradient)")
        assertThat(bottomMenu).contains("padding(horizontal = 18.dp, vertical = 12.dp)")
        assertThat(bottomMenu).contains("LocalMcGoVisualTokens.current")
        assertThat(bottomMenu).contains("MaterialTheme.colorScheme.primary")
        assertThat(bottomMenu).contains("RoundedCornerShape(999.dp)")
        assertThat(bottomMenu).contains("alpha = 0.7f * bottomBarAlpha")
        assertThat(bottomMenu).contains("visuals.cardContainerColor")
        assertThat(bottomMenu).contains("transparentCards")
        assertThat(bottomMenu).contains("visuals.cardStrokeColor")
        assertThat(bottomMenu).contains("shadowElevation = 24.dp")
        assertThat(bottomMenu).contains("verticalArrangement = Arrangement.spacedBy(6.dp)")
        assertThat(bottomMenu).contains("horizontalAlignment = Alignment.CenterHorizontally")
        assertThat(bottomMenu).contains("selectedContentColor")
        assertThat(bottomMenu).contains("unselectedContentColor")
        assertThat(bottomMenu).contains("Box(")
        assertThat(bottomMenu).contains("CircleShape")
        assertThat(bottomMenu).contains("selectable(")
        assertThat(bottomMenu).contains("Role.Tab")
        assertThat(bottomMenu).contains("indication = null")
        assertThat(bottomMenu).contains("MutableInteractionSource()")
        assertThat(bottomMenu).contains("clip(RoundedCornerShape(999.dp))")
        assertThat(bottomMenu).contains("ripple(")
        assertThat(bottomMenu).contains("bounded = true")
        assertThat(bottomMenu).contains("radius = 28.dp")
        assertThat(bottomMenu).contains("McGoDestination.entries.forEach")
        assertThat(bottomMenu).doesNotContain("FilterChip(")
        assertThat(bottomMenu).doesNotContain("Color(0xFF0088CC)")
    }


    @Test
    fun editPaperServerDialog_usesInlineChromeAndNonStickyConfigActions() {
        val editDialog = source.substringBetween(
            start = "private fun EditPaperServerDialog(",
            end = "private fun PaperServerPropertiesEditorDialog(",
        )
        val propertiesDialog = source.substringBetween(
            start = "private fun PaperServerPropertiesEditorDialog(",
            end = "private fun EditSettingsSectionCard(",
        )
        val scaffold = source.substringBetween(
            start = "private fun EditFullScreenScaffold(",
            end = "private fun EditSettingsInfoCard(",
        )

        assertThat(source).contains("private enum class EditFullScreenScaffoldLayoutMode")
        assertThat(editDialog).contains("layoutMode = EditFullScreenScaffoldLayoutMode.InlineChrome")
        assertThat(propertiesDialog).contains("layoutMode = EditFullScreenScaffoldLayoutMode.PinnedChrome")
        assertThat(editDialog).contains("Text(\"编辑 server.properties\")")
        assertThat(editDialog).doesNotContain("Text(\"进阶：直接编辑 server.properties\")")
        assertThat(editDialog).doesNotContain(".verticalScroll(rememberScrollState())")
        assertThat(scaffold).contains("if (layoutMode == EditFullScreenScaffoldLayoutMode.InlineChrome)")
        assertThat(scaffold).contains("verticalScroll(rememberScrollState())")
        assertThat(scaffold).contains("headerCard()")
        assertThat(scaffold).contains("footerCard()")
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
