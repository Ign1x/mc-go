package com.mcgo.app.server

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.mcgo.app.ui.storage.ServerProfileStoreGlobalLock
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException

private const val AuthorizedServerProfilesFileName = "server_profiles.properties"
private const val AuthorizedServersDirectoryName = "servers"
private const val ManagedServerIconFileName = "server-icon.png"
private const val ManagedServerWorkspaceReadyMarkerName = ".mcgo-workspace-ready"
private val ManagedServerWorkspaceIgnoredTopLevelNames = setOf(
    "logs",
    "frp",
    ManagedServerIconFileName,
    ManagedServerWorkspaceReadyMarkerName,
)

enum class ManagedServerWorkspaceMode(
    val shouldSyncBack: Boolean,
    val shouldClearPrivateWorkspaceOnSuccessfulSync: Boolean,
) {
    DirectExternal(
        shouldSyncBack = false,
        shouldClearPrivateWorkspaceOnSuccessfulSync = false,
    ),
    PrivateEphemeralMirror(
        shouldSyncBack = true,
        shouldClearPrivateWorkspaceOnSuccessfulSync = true,
    ),
    PrivatePersistentFallback(
        shouldSyncBack = true,
        shouldClearPrivateWorkspaceOnSuccessfulSync = false,
    ),
}

data class ManagedServerWorkspaceAccess(
    val path: Path,
    val mode: ManagedServerWorkspaceMode,
) {
    val usesEphemeralMirror: Boolean
        get() = mode == ManagedServerWorkspaceMode.PrivateEphemeralMirror
}

internal data class NewModpackServerImportFailureRecovery(
    val keepServerEntry: Boolean,
    val deletePrivateWorkspace: Boolean,
    val deleteAuthorizedWorkspace: Boolean,
)

internal fun resolveNewModpackServerImportFailureRecovery(
    workspaceMode: ManagedServerWorkspaceMode,
    importCompleted: Boolean,
): NewModpackServerImportFailureRecovery = when {
    !importCompleted -> NewModpackServerImportFailureRecovery(
        keepServerEntry = false,
        deletePrivateWorkspace = workspaceMode != ManagedServerWorkspaceMode.DirectExternal,
        deleteAuthorizedWorkspace = true,
    )
    workspaceMode == ManagedServerWorkspaceMode.DirectExternal -> NewModpackServerImportFailureRecovery(
        keepServerEntry = true,
        deletePrivateWorkspace = false,
        deleteAuthorizedWorkspace = false,
    )
    else -> NewModpackServerImportFailureRecovery(
        keepServerEntry = true,
        deletePrivateWorkspace = false,
        deleteAuthorizedWorkspace = true,
    )
}

internal suspend fun runNewModpackServerImportFailureCleanup(
    recovery: NewModpackServerImportFailureRecovery,
    deletePrivateWorkspace: suspend () -> Unit,
    deleteAuthorizedWorkspace: suspend () -> Unit,
    logCleanupFailure: suspend (cleanupTarget: String, cleanupError: Throwable) -> Unit,
) {
    suspend fun attemptCleanup(cleanupTarget: String, cleanup: suspend () -> Unit) {
        try {
            cleanup()
        } catch (cleanupError: Throwable) {
            cleanupError.rethrowIfCoroutineCancellation()
            try {
                logCleanupFailure(cleanupTarget, cleanupError)
            } catch (logError: Throwable) {
                logError.rethrowIfCoroutineCancellation()
            }
        }
    }
    if (recovery.deletePrivateWorkspace) {
        attemptCleanup("privateWorkspace", deletePrivateWorkspace)
    }
    if (recovery.deleteAuthorizedWorkspace) {
        attemptCleanup("authorizedWorkspace", deleteAuthorizedWorkspace)
    }
}

private fun Throwable.rethrowIfCoroutineCancellation() {
    if (this is CancellationException) throw this
}

internal fun shouldPersistManagedServerWorkspaceAfterLaunchAttempt(
    workspaceMode: ManagedServerWorkspaceMode,
    runtimeLaunchSubmitted: Boolean,
    completedInstallerBootstrapOnly: Boolean = false,
): Boolean = workspaceMode.shouldSyncBack && (runtimeLaunchSubmitted || completedInstallerBootstrapOnly)

internal data class AuthorizedModpackImportResult(
    val metadata: ImportedModpackServerMetadata,
    val setupScriptNames: List<String>,
)

data class ManagedServerWorkspaceSyncProgress(
    val fileCount: Int,
    val totalFileCount: Int,
    val totalBytes: Long,
) {
    fun toDiagnosticSyncProgressMessage(): String =
        "正在同步整合包到已授权目录 · files=$fileCount/$totalFileCount bytes=$totalBytes"
}

private data class AuthorizedModpackExtractionResult(
    val entryNames: List<String>,
    val sha256ByEntryName: Map<String, String>,
    val setupScriptNames: List<String>,
    val summary: ManagedServerArchiveExtractionSummary,
)

internal fun importManagedServerModpackArchiveToAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
    archiveInput: InputStream,
    archiveTotalBytes: Long? = null,
    onProgress: ((Int, String) -> Unit)? = null,
): AuthorizedModpackImportResult {
    fun reportProgress(progress: Int, message: String) {
        runCatching { onProgress?.invoke(progress.coerceIn(1, 100), message) }
    }

    val targetServerDir = authorizedManagedServerWorkspaceDocumentFile(
        context = context,
        authorizedDirectoryUri = authorizedDirectoryUri,
        serverId = serverId,
    ) ?: error("服务器目录未授权，请先选择并授权 MCGO 目录")

    return try {
        reportProgress(2, "正在清理授权目录中的旧整合包文件")
        clearDocumentFileChildren(targetServerDir)
        reportProgress(5, "正在解压整合包到授权目录")
        val extraction = unzipManagedServerArchiveToDocumentTree(
            context = context,
            archiveInput = archiveInput,
            targetServerDir = targetServerDir,
            archiveTotalBytes = archiveTotalBytes,
            onProgress = { progress ->
                reportProgress(progress.toImportProgress(start = 6, end = 70), progress.toDiagnosticExtractionProgressMessage())
            },
        )
        reportProgress(70, extraction.summary.toDiagnosticProgressMessage())
        val metadata = detectImportedModpackServerMetadataFromEntryNames(extraction.entryNames)
        val targetJarFileName = managedServerTargetJarFileName(
            serverTypeName = metadata.serverType.name,
            minecraftVersion = metadata.minecraftVersion,
        )
        resolveInstalledPayloadEntryName(
            entryNames = extraction.entryNames,
            targetJarFileName = targetJarFileName,
        )?.let { payloadEntryName ->
            extraction.sha256ByEntryName[payloadEntryName]?.let { sha256 ->
                writeAuthorizedPayloadSha(
                    context = context,
                    targetServerDir = targetServerDir,
                    payloadEntryName = payloadEntryName,
                    sha256 = sha256,
                )
            }
        }
        check(writeAuthorizedManagedServerWorkspaceReady(context, authorizedDirectoryUri, serverId)) {
            "写入授权目录就绪标记失败"
        }
        reportProgress(100, "整合包导入完成")
        AuthorizedModpackImportResult(
            metadata = metadata,
            setupScriptNames = extraction.setupScriptNames,
        )
    } catch (error: Exception) {
        deleteManagedServerWorkspaceFromAuthorizedDirectory(context, authorizedDirectoryUri, serverId)
        throw error
    }
}

internal fun detectImportedModpackServerMetadataFromEntryNames(entryNames: List<String>): ImportedModpackServerMetadata {
    val normalizedEntries = entryNames.map { it.replace('\\', '/').trimStart('/') }
    detectInstallerPackMetadataFromEntryNames(normalizedEntries)?.let { return it }

    fun build(serverType: com.mcgo.app.ui.model.MinecraftServerType, minecraftVersion: String): ImportedModpackServerMetadata =
        ImportedModpackServerMetadata(
            serverType = serverType,
            minecraftVersion = minecraftVersion,
            javaMajorVersion = com.mcgo.app.ui.model.recommendedJavaMajorVersion(minecraftVersion),
        )

    fun findVersionFromPaths(): String? {
        val versionRegex = Regex("""/(?:server|minecraftforge/forge|neoforge)/((?:1\.)?\d+\.\d+(?:\.\d+)?)(?:-|/)""")
        return normalizedEntries
            .asSequence()
            .map { rawPath -> "/$rawPath" }
            .mapNotNull { rawPath -> versionRegex.find(rawPath)?.groupValues?.getOrNull(1) }
            .filter { version -> validatePaperVersionOrNull(version) != null }
            .toList()
            .maxWithOrNull(::compareMinecraftVersionParts)
    }

    if (normalizedEntries.any { it.substringAfterLast('/') == "fabric-server-launch.jar" }) {
        return build(com.mcgo.app.ui.model.MinecraftServerType.Fabric, findVersionFromPaths() ?: "1.21.4")
    }
    if (normalizedEntries.any { it.substringAfterLast('/') == "quilt-server-launch.jar" }) {
        return build(com.mcgo.app.ui.model.MinecraftServerType.Quilt, findVersionFromPaths() ?: "1.21.4")
    }
    normalizedEntries.firstOrNull { path ->
        path.substringAfterLast('/') == "unix_args.txt" && path.contains("/net/minecraftforge/forge/")
    }?.let { argsPath ->
        val version = Regex("""/forge/((?:1\.)?\d+\.\d+(?:\.\d+)?)-""")
            .find("/$argsPath")
            ?.groupValues
            ?.getOrNull(1)
            ?: "1.20.1"
        return build(com.mcgo.app.ui.model.MinecraftServerType.Forge, version)
    }
    normalizedEntries.firstOrNull { path ->
        path.substringAfterLast('/') == "unix_args.txt" && path.contains("/net/neoforged/neoforge/")
    }?.let { argsPath ->
        val rawVersion = Regex("""/neoforge/((?:1\.)?\d+\.\d+(?:\.\d+)?)(?:[./])""")
            .find("/$argsPath")
            ?.groupValues
            ?.getOrNull(1)
            ?: "1.21.4"
        val version = normalizeNeoForgeMinecraftVersion(rawVersion)
        return build(com.mcgo.app.ui.model.MinecraftServerType.NeoForge, version)
    }
    return build(com.mcgo.app.ui.model.MinecraftServerType.Paper, findVersionFromPaths() ?: "1.21.4")
}

internal fun resolveInstalledPayloadEntryName(
    entryNames: List<String>,
    targetJarFileName: String,
): String? {
    val targetName = targetJarFileName.lowercase()
    val targetKind = when {
        targetName.startsWith("fabric-") -> "fabric"
        targetName.startsWith("forge-") -> "forge"
        targetName.startsWith("neoforge-") -> "neoforge"
        targetName.startsWith("quilt-") -> "quilt"
        else -> "generic"
    }
    val candidates = entryNames
        .map { it.replace('\\', '/').trimStart('/') }
        .filter { entryName ->
            val name = entryName.substringAfterLast('/')
            name !in ReservedManagedServerImportEntries && when {
                name == "fabric-server-launch.jar" -> true
                name == "server.jar" -> true
                name == "quilt-server-launch.jar" -> true
                name.endsWith("-server.jar") -> true
                name.endsWith("-universal.jar") -> true
                name.endsWith("-shim.jar") -> true
                else -> false
            }
        }
    fun rank(path: String): Int {
        val name = path.substringAfterLast('/')
        return when (targetKind) {
            "fabric" -> when {
                name == "fabric-server-launch.jar" -> 0
                name == "server.jar" -> 1
                else -> 9
            }
            "quilt" -> when {
                name == "quilt-server-launch.jar" -> 0
                name == "server.jar" -> 1
                else -> 9
            }
            "forge" -> when {
                name.endsWith("-server.jar") && path.contains("/net/minecraftforge/forge/") -> 0
                name == "server.jar" -> 1
                name.endsWith("-universal.jar") -> 2
                name.endsWith("-shim.jar") -> 3
                else -> 9
            }
            "neoforge" -> when {
                name.endsWith("-server.jar") && path.contains("/net/neoforged/neoforge/") -> 0
                name.endsWith("-universal.jar") && path.contains("/net/neoforged/neoforge/") -> 1
                name.endsWith("-shim.jar") && path.contains("/net/neoforged/neoforge/") -> 2
                name == "server.jar" -> 3
                else -> 9
            }
            else -> when {
                name == "fabric-server-launch.jar" -> 0
                name == "quilt-server-launch.jar" -> 1
                name == "server.jar" -> 2
                name.endsWith("-server.jar") -> 3
                name.endsWith("-universal.jar") -> 4
                name.endsWith("-shim.jar") -> 5
                else -> 9
            }
        }
    }
    return candidates.sortedWith(compareBy<String>({ rank(it) }, { it.length })).firstOrNull { rank(it) < 9 }
}

private fun detectInstallerPackMetadataFromEntryNames(entryNames: List<String>): ImportedModpackServerMetadata? {
    val installerPattern = Regex("(?:^|/)neoforge-(\\d+\\.\\d+\\.\\d+)-installer\\.jar$", RegexOption.IGNORE_CASE)
    val artifactVersion = entryNames.asSequence()
        .mapNotNull { entryName -> installerPattern.find(entryName)?.groupValues?.getOrNull(1) }
        .firstOrNull()
        ?: return null
    val minecraftVersion = normalizeNeoForgeMinecraftVersion(artifactVersion)
    return ImportedModpackServerMetadata(
        serverType = com.mcgo.app.ui.model.MinecraftServerType.NeoForge,
        minecraftVersion = minecraftVersion,
        javaMajorVersion = com.mcgo.app.ui.model.recommendedJavaMajorVersion(minecraftVersion),
    )
}

private fun normalizeNeoForgeMinecraftVersion(rawVersion: String): String = rawVersion.split('.').let { parts ->
    when {
        rawVersion.startsWith("1.") -> rawVersion
        parts.size >= 3 && (parts[0].toIntOrNull() ?: 0) < 26 -> "1.${parts[0]}.${parts[1]}"
        else -> rawVersion
    }
}

private fun compareMinecraftVersionParts(left: String, right: String): Int {
    val leftParts = left.split('.').map { it.toIntOrNull() ?: Int.MIN_VALUE }
    val rightParts = right.split('.').map { it.toIntOrNull() ?: Int.MIN_VALUE }
    val max = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until max) {
        val comparison = (leftParts.getOrNull(index) ?: 0).compareTo(rightParts.getOrNull(index) ?: 0)
        if (comparison != 0) return comparison
    }
    return left.compareTo(right)
}

private fun managedServerTargetJarFileName(serverTypeName: String, minecraftVersion: String): String = when (serverTypeName) {
    "Vanilla" -> vanillaServerJarFileName(minecraftVersion)
    "Paper" -> paperServerJarFileName(minecraftVersion)
    "Purpur" -> purpurServerJarFileName(minecraftVersion)
    "Fabric" -> fabricServerJarFileName(minecraftVersion)
    "Forge" -> forgeServerJarFileName(minecraftVersion)
    "NeoForge" -> neoForgeServerJarFileName(minecraftVersion)
    "Quilt" -> quiltServerJarFileName(minecraftVersion)
    else -> paperServerJarFileName(minecraftVersion)
}

internal fun shouldPreferAuthorizedWorkspaceOverPrivate(
    privateRecoverable: Boolean,
    authorizedRecoverable: Boolean,
    authorizedReady: Boolean,
    privateLastModifiedMillis: Long,
    authorizedLastModifiedMillis: Long,
): Boolean {
    if (authorizedReady) return true
    if (!authorizedRecoverable) return false
    if (!privateRecoverable) return true
    return authorizedLastModifiedMillis >= privateLastModifiedMillis
}

fun restoreServerProfilesFromAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    targetProfilesPath: Path,
): Boolean = synchronized(ServerProfileStoreGlobalLock) {
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return@synchronized false
    val profiles = root.findFile(AuthorizedServerProfilesFileName) ?: return@synchronized false
    context.contentResolver.openInputStream(profiles.uri)?.use { input ->
        targetProfilesPath.parent?.let(Files::createDirectories)
        Files.copy(input, targetProfilesPath, StandardCopyOption.REPLACE_EXISTING)
    } ?: return@synchronized false
    true
}

fun authorizedServerProfilesAvailable(
    context: Context,
    authorizedDirectoryUri: String?,
): Boolean = authorizedDirectoryRoot(context, authorizedDirectoryUri)
    ?.findFile(AuthorizedServerProfilesFileName)
    ?.isFile == true

fun syncServerProfilesToAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    sourceProfilesPath: Path,
) = synchronized(ServerProfileStoreGlobalLock) {
    if (!Files.isRegularFile(sourceProfilesPath)) return@synchronized
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return@synchronized
    val target = root.findFile(AuthorizedServerProfilesFileName)
        ?: root.createFile("text/x-java-properties", AuthorizedServerProfilesFileName)
        ?: return@synchronized
    context.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
        Files.newInputStream(sourceProfilesPath).use { input -> input.copyTo(output) }
    }
}

fun restoreManagedServerIconFromAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
    targetIconPath: Path,
): Boolean {
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: run {
        Files.deleteIfExists(targetIconPath)
        return false
    }
    val serversDir = root.findFile(AuthorizedServersDirectoryName) ?: run {
        Files.deleteIfExists(targetIconPath)
        return false
    }
    val serverDir = serversDir.findFile(sanitizeManagedServerId(serverId)) ?: run {
        Files.deleteIfExists(targetIconPath)
        return false
    }
    val iconFile = serverDir.findFile(targetIconPath.fileName.toString()) ?: run {
        Files.deleteIfExists(targetIconPath)
        return false
    }
    targetIconPath.parent?.let(Files::createDirectories)
    context.contentResolver.openInputStream(iconFile.uri)?.use { input ->
        Files.copy(input, targetIconPath, StandardCopyOption.REPLACE_EXISTING)
    } ?: run {
        Files.deleteIfExists(targetIconPath)
        return false
    }
    return true
}

fun restoreManagedServerWorkspaceFromAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
    targetWorkspaceDir: Path,
): Boolean {
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: run {
        clearManagedServerWorkspace(targetWorkspaceDir)
        return false
    }
    val serversDir = root.findFile(AuthorizedServersDirectoryName) ?: run {
        clearManagedServerWorkspace(targetWorkspaceDir)
        return false
    }
    val serverDir = serversDir.findFile(sanitizeManagedServerId(serverId)) ?: run {
        clearManagedServerWorkspace(targetWorkspaceDir)
        return false
    }
    clearManagedServerWorkspace(targetWorkspaceDir)
    copyDocumentTreeToPath(context, serverDir, targetWorkspaceDir)
    return true
}

fun resolveManagedServerWorkspaceDirectory(
    filesDir: Path,
    authorizedServersRoot: Path?,
    serverId: String,
): Path = authorizedServersRoot
    ?.resolve(sanitizeManagedServerId(serverId))
    ?: managedPaperServerDirectory(filesDir, serverId)

internal fun managedServerWorkspaceReadyMarker(workspaceDir: Path): Path =
    workspaceDir.resolve(ManagedServerWorkspaceReadyMarkerName)

internal fun writeManagedServerWorkspaceReadyMarker(workspaceDir: Path) {
    Files.createDirectories(workspaceDir)
    Files.write(managedServerWorkspaceReadyMarker(workspaceDir), "ready\n".toByteArray())
}

internal fun hasManagedServerWorkspaceReadyMarker(workspaceDir: Path): Boolean =
    Files.isRegularFile(managedServerWorkspaceReadyMarker(workspaceDir))

internal fun hasAuthorizedManagedServerWorkspaceReady(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
): Boolean {
    val directRoot = resolveAuthorizedServersRootPath(context, authorizedDirectoryUri)
        ?.takeIf(::canAccessAuthorizedServersRootDirectly)
    if (directRoot != null) {
        return hasManagedServerWorkspaceReadyMarker(directRoot.resolve(sanitizeManagedServerId(serverId)))
    }
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return false
    val marker = root.findFile(AuthorizedServersDirectoryName)
        ?.findFile(sanitizeManagedServerId(serverId))
        ?.findFile(ManagedServerWorkspaceReadyMarkerName)
    return marker?.isFile == true
}

internal fun resolveAuthorizedDirectoryPathFromTreeDocumentId(treeDocumentId: String): Path? = runCatching {
    resolveAuthorizedDirectoryPathFromTreeDocumentId(
        treeDocumentId = treeDocumentId,
        externalRoot = Environment.getExternalStorageDirectory().toPath(),
    )
}.getOrNull()

internal fun resolveAuthorizedDirectoryPathFromTreeDocumentId(treeDocumentId: String, externalRoot: Path): Path? {
    val externalRelativePath = when {
        treeDocumentId == "primary:" || treeDocumentId == "primary" -> ""
        treeDocumentId.startsWith("primary:") -> treeDocumentId.removePrefix("primary:")
        else -> return null
    }
    val segments = externalRelativePath.split('/').filter { it.isNotBlank() }
    if (segments.size < 2) return null
    val isAllowedRoot = segments[0] == "Android" && segments[1] in setOf("data", "media", "obb")
    if (!isAllowedRoot) return null
    return externalRoot.resolve(externalRelativePath)
}

fun resolveAuthorizedServersRootPath(context: Context, authorizedDirectoryUri: String?): Path? {
    val uri = authorizedDirectoryUri?.let(Uri::parse) ?: return null
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return null
    val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val targetRoot = resolveAuthorizedDirectoryPathFromTreeDocumentId(treeDocumentId) ?: return null
    if (!runCatching { root.exists() && root.isDirectory }.getOrDefault(false)) return null
    return targetRoot.resolve(AuthorizedServersDirectoryName)
}

private fun managedServerWorkspaceHasRecoverableData(workspaceDir: Path): Boolean {
    if (!Files.isDirectory(workspaceDir, LinkOption.NOFOLLOW_LINKS)) return false
    return Files.list(workspaceDir).use { children ->
        children.anyMatch { child -> child.fileName.toString() !in ManagedServerWorkspaceIgnoredTopLevelNames }
    }
}

private fun managedServerWorkspaceLastModifiedMillis(workspaceDir: Path): Long {
    if (!Files.exists(workspaceDir)) return Long.MIN_VALUE
    return Files.walk(workspaceDir).use { paths ->
        paths.mapToLong { path -> Files.getLastModifiedTime(path).toMillis() }
            .max()
            .orElse(Long.MIN_VALUE)
    }
}

private fun canAccessAuthorizedServersRootDirectly(authorizedServersRoot: Path): Boolean = runCatching {
    val probeDir = authorizedServersRoot.resolve(".mcgo-probe")
    Files.createDirectories(authorizedServersRoot)
    Files.createDirectories(probeDir)
    val probeFile = probeDir.resolve("write-test.tmp")
    Files.write(probeFile, "ok".toByteArray())
    Files.deleteIfExists(probeFile)
    Files.deleteIfExists(probeDir)
    true
}.getOrElse {
    false
}

fun prepareManagedServerWorkspaceForForegroundAccess(
    filesDir: Path,
    authorizedServersRoot: Path?,
    serverId: String,
): Path {
    val privateWorkspaceDir = managedPaperServerDirectory(filesDir, serverId)
    if (authorizedServersRoot == null) {
        Files.createDirectories(privateWorkspaceDir)
        return privateWorkspaceDir
    }
    val authorizedWorkspaceDir = resolveManagedServerWorkspaceDirectory(filesDir, authorizedServersRoot, serverId)
    clearManagedServerWorkspace(privateWorkspaceDir)
    if (Files.isDirectory(authorizedWorkspaceDir, LinkOption.NOFOLLOW_LINKS)) {
        copyPathToPath(authorizedWorkspaceDir, privateWorkspaceDir)
    } else {
        Files.createDirectories(privateWorkspaceDir)
    }
    return privateWorkspaceDir
}

fun releaseManagedServerWorkspaceAfterForegroundAccess(
    filesDir: Path,
    authorizedServersRoot: Path?,
    serverId: String,
) {
    val privateWorkspaceDir = managedPaperServerDirectory(filesDir, serverId)
    if (authorizedServersRoot == null || !Files.exists(privateWorkspaceDir)) return
    val authorizedWorkspaceDir = resolveManagedServerWorkspaceDirectory(filesDir, authorizedServersRoot, serverId)
    copyPathToPath(privateWorkspaceDir, authorizedWorkspaceDir)
    clearManagedServerWorkspace(privateWorkspaceDir)
}

fun discardManagedServerWorkspaceAfterForegroundAccess(
    filesDir: Path,
    authorizedServersRoot: Path?,
    serverId: String,
) {
    val privateWorkspaceDir = managedPaperServerDirectory(filesDir, serverId)
    if (!Files.exists(privateWorkspaceDir)) return
    clearManagedServerWorkspace(privateWorkspaceDir)
}

fun prepareManagedServerWorkspaceAccess(
    context: Context,
    authorizedDirectoryUri: String?,
    filesDir: Path,
    serverId: String,
): ManagedServerWorkspaceAccess {
    val privateWorkspaceDir = managedPaperServerDirectory(filesDir, serverId)
    val directAuthorizedServersRoot = resolveAuthorizedServersRootPath(context, authorizedDirectoryUri)
        ?.takeIf(::canAccessAuthorizedServersRootDirectly)
    if (directAuthorizedServersRoot != null) {
        val directWorkspaceDir = resolveManagedServerWorkspaceDirectory(filesDir, directAuthorizedServersRoot, serverId)
        val directReady = hasManagedServerWorkspaceReadyMarker(directWorkspaceDir)
        val privateRecoverable = managedServerWorkspaceHasRecoverableData(privateWorkspaceDir)
        val authorizedRecoverable = managedServerWorkspaceHasRecoverableData(directWorkspaceDir)
        val preferAuthorized = shouldPreferAuthorizedWorkspaceOverPrivate(
            privateRecoverable = privateRecoverable,
            authorizedRecoverable = authorizedRecoverable,
            authorizedReady = directReady,
            privateLastModifiedMillis = managedServerWorkspaceLastModifiedMillis(privateWorkspaceDir),
            authorizedLastModifiedMillis = managedServerWorkspaceLastModifiedMillis(directWorkspaceDir),
        )
        if (preferAuthorized || !privateRecoverable) {
            Files.createDirectories(directWorkspaceDir)
            if (authorizedRecoverable) {
                writeManagedServerWorkspaceReadyMarker(directWorkspaceDir)
            }
            return ManagedServerWorkspaceAccess(path = directWorkspaceDir, mode = ManagedServerWorkspaceMode.DirectExternal)
        }
        Files.createDirectories(privateWorkspaceDir)
        return ManagedServerWorkspaceAccess(path = privateWorkspaceDir, mode = ManagedServerWorkspaceMode.PrivatePersistentFallback)
    }
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: run {
        Files.createDirectories(privateWorkspaceDir)
        return ManagedServerWorkspaceAccess(path = privateWorkspaceDir, mode = ManagedServerWorkspaceMode.PrivatePersistentFallback)
    }
    val serverDir = root.findFile(AuthorizedServersDirectoryName)
        ?.findFile(sanitizeManagedServerId(serverId))
    val privateRecoverable = managedServerWorkspaceHasRecoverableData(privateWorkspaceDir)
    val authorizedReady = hasAuthorizedManagedServerWorkspaceReady(context, authorizedDirectoryUri, serverId)
    val authorizedRecoverable = serverDir?.isDirectory == true && serverDir.listFiles().any { child ->
        val name = child.name ?: return@any false
        name !in ManagedServerWorkspaceIgnoredTopLevelNames
    }
    val preferAuthorized = shouldPreferAuthorizedWorkspaceOverPrivate(
        privateRecoverable = privateRecoverable,
        authorizedRecoverable = authorizedRecoverable,
        authorizedReady = authorizedReady,
        privateLastModifiedMillis = managedServerWorkspaceLastModifiedMillis(privateWorkspaceDir),
        authorizedLastModifiedMillis = if (serverDir?.isDirectory == true) serverDir.lastModified() else Long.MIN_VALUE,
    )
    if (!preferAuthorized && privateRecoverable) {
        Files.createDirectories(privateWorkspaceDir)
        return ManagedServerWorkspaceAccess(path = privateWorkspaceDir, mode = ManagedServerWorkspaceMode.PrivatePersistentFallback)
    }
    clearManagedServerWorkspace(privateWorkspaceDir)
    if (serverDir?.isDirectory == true) {
        copyDocumentTreeToPath(context, serverDir, privateWorkspaceDir)
        if (authorizedRecoverable) {
            writeManagedServerWorkspaceReadyMarker(privateWorkspaceDir)
        }
    } else {
        Files.createDirectories(privateWorkspaceDir)
    }
    return ManagedServerWorkspaceAccess(path = privateWorkspaceDir, mode = ManagedServerWorkspaceMode.PrivateEphemeralMirror)
}

fun prepareManagedServerWorkspaceForForegroundAccess(
    context: Context,
    authorizedDirectoryUri: String?,
    filesDir: Path,
    serverId: String,
): Path = prepareManagedServerWorkspaceAccess(
    context = context,
    authorizedDirectoryUri = authorizedDirectoryUri,
    filesDir = filesDir,
    serverId = serverId,
).path

fun releaseManagedServerWorkspaceAfterForegroundAccess(
    context: Context,
    authorizedDirectoryUri: String?,
    filesDir: Path,
    serverId: String,
    workspaceMode: ManagedServerWorkspaceMode = ManagedServerWorkspaceMode.PrivateEphemeralMirror,
    onProgress: ((ManagedServerWorkspaceSyncProgress) -> Unit)? = null,
): Boolean {
    if (!workspaceMode.shouldSyncBack) return true
    val privateWorkspaceDir = managedPaperServerDirectory(filesDir, serverId)
    if (!Files.exists(privateWorkspaceDir)) return true
    val synced = syncManagedServerWorkspaceToAuthorizedDirectory(
        context = context,
        authorizedDirectoryUri = authorizedDirectoryUri,
        serverId = serverId,
        sourceWorkspaceDir = privateWorkspaceDir,
        onProgress = onProgress,
    )
    if (synced && workspaceMode.shouldClearPrivateWorkspaceOnSuccessfulSync) {
        clearManagedServerWorkspace(privateWorkspaceDir)
    }
    return synced
}

fun discardManagedServerWorkspaceAfterForegroundAccess(
    context: Context,
    authorizedDirectoryUri: String?,
    filesDir: Path,
    serverId: String,
    workspaceMode: ManagedServerWorkspaceMode = ManagedServerWorkspaceMode.PrivateEphemeralMirror,
): Boolean {
    if (!workspaceMode.shouldClearPrivateWorkspaceOnSuccessfulSync) return true
    discardManagedServerWorkspaceAfterForegroundAccess(
        filesDir = filesDir,
        authorizedServersRoot = resolveAuthorizedServersRootPath(context, authorizedDirectoryUri),
        serverId = serverId,
    )
    return true
}

fun syncManagedServerIconToAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
    iconPath: Path,
): Boolean {
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return false
    val serversDir = root.findFile(AuthorizedServersDirectoryName)
        ?: root.createDirectory(AuthorizedServersDirectoryName)
        ?: return false
    val targetServerDir = serversDir.findFile(sanitizeManagedServerId(serverId))
        ?: serversDir.createDirectory(sanitizeManagedServerId(serverId))
        ?: return false
    val targetFile = targetServerDir.findFile(iconPath.fileName.toString())
        ?: targetServerDir.createFile("image/png", iconPath.fileName.toString())
        ?: return false
    context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
        Files.newInputStream(iconPath).use { input -> input.copyTo(output) }
    } ?: return false
    return true
}

fun deleteManagedServerIconFromAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
    fileName: String,
) {
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return
    root.findFile(AuthorizedServersDirectoryName)
        ?.findFile(sanitizeManagedServerId(serverId))
        ?.findFile(fileName)
        ?.delete()
}

private fun writeAuthorizedManagedServerWorkspaceReady(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
): Boolean {
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return false
    val serversDir = root.findFile(AuthorizedServersDirectoryName)
        ?: root.createDirectory(AuthorizedServersDirectoryName)
        ?: return false
    val targetServerDir = serversDir.findFile(sanitizeManagedServerId(serverId))
        ?: serversDir.createDirectory(sanitizeManagedServerId(serverId))
        ?: return false
    val markerFile = targetServerDir.findFile(ManagedServerWorkspaceReadyMarkerName)
        ?: targetServerDir.createFile("application/octet-stream", ManagedServerWorkspaceReadyMarkerName)
        ?: return false
    context.contentResolver.openOutputStream(markerFile.uri, "wt")?.use { output ->
        output.write("ready\n".toByteArray())
    } ?: return false
    return true
}

private fun clearAuthorizedManagedServerWorkspaceReady(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
) {
    val directRoot = resolveAuthorizedServersRootPath(context, authorizedDirectoryUri)
        ?.takeIf(::canAccessAuthorizedServersRootDirectly)
    if (directRoot != null) {
        Files.deleteIfExists(managedServerWorkspaceReadyMarker(directRoot.resolve(sanitizeManagedServerId(serverId))))
        return
    }
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return
    root.findFile(AuthorizedServersDirectoryName)
        ?.findFile(sanitizeManagedServerId(serverId))
        ?.findFile(ManagedServerWorkspaceReadyMarkerName)
        ?.delete()
}

fun syncManagedServerWorkspaceToAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
    sourceWorkspaceDir: Path,
    onProgress: ((ManagedServerWorkspaceSyncProgress) -> Unit)? = null,
): Boolean {
    if (!Files.isDirectory(sourceWorkspaceDir, LinkOption.NOFOLLOW_LINKS)) return false
    val directAuthorizedWorkspace = resolveAuthorizedServersRootPath(context, authorizedDirectoryUri)
        ?.takeIf(::canAccessAuthorizedServersRootDirectly)
        ?.resolve(sanitizeManagedServerId(serverId))
    if (directAuthorizedWorkspace != null && sourceWorkspaceDir.normalize() == directAuthorizedWorkspace.normalize()) {
        writeManagedServerWorkspaceReadyMarker(directAuthorizedWorkspace)
        return true
    }
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return false
    val serversDir = root.findFile(AuthorizedServersDirectoryName)
        ?: root.createDirectory(AuthorizedServersDirectoryName)
        ?: return false
    val targetServerDir = serversDir.findFile(sanitizeManagedServerId(serverId))
        ?: serversDir.createDirectory(sanitizeManagedServerId(serverId))
        ?: return false
    clearAuthorizedManagedServerWorkspaceReady(context, authorizedDirectoryUri, serverId)
    return runCatching {
        val progressReporter = onProgress?.let { ManagedServerWorkspaceSyncProgressReporter(sourceWorkspaceDir, it) }
        progressReporter?.report(force = true)
        copyPathToDocumentTree(context, sourceWorkspaceDir, targetServerDir, progressReporter)
        val directAuthorizedWorkspace = resolveAuthorizedServersRootPath(context, authorizedDirectoryUri)
            ?.takeIf(::canAccessAuthorizedServersRootDirectly)
            ?.resolve(sanitizeManagedServerId(serverId))
        if (directAuthorizedWorkspace != null) {
            writeManagedServerWorkspaceReadyMarker(directAuthorizedWorkspace)
        } else {
            check(writeAuthorizedManagedServerWorkspaceReady(context, authorizedDirectoryUri, serverId))
        }
        true
    }.getOrDefault(false)
}

fun migratePrivateServerDataToAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    filesDir: Path,
    serverIds: List<String>,
): Set<String> {
    val migratedServerIds = mutableSetOf<String>()
    serverIds.distinct().forEach { serverId ->
        var workspaceSynced = true
        val workspace = managedPaperServerDirectory(filesDir, serverId)
        if (Files.exists(workspace)) {
            workspaceSynced = syncManagedServerWorkspaceToAuthorizedDirectory(
                context = context,
                authorizedDirectoryUri = authorizedDirectoryUri,
                serverId = serverId,
                sourceWorkspaceDir = workspace,
            )
        }
        val icon = managedPaperServerIconFile(filesDir, serverId)
        val iconSynced = if (Files.isRegularFile(icon)) {
            syncManagedServerIconToAuthorizedDirectory(
                context = context,
                authorizedDirectoryUri = authorizedDirectoryUri,
                serverId = serverId,
                iconPath = icon,
            )
        } else {
            true
        }
        if (workspaceSynced && iconSynced) {
            migratedServerIds += serverId
        }
    }
    return migratedServerIds
}

fun deleteManagedServerWorkspaceFromAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
) {
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return
    root.findFile(AuthorizedServersDirectoryName)
        ?.findFile(sanitizeManagedServerId(serverId))
        ?.delete()
}

private fun authorizedManagedServerWorkspaceDocumentFile(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
): DocumentFile? {
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return null
    val serversDir = root.findFile(AuthorizedServersDirectoryName)
        ?: root.createDirectory(AuthorizedServersDirectoryName)
        ?: return null
    return serversDir.findFile(sanitizeManagedServerId(serverId))
        ?: serversDir.createDirectory(sanitizeManagedServerId(serverId))
}

private fun unzipManagedServerArchiveToDocumentTree(
    context: Context,
    archiveInput: InputStream,
    targetServerDir: DocumentFile,
    archiveTotalBytes: Long? = null,
    onProgress: ((ManagedServerArchiveExtractionSummary) -> Unit)? = null,
): AuthorizedModpackExtractionResult {
    val entryNames = mutableListOf<String>()
    val sha256ByEntryName = mutableMapOf<String, String>()
    val setupScriptNames = mutableListOf<String>()
    var fileCount = 0
    var directoryCount = 0
    var totalBytes = 0L
    var skippedReservedEntryCount = 0
    var hasReportedExtractionProgress = false
    var lastReportedBytes = 0L
    var lastReportedEntryCount = 0
    var lastReportedArchiveBytes = 0L
    var archiveBytesRead = 0L
    val countingInput = CountingInputStream(archiveInput)
    val extractionStartedAtNanos = System.nanoTime()
    fun elapsedExtractionMillis(): Long = ((System.nanoTime() - extractionStartedAtNanos) / 1_000_000L).coerceAtLeast(1L)
    fun refreshArchiveBytesRead() {
        archiveBytesRead = maxOf(archiveBytesRead, countingInput.bytesRead)
    }
    fun currentSummary(): ManagedServerArchiveExtractionSummary = ManagedServerArchiveExtractionSummary(
        fileCount = fileCount,
        directoryCount = directoryCount,
        totalBytes = totalBytes,
        skippedReservedEntryCount = skippedReservedEntryCount,
        elapsedMillis = elapsedExtractionMillis(),
        archiveBytesRead = archiveBytesRead,
        archiveTotalBytes = archiveTotalBytes?.takeIf { it > 0L },
    )
    fun reportExtractionProgress() {
        refreshArchiveBytesRead()
        val entryCount = fileCount + directoryCount + skippedReservedEntryCount
        if (entryCount == 0 && totalBytes == lastReportedBytes && archiveBytesRead == lastReportedArchiveBytes) return
        val shouldReport = !hasReportedExtractionProgress ||
            (entryCount > 0 && lastReportedEntryCount == 0) ||
            entryCount - lastReportedEntryCount >= 25 ||
            totalBytes - lastReportedBytes >= ManagedServerImportCopyProgressIntervalBytes ||
            archiveBytesRead - lastReportedArchiveBytes >= ManagedServerImportCopyProgressIntervalBytes
        if (shouldReport) {
            hasReportedExtractionProgress = true
            lastReportedEntryCount = entryCount
            lastReportedBytes = totalBytes
            lastReportedArchiveBytes = archiveBytesRead
            onProgress?.invoke(currentSummary())
        }
    }
    ZipInputStream(BufferedInputStream(countingInput, ManagedServerImportBufferBytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            try {
                val normalized = normalizeAuthorizedImportEntryName(entry.name)
                if (normalized.isBlank()) continue
                if (normalized.substringAfterLast('/') in ReservedManagedServerImportEntries) {
                    skippedReservedEntryCount += 1
                    reportExtractionProgress()
                    continue
                }
                val segments = normalized.split('/').filter(String::isNotBlank)
                if (entry.isDirectory) {
                    resolveOrCreateDocumentDirectory(targetServerDir, segments)
                    directoryCount += 1
                    reportExtractionProgress()
                } else {
                    val parent = resolveOrCreateDocumentDirectory(targetServerDir, segments.dropLast(1))
                    val fileName = segments.last()
                    val targetFile = replaceOrCreateDocumentFile(parent, fileName)
                    val totalBytesBeforeEntry = totalBytes
                    val copyResult = context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
                        copyZipEntryToDocumentFile(zip, output) { entryByteCount ->
                            totalBytes = totalBytesBeforeEntry + entryByteCount
                            reportExtractionProgress()
                        }
                    } ?: error("打开授权文件输出流失败：$normalized")
                    totalBytes = totalBytesBeforeEntry + copyResult.byteCount
                    entryNames += normalized
                    sha256ByEntryName[normalized] = copyResult.sha256
                    fileCount += 1
                    reportExtractionProgress()
                    if (isImportedSetupScriptName(normalized, copyResult.contentPrefix)) {
                        setupScriptNames += normalized
                    }
                }
            } finally {
                zip.closeEntry()
            }
        }
    }
    refreshArchiveBytesRead()
    return AuthorizedModpackExtractionResult(
        entryNames = entryNames,
        sha256ByEntryName = sha256ByEntryName,
        setupScriptNames = setupScriptNames.sorted(),
        summary = ManagedServerArchiveExtractionSummary(
            fileCount = fileCount,
            directoryCount = directoryCount,
            totalBytes = totalBytes,
            skippedReservedEntryCount = skippedReservedEntryCount,
            elapsedMillis = elapsedExtractionMillis(),
            archiveBytesRead = archiveBytesRead,
            archiveTotalBytes = archiveTotalBytes?.takeIf { it > 0L },
        ),
    )
}

private data class CopiedZipEntry(
    val sha256: String,
    val contentPrefix: String,
    val byteCount: Long,
)

private fun copyZipEntryToDocumentFile(
    zip: ZipInputStream,
    output: java.io.OutputStream,
    onEntryBytesCopied: ((Long) -> Unit)? = null,
): CopiedZipEntry {
    val digest = MessageDigest.getInstance("SHA-256")
    val prefixBytes = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(ManagedServerImportBufferBytes)
    var byteCount = 0L
    while (true) {
        val read = zip.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
        output.write(buffer, 0, read)
        byteCount += read.toLong()
        onEntryBytesCopied?.invoke(byteCount)
        if (prefixBytes.size() < 512) {
            prefixBytes.write(buffer, 0, minOf(read, 512 - prefixBytes.size()))
        }
    }
    return CopiedZipEntry(
        sha256 = digest.digest().joinToString(separator = "") { byte -> "%02x".format(Locale.US, byte) },
        contentPrefix = prefixBytes.toString(Charsets.UTF_8.name()),
        byteCount = byteCount,
    )
}

private fun normalizeAuthorizedImportEntryName(rawName: String): String {
    val normalized = rawName.replace('\\', '/').trimStart('/')
    if (normalized.isBlank()) return ""
    val segments = normalized.split('/').filter(String::isNotBlank)
    require(segments.none { segment -> segment == "." || segment == ".." }) { "整合包包含越界路径：$rawName" }
    return segments.joinToString("/")
}

private fun resolveOrCreateDocumentDirectory(root: DocumentFile, segments: List<String>): DocumentFile {
    var current = root
    segments.forEach { segment ->
        val existing = current.findFile(segment)
        if (existing?.isFile == true) {
            check(existing.delete()) { "删除授权目录冲突文件失败：$segment" }
        }
        current = current.findFile(segment)
            ?: current.createDirectory(segment)
            ?: error("创建授权目录失败：$segment")
    }
    return current
}

private fun replaceOrCreateDocumentFile(parent: DocumentFile, fileName: String): DocumentFile {
    val existing = parent.findFile(fileName)
    if (existing != null) {
        check(existing.delete()) { "删除授权目录旧文件失败：$fileName" }
    }
    return parent.createFile("application/octet-stream", fileName)
        ?: error("创建授权文件失败：$fileName")
}

private fun isImportedSetupScriptName(entryName: String, contentPrefix: String): Boolean {
    val fileName = entryName.substringAfterLast('/')
    if (fileName.startsWith(".mcgo-") || fileName.contains(".mcgo-android-")) return false
    if (fileName.endsWith(".sh", ignoreCase = true)) return true
    return contentPrefix.startsWith("#!") && contentPrefix.contains("sh", ignoreCase = true)
}

private fun writeAuthorizedPayloadSha(
    context: Context,
    targetServerDir: DocumentFile,
    payloadEntryName: String,
    sha256: String,
) {
    val segments = payloadEntryName.split('/').filter(String::isNotBlank)
    if (segments.isEmpty()) return
    val parent = resolveOrCreateDocumentDirectory(targetServerDir, segments.dropLast(1))
    val shaFileName = "${segments.last()}.sha256"
    val targetFile = replaceOrCreateDocumentFile(parent, shaFileName)
    context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
        output.write("$sha256\n".toByteArray())
    } ?: error("写入授权目录校验文件失败：$shaFileName")
}

private fun clearDocumentFileChildren(directory: DocumentFile) {
    directory.listFiles().forEach { child ->
        check(child.delete()) { "删除授权目录旧文件失败：${child.name}" }
    }
}

fun deleteManagedServerWorkspaceFromPrivateDirectory(filesDir: Path, serverId: String) {
    clearManagedServerWorkspace(managedPaperServerDirectory(filesDir, serverId))
}

private fun authorizedDirectoryRoot(context: Context, authorizedDirectoryUri: String?): DocumentFile? {
    val uri = authorizedDirectoryUri?.let(Uri::parse) ?: return null
    val hasPersistedGrant = context.contentResolver.persistedUriPermissions.any { permission ->
        permission.uri == uri && permission.isReadPermission && permission.isWritePermission
    }
    if (!hasPersistedGrant) return null
    return runCatching {
        DocumentFile.fromTreeUri(context, uri)?.takeIf { it.exists() && it.isDirectory }
    }.getOrNull()
}

private class ManagedServerWorkspaceSyncProgressReporter(
    sourceDir: Path,
    private val onProgress: (ManagedServerWorkspaceSyncProgress) -> Unit,
) {
    private val totalFileCount: Int = countManagedWorkspaceRegularFiles(sourceDir)
    private var fileCount = 0
    private var totalBytes = 0L
    private var lastAttemptedFileCount = -1
    private var lastAttemptedBytes = Long.MIN_VALUE

    fun report(force: Boolean = false) {
        val shouldReport = force ||
            lastAttemptedFileCount < 0 ||
            fileCount > lastAttemptedFileCount ||
            totalBytes - lastAttemptedBytes >= ManagedServerImportCopyProgressIntervalBytes
        if (!shouldReport) return
        val progress = ManagedServerWorkspaceSyncProgress(
            fileCount = fileCount,
            totalFileCount = totalFileCount,
            totalBytes = totalBytes,
        )
        runCatching { onProgress(progress) }
        lastAttemptedFileCount = fileCount
        lastAttemptedBytes = totalBytes
    }

    fun copyRegularFile(context: Context, sourceFile: Path, targetFile: DocumentFile) {
        val buffer = ByteArray(ManagedServerImportBufferBytes)
        context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
            Files.newInputStream(sourceFile).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    totalBytes += read.toLong()
                    report()
                }
            }
        } ?: error("打开授权文件输出流失败：${sourceFile.fileName}")
        fileCount += 1
        report(force = true)
    }
}

private fun countManagedWorkspaceRegularFiles(sourceDir: Path): Int {
    if (!Files.isDirectory(sourceDir, LinkOption.NOFOLLOW_LINKS)) return 0
    Files.walk(sourceDir).use { paths ->
        return paths.filter { path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) }.count().toInt()
    }
}

private fun copyPathToDocumentTree(
    context: Context,
    sourceDir: Path,
    targetDir: DocumentFile,
    progressReporter: ManagedServerWorkspaceSyncProgressReporter? = null,
) {
    val sourceChildren = listManagedWorkspacePlainChildren(sourceDir)
    val sourceNames = sourceChildren.map { it.fileName.toString() }.toSet()
    targetDir.listFiles().forEach { existing ->
        val existingName = existing.name ?: return@forEach
        if (existingName !in sourceNames) {
            check(existing.delete()) { "删除授权目录旧文件失败：$existingName" }
        }
    }
    sourceChildren.forEach { child ->
        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
            val existingEntry = targetDir.findFile(child.fileName.toString())
            if (existingEntry?.isFile == true) {
                check(existingEntry.delete()) { "删除授权目录冲突文件失败：${child.fileName}" }
            }
            val directory = targetDir.findFile(child.fileName.toString())
                ?: targetDir.createDirectory(child.fileName.toString())
                ?: error("创建授权目录失败：${child.fileName}")
            copyPathToDocumentTree(context, child, directory, progressReporter)
        } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
            val existingFile = targetDir.findFile(child.fileName.toString())
            if (existingFile?.isDirectory == true) {
                check(existingFile.delete()) { "删除授权目录冲突目录失败：${child.fileName}" }
            }
            val targetFile = targetDir.findFile(child.fileName.toString())
                ?: targetDir.createFile("application/octet-stream", child.fileName.toString())
                ?: error("创建授权文件失败：${child.fileName}")
            if (progressReporter != null) {
                progressReporter.copyRegularFile(context, child, targetFile)
            } else {
                context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
                    Files.newInputStream(child).use { input -> input.copyTo(output) }
                } ?: error("打开授权文件输出流失败：${child.fileName}")
            }
        }
    }
}

private fun copyDocumentTreeToPath(
    context: Context,
    sourceDir: DocumentFile,
    targetDir: Path,
) {
    Files.createDirectories(targetDir)
    sourceDir.listFiles().forEach { child ->
        val target = targetDir.resolve(child.name ?: return@forEach)
        if (child.isDirectory) {
            copyDocumentTreeToPath(context, child, target)
        } else if (child.isFile) {
            target.parent?.let(Files::createDirectories)
            context.contentResolver.openInputStream(child.uri)?.use { input ->
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            } ?: error("打开授权目录输入流失败：${child.name}")
        }
    }
}

private fun copyPathToPath(sourceDir: Path, targetDir: Path) {
    if (!Files.isDirectory(sourceDir, LinkOption.NOFOLLOW_LINKS)) return
    if (Files.exists(targetDir, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(targetDir, LinkOption.NOFOLLOW_LINKS)) {
        Files.deleteIfExists(targetDir)
    }
    Files.createDirectories(targetDir)
    val sourceChildren = listManagedWorkspacePlainChildren(sourceDir)
    val sourceNames = sourceChildren.map { it.fileName.toString() }.toSet()
    Files.list(targetDir).use { existingChildren ->
        existingChildren.forEach { existing ->
            if (existing.fileName.toString() !in sourceNames) {
                deleteManagedWorkspacePathEntry(existing)
            }
        }
    }
    sourceChildren.forEach { child ->
        val target = targetDir.resolve(child.fileName.toString())
        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(target)
            }
            copyPathToPath(child, target)
        } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                clearManagedServerWorkspace(target)
            } else if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(target)
            }
            target.parent?.let(Files::createDirectories)
            Files.copy(child, target, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS)
        }
    }
}

private fun listManagedWorkspacePlainChildren(directory: Path): List<Path> = Files.list(directory).use { children ->
    children.iterator().asSequence()
        .filter { child ->
            Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)
        }
        .toList()
}

private fun deleteManagedWorkspacePathEntry(path: Path) {
    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        clearManagedServerWorkspace(path)
    } else {
        Files.deleteIfExists(path)
    }
}

private fun clearManagedServerWorkspace(targetDir: Path) {
    if (!Files.exists(targetDir, LinkOption.NOFOLLOW_LINKS)) return
    if (!Files.isDirectory(targetDir, LinkOption.NOFOLLOW_LINKS)) {
        Files.deleteIfExists(targetDir)
        return
    }
    Files.walk(targetDir).use { stream ->
        stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}
