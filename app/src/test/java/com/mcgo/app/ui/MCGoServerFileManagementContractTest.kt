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
        assertThat(serversScreenSource).contains("enabled = !server.isRuntimeBusy()")
        assertThat(serversScreenSource).contains("Icon(Icons.Outlined.Terminal, contentDescription = stringResource(R.string.server_action_console))")
        assertThat(serversScreenSource).contains("Icon(Icons.Outlined.Folder, contentDescription = \"文件管理\")\n                }")
        assertThat(serversScreenSource.indexOf("Icons.Outlined.Folder")).isLessThan(serversScreenSource.indexOf("Icons.Outlined.Terminal"))
        assertThat(serversScreenSource).contains("ActivityResultContracts.OpenDocument()")
        assertThat(serversScreenSource).contains("ActivityResultContracts.CreateDocument(\"application/zip\")")
        assertThat(appSource).contains("importManagedServerWorldArchive(")
        assertThat(appSource).contains("exportManagedServerWorldArchive(")
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
