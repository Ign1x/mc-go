package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlin.test.Test

class AuthorizedServerDirectoryWorkspaceRoutingTest {

    @Test
    fun resolveManagedServerWorkspaceDirectory_fallsBackToPrivateDirectoryWithoutAuthorizedRoot() {
        val filesDir = Files.createTempDirectory("mcgo-private-workspace")

        val resolved = resolveManagedServerWorkspaceDirectory(
            filesDir = filesDir,
            authorizedServersRoot = null,
            serverId = "server-demo",
        )

        assertThat(resolved).isEqualTo(managedPaperServerDirectory(filesDir, "server-demo"))
    }

    @Test
    fun resolveManagedServerWorkspaceDirectory_prefersAuthorizedServersRootWhenAvailable() {
        val filesDir = Files.createTempDirectory("mcgo-private-workspace-authorized")
        val authorizedRoot = Files.createTempDirectory("mcgo-authorized-workspace-root")

        val resolved = resolveManagedServerWorkspaceDirectory(
            filesDir = filesDir,
            authorizedServersRoot = authorizedRoot.resolve("servers"),
            serverId = "server-demo",
        )

        assertThat(resolved).isEqualTo(authorizedRoot.resolve("servers/server-demo"))
        assertThat(resolved).isNotEqualTo(managedPaperServerDirectory(filesDir, "server-demo"))
    }

    @Test
    fun shouldPreferAuthorizedWorkspaceOverPrivate_prefersAuthorizedWhenReadyEvenIfPrivateLooksNewer() {
        val preferred = shouldPreferAuthorizedWorkspaceOverPrivate(
            privateRecoverable = true,
            authorizedRecoverable = true,
            authorizedReady = true,
            privateLastModifiedMillis = 2_000L,
            authorizedLastModifiedMillis = 1_000L,
        )

        assertThat(preferred).isTrue()
    }

    @Test
    fun shouldPreferAuthorizedWorkspaceOverPrivate_prefersLegacyAuthorizedWorkspaceWhenItIsNewer() {
        val preferred = shouldPreferAuthorizedWorkspaceOverPrivate(
            privateRecoverable = true,
            authorizedRecoverable = true,
            authorizedReady = false,
            privateLastModifiedMillis = 1_000L,
            authorizedLastModifiedMillis = 2_000L,
        )

        assertThat(preferred).isTrue()
    }

    @Test
    fun shouldPreferAuthorizedWorkspaceOverPrivate_prefersPrivateWhenLegacyAuthorizedWorkspaceIsOlder() {
        val preferred = shouldPreferAuthorizedWorkspaceOverPrivate(
            privateRecoverable = true,
            authorizedRecoverable = true,
            authorizedReady = false,
            privateLastModifiedMillis = 2_000L,
            authorizedLastModifiedMillis = 1_000L,
        )

        assertThat(preferred).isFalse()
    }

    @Test
    fun managedServerWorkspaceMode_syncPolicy_keepsPersistentFallbackButDoesNotClearIt() {
        assertThat(ManagedServerWorkspaceMode.PrivatePersistentFallback.shouldSyncBack).isTrue()
        assertThat(ManagedServerWorkspaceMode.PrivatePersistentFallback.shouldClearPrivateWorkspaceOnSuccessfulSync).isFalse()
        assertThat(ManagedServerWorkspaceMode.PrivateEphemeralMirror.shouldSyncBack).isTrue()
        assertThat(ManagedServerWorkspaceMode.PrivateEphemeralMirror.shouldClearPrivateWorkspaceOnSuccessfulSync).isTrue()
        assertThat(ManagedServerWorkspaceMode.DirectExternal.shouldSyncBack).isFalse()
        assertThat(shouldPersistManagedServerWorkspaceAfterLaunchAttempt(ManagedServerWorkspaceMode.PrivateEphemeralMirror, runtimeLaunchSubmitted = false)).isFalse()
        assertThat(shouldPersistManagedServerWorkspaceAfterLaunchAttempt(ManagedServerWorkspaceMode.PrivateEphemeralMirror, runtimeLaunchSubmitted = true)).isTrue()
        assertThat(shouldPersistManagedServerWorkspaceAfterLaunchAttempt(ManagedServerWorkspaceMode.PrivatePersistentFallback, runtimeLaunchSubmitted = true)).isTrue()
        assertThat(shouldPersistManagedServerWorkspaceAfterLaunchAttempt(ManagedServerWorkspaceMode.DirectExternal, runtimeLaunchSubmitted = true)).isFalse()
    }

    @Test
    fun resolveAuthorizedDirectoryPathFromTreeDocumentId_supportsPrimaryTreePaths() {
        val externalRoot = Files.createTempDirectory("mcgo-primary-root")
        val rootPath = resolveAuthorizedDirectoryPathFromTreeDocumentId(
            treeDocumentId = "primary:Android/data/com.example/files",
            externalRoot = externalRoot,
        )

        assertThat(rootPath).isNotNull()
        assertThat(rootPath).isEqualTo(externalRoot.resolve("Android/data/com.example/files"))
    }

    @Test
    fun resolveAuthorizedDirectoryPathFromTreeDocumentId_rejectsGeneralPrimaryFoldersThatCannotBeDirectlyMounted() {
        val externalRoot = Files.createTempDirectory("mcgo-primary-root-general")

        val rootPath = resolveAuthorizedDirectoryPathFromTreeDocumentId(
            treeDocumentId = "primary:Download/mcgo",
            externalRoot = externalRoot,
        )

        assertThat(rootPath).isNull()
    }

    @Test
    fun resolveAuthorizedDirectoryPathFromTreeDocumentId_rejectsPrimaryStorageRootForDirectMount() {
        val externalRoot = Files.createTempDirectory("mcgo-primary-root-direct")

        val rootPath = resolveAuthorizedDirectoryPathFromTreeDocumentId(
            treeDocumentId = "primary:",
            externalRoot = externalRoot,
        )

        assertThat(rootPath).isNull()
    }

    @Test
    fun prepareAndReleaseManagedServerWorkspaceForForegroundAccess_restoreThenCleanPrivateMirror() {
        val filesDir = Files.createTempDirectory("mcgo-private-foreground")
        val authorizedRoot = Files.createTempDirectory("mcgo-authorized-foreground")
        val authorizedServersRoot = authorizedRoot.resolve("servers")
        val authorizedServerDir = authorizedServersRoot.resolve("server-demo")
        Files.createDirectories(authorizedServerDir)
        Files.write(authorizedServerDir.resolve("server.properties"), "motd=hello\n".toByteArray())

        val prepared = prepareManagedServerWorkspaceForForegroundAccess(
            filesDir = filesDir,
            authorizedServersRoot = authorizedServersRoot,
            serverId = "server-demo",
        )

        val privateMirror = managedPaperServerDirectory(filesDir, "server-demo")
        assertThat(prepared).isEqualTo(privateMirror)
        assertThat(Files.isRegularFile(privateMirror.resolve("server.properties"))).isTrue()

        Files.write(privateMirror.resolve("ops.json"), "[]\n".toByteArray())
        releaseManagedServerWorkspaceAfterForegroundAccess(
            filesDir = filesDir,
            authorizedServersRoot = authorizedServersRoot,
            serverId = "server-demo",
        )

        assertThat(Files.exists(privateMirror)).isFalse()
        assertThat(Files.isRegularFile(authorizedServerDir.resolve("ops.json"))).isTrue()
    }

    @Test
    fun releaseManagedServerWorkspaceAfterForegroundAccess_replacesConflictingAuthorizedFileEntriesWithDirectories() {
        val filesDir = Files.createTempDirectory("mcgo-private-conflict")
        val authorizedRoot = Files.createTempDirectory("mcgo-authorized-conflict")
        val authorizedServersRoot = authorizedRoot.resolve("servers")
        val authorizedServerDir = authorizedServersRoot.resolve("server-demo")
        Files.createDirectories(authorizedServerDir)
        Files.write(authorizedServerDir.resolve("mods"), "legacy-file".toByteArray())

        val privateMirror = managedPaperServerDirectory(filesDir, "server-demo")
        Files.createDirectories(privateMirror.resolve("mods"))
        Files.write(privateMirror.resolve("mods/example.jar"), "jar".toByteArray())

        releaseManagedServerWorkspaceAfterForegroundAccess(
            filesDir = filesDir,
            authorizedServersRoot = authorizedServersRoot,
            serverId = "server-demo",
        )

        assertThat(Files.isDirectory(authorizedServerDir.resolve("mods"))).isTrue()
        assertThat(Files.isRegularFile(authorizedServerDir.resolve("mods/example.jar"))).isTrue()
    }
}
