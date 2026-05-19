package com.mcgo.app.ui.screens

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class TunnelsScreenContractTest {
    private val appSource: String = readSource("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")
    private val tunnelsScreenSource: String = readSource("app/src/main/java/com/mcgo/app/ui/screens/TunnelScreen.kt")

    @Test
    fun emptyTunnelListOffersDirectCreateAction() {
        val emptyStateSource = tunnelsScreenSource
            .substringAfter("if (tunnels.isEmpty()) {")
            .substringBefore("        } else {")

        assertThat(tunnelsScreenSource).contains("onRequestCreateTunnel: () -> Unit = {}")
        assertThat(emptyStateSource).contains("Text(text = \"还没有隧道\"")
        assertThat(emptyStateSource).contains("TextButton(onClick = onRequestCreateTunnel)")
        assertThat(emptyStateSource).contains("Text(\"添加第一个隧道\")")
        assertThat(emptyStateSource).contains("可先从粘贴配置开始")
        assertThat(appSource).contains("onRequestCreateTunnel = { showTunnelComposer = true }")
    }

    @Test
    fun tunnelDeleteRequiresConfirmationDialog() {
        val screenBodySource = tunnelsScreenSource
            .substringAfter("fun TunnelsScreen(")
            .substringBefore("@Composable\nprivate fun TunnelCard(")
        val deleteDialogSource = tunnelsScreenSource
            .substringAfter("private fun DeleteTunnelDialog(")
            .substringBefore("@Composable\nprivate fun TunnelCard(")

        assertThat(screenBodySource).contains("var pendingDeleteTunnel by remember { mutableStateOf<TunnelProfile?>(null) }")
        assertThat(screenBodySource).contains("pendingDeleteTunnel?.let { tunnel ->")
        assertThat(screenBodySource).contains("DeleteTunnelDialog(")
        assertThat(screenBodySource).contains("onDelete = { pendingDeleteTunnel = tunnel }")
        assertThat(screenBodySource).doesNotContain("onDelete = { onDeleteTunnel(tunnel.id) }")
        assertThat(deleteDialogSource).contains("title = { Text(\"确认删除隧道\") }")
        assertThat(deleteDialogSource).contains("text = { Text(\"删除后不会影响已保存的服务器配置，但该隧道入口会从列表中移除：\${tunnel.name}\") }")
        assertThat(deleteDialogSource).contains("Text(\"删除\")")
        assertThat(screenBodySource).contains("onDeleteTunnel(tunnel.id)")
        assertThat(screenBodySource).contains("pendingDeleteTunnel = null")
    }

    @Test
    fun tunnelDeletionShowsSuccessFeedbackAfterProfileRemoval() {
        val deleteHandlerSource = appSource
            .substringAfter("onDeleteTunnel = { tunnelId ->")
            .substringBefore("                        modifier = Modifier.fillMaxSize(),")

        assertThat(deleteHandlerSource).contains("val targetTunnel = tunnels.firstOrNull { it.id == tunnelId } ?: return@TunnelsScreen")
        assertThat(deleteHandlerSource).contains("onTunnelsChangeAndPersist(updatedTunnels)")
        assertThat(deleteHandlerSource).contains("snackbarHostState.showSnackbar(\"已删除隧道 \${targetTunnel.name}\")")
        assertThat(deleteHandlerSource.indexOf("onTunnelsChangeAndPersist(updatedTunnels)"))
            .isLessThan(deleteHandlerSource.indexOf("snackbarHostState.showSnackbar(\"已删除隧道"))
    }

    @Test
    fun tunnelSaveShowsCreateOrUpdateFeedbackAfterPersistence() {
        val saveHandlerSource = appSource
            .substringAfter("onSaveTunnel = { profile ->")
            .substringBefore("                        onEditTunnel = { tunnelId ->")

        assertThat(saveHandlerSource).contains("val saveMessage = if (editingTunnelId == null) \"已新增隧道 \${profile.name}\" else \"已更新隧道 \${profile.name}\"")
        assertThat(saveHandlerSource).contains("onTunnelsChangeAndPersist(updated)")
        assertThat(saveHandlerSource).contains("editingTunnelId = null")
        assertThat(saveHandlerSource).contains("snackbarHostState.showSnackbar(saveMessage)")
        assertThat(saveHandlerSource.indexOf("onTunnelsChangeAndPersist(updated)"))
            .isLessThan(saveHandlerSource.indexOf("snackbarHostState.showSnackbar(saveMessage)"))
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
