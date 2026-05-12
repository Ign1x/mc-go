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
        assertThat(serversScreenSource).doesNotContain("TextButton(onClick = { fileMenuExpanded = true })")
        assertThat(serversScreenSource).doesNotContain("Text(\"导入存档 ·")
        assertThat(serversScreenSource).doesNotContain("Text(\"导出存档 ·")
        assertThat(serversScreenSource).contains("var fileMenuExpanded by remember(server.id) { mutableStateOf(false) }")
        assertThat(serversScreenSource).contains("Icon(Icons.Outlined.Folder, contentDescription = \"文件管理\")")
        assertThat(serversScreenSource).contains("Text(\"导入存档\")")
        assertThat(serversScreenSource).contains("Text(\"导出存档\")")
        assertThat(serversScreenSource).contains("Text(\"安装模组\")")
        assertThat(serversScreenSource).contains("Text(\"导入整合包\")")
        assertThat(serversScreenSource).contains("server.serverType == MinecraftServerType.Fabric ||")
        assertThat(serversScreenSource).contains("server.serverType == MinecraftServerType.Forge ||")
        assertThat(serversScreenSource).contains("server.serverType == MinecraftServerType.NeoForge ||")
        assertThat(serversScreenSource).contains("server.serverType == MinecraftServerType.Quilt) && !server.isRuntimeBusy()")
        assertThat(serversScreenSource).contains("enabled = !server.isRuntimeBusy()")
        assertThat(serversScreenSource).contains("Icon(Icons.Outlined.Terminal, contentDescription = stringResource(R.string.server_action_console))")
        assertThat(serversScreenSource).contains("Icon(Icons.Outlined.Folder, contentDescription = \"文件管理\")\n                }")
        assertThat(serversScreenSource.indexOf("Icons.Outlined.Folder")).isLessThan(serversScreenSource.indexOf("Icons.Outlined.Terminal"))
        assertThat(serversScreenSource).contains("ActivityResultContracts.OpenDocument()")
        assertThat(serversScreenSource).contains("ActivityResultContracts.CreateDocument(\"application/zip\")")
        assertThat(serversScreenSource).contains("if (servers.isEmpty())")
        assertThat(serversScreenSource).contains("Text(text = \"还没有服务器\"")
        assertThat(serversScreenSource).contains("默认先留空。你可以先创建原版 / Paper / Purpur / Fabric / Forge / NeoForge / Quilt 服务器。")
        assertThat(serversScreenSource).contains("添加后的服务器都可以继续启动、编辑、导入整合包或删除。")
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
        assertThat(appSource).contains("当前仅 Fabric / Forge / NeoForge / Quilt 服务器支持导入整合包")
        assertThat(appSource).contains("syncManagedServerWorkspaceToAuthorizedDirectory(")
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
