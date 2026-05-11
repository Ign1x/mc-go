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
    fun createServerDialog_usesNoServerIconEntryAndStillCreatesSupportedServerTypes() {
        val createDialogSource = serversScreenSource.substringAfter("private fun CreateServerDialog(").substringBefore("@Composable\nprivate fun StartServerDialog(")
        assertThat(createDialogSource).contains("servers: List<ServerCardState>")
        assertThat(createDialogSource).contains("Text(\"创建服务器\")")
        assertThat(createDialogSource).contains("label = { Text(type.label) }")
        assertThat(createDialogSource).contains("when (selectedServerType)")
        assertThat(createDialogSource).contains("var versionWasAutoSelected by remember { mutableStateOf(true) }")
        assertThat(createDialogSource).contains("LaunchedEffect(selectedServerType, vanillaVersionOptions, paperVersionOptions, purpurVersionOptions, fabricVersionOptions, forgeVersionOptions, neoForgeVersionOptions, quiltVersionOptions)")
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
    fun createServerDialog_hasNoServerIconEntryOrCropFlow() {
        val createDialogSource = serversScreenSource.substringAfter("private fun CreateServerDialog(")
            .substringBefore("@Composable\nprivate fun StartServerDialog(")

        assertThat(createDialogSource).doesNotContain("ServerIconEditorCard(")
        assertThat(createDialogSource).doesNotContain("pickButtonLabel = \"选择图标\"")
        assertThat(createDialogSource).doesNotContain("Text(\"更换图标\")")
        assertThat(createDialogSource).doesNotContain("Text(\"移除图标\")")
        assertThat(createDialogSource).doesNotContain("Text(\"选择图标\")")
        assertThat(createDialogSource).doesNotContain("ServerIconCropDialog(")
        assertThat(createDialogSource).doesNotContain("writeManagedServerIcon(")
        assertThat(createDialogSource).doesNotContain("syncManagedServerIconToAuthorizedDirectory(")
        assertThat(createDialogSource).doesNotContain("pendingServerIconChange")
        assertThat(createDialogSource).doesNotContain("serverIconVersion = when (pendingServerIconChange)")
        assertThat(createDialogSource).doesNotContain("runCatching { decodeServerIconPreviewBitmap(context, uri) }")
    }

    @Test
    fun serverModelRuntimeAndPersistence_supportVanillaPaperPurpurFabricForgeNeoForgeAndQuiltTypes() {
        assertThat(modelSource).contains("Vanilla(\"Vanilla\")")
        assertThat(modelSource).contains("Paper(\"Paper\")")
        assertThat(modelSource).contains("Purpur(\"Purpur\")")
        assertThat(modelSource).contains("Fabric(\"Fabric\")")
        assertThat(modelSource).contains("Forge(\"Forge\")")
        assertThat(modelSource).contains("NeoForge(\"NeoForge\")")
        assertThat(modelSource).contains("Quilt(\"Quilt\")")
        assertThat(modelSource).contains("fun createVanillaServer(")
        assertThat(modelSource).contains("fun createPaperServer(")
        assertThat(modelSource).contains("fun createPurpurServer(")
        assertThat(modelSource).contains("fun createFabricServer(")
        assertThat(modelSource).contains("fun createForgeServer(")
        assertThat(modelSource).contains("fun createNeoForgeServer(")
        assertThat(modelSource).contains("fun createQuiltServer(")
        assertThat(modelSource).contains("serverType = MinecraftServerType.Vanilla")
        assertThat(modelSource).contains("serverType = MinecraftServerType.Paper")
        assertThat(modelSource).contains("serverType = MinecraftServerType.Purpur")
        assertThat(modelSource).contains("serverType = MinecraftServerType.Fabric")
        assertThat(modelSource).contains("serverType = MinecraftServerType.Forge")
        assertThat(modelSource).contains("serverType = MinecraftServerType.NeoForge")
        assertThat(modelSource).contains("serverType = MinecraftServerType.Quilt")

        assertThat(runtimeSource).contains("fun fabricServerJarFileName(")
        assertThat(runtimeSource).contains("fun forgeServerJarFileName(")
        assertThat(runtimeSource).contains("fun neoForgeServerJarFileName(")
        assertThat(runtimeSource).contains("fun quiltServerJarFileName(")
        assertThat(runtimeSource).contains("fun fetchFabricVersions(")
        assertThat(runtimeSource).contains("fun fetchForgeVersions(")
        assertThat(runtimeSource).contains("fun fetchNeoForgeVersions(")
        assertThat(runtimeSource).contains("fun fetchQuiltVersions(")
        assertThat(runtimeSource).contains("fun resolveSupportedModLoaderMinecraftVersions(")
        assertThat(runtimeSource).contains("fun resolveInstalledPayloadJar(")
        assertThat(runtimeSource).contains("fun shouldReuseInstalledServerPayload(")
        assertThat(runtimeSource).contains("fun validateBundledAndroidJnaCompatibilityForLaunchTarget(")
        assertThat(runtimeSource).contains("fun downloadFabricServerJar(")
        assertThat(runtimeSource).contains("fun installForgeServer(")
        assertThat(runtimeSource).contains("fun installNeoForgeServer(")
        assertThat(runtimeSource).contains("fun installQuiltServer(")
        assertThat(runtimeSource).contains("fun importManagedServerModpackArchive(")
        assertThat(runtimeSource).contains("fun installManagedServerModFile(")
        assertThat(runtimeSource).contains("MinecraftServerType.Fabric")
        assertThat(runtimeSource).contains("MinecraftServerType.Forge")
        assertThat(runtimeSource).contains("MinecraftServerType.NeoForge")
        assertThat(runtimeSource).contains("MinecraftServerType.Quilt")

        assertThat(serviceSource).contains("MinecraftServerType.Fabric")
        assertThat(serviceSource).contains("MinecraftServerType.Forge")
        assertThat(serviceSource).contains("MinecraftServerType.NeoForge")
        assertThat(serviceSource).contains("MinecraftServerType.Quilt")
        assertThat(serviceSource).contains("downloadFabricServerJar(server.minecraftVersion, runtimeContext.jarPath)")
        assertThat(serviceSource).contains("installForgeServer(")
        assertThat(serviceSource).contains("installNeoForgeServer(")
        assertThat(serviceSource).contains("installQuiltServer(")
        assertThat(serviceSource).contains("val launchConfig = buildManagedPaperLaunchConfig(")
        assertThat(serviceSource).contains("runManagedServerSetupScriptIfNeeded(")
        assertThat(serviceSource).contains("shouldReuseInstalledServerPayload(")
        assertThat(serviceSource).contains("PaperJvmLauncher.launch(launchConfig)")

        assertThat(storeSource).contains("MinecraftServerType.Fabric")
        assertThat(storeSource).contains("MinecraftServerType.Forge")
        assertThat(storeSource).contains("MinecraftServerType.NeoForge")
        assertThat(storeSource).contains("MinecraftServerType.Quilt")

        assertThat(tunnelModelSource).contains("fabric-")
        assertThat(tunnelModelSource).contains("forge-")
        assertThat(tunnelModelSource).contains("neoforge-")
        assertThat(tunnelModelSource).contains("quilt-")
        assertThat(tunnelModelSource).contains("Fabric")
        assertThat(tunnelModelSource).contains("Forge")
        assertThat(tunnelModelSource).contains("NeoForge")
        assertThat(tunnelModelSource).contains("Quilt")
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
