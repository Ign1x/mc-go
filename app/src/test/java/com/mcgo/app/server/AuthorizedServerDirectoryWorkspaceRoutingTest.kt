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
    fun resolveNewModpackServerImportFailureRecovery_rollsBackFreshServerWhenImportNeverFinished() {
        val recovery = resolveNewModpackServerImportFailureRecovery(
            workspaceMode = ManagedServerWorkspaceMode.PrivateEphemeralMirror,
            importCompleted = false,
        )

        assertThat(recovery.keepServerEntry).isEqualTo(false)
        assertThat(recovery.deletePrivateWorkspace).isEqualTo(true)
        assertThat(recovery.deleteAuthorizedWorkspace).isEqualTo(true)
    }

    @Test
    fun resolveNewModpackServerImportFailureRecovery_keepsServerWhenImportedWorkspaceNeedsFailedSyncBack() {
        val recovery = resolveNewModpackServerImportFailureRecovery(
            workspaceMode = ManagedServerWorkspaceMode.PrivateEphemeralMirror,
            importCompleted = true,
        )

        assertThat(recovery.keepServerEntry).isEqualTo(true)
        assertThat(recovery.deletePrivateWorkspace).isEqualTo(false)
        assertThat(recovery.deleteAuthorizedWorkspace).isEqualTo(true)
    }

    @Test
    fun resolveNewModpackServerImportFailureRecovery_keepsDirectExternalWorkspaceAfterPostImportFailure() {
        val recovery = resolveNewModpackServerImportFailureRecovery(
            workspaceMode = ManagedServerWorkspaceMode.DirectExternal,
            importCompleted = true,
        )

        assertThat(recovery.keepServerEntry).isEqualTo(true)
        assertThat(recovery.deletePrivateWorkspace).isEqualTo(false)
        assertThat(recovery.deleteAuthorizedWorkspace).isEqualTo(false)
    }

    @Test
    fun shouldSyncImportedModpackWorkspaceImmediately_keepsAuthorizedCopiesForInstallerBootstrapPacks() {
        assertThat(
            shouldSyncImportedModpackWorkspaceImmediately(
                workspaceMode = ManagedServerWorkspaceMode.PrivateEphemeralMirror,
                containsInstallerBootstrap = true,
            ),
        ).isTrue()
        assertThat(
            shouldSyncImportedModpackWorkspaceImmediately(
                workspaceMode = ManagedServerWorkspaceMode.PrivatePersistentFallback,
                containsInstallerBootstrap = true,
            ),
        ).isTrue()
        assertThat(
            shouldSyncImportedModpackWorkspaceImmediately(
                workspaceMode = ManagedServerWorkspaceMode.DirectExternal,
                containsInstallerBootstrap = true,
            ),
        ).isTrue()
        assertThat(
            shouldSyncImportedModpackWorkspaceImmediately(
                workspaceMode = ManagedServerWorkspaceMode.PrivateEphemeralMirror,
                containsInstallerBootstrap = false,
            ),
        ).isTrue()
    }

    @Test
    fun detectImportedModpackServerMetadataFromEntryNames_detectsSafDirectExtractionWithoutPrivateMirror() {
        val metadata = detectImportedModpackServerMetadataFromEntryNames(
            listOf(
                "libraries/net/neoforged/neoforge/21.1.224/unix_args.txt",
                "neoforge-21.1.224-installer.jar",
                "startserver.sh",
            ),
        )

        assertThat(metadata.serverType.name).isEqualTo("NeoForge")
        assertThat(metadata.minecraftVersion).isEqualTo("1.21.1")
        assertThat(metadata.javaMajorVersion).isEqualTo(21)
    }

    @Test
    fun resolveInstalledPayloadEntryName_prefersFabricLauncherForSafDirectExtraction() {
        val payload = resolveInstalledPayloadEntryName(
            entryNames = listOf(
                "server.jar",
                "fabric-server-launch.jar",
                "mods/fabric-api.jar",
            ),
            targetJarFileName = "fabric-1.21.4.jar",
        )

        assertThat(payload).isEqualTo("fabric-server-launch.jar")
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

    @Test
    fun discardManagedServerWorkspaceAfterForegroundAccess_clearsPrivateMirrorWithoutSyncingAuthorizedWorkspace() {
        val filesDir = Files.createTempDirectory("mcgo-private-discard")
        val authorizedRoot = Files.createTempDirectory("mcgo-authorized-discard")
        val authorizedServersRoot = authorizedRoot.resolve("servers")
        val authorizedServerDir = authorizedServersRoot.resolve("server-demo")
        Files.createDirectories(authorizedServerDir)
        Files.write(authorizedServerDir.resolve("server.properties"), "motd=hello\n".toByteArray())

        val privateMirror = managedPaperServerDirectory(filesDir, "server-demo")
        Files.createDirectories(privateMirror)
        Files.write(privateMirror.resolve("server.properties"), "motd=stale\n".toByteArray())
        Files.write(privateMirror.resolve("ops.json"), "[]\n".toByteArray())

        discardManagedServerWorkspaceAfterForegroundAccess(
            filesDir = filesDir,
            authorizedServersRoot = authorizedServersRoot,
            serverId = "server-demo",
        )

        assertThat(Files.exists(privateMirror)).isFalse()
        assertThat(String(Files.readAllBytes(authorizedServerDir.resolve("server.properties")))).isEqualTo("motd=hello\n")
        assertThat(Files.exists(authorizedServerDir.resolve("ops.json"))).isFalse()
    }
}
