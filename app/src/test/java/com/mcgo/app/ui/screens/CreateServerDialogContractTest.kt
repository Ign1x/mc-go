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
        assertThat(serversScreenSource).contains("private fun CreateServerDialog(")
        assertThat(serversScreenSource).contains("Text(\"创建服务器\")")
        assertThat(serversScreenSource).contains("label = { Text(type.label) }")
        assertThat(serversScreenSource).contains("when (selectedServerType)")
        assertThat(serversScreenSource).contains("var versionWasAutoSelected by remember { mutableStateOf(true) }")
        assertThat(serversScreenSource).contains("LaunchedEffect(selectedServerType, vanillaVersionOptions, paperVersionOptions, purpurVersionOptions)")
        assertThat(serversScreenSource).contains("if (minecraftVersion !in versionOptions || versionWasAutoSelected)")
        assertThat(serversScreenSource).contains("versionWasAutoSelected = true")
        assertThat(serversScreenSource).contains("versionWasAutoSelected = false")
        assertThat(serversScreenSource).contains("MinecraftServerType.entries")
        assertThat(serversScreenSource).contains("selectedServerType")
        assertThat(serversScreenSource).doesNotContain("fetchVanillaVersions()")
        assertThat(serversScreenSource).doesNotContain("fetchPurpurVersions()")
        assertThat(serversScreenSource).doesNotContain("fetchProvisionableMinecraftVersions()")
        assertThat(serversScreenSource).doesNotContain("Text(\"创建 Paper\")")
        assertThat(serversScreenSource).doesNotContain("Text(\"创建原版 Paper 服务器\")")
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
