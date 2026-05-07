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
        assertThat(appSource).contains("syncServerProfilesToAuthorizedDirectory(")
        assertThat(appSource).contains("deleteManagedServerWorkspaceFromAuthorizedDirectory(")
        assertThat(appSource).contains("authorizedServerProfilesAvailable(")
        assertThat(appSource).contains("if (authorizedProfilesAvailable)")

        assertThat(serviceSource).contains("restoreManagedServerWorkspaceFromAuthorizedDirectory(")
        assertThat(serviceSource).contains("syncManagedServerWorkspaceToAuthorizedDirectory(")
        assertThat(serviceSource).contains("workspaceSyncJob")
        assertThat(serviceSource).contains("finally {")

        assertThat(receiverSource).contains("goAsync()")
        assertThat(receiverSource).contains("syncPaperRuntimeEvent(context, event)")
        assertThat(receiverSource).doesNotContain("syncPaperRuntimeEvent(context.filesDir.toPath(), event)")

        assertThat(runtimePermissionModelsSource).contains("卸载 App 后重新授权同一目录，仍可找回服务器数据")
        assertThat(runtimePermissionModelsSource).doesNotContain("仅影响导入、备份与编辑")
        assertThat(authorizedSyncSource).contains("clearManagedServerWorkspace(")
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
