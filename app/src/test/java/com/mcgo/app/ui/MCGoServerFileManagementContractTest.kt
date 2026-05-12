package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoServerFileManagementContractTest {
    private val appSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")))
    private val serversScreenSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt")))
    private val modelSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/model/McGoUiModels.kt")))
    private val eventSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerEvents.kt")))
    private val archiveSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/ManagedServerWorldArchive.kt")))

    @Test
    fun serverCard_placesPerServerFileManagementToTheLeftOfConsole() {
        val serverCardSource = serversScreenSource.substringAfter("private fun ServerCard(").substringBefore("@Composable\nprivate fun RuntimeProgressPanel(")
        assertThat(serverCardSource).doesNotContain("TextButton(onClick = { fileMenuExpanded = true })")
        assertThat(serverCardSource).doesNotContain("Text(\"导入存档 ·")
        assertThat(serverCardSource).doesNotContain("Text(\"导出存档 ·")
        assertThat(serverCardSource).contains("var fileMenuExpanded by remember(server.id) { mutableStateOf(false) }")
        assertThat(serverCardSource).contains("Icon(Icons.Outlined.Folder, contentDescription = \"文件管理\")")
        assertThat(serverCardSource).contains("Text(\"导入存档\")")
        assertThat(serverCardSource).contains("Text(\"导出存档\")")
        assertThat(serverCardSource).contains("Text(\"安装模组\")")
        assertThat(serverCardSource).doesNotContain("Text(\"导入整合包\")")
        assertThat(serverCardSource).contains("server.serverType == MinecraftServerType.Fabric ||")
        assertThat(serverCardSource).contains("server.serverType == MinecraftServerType.Forge ||")
        assertThat(serverCardSource).contains("server.serverType == MinecraftServerType.NeoForge ||")
        assertThat(serverCardSource).contains("server.serverType == MinecraftServerType.Quilt) && !server.isRuntimeBusy()")
        assertThat(serverCardSource).contains("enabled = !server.isRuntimeBusy()")
        assertThat(serverCardSource).contains("Icon(Icons.Outlined.Terminal, contentDescription = stringResource(R.string.server_action_console))")
        assertThat(serverCardSource).contains("Icon(Icons.Outlined.Folder, contentDescription = \"文件管理\")\n                }")
        assertThat(serverCardSource.indexOf("Icons.Outlined.Folder")).isLessThan(serverCardSource.indexOf("Icons.Outlined.Terminal"))
        assertThat(serverCardSource).contains("ActivityResultContracts.OpenDocument()")
        assertThat(serverCardSource).contains("ActivityResultContracts.CreateDocument(\"application/zip\")")
        assertThat(serversScreenSource).contains("if (servers.isEmpty())")
        assertThat(serversScreenSource).contains("Text(text = \"还没有服务器\"")
        assertThat(serversScreenSource).contains("默认先留空。你可以先创建原版 / Paper / Purpur / Fabric / Forge / NeoForge / Quilt 服务器。")
        assertThat(serversScreenSource).contains("添加后的服务器都可以继续启动、编辑或删除。")
        assertThat(appSource).contains("importManagedServerWorldArchive(")
        assertThat(appSource).contains("exportManagedServerWorldArchive(")
        assertThat(appSource).contains("installManagedServerModFile(")
        assertThat(appSource).contains("importManagedServerModpackArchive(")
        assertThat(appSource).doesNotContain("runManagedServerSetupScriptIfNeeded(")
        assertThat(appSource).contains("findManagedServerSetupScript(workDir)")
        assertThat(appSource).contains("requiresManagedServerSetupApproval(workDir)")
        assertThat(appSource).contains("approveManagedServerSetupScript(workDir)")
        assertThat(appSource).contains("整合包包含安装脚本")
        assertThat(appSource).contains("请先确认执行整合包安装脚本")
    }

    @Test
    fun serverCard_disables_mutating_actions_while_modpack_import_is_in_progress() {
        val serverCardSource = serversScreenSource.substringAfter("private fun ServerCard(").substringBefore("@Composable\nprivate fun RuntimeProgressPanel(")
        assertThat(serversScreenSource).contains("currentModpackImportServerIds: Set<String> = emptySet()")
        assertThat(serverCardSource).contains("val modpackImportInProgress = currentModpackImportServerIds.contains(server.id)")
        assertThat(serverCardSource).contains("enabled = !modpackImportInProgress")
        assertThat(serverCardSource).contains("val startEnabled = canStartServerFromUi(server) && !modpackImportInProgress")
        assertThat(serverCardSource).contains("val stopEnabled = server.isRuntimeBusy() && !modpackImportInProgress")
        assertThat(appSource).contains("currentModpackImportServerIds = currentModpackImportServerIds + server.id")
        assertThat(appSource).contains("currentModpackImportServerIds = currentModpackImportServerIds - server.id")
        assertThat(appSource).contains("resolveNewModpackServerImportFailureRecovery(")
        assertThat(appSource).contains("if (recovery.keepServerEntry)")
        assertThat(appSource).contains("recoveredImportedServer")
        assertThat(appSource).contains("markModpackImportRecoveredAfterSyncFailure(")
    }

    @Test
    fun consoleDialog_showsOnlinePlayersAndLongPressActions() {
        assertThat(appSource).contains("text = \"在线玩家\"")
        assertThat(appSource).contains("combinedClickable(")
        assertThat(appSource).contains("onLongClick =")
        assertThat(appSource).contains("复制昵称")
        assertThat(appSource).contains("踢出玩家")
        assertThat(appSource).contains("授予 OP")
        assertThat(appSource).contains("移除 OP")
    }

    @Test
    fun serverStateAndRuntimeEvents_preserveOnlinePlayerNames() {
        assertThat(modelSource).contains("val onlinePlayerNames: List<String> = emptyList()")
        assertThat(eventSource).contains("val onlinePlayerNames: List<String>? = null")
        assertThat(archiveSource).contains("fun importManagedServerWorldArchive(")
        assertThat(archiveSource).contains("fun exportManagedServerWorldArchive(")
        assertThat(archiveSource).contains("fun detectImportedWorldDirectory(")
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
