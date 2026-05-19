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

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
