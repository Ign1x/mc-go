package com.mcgo.app.server

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mcgo.app.ui.storage.ServerProfileStoreGlobalLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val AuthorizedServerProfilesFileName = "server_profiles.properties"
private const val AuthorizedServersDirectoryName = "servers"

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

fun restoreManagedServerWorkspaceFromAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
    targetWorkspaceDir: Path,
): Boolean {
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return false
    val serversDir = root.findFile(AuthorizedServersDirectoryName) ?: return false
    val serverDir = serversDir.findFile(sanitizeManagedServerId(serverId)) ?: return false
    clearManagedServerWorkspace(targetWorkspaceDir)
    copyDocumentTreeToPath(context, serverDir, targetWorkspaceDir)
    return true
}

fun syncManagedServerWorkspaceToAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
    sourceWorkspaceDir: Path,
) {
    if (!Files.isDirectory(sourceWorkspaceDir)) return
    val root = authorizedDirectoryRoot(context, authorizedDirectoryUri) ?: return
    val serversDir = root.findFile(AuthorizedServersDirectoryName)
        ?: root.createDirectory(AuthorizedServersDirectoryName)
        ?: return
    val targetServerDir = serversDir.findFile(sanitizeManagedServerId(serverId))
        ?: serversDir.createDirectory(sanitizeManagedServerId(serverId))
        ?: return
    copyPathToDocumentTree(context, sourceWorkspaceDir, targetServerDir)
}

fun migratePrivateServerDataToAuthorizedDirectory(
    context: Context,
    authorizedDirectoryUri: String?,
    filesDir: Path,
    serverIds: List<String>,
) {
    serverIds.distinct().forEach { serverId ->
        val workspace = managedPaperServerDirectory(filesDir, serverId)
        if (Files.exists(workspace)) {
            syncManagedServerWorkspaceToAuthorizedDirectory(
                context = context,
                authorizedDirectoryUri = authorizedDirectoryUri,
                serverId = serverId,
                sourceWorkspaceDir = workspace,
            )
        }
    }
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
            existing.delete()
        }
    }
    Files.list(sourceDir).use { children ->
        children.forEach { child ->
            if (Files.isDirectory(child)) {
                val existingEntry = targetDir.findFile(child.fileName.toString())
                if (existingEntry?.isFile == true) {
                    existingEntry.delete()
                }
                val directory = targetDir.findFile(child.fileName.toString())
                    ?: targetDir.createDirectory(child.fileName.toString())
                    ?: return@forEach
                copyPathToDocumentTree(context, child, directory)
            } else if (Files.isRegularFile(child)) {
                val existingFile = targetDir.findFile(child.fileName.toString())
                if (existingFile?.isDirectory == true) {
                    existingFile.delete()
                }
                val targetFile = targetDir.findFile(child.fileName.toString())
                    ?: targetDir.createFile("application/octet-stream", child.fileName.toString())
                    ?: return@forEach
                context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
                    Files.newInputStream(child).use { input -> input.copyTo(output) }
                }
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
