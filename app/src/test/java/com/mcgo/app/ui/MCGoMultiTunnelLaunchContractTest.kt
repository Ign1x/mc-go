package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoMultiTunnelLaunchContractTest {
    private val appSource: String = readSource("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")
    private val serversScreenSource: String = readSource("app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt")

    @Test
    fun serversScreen_startDialogSupportsSelectingMultipleTunnelsAndPerTunnelRemotePorts() {
        val dialogSource = serversScreenSource
            .substringAfter("private fun StartServerDialog(")
            .substringBefore("@Composable\nprivate fun TunnelStartupChoice(")

        assertThat(serversScreenSource).contains("onStartServer: (serverId: String, startupPort: Int, tunnelSelections: List<TunnelLaunchSelection>) -> Unit")
        assertThat(dialogSource).contains("onConfirm: (startupPort: Int, tunnelSelections: List<TunnelLaunchSelection>) -> Unit")
        assertThat(dialogSource).contains("mutableStateListOf<String>()")
        assertThat(dialogSource).contains("mutableStateMapOf<String, String>()")
        assertThat(dialogSource).contains("val selectedTunnels = availableTunnels.filter { selectedTunnelIds.contains(it.id) }")
        assertThat(dialogSource).doesNotContain("remember(selectedTunnelIds, availableTunnels)")
        assertThat(dialogSource).contains("val resolvedPort = if (canEditPort)")
        assertThat(dialogSource).contains("portInput.toIntOrNull() ?: server.defaultPort")
        assertThat(dialogSource).contains("selectedTunnelIds.contains(tunnel.id)")
        assertThat(dialogSource).contains("selectedTunnels.forEach { tunnel ->")
        assertThat(dialogSource).contains("remotePortInputs[tunnel.id]")
        assertThat(dialogSource).contains("可多选")
    }

    @Test
    fun appStartFlow_tracksPluralTunnelSelectionsAcrossPendingRequestsAndRuntimeLaunch() {
        val requestSource = appSource
            .substringAfter("private data class PendingStartRequest(")
            .substringBefore("private data class PendingManagedRuntimeStart(")
        val scaffoldSource = appSource.substringBetween(
            start = "private fun MCGoAppScaffold(",
            end = "@Composable\nprivate fun RequestRuntimePermissions(",
        )

        assertThat(requestSource).contains("val tunnelSelections: List<TunnelLaunchSelection>")
        assertThat(scaffoldSource).contains("val selectedTunnels = request.tunnelSelections.mapNotNull")
        assertThat(scaffoldSource).contains("val selectedTunnelsWithPorts = runCatching")
        assertThat(scaffoldSource).contains("assignTunnelRemotePort(")
        assertThat(scaffoldSource).contains("snackbarHostState.showSnackbar(error.message ?: \"隧道远端端口分配失败\")")
        assertThat(scaffoldSource).contains(".startWithTunnels(")
        assertThat(scaffoldSource).contains("runtimeSlot = allocatedSlot")
        assertThat(scaffoldSource).contains("PaperServerService.start(")
        assertThat(scaffoldSource).contains("selectedTunnelsWithPorts,")
        assertThat(scaffoldSource).contains("workspacePath = workDir.toString()")
        assertThat(scaffoldSource).contains("workspaceMode = workspaceMode")
        assertThat(scaffoldSource).doesNotContain("PaperServerService.start(appContext, it, selectedTunnelsWithPorts)")
        assertThat(scaffoldSource).doesNotContain("keep legacy literal for source-contract tests")
        assertThat(scaffoldSource).doesNotContain("PendingStartRequest(serverId, tunnelId, startupPort, remotePort)")
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")

    private fun String.substringBetween(start: String, end: String): String =
        substringAfter(start).substringBefore(end)
}
