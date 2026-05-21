package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class AuthorizedServerDirectoryContractTest {
    private val appSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")))
    private val serviceSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerService.kt")))
    private val receiverSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperRuntimeEventReceiver.kt")))
    private val authorizedSyncSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/AuthorizedServerDirectorySync.kt")))
    private val runtimePermissionModelsSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/model/RuntimePermissionModels.kt")))

    @Test
    fun authorizedServerDirectory_becomesDurableSourceOfTruthForServerData() {
        assertThat(appSource).contains("restoreServerProfilesFromAuthorizedDirectory(")
        assertThat(appSource).contains("migratePrivateServerDataToAuthorizedDirectory(")
        assertThat(appSource).contains("activeRuntimeSlotsOnLaunch")
        assertThat(appSource).contains("syncServerProfilesToAuthorizedDirectory(")
        assertThat(appSource).contains("deleteManagedServerWorkspaceFromAuthorizedDirectory(")
        assertThat(appSource).contains("authorizedServerProfilesAvailable(")
        assertThat(appSource).contains("if (authorizedProfilesAvailable)")
        assertThat(appSource).contains("prepareManagedServerWorkspaceAccess(")
        assertThat(appSource).contains("releaseManagedServerWorkspaceAfterForegroundAccess(")

        assertThat(serviceSource).contains("prepareManagedServerWorkspaceForForegroundAccess(")
        assertThat(serviceSource).contains("syncManagedServerWorkspaceToAuthorizedDirectory(")
        assertThat(serviceSource).contains("releaseManagedServerWorkspaceAfterForegroundAccess(")
        assertThat(appSource).contains("restoreManagedServerIconFromAuthorizedDirectory(")
        assertThat(authorizedSyncSource).contains("fun restoreManagedServerIconFromAuthorizedDirectory(")
        assertThat(authorizedSyncSource).contains("Files.deleteIfExists(targetIconPath)")
        assertThat(authorizedSyncSource).contains("fun restoreManagedServerWorkspaceFromAuthorizedDirectory(")
        assertThat(authorizedSyncSource).contains("Files.deleteIfExists(targetIconPath)")
        assertThat(authorizedSyncSource).contains("clearManagedServerWorkspace(targetWorkspaceDir)")
        assertThat(authorizedSyncSource).contains("sanitizeManagedServerId(serverId)) ?: run {")
        assertThat(authorizedSyncSource).contains("fun resolveManagedServerWorkspaceDirectory(")
        assertThat(authorizedSyncSource).contains("fun prepareManagedServerWorkspaceForForegroundAccess(")
        assertThat(authorizedSyncSource).contains("fun releaseManagedServerWorkspaceAfterForegroundAccess(")
        assertThat(authorizedSyncSource).contains("fun syncManagedServerIconToAuthorizedDirectory(")
        assertThat(authorizedSyncSource).contains("fun deleteManagedServerIconFromAuthorizedDirectory(")
        assertThat(authorizedSyncSource).contains("fun migratePrivateServerDataToAuthorizedDirectory(")
        assertThat(authorizedSyncSource).contains("val icon = managedPaperServerIconFile(filesDir, serverId)")
        assertThat(authorizedSyncSource).contains("syncManagedServerIconToAuthorizedDirectory(")
        assertThat(serviceSource).contains("workspaceSyncJob")
        assertThat(serviceSource).contains("finally {")

        assertThat(receiverSource).contains("goAsync()")
        assertThat(receiverSource).contains("syncPaperRuntimeEvent(context, event)")
        assertThat(receiverSource).doesNotContain("syncPaperRuntimeEvent(context.filesDir.toPath(), event)")

        assertThat(runtimePermissionModelsSource).contains("卸载 App 后重新授权同一目录，仍可找回服务器数据")
        assertThat(runtimePermissionModelsSource).doesNotContain("仅影响导入、备份与编辑")
        assertThat(authorizedSyncSource).contains("clearManagedServerWorkspace(")
    }

    @Test
    fun appShell_doesNotKeepUnusedStartupWorkspaceRestoreShim() {
        assertThat(appSource).doesNotContain("private fun restoreManagedManagedServerWorkspaceOnStartup(")
        assertThat(appSource).doesNotContain("import com.mcgo.app.server.restoreManagedServerWorkspaceFromAuthorizedDirectory")
        assertThat(appSource).doesNotContain("import com.mcgo.app.server.resolveManagedServerWorkspaceDirectory")
        assertThat(appSource).doesNotContain("import com.mcgo.app.server.prepareManagedServerWorkspaceForForegroundAccess")
        assertThat(authorizedSyncSource).contains("fun restoreManagedServerWorkspaceFromAuthorizedDirectory(")
        assertThat(authorizedSyncSource).contains("fun resolveManagedServerWorkspaceDirectory(")
        assertThat(authorizedSyncSource).contains("fun prepareManagedServerWorkspaceForForegroundAccess(")
    }

    @Test
    fun modpackImport_usesWorkspaceModeDirectlyInsteadOfAlwaysTrueSyncPolicyHelper() {
        val importSlice = appSource.substringAfter("fun createServerFromModpackNow(server: ServerCardState, archiveUri: Uri) {")
            .substringBefore("fun startServerNow(request: PendingStartRequest)")
        val approvalSlice = appSource.substringAfter("pendingModpackSetupApproval?.let { pendingApproval ->")
            .substringBefore("AnimatedContent(targetState = destination, label = \"appDestination\")")

        assertThat(authorizedSyncSource).doesNotContain("shouldSyncImportedModpackWorkspaceImmediately(")
        assertThat(appSource).doesNotContain("import com.mcgo.app.server.shouldSyncImportedModpackWorkspaceImmediately")
        assertThat(importSlice).doesNotContain("val shouldSyncImportedWorkspaceImmediately")
        assertThat(importSlice).contains("if (workspaceAccess.mode.shouldSyncBack) {")
        assertThat(approvalSlice).doesNotContain("val containsInstallerBootstrap = isInstallerBootstrapScript(")
        assertThat(approvalSlice).doesNotContain("shouldSyncImportedModpackWorkspaceImmediately(")
        assertThat(approvalSlice).contains("if (workspaceAccess.mode.shouldSyncBack) {")
    }

    @Test
    fun modpackImport_usesSafDirectExtractionForAuthorizedDocumentTreeInsteadOfPrivateMirrorCopy() {
        val importSlice = appSource.substringAfter("fun createServerFromModpackNow(server: ServerCardState, archiveUri: Uri) {")
            .substringBefore("fun startServerNow(request: PendingStartRequest)")
        val callbackSlice = appSource.substringAfter("onCreateServerFromModpack = { server, archiveUri ->")
            .substringBefore("onImportWorldArchive = { serverId, archiveUri ->")

        assertThat(authorizedSyncSource).contains("fun importManagedServerModpackArchiveToAuthorizedDirectory(")
        assertThat(authorizedSyncSource).contains("ZipInputStream(BufferedInputStream(archiveInput))")
        assertThat(authorizedSyncSource).contains("createFile(\"application/octet-stream\"")
        assertThat(authorizedSyncSource).contains("writeAuthorizedManagedServerWorkspaceReady(")
        assertThat(authorizedSyncSource).contains("deleteManagedServerWorkspaceFromAuthorizedDirectory(")
        assertThat(authorizedSyncSource).contains("check(existing.delete()) { \"删除授权目录旧文件失败")
        assertThat(authorizedSyncSource).contains("return parent.createFile(\"application/octet-stream\", fileName)")
        assertThat(authorizedSyncSource).doesNotContain("return parent.findFile(fileName)")
        assertThat(importSlice).contains("val serverDirectoryUriTextAtImportStart = serverDirectoryUriText")
        assertThat(importSlice).contains("resolveAuthorizedServersRootPath(appContext, serverDirectoryUriTextAtImportStart) == null")
        assertThat(importSlice).contains("appContext.contentResolver.openInputStream(archiveUri)?.use { input ->")
        assertThat(importSlice).contains("importManagedServerModpackArchiveToAuthorizedDirectory(")
        assertThat(importSlice).contains("importedWorkspaceMode = ManagedServerWorkspaceMode.DirectExternal")
        assertThat(importSlice).doesNotContain("importedWorkspaceMode = ManagedServerWorkspaceMode.PrivateEphemeralMirror\n                                            val directImport")
        assertThat(callbackSlice).contains("createServerFromModpackNow(server, archiveUri)")
        assertThat(authorizedSyncSource).contains("正在解压整合包到授权目录")
        assertThat(importSlice.indexOf("importManagedServerModpackArchiveToAuthorizedDirectory(")).isLessThan(
            importSlice.indexOf("Files.createTempFile(\"mcgo-modpack-\", \".zip\")"),
        )
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
