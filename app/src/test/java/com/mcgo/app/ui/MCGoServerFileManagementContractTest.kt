package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoServerFileManagementContractTest {
    private val appSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")))
    private val serverConsoleDialogSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/ServerConsoleDialog.kt")))
    private val serversScreenSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt")))
    private val modpackSetupDialogSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/ModpackSetupApprovalDialog.kt")))
    private val appPendingActionsSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoAppPendingActions.kt")))
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
        assertThat(serversScreenSource).contains("onRequestCreateServer: () -> Unit = {}")
        assertThat(serversScreenSource).contains("TextButton(onClick = onRequestCreateServer)")
        assertThat(serversScreenSource).contains("Text(\"创建第一个服务器\")")
        assertThat(serversScreenSource).contains("创建弹窗中也可以直接导入整合包。")
        assertThat(appSource).contains("onRequestCreateServer = { showServerComposer = true }")
        assertThat(appSource).contains("importManagedServerWorldArchive(")
        assertThat(appSource).contains("exportManagedServerWorldArchive(")
        assertThat(appSource).contains("installManagedServerModFile(")
        assertThat(appSource).contains("importManagedServerModpackArchive(")
        assertThat(appSource).doesNotContain("runManagedServerSetupScriptIfNeeded(")
        assertThat(appSource).contains("discoverManagedServerSetupScripts(workDir)")
        assertThat(appSource).contains("requiresManagedServerSetupApproval(workDir)")
        assertThat(appSource).contains("approveManagedServerSetupScript(workspaceAccess.path, selectedScriptRelativePath)")
        assertThat(modpackSetupDialogSource).contains("请输入要执行的服务器目录相对路径")
        assertThat(modpackSetupDialogSource).contains("脚本 stdout/stderr")
    }

    @Test
    fun serverCard_disables_mutating_actions_while_modpack_import_is_in_progress() {
        val serverCardSource = serversScreenSource.substringAfter("private fun ServerCard(").substringBefore("@Composable\nprivate fun RuntimeProgressPanel(")
        assertThat(serversScreenSource).contains("currentModpackImportServerIds: Set<String> = emptySet()")
        assertThat(serverCardSource).contains("val modpackImportInProgress = currentModpackImportServerIds.contains(server.id)")
        assertThat(serverCardSource).contains("enabled = !modpackImportInProgress")
        assertThat(serverCardSource).contains("val startEnabled = canStartServerFromUi(server) && !modpackImportInProgress")
        assertThat(serverCardSource).contains("val stopEnabled = server.isRuntimeBusy() && !modpackImportInProgress")
        assertThat(serverCardSource).contains("RuntimeProgressPanel(server, modpackImportInProgress = modpackImportInProgress)")
        assertThat(serversScreenSource).contains("private fun RuntimeProgressPanel(server: ServerCardState, modpackImportInProgress: Boolean = false)")
        assertThat(serversScreenSource).contains("isModpackImportProgressActive(")
        assertThat(serversScreenSource).contains("latestRuntimeLog.contains(\"导入整合包\") ||")
        assertThat(serversScreenSource).contains("latestRuntimeLog.contains(\"整合包导入\")")
        assertThat(serversScreenSource).contains("runtimeProgressTitle(")
        assertThat(serversScreenSource).contains("launchStatus == ServerLaunchStatus.Stopping -> \"停止进度\"")
        assertThat(serversScreenSource).contains("importProgressActive -> \"导入进度\"")
        assertThat(serversScreenSource).contains("else -> \"启动进度\"")
        assertThat(serversScreenSource).contains("val progressColor = if (importProgressActive)")
        assertThat(serversScreenSource).contains("server.runtimeLogs.takeLast(6).forEach")
        assertThat(appSource).contains("currentModpackImportServerIds = currentModpackImportServerIds + server.id")
        assertThat(appSource).contains("currentModpackImportServerIds = currentModpackImportServerIds - server.id")
        assertThat(appSource).contains("resolveNewModpackServerImportFailureRecovery(")
        assertThat(appSource).contains("if (recovery.keepServerEntry)")
        assertThat(appSource).contains("recoveredImportedServer")
        assertThat(appSource).contains("markModpackImportRecoveredAfterSyncFailure(")
        assertThat(appSource).doesNotContain("val containsInstallerBootstrap = isInstallerBootstrapScript(")
        assertThat(appSource).contains("确认整合包安装脚本后同步服务器目录失败")
    }

    @Test
    fun createServerFromModpackNow_writesLifecycleDiagnosticsWithoutRawUri() {
        val createFromModpackSource = appSource
            .substringAfter("fun createServerFromModpackNow(server: ServerCardState, archiveUri: Uri) {")
            .substringBefore("fun startServerNow(request: PendingStartRequest) {")
        val modpackCallbackSource = appSource
            .substringAfter("onCreateServerFromModpack = { server, archiveUri ->")
            .substringBefore("onImportWorldArchive = { serverId, archiveUri ->")
        val directoryPickerSource = appSource
            .substringAfter("val directoryPickerLauncher = rememberLauncherForActivityResult(")
            .substringBefore("LaunchedEffect(serverDirectoryUriText)")

        assertThat(createFromModpackSource).contains("appendMcGoAppDebugLog(")
        assertThat(createFromModpackSource).contains("message = \"开始导入整合包\"")
        assertThat(createFromModpackSource).contains("message = \"整合包导入完成\"")
        assertThat(createFromModpackSource).contains("message = \"整合包导入失败\"")
        assertThat(createFromModpackSource).contains("archiveDisplayName")
        assertThat(createFromModpackSource).contains("workspaceMode")
        assertThat(modpackCallbackSource).contains("\"整合包文件已选择\"")
        assertThat(modpackCallbackSource).contains("\"整合包导入等待目录授权\"")
        assertThat(modpackCallbackSource).contains("\"整合包导入等待目录同步\"")
        assertThat(directoryPickerSource).contains("\"请求服务器目录授权\"")
        assertThat(directoryPickerSource).contains("\"服务器目录授权失败\"")
        assertThat(directoryPickerSource).contains("\"服务器目录授权取消\"")
        assertThat(createFromModpackSource).contains("suspend fun updateImportProgress(progress: Int, message: String)")
        assertThat(createFromModpackSource).contains("withContext(Dispatchers.Main.immediate)")
        assertThat(createFromModpackSource).contains("runBlocking { updateImportProgress(mapped, message) }")
        val tempPackCopySource = createFromModpackSource
            .substringAfter("val tempPack = Files.createTempFile")
            .substringBefore("val workspaceAccess = prepareManagedServerWorkspaceAccess")
        assertThat(tempPackCopySource.indexOf("try {")).isLessThan(tempPackCopySource.indexOf("openInputStream(archiveUri)"))
        assertThat(createFromModpackSource).doesNotContain("val updateImportProgress = { progress: Int, message: String ->")
        assertThat(createFromModpackSource).doesNotContain("archiveUri.toString()")
        assertThat(modpackCallbackSource).doesNotContain("archiveUri.toString()")
    }

    @Test
    fun modpackSetupApprovalDialog_confirmsAndContinuesStartingImmediately() {
        val approvalDialogSource = appSource
            .substringAfter("pendingModpackSetupApproval?.let { pendingApproval ->")
            .substringBefore("AnimatedContent(targetState = destination, label = \"appDestination\")")

        assertThat(approvalDialogSource).doesNotContain("安装完成后，请再次点击启动服务器")
        assertThat(approvalDialogSource).contains("ModpackSetupApprovalDialog(")
        assertThat(modpackSetupDialogSource).contains("Text(\"确认安装并启动\")")
        assertThat(approvalDialogSource).contains("startServerNow(pendingApproval.request)")
        assertThat(approvalDialogSource).contains("已确认安装脚本并继续启动")
    }

    @Test
    fun createServerFromModpackNow_movesProfileSyncOffMainThread() {
        val createFromModpackSource = appSource
            .substringAfter("fun createServerFromModpackNow(server: ServerCardState, archiveUri: Uri) {")
            .substringBefore("fun startServerNow(request: PendingStartRequest) {")
        val provisionalSyncSource = createFromModpackSource
            .substringAfter("onServersChange(provisionalServers)")
            .substringBefore("var importCompleted = false")
        val successSyncSource = createFromModpackSource
            .substringAfter("onServersChange(updatedServers)")
            .substringBefore("showServerComposer = false")
        val failureSyncSource = createFromModpackSource
            .substringAfter("onServersChange(recoveredServers)")
            .substringBefore("if (recovery.deletePrivateWorkspace)")

        assertThat(provisionalSyncSource.trimStart()).startsWith("withContext(Dispatchers.IO) {")
        assertThat(provisionalSyncSource).contains("syncServerProfilesToAuthorizedDirectoryNow(provisionalServers, serverDirectoryUriTextAtImportStart)")
        assertThat(successSyncSource.trimStart()).startsWith("withContext(Dispatchers.IO) {")
        assertThat(successSyncSource).contains("syncServerProfilesToAuthorizedDirectoryNow(updatedServers, serverDirectoryUriTextAtImportStart)")
        assertThat(failureSyncSource.trimStart()).startsWith("withContext(Dispatchers.IO) {")
        assertThat(failureSyncSource).contains("syncServerProfilesToAuthorizedDirectoryNow(recoveredServers, serverDirectoryUriTextAtImportStart)")
    }

    @Test
    fun startServerNow_movesWorkspacePreparationOffMainThread() {
        val startServerSource = appSource
            .substringAfter("fun startServerNow(request: PendingStartRequest) {")
            .substringBefore("val queuedStartRequest = pendingStartRequest")

        assertThat(startServerSource).contains("scope.launch")
        assertThat(startServerSource).contains("withContext(Dispatchers.IO)")
        assertThat(startServerSource).contains("prepareManagedServerWorkspaceAccess(")
        assertThat(startServerSource.indexOf("withContext(Dispatchers.IO)")).isLessThan(startServerSource.indexOf("prepareManagedServerWorkspaceAccess("))
    }

    @Test
    fun startServerNow_refreshesLatestServerStateAfterWorkspacePreparation() {
        val startServerSource = appSource
            .substringAfter("fun startServerNow(request: PendingStartRequest) {")
            .substringBefore("val queuedStartRequest = pendingStartRequest")
        val postPrepareSource = startServerSource.substringAfter("val workDir = workspaceAccess.path")

        assertThat(postPrepareSource).contains("val currentServers = latestServers")
        assertThat(postPrepareSource).contains("val targetServer = currentServers.firstOrNull { it.id == request.serverId }")
        assertThat(postPrepareSource).contains("releasePreparedWorkspaceIfNeeded()")
    }

    @Test
    fun startServerNow_claimsPendingStartBeforePreparingWorkspace() {
        val startServerSource = appSource
            .substringAfter("fun startServerNow(request: PendingStartRequest) {")
            .substringBefore("val queuedStartRequest = pendingStartRequest")

        assertThat(appSource).contains("var pendingStartServerIds by remember { mutableStateOf<Set<String>>(emptySet()) }")
        assertThat(startServerSource).contains("if (request.serverId in pendingStartServerIds)")
        assertThat(startServerSource).contains("pendingStartServerIds = pendingStartServerIds + request.serverId")
        assertThat(startServerSource).contains("pendingStartServerIds = pendingStartServerIds - request.serverId")
        assertThat(startServerSource.indexOf("pendingStartServerIds = pendingStartServerIds + request.serverId"))
            .isLessThan(startServerSource.indexOf("prepareManagedServerWorkspaceAccess("))
    }

    @Test
    fun stalePreparedWorkspaceCleanup_discardsMirrorInsteadOfSyncingBack() {
        val startServerSource = appSource
            .substringAfter("fun startServerNow(request: PendingStartRequest) {")
            .substringBefore("val queuedStartRequest = pendingStartRequest")
        val approvalDialogSource = appSource
            .substringAfter("pendingModpackSetupApproval?.let { pendingApproval ->")
            .substringBefore("AnimatedContent(targetState = destination, label = \"appDestination\")")

        assertThat(appSource).contains("discardManagedServerWorkspaceAfterForegroundAccess(")
        assertThat(startServerSource.substringAfter("if (targetServer == null) {").substringBefore("return@launch"))
            .contains("discardManagedServerWorkspaceAfterForegroundAccess(")
        assertThat(startServerSource.substringAfter("if (!canStartServerFromUi(targetServer)) {").substringBefore("return@launch"))
            .contains("discardManagedServerWorkspaceAfterForegroundAccess(")
        assertThat(approvalDialogSource).contains("discardManagedServerWorkspaceAfterForegroundAccess(")
        assertThat(approvalDialogSource).doesNotContain("cleanupPreparedManagedServerWorkspace(pendingApproval.request.serverId, pendingApproval.workspaceMode)")
    }

    @Test
    fun appPendingActionModels_liveOutsideMainAppFile() {
        listOf(
            "private data class PendingStartRequest(",
            "private data class PendingManagedRuntimeStart(",
            "private data class PendingModpackSetupApproval(",
            "private data class PendingCreateServer(",
            "private data class PendingCreateServerFromModpack(",
            "private fun managedSetupScriptRelativePath(",
            "private enum class PendingServerDirectoryAction",
        ).forEach { oldDefinition ->
            assertThat(appSource).doesNotContain(oldDefinition)
        }
        assertThat(appSource).contains("startServerNow(request: PendingStartRequest)")
        assertThat(appSource).contains("PendingCreateServerFromModpack(server, archiveUri)")
        assertThat(appSource).contains("managedSetupScriptRelativePath(workDir, script)")
        assertThat(appPendingActionsSource).contains("internal data class PendingStartRequest(")
        assertThat(appPendingActionsSource).contains("val tunnelSelections: List<TunnelLaunchSelection>")
        assertThat(appPendingActionsSource).contains("internal data class PendingManagedRuntimeStart(")
        assertThat(appPendingActionsSource).contains("internal data class PendingModpackSetupApproval(")
        assertThat(appPendingActionsSource).contains("val workspaceMode: ManagedServerWorkspaceMode")
        assertThat(appPendingActionsSource).contains("internal data class PendingCreateServer(")
        assertThat(appPendingActionsSource).contains("internal data class PendingCreateServerFromModpack(")
        assertThat(appPendingActionsSource).contains("internal fun managedSetupScriptRelativePath(")
        assertThat(appPendingActionsSource).contains("replace('\\\\', '/')")
        assertThat(appPendingActionsSource).contains("internal enum class PendingServerDirectoryAction")
    }

    @Test
    fun serverCreationWaitsForDirectoryAuthorizationAndThenResumes() {
        val createServerSource = appSource
            .substringAfter("onCreateServer = { server ->")
            .substringBefore("onCreateServerFromModpack = { server, archiveUri ->")
        val modpackCreateSource = appSource
            .substringAfter("onCreateServerFromModpack = { server, archiveUri ->")
            .substringBefore("onImportWorldArchive = { serverId, archiveUri ->")
        val queuedCreateSource = appSource
            .substringAfter("val queuedCreateServer = pendingCreateServer")
            .substringBefore("LaunchedEffect(installedJavaVersions, pendingManagedRuntimeStarts)")
        val queuedStartSource = appSource
            .substringAfter("val queuedStartRequest = pendingStartRequest")
            .substringBefore("val queuedCreateServer = pendingCreateServer")

        assertThat(appSource).contains("var pendingCreateServer by remember { mutableStateOf<PendingCreateServer?>(null) }")
        assertThat(appSource).contains("var serverDirectoryGrantProcessing by remember { mutableStateOf(false) }")
        assertThat(appSource).contains("fun createServerNow(server: ServerCardState)")
        assertThat(appSource).contains("fun createServerFromModpackNow(server: ServerCardState, archiveUri: Uri)")
        assertThat(createServerSource).contains("pendingCreateServer = PendingCreateServer(server)")
        assertThat(createServerSource).contains("requestServerDirectory(PendingServerDirectoryAction.CreateServer)")
        assertThat(createServerSource).contains("if (serverDirectoryGrantProcessing)")
        assertThat(createServerSource).doesNotContain("requestServerDirectory(PendingServerDirectoryAction.InitialSetup)")
        assertThat(modpackCreateSource).contains("pendingCreateServerFromModpack = PendingCreateServerFromModpack(server, archiveUri)")
        assertThat(modpackCreateSource).contains("requestServerDirectory(PendingServerDirectoryAction.CreateServerFromModpack)")
        assertThat(modpackCreateSource).contains("if (serverDirectoryGrantProcessing)")
        assertThat(modpackCreateSource).doesNotContain("requestServerDirectory(PendingServerDirectoryAction.InitialSetup)")
        assertThat(queuedCreateSource).contains("createServerNow(queuedCreateServer.server)")
        assertThat(queuedStartSource).contains("!serverDirectoryGrantProcessing")
        assertThat(queuedCreateSource).contains("createServerFromModpackNow(queuedModpackCreate.server, queuedModpackCreate.archiveUri)")
        assertThat(queuedCreateSource).contains("!serverDirectoryGrantProcessing")
        assertThat(queuedCreateSource).contains("showServerComposer = true")
    }

    @Test
    fun deletingRunningServerCommunicatesDeferredRemoval() {
        val deleteHandlerSource = appSource
            .substringAfter("onDeleteServer = { serverId ->")
            .substringBefore("                        onOpenConsole = { serverId ->")
        val runningDeleteBranch = deleteHandlerSource
            .substringAfter("if (targetServer?.isRuntimeBusy() == true) {")
            .substringBefore("} else {")

        assertThat(runningDeleteBranch).contains("requestServerDeletion(server)")
        assertThat(runningDeleteBranch).contains("snackbarHostState.showSnackbar(\"已请求停止并删除 \${targetServer.name}，退出后会自动移除\")")
        assertThat(runningDeleteBranch).doesNotContain("snackbarHostState.showSnackbar(\"已停止并删除")
    }

    @Test
    fun consoleDialog_showsOnlinePlayersAndLongPressActions() {
        assertThat(appSource).contains("ServerConsoleDialog(")
        assertThat(appSource).doesNotContain("private fun ServerConsoleDialog(")
        assertThat(serverConsoleDialogSource).contains("internal fun ServerConsoleDialog(")
        assertThat(serverConsoleDialogSource).contains("text = \"在线玩家\"")
        assertThat(serverConsoleDialogSource).contains("combinedClickable(")
        assertThat(serverConsoleDialogSource).contains("onLongClick =")
        assertThat(serverConsoleDialogSource).contains("复制昵称")
        assertThat(serverConsoleDialogSource).contains("踢出玩家")
        assertThat(serverConsoleDialogSource).contains("授予 OP")
        assertThat(serverConsoleDialogSource).contains("移除 OP")
        assertThat(serverConsoleDialogSource).contains("ClipData.newPlainText(\"${'$'}{server.name} logs\", consoleText)")
        assertThat(serverConsoleDialogSource).doesNotContain("?.let { java.io.File(it) }")
        assertThat(serverConsoleDialogSource).doesNotContain("?.readText()")
        val runtimeProgressPanelSource = serversScreenSource
            .substringAfter("private fun RuntimeProgressPanel(server: ServerCardState) {")
            .substringBefore("@Composable\nprivate fun DeleteServerDialog(")
        assertThat(runtimeProgressPanelSource).contains("resolveServerConsoleText(server)")
        assertThat(runtimeProgressPanelSource).contains("ClipData.newPlainText(\"${'$'}{server.name} MC-GO logs\", consoleText)")
        assertThat(runtimeProgressPanelSource).doesNotContain("?.let(::File)")
        assertThat(runtimeProgressPanelSource).doesNotContain("?.readText()")
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
