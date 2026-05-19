package com.mcgo.app.server

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val ManagedServerIconFileName = "server-icon.png"

fun managedPaperServerIconFile(filesDir: Path, serverId: String): Path =
    managedPaperServerDirectory(filesDir, serverId).resolve(ManagedServerIconFileName)

fun writeManagedServerIcon(filesDir: Path, serverId: String, pngBytes: ByteArray) {
    val iconFile = managedPaperServerIconFile(filesDir, serverId)
    Files.createDirectories(iconFile.parent)
    val tempFile = iconFile.resolveSibling("${ManagedServerIconFileName}.tmp")
    Files.write(tempFile, pngBytes)
    try {
        Files.move(tempFile, iconFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(tempFile, iconFile, StandardCopyOption.REPLACE_EXISTING)
    }
}

fun deleteManagedServerIcon(filesDir: Path, serverId: String) {
    Files.deleteIfExists(managedPaperServerIconFile(filesDir, serverId))
}
