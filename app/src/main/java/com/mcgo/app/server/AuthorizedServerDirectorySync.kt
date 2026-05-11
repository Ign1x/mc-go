package com.mcgo.app.server

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.mcgo.app.ui.storage.ServerProfileStoreGlobalLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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

internal fun shouldPersistManagedServerWorkspaceAfterLaunchAttempt(
    workspaceMode: ManagedServerWorkspaceMode,
    runtimeLaunchSubmitted: Boolean,
): Boolean = workspaceMode.shouldSyncBack && runtimeLaunchSubmitted

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
    if (!Files.isDirectory(workspaceDir)) return false
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
    if (Files.isDirectory(authorizedWorkspaceDir)) {
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
): Boolean {
    if (!workspaceMode.shouldSyncBack) return true
    val privateWorkspaceDir = managedPaperServerDirectory(filesDir, serverId)
    if (!Files.exists(privateWorkspaceDir)) return true
    val synced = syncManagedServerWorkspaceToAuthorizedDirectory(
        context = context,
        authorizedDirectoryUri = authorizedDirectoryUri,
        serverId = serverId,
        sourceWorkspaceDir = privateWorkspaceDir,
    )
    if (synced && workspaceMode.shouldClearPrivateWorkspaceOnSuccessfulSync) {
        clearManagedServerWorkspace(privateWorkspaceDir)
    }
    return synced
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
): Boolean {
    if (!Files.isDirectory(sourceWorkspaceDir)) return false
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
        copyPathToDocumentTree(context, sourceWorkspaceDir, targetServerDir)
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

private fun copyPathToDocumentTree(
    context: Context,
    sourceDir: Path,
    targetDir: DocumentFile,
) {
    val sourceNames = Files.list(sourceDir).use { children ->
        children.map { it.fileName.toString() }.toArray().map { it as String }.toSet()
    }
    targetDir.listFiles().forEach { existing ->
        val existingName = existing.name ?: return@forEach
        if (existingName !in sourceNames) {
            check(existing.delete()) { "删除授权目录旧文件失败：$existingName" }
        }
    }
    Files.list(sourceDir).use { children ->
        children.forEach { child ->
            if (Files.isDirectory(child)) {
                val existingEntry = targetDir.findFile(child.fileName.toString())
                if (existingEntry?.isFile == true) {
                    check(existingEntry.delete()) { "删除授权目录冲突文件失败：${child.fileName}" }
                }
                val directory = targetDir.findFile(child.fileName.toString())
                    ?: targetDir.createDirectory(child.fileName.toString())
                    ?: error("创建授权目录失败：${child.fileName}")
                copyPathToDocumentTree(context, child, directory)
            } else if (Files.isRegularFile(child)) {
                val existingFile = targetDir.findFile(child.fileName.toString())
                if (existingFile?.isDirectory == true) {
                    check(existingFile.delete()) { "删除授权目录冲突目录失败：${child.fileName}" }
                }
                val targetFile = targetDir.findFile(child.fileName.toString())
                    ?: targetDir.createFile("application/octet-stream", child.fileName.toString())
                    ?: error("创建授权文件失败：${child.fileName}")
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
    Files.createDirectories(targetDir)
    val sourceNames = Files.list(sourceDir).use { children ->
        children.map { it.fileName.toString() }.toArray().map { it as String }.toSet()
    }
    Files.list(targetDir).use { existingChildren ->
        existingChildren.forEach { existing ->
            if (existing.fileName.toString() !in sourceNames) {
                if (Files.isDirectory(existing)) {
                    clearManagedServerWorkspace(existing)
                } else {
                    Files.deleteIfExists(existing)
                }
            }
        }
    }
    Files.list(sourceDir).use { children ->
        children.forEach { child ->
            val target = targetDir.resolve(child.fileName.toString())
            if (Files.isDirectory(child)) {
                if (Files.isRegularFile(target)) {
                    Files.deleteIfExists(target)
                }
                copyPathToPath(child, target)
            } else if (Files.isRegularFile(child)) {
                if (Files.isDirectory(target)) {
                    clearManagedServerWorkspace(target)
                }
                target.parent?.let(Files::createDirectories)
                Files.copy(child, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

private fun clearManagedServerWorkspace(targetDir: Path) {
    if (!Files.exists(targetDir)) return
    Files.walk(targetDir).use { stream ->
        stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}
