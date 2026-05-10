package com.mcgo.app.ui.screens

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class CreateServerDialogContractTest {
    private val appSource: String = readSource("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")
    private val serversScreenSource: String = readSource("app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt")
    private val modelSource: String = readSource("app/src/main/java/com/mcgo/app/ui/model/McGoUiModels.kt")
    private val runtimeSource: String = readSource("app/src/main/java/com/mcgo/app/server/PaperServerRuntime.kt")
    private val serviceSource: String = readSource("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")
    private val storeSource: String = readSource("app/src/main/java/com/mcgo/app/ui/storage/ServerProfileStore.kt")
    private val tunnelModelSource: String = readSource("app/src/main/java/com/mcgo/app/ui/model/TunnelModels.kt")

    @Test
    fun createServerEntryAndDialog_allowChoosingVanillaPaperOrPurpur() {
        assertThat(appSource).contains("Text(\"创建服务器\")")
        assertThat(appSource).doesNotContain("Text(\"创建 Paper\")")

        assertThat(appSource).contains("val vanillaVersions by produceState(initialValue = fallbackVanillaVersions())")
        assertThat(appSource).contains("fetchVanillaVersions()")
        assertThat(appSource).contains("val paperVersions by produceState")
        assertThat(appSource).contains("fetchPaperVersions()")
        assertThat(appSource).contains("val purpurVersions by produceState(initialValue = fallbackPurpurVersions())")
        assertThat(appSource).contains("fetchPurpurVersions()")
        assertThat(appSource).contains("vanillaVersions = vanillaVersions")
        assertThat(appSource).contains("paperVersions = paperVersions")
        assertThat(appSource).contains("purpurVersions = purpurVersions")
        val createDialogSource = serversScreenSource.substringAfter("private fun CreateServerDialog(").substringBefore("@Composable\nprivate fun StartServerDialog(")
        assertThat(createDialogSource).contains("servers: List<ServerCardState>")
        assertThat(createDialogSource).contains("Text(\"创建服务器\")")
        assertThat(createDialogSource).contains("label = { Text(type.label) }")
        assertThat(createDialogSource).contains("when (selectedServerType)")
        assertThat(createDialogSource).contains("var versionWasAutoSelected by remember { mutableStateOf(true) }")
        assertThat(createDialogSource).contains("LaunchedEffect(selectedServerType, vanillaVersionOptions, paperVersionOptions, purpurVersionOptions)")
        assertThat(createDialogSource).contains("if (minecraftVersion !in versionOptions || versionWasAutoSelected)")
        assertThat(createDialogSource).contains("versionWasAutoSelected = true")
        assertThat(createDialogSource).contains("versionWasAutoSelected = false")
        assertThat(createDialogSource).contains("MinecraftServerType.entries")
        assertThat(createDialogSource).contains("selectedServerType")
        assertThat(createDialogSource).contains("var javaSelectionMode by remember { mutableStateOf(JavaSelectionMode.Recommended) }")
        assertThat(createDialogSource).contains("val javaMenuOptions = listOf<String>(\"自动\") + javaVersionOptions.map")
        assertThat(createDialogSource).contains("if (selected == \"自动\")")
        assertThat(createDialogSource).contains("javaSelectionMode = JavaSelectionMode.Recommended")
        assertThat(createDialogSource).contains("javaSelectionMode = JavaSelectionMode.Manual")
        assertThat(createDialogSource).contains("value = if (javaSelectionMode == JavaSelectionMode.Recommended) {\n                            \"自动\"")
        assertThat(createDialogSource).contains("pickAvailableManagedServerPort(")
        assertThat(createDialogSource).doesNotContain("默认端口")
        assertThat(createDialogSource).doesNotContain("var port by remember")
        assertThat(createDialogSource).doesNotContain("resolvedPort = port")
        assertThat(createDialogSource).doesNotContain("Text(\"跟随推荐\")")
        assertThat(createDialogSource).doesNotContain("Text(\"手动指定\")")
        assertThat(createDialogSource).doesNotContain("JavaSelectionModeChipRow(")
        assertThat(createDialogSource).doesNotContain("fetchVanillaVersions()")
        assertThat(createDialogSource).doesNotContain("fetchPurpurVersions()")
        assertThat(createDialogSource).doesNotContain("fetchProvisionableMinecraftVersions()")
        assertThat(createDialogSource).doesNotContain("Text(\"创建 Paper\")")
        assertThat(createDialogSource).doesNotContain("Text(\"创建原版 Paper 服务器\")")
    }

    @Test
    fun createServerDialog_supportsPickingCroppingAndPersistingServerIcon() {
        val createDialogSource = serversScreenSource.substringAfter("private fun CreateServerDialog(")
            .substringBefore("@Composable\nprivate fun StartServerDialog(")

        assertThat(createDialogSource).contains("serverDirectoryUri: String?")
        assertThat(createDialogSource).contains("dynamicBackground: Boolean")
        assertThat(createDialogSource).contains("rememberLauncherForActivityResult(")
        assertThat(createDialogSource).contains("ActivityResultContracts.PickVisualMedia()")
        assertThat(createDialogSource).contains("PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)")
        assertThat(createDialogSource).contains("ServerIconEditorCard(")
        assertThat(createDialogSource).contains("showRemoveAction = false")
        assertThat(createDialogSource).contains("pickButtonLabel = \"选择图标\"")
        assertThat(createDialogSource).doesNotContain("Text(\"更换图标\")")
        assertThat(createDialogSource).doesNotContain("Text(\"移除图标\")")
        assertThat(createDialogSource).contains("ServerIconCropDialog(")
        assertThat(createDialogSource).contains("writeManagedServerIcon(")
        assertThat(createDialogSource).contains("syncManagedServerIconToAuthorizedDirectory(")
        assertThat(createDialogSource).contains("pendingServerIconChange")
        assertThat(createDialogSource).contains("serverIconVersion = when (pendingServerIconChange)")
        assertThat(createDialogSource).contains("val activePendingServerIconCrop = pendingServerIconCrop")
        assertThat(createDialogSource).contains("if (activePendingServerIconCrop != null)")
        assertThat(createDialogSource).contains("return")
        assertThat(createDialogSource).contains("runCatching { decodeServerIconPreviewBitmap(context, uri) }")
        assertThat(createDialogSource).doesNotContain("getOrNull()")
        assertThat(createDialogSource).contains("onFailure { error ->")
        assertThat(createDialogSource).contains("服务器图标读取失败：")
    }

    @Test
    fun serverModelRuntimeAndPersistence_supportVanillaPaperAndPurpurTypes() {
        assertThat(modelSource).contains("Vanilla(\"Vanilla\")")
        assertThat(modelSource).contains("Paper(\"Paper\")")
        assertThat(modelSource).contains("Purpur(\"Purpur\")")
        assertThat(modelSource).contains("fun createVanillaServer(")
        assertThat(modelSource).contains("fun createPaperServer(")
        assertThat(modelSource).contains("fun createPurpurServer(")
        assertThat(modelSource).contains("serverType = MinecraftServerType.Vanilla")
        assertThat(modelSource).contains("serverType = MinecraftServerType.Paper")
        assertThat(modelSource).contains("serverType = MinecraftServerType.Purpur")

        assertThat(runtimeSource).contains("fun vanillaServerJarFileName(")
        assertThat(runtimeSource).contains("fun paperServerJarFileName(")
        assertThat(runtimeSource).contains("fun purpurServerJarFileName(")
        assertThat(runtimeSource).contains("fun downloadVanillaServerJar(")
        assertThat(runtimeSource).contains("fun downloadLatestPaperJar(")
        assertThat(runtimeSource).contains("fun downloadPurpurServerJar(")
        assertThat(runtimeSource).contains("fun fetchVanillaVersions(")
        assertThat(runtimeSource).contains("fun fetchPaperVersions(")
        assertThat(runtimeSource).contains("fun fetchPurpurVersions(")
        assertThat(runtimeSource).contains("when (server.serverType)")
        assertThat(runtimeSource).contains("MinecraftServerType.Vanilla")
        assertThat(runtimeSource).contains("MinecraftServerType.Paper")
        assertThat(runtimeSource).contains("MinecraftServerType.Purpur")

        assertThat(serviceSource).contains("putExtra(\"serverType\", server.serverType.name)")
        assertThat(serviceSource).contains("extras[\"serverType\"]")
        assertThat(serviceSource).contains("MinecraftServerType.Vanilla")
        assertThat(serviceSource).contains("MinecraftServerType.Purpur")

        assertThat(storeSource).contains("properties.setProperty(prefix + \"serverType\", server.serverType.name)")
        assertThat(storeSource).contains("MinecraftServerType.Vanilla")
        assertThat(storeSource).contains("MinecraftServerType.Purpur")

        assertThat(tunnelModelSource).contains("when (serverType)")
        assertThat(tunnelModelSource).contains("vanilla-")
        assertThat(tunnelModelSource).contains("paper-")
        assertThat(tunnelModelSource).contains("purpur-")
        assertThat(tunnelModelSource).contains("serverFlavorLabel")
        assertThat(tunnelModelSource).contains("已生成 \${serverFlavorLabel} 启动计划")
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
