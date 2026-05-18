package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoEditPageDesignContractTest {
    private val source: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")))
    private val bottomMenuSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/FloatingGlassBottomMenu.kt")))
    private val editChromeSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/EditPageChrome.kt")))
    private val editDialogsSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/EditServerDialogs.kt")))
    private val mainActivitySource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/MainActivity.kt")))
    private val settingsScreenSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/SettingsScreen.kt")))

    @Test
    fun editPages_shareFullScreenFluidScaffoldWithCurrentVisualTokens() {
        val editDialog = editDialogsSource.substringBetween(
            start = "internal fun EditPaperServerDialog(",
            end = "private fun PaperServerPropertiesEditorDialog(",
        )
        val propertiesDialog = editDialogsSource.substringAfter("private fun PaperServerPropertiesEditorDialog(")

        assertThat(editDialog).contains("EditFullScreenScaffold(")
        assertThat(propertiesDialog).contains("EditFullScreenScaffold(")
        assertThat(source).doesNotContain("private fun EditFullScreenScaffold(")
        assertThat(editChromeSource).contains("internal fun EditFullScreenScaffold(")
        assertThat(editChromeSource).contains("FluidGradientBackground(")
        assertThat(editChromeSource).contains("LocalMcGoVisualTokens.current")
        assertThat(editChromeSource).contains("animate = dynamicBackground")
        assertThat(source).contains("dynamicBackground = appearancePreferences.dynamicBackground")
    }

    @Test
    fun editPageComponents_areDarkModeAwareAndAvoidLightOnlySurfaces() {
        val editSupportSource = editDialogsSource.substringAfter("internal fun EditPaperServerDialog(") + editChromeSource

        assertThat(editSupportSource).contains("editPageColors()")
        assertThat(editChromeSource).contains("MaterialTheme.colorScheme")
        assertThat(editSupportSource).contains("navigationBarsPadding()")
        assertThat(editSupportSource).contains("statusBarsPadding()")
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
    fun editOverlays_stayInsideActivityLayerInsteadOfCreatingSeparateDialogWindows() {
        val editDialog = editDialogsSource.substringBetween(
            start = "internal fun EditPaperServerDialog(",
            end = "private fun PaperServerPropertiesEditorDialog(",
        )
        val propertiesDialog = editDialogsSource.substringAfter("private fun PaperServerPropertiesEditorDialog(")

        assertThat(editDialogsSource).contains("import androidx.activity.compose.BackHandler")
        assertThat(editDialog).contains("BackHandler(enabled = true, onBack = onDismiss)")
        assertThat(propertiesDialog).contains("BackHandler(enabled = true, onBack = onDismiss)")
        assertThat(editDialog).doesNotContain("\n    Dialog(")
        assertThat(editDialog).doesNotContain("DialogProperties(")
        assertThat(propertiesDialog).doesNotContain("\n    Dialog(")
        assertThat(propertiesDialog).doesNotContain("DialogProperties(")
    }

    @Test
    fun editOverlays_reuseActivityEdgeToEdgeWithoutDialogWindowSystemBarHacks() {
        val scaffold = editChromeSource.substringBetween(
            start = "internal fun EditFullScreenScaffold(",
            end = "internal fun EditSettingsInfoCard(",
        )

        assertThat(scaffold).doesNotContain("EditDialogImmersiveSystemBars()")
        assertThat(editChromeSource).doesNotContain("private fun EditDialogImmersiveSystemBars(")
        assertThat(mainActivitySource).contains("enableEdgeToEdge()")
    }

    @Test
    fun editDialogs_chooseVersionOptionsByCurrentServerType() {
        val editDialog = editDialogsSource.substringBetween(
            start = "internal fun EditPaperServerDialog(",
            end = "private fun PaperServerPropertiesEditorDialog(",
        )

        assertThat(editDialog).contains("when (server.serverType)")
        assertThat(editDialog).contains("MinecraftServerType.Vanilla")
        assertThat(editDialog).contains("MinecraftServerType.Paper")
        assertThat(editDialog).contains("MinecraftServerType.Purpur")
        assertThat(editDialog).contains("MinecraftServerType.Fabric")
        assertThat(editDialog).contains("vanillaVersions")
        assertThat(editDialog).contains("paperVersions")
        assertThat(editDialog).contains("purpurVersions")
        assertThat(editDialog).contains("fabricVersions")
    }

    @Test
    fun editPages_useFloatingTopBarInsteadOfWholePageSafeAreaInset() {
        val scaffold = editChromeSource.substringBetween(
            start = "internal fun EditFullScreenScaffold(",
            end = "internal fun buildServerPropertiesAnnotatedText(",
        )

        assertThat(scaffold).doesNotContain("contentTopPadding = 120.dp")
        assertThat(scaffold).doesNotContain("footerBottomPadding = 28.dp")
        assertThat(scaffold).contains("headerOverlayHeightPx")
        assertThat(scaffold).contains("footerOverlayHeightPx")
        assertThat(scaffold).contains("onSizeChanged")
        assertThat(scaffold).contains("LocalDensity.current")
        assertThat(scaffold).contains("navigationBarsPadding()")
    }

    @Test
    fun editOverlays_consumeBackgroundTouchesInsteadOfPassingThrough() {
        val scaffold = editChromeSource.substringBetween(
            start = "internal fun EditFullScreenScaffold(",
            end = "internal fun EditSettingsInfoCard(",
        )
        val blocker = editChromeSource.substringBetween(
            start = "private fun EditOverlayInteractionBlocker(",
            end = "@Composable\ninternal fun EditSettingsInfoCard(",
        )

        assertThat(scaffold).contains("EditOverlayInteractionBlocker()")
        assertThat(blocker).contains("MutableInteractionSource()")
        assertThat(blocker).contains("clickable(")
        assertThat(blocker).contains("indication = null")
        assertThat(blocker).contains("onClick = {}")
    }

    @Test
    fun editPagesAvoidImeAndBringFocusedInputsIntoView() {
        val editDialog = editDialogsSource.substringBetween(
            start = "internal fun EditPaperServerDialog(",
            end = "private fun PaperServerPropertiesEditorDialog(",
        )
        val propertiesDialog = editDialogsSource.substringAfter("private fun PaperServerPropertiesEditorDialog(")
        val scaffold = editChromeSource.substringBetween(
            start = "internal fun EditFullScreenScaffold(",
            end = "internal fun EditSettingsInfoCard(",
        )
        val textRow = editChromeSource.substringBetween(
            start = "internal fun EditTextSettingRow(",
            end = "internal fun <T> EditMenuSettingRow(",
        )

        assertThat(editChromeSource).contains("import androidx.compose.foundation.relocation.BringIntoViewRequester")
        assertThat(source + editChromeSource).contains("import androidx.compose.foundation.relocation.bringIntoViewRequester")
        assertThat(source + editChromeSource).contains("import androidx.compose.foundation.layout.imePadding")
        assertThat(editChromeSource).contains("import androidx.compose.foundation.layout.statusBarsPadding")
        assertThat(source + editChromeSource).contains("import androidx.compose.ui.focus.onFocusEvent")
        assertThat(scaffold).contains("imePadding()")
        assertThat(scaffold).doesNotContain("safeDrawingPadding()")
        assertThat(editDialog).contains("layoutMode = EditFullScreenScaffoldLayoutMode.ScrollableChrome")
        assertThat(propertiesDialog).contains("rememberImeBringIntoViewRequester()")
        assertThat(propertiesDialog).contains("bringIntoViewRequester(propertiesBringIntoViewRequester)")
        assertThat(propertiesDialog).contains("onFocusEvent")
        assertThat(textRow).contains("rememberImeBringIntoViewRequester()")
        assertThat(textRow).contains("bringIntoViewRequester(bringIntoViewRequester)")
        assertThat(textRow).contains("onFocusEvent")
        assertThat(editChromeSource).contains("internal fun rememberImeBringIntoViewRequester(): Pair<BringIntoViewRequester, (Boolean) -> Unit>")
        assertThat(editChromeSource).contains("bringIntoViewRequester.bringIntoView()")
    }

    @Test
    fun editPageMenuRows_anchorDropdownToTrailingValueAreaInsteadOfWholeRow() {
        val menuRow = editChromeSource.substringBetween(
            start = "internal fun <T> EditMenuSettingRow(",
            end = "@Composable\ninternal fun EditSwitchSettingRow(",
        )

        assertThat(menuRow).contains("modifier = Modifier.wrapContentWidth(align = Alignment.End)")
        assertThat(menuRow).contains("contentAlignment = Alignment.CenterEnd")
        assertThat(menuRow).contains("DropdownMenu(")
        assertThat(menuRow).doesNotContain("Box {\n        EditSettingRowShell")
    }

    @Test
    fun editOverlayState_clearsStaleEditingServerSelection() {
        val scaffold = source.substringBetween(
            start = "private fun MCGoAppScaffold(",
            end = "@Composable\nprivate fun RequestRuntimePermissions(",
        )

        assertThat(scaffold).contains("val activeEditingServer = editingServerId?.let { serverId ->")
        assertThat(scaffold).contains("LaunchedEffect(editingServerId, servers)")
        assertThat(scaffold).contains("if (editingServerId != null && activeEditingServer == null)")
        assertThat(scaffold).contains("editingServerId = null")
    }

    @Test
    fun bottomNavigationUsesFloatingTranslucentGlassMenu() {
        val scaffold = source.substringBetween(
            start = "private fun MCGoAppScaffold(",
            end = "@Composable\nprivate fun RequestRuntimePermissions(",
        )
        val rootScaffoldSection = source.substringBetween(
            start = "    Box(modifier = Modifier.fillMaxSize()) {",
            end = "@Composable\nprivate fun RequestRuntimePermissions(",
        )
        val bottomMenu = bottomMenuSource

        assertThat(source).contains("FloatingGlassBottomMenu(")
        assertThat(source).doesNotContain("private fun FloatingGlassBottomMenu(")
        assertThat(bottomMenuSource).contains("internal enum class McGoDestination(")
        assertThat(settingsScreenSource).contains("settingsDestination: SettingsDestination = SettingsDestination.Overview")
        assertThat(settingsScreenSource).contains("onSettingsDestinationChange: (SettingsDestination) -> Unit = {}")
        assertThat(settingsScreenSource).doesNotContain("LaunchedEffect(navigationState.canNavigateBack)")
        assertThat(settingsScreenSource).doesNotContain("onBottomBarVisibilityChange(!navigationState.canNavigateBack)")
        assertThat(scaffold).contains("val activeEditingServer = editingServerId?.let { serverId ->")
        assertThat(scaffold).contains("activeEditingServer == null && !(destination == McGoDestination.Settings && settingsDestination != SettingsDestination.Overview)")
        assertThat(scaffold).contains("McGoDestination.Servers -> if (activeEditingServer == null)")
        assertThat(rootScaffoldSection).contains("FloatingGlassBottomMenu(")
        assertThat(rootScaffoldSection).contains("Scaffold(")
        assertThat(rootScaffoldSection).contains("AnimatedVisibility(")
        assertThat(rootScaffoldSection).contains("visible = activeEditingServer != null")
        assertThat(rootScaffoldSection).contains("EditPaperServerDialog(")
        assertThat(rootScaffoldSection).contains("destination == McGoDestination.Settings && settingsDestination != SettingsDestination.Overview")
        assertThat(scaffold).contains("settingsDestination = SettingsDestination.Overview")
        assertThat(scaffold).contains("settingsDestination = it")
        assertThat(rootScaffoldSection).doesNotContain("NavigationBar(")
        assertThat(rootScaffoldSection).doesNotContain("NavigationBarItem(")
        assertThat(rootScaffoldSection).contains("containerColor = Color.Transparent")
        assertThat(rootScaffoldSection).doesNotContain(".padding(innerPadding)")
        assertThat(rootScaffoldSection).contains("innerPadding.calculateTopPadding()")
        assertThat(rootScaffoldSection).contains("innerPadding.calculateStartPadding(layoutDirection)")
        assertThat(rootScaffoldSection).contains("innerPadding.calculateEndPadding(layoutDirection)")
        assertThat(bottomMenu).contains("navigationBarsPadding()")
        assertThat(bottomMenu).contains("Brush.verticalGradient(")
        assertThat(bottomMenu).contains("menuBackdropGradient")
        assertThat(bottomMenu).doesNotContain("fillMaxSize()")
        assertThat(bottomMenu).contains("background(menuBackdropGradient)")
        assertThat(bottomMenu).contains("padding(horizontal = 18.dp, vertical = 12.dp)")
        assertThat(bottomMenu).contains("Color.Transparent")
        assertThat(bottomMenu).contains("copy(alpha = 0.04f * bottomBarAlpha)")
        assertThat(bottomMenu).contains("copy(alpha = 0.18f * bottomBarAlpha)")
        assertThat(bottomMenu).contains("copy(alpha = 0.32f * bottomBarAlpha)")
        assertThat(bottomMenu).contains("startY = 0f")
        assertThat(bottomMenu).contains("endY = Float.POSITIVE_INFINITY")
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
    fun editOverlayImmersion_disablesNavigationBarContrastScrimForTransparentGestureHandle() {
        assertThat(mainActivitySource).contains("enableEdgeToEdge()")
        assertThat(mainActivitySource).contains("if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)")
        assertThat(mainActivitySource).contains("window.isNavigationBarContrastEnforced = false")
    }

    @Test
    fun editPaperServerDialog_usesScrollableChromeAndNonStickyConfigActions() {
        val editDialog = editDialogsSource.substringBetween(
            start = "internal fun EditPaperServerDialog(",
            end = "private fun PaperServerPropertiesEditorDialog(",
        )
        val propertiesDialog = editDialogsSource.substringAfter("private fun PaperServerPropertiesEditorDialog(")
        val scaffold = editChromeSource.substringBetween(
            start = "internal fun EditFullScreenScaffold(",
            end = "internal fun EditSettingsInfoCard(",
        )

        assertThat(editChromeSource).contains("internal enum class EditFullScreenScaffoldLayoutMode")
        assertThat(editDialog).contains("layoutMode = EditFullScreenScaffoldLayoutMode.ScrollableChrome")
        assertThat(propertiesDialog).contains("layoutMode = EditFullScreenScaffoldLayoutMode.PinnedChrome")
        assertThat(editDialog).contains("Text(\"编辑 server.properties\")")
        assertThat(editDialog).doesNotContain("Text(\"进阶：直接编辑 server.properties\")")
        assertThat(editDialog).contains("title = \"当前运行中，只更新配置资料\"")
        assertThat(editDialog).contains("Icons.Outlined.Warning")
        assertThat(propertiesDialog).doesNotContain("受管理字段会同步回表单")
        assertThat(propertiesDialog).doesNotContain("其他未知项会保留为 override")
        assertThat(propertiesDialog).doesNotContain("# 在这里直接编辑 server.properties")
        assertThat(editChromeSource).contains("AnnotatedString")
        assertThat(editChromeSource).contains("SpanStyle")
        assertThat(editDialog).doesNotContain(".verticalScroll(rememberScrollState())")
        assertThat(scaffold).contains("EditFullScreenScaffoldLayoutMode.ScrollableChrome ->")
        assertThat(scaffold).contains("headerInline()")
        assertThat(scaffold).contains("footerInline()")
        assertThat(scaffold).doesNotContain("if (layoutMode == EditFullScreenScaffoldLayoutMode.InlineChrome)")
        assertThat(scaffold).contains("verticalScroll(rememberScrollState())")
        assertThat(scaffold).contains("navigationBarsPadding()")
        assertThat(scaffold).contains("imePadding()")
    }


    @Test
    fun editPageChromeSupport_isExtractedOutOfMainAppFile() {
        listOf(
            "private data class EditPageColors(",
            "private fun editPageColors(",
            "private fun EditFullScreenScaffold(",
            "private fun EditSettingsInfoCard(",
            "private fun EditSettingsSectionCard(",
            "private fun EditTextSettingRow(",
            "private fun <T> EditMenuSettingRow(",
            "private fun EditSwitchSettingRow(",
            "private fun PaperGameMode.displayLabel(",
        ).forEach { oldDefinition ->
            assertThat(source).doesNotContain(oldDefinition)
        }
        assertThat(editChromeSource).contains("internal data class EditPageColors(")
        assertThat(editChromeSource).contains("internal fun editPageColors(")
        assertThat(editChromeSource).contains("internal fun EditFullScreenScaffold(")
        assertThat(editChromeSource).contains("internal fun EditSettingsInfoCard(")
        assertThat(editChromeSource).contains("internal fun EditSettingsSectionCard(")
        assertThat(editChromeSource).contains("internal fun EditTextSettingRow(")
        assertThat(editChromeSource).contains("internal fun <T> EditMenuSettingRow(")
        assertThat(editChromeSource).contains("internal fun EditSwitchSettingRow(")
        assertThat(editChromeSource).contains("internal fun PaperGameMode.displayLabel()")
        assertThat(editChromeSource).contains("internal fun PaperDifficulty.displayLabel()")
    }
    @Test
    fun editServerDialogs_areExtractedOutOfMainAppFile() {
        assertThat(source).contains("EditPaperServerDialog(")
        listOf(
            "private data class PendingServerIconCrop(",
            "private fun EditPaperServerDialog(",
            "private enum class EditServerOverlayDestination",
            "private fun PaperServerPropertiesEditorDialog(",
        ).forEach { oldDefinition ->
            assertThat(source).doesNotContain(oldDefinition)
        }
        assertThat(editDialogsSource).contains("private data class PendingServerIconCrop(")
        assertThat(editDialogsSource).contains("internal fun EditPaperServerDialog(")
        assertThat(editDialogsSource).contains("private enum class EditServerOverlayDestination")
        assertThat(editDialogsSource).contains("private fun PaperServerPropertiesEditorDialog(")
        assertThat(editDialogsSource).contains("rememberLauncherForActivityResult(")
        assertThat(editDialogsSource).contains("PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)")
        assertThat(editDialogsSource).contains("writeManagedServerIcon(")
        assertThat(editDialogsSource).contains("syncManagedServerIconToAuthorizedDirectory(")
        assertThat(editDialogsSource).contains("deleteManagedServerIconFromAuthorizedDirectory(")
        assertThat(editDialogsSource).contains("buildPaperServerPropertiesEditorText(")
        assertThat(editDialogsSource).contains("parsePaperServerPropertiesEditorText(")
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
