package com.mcgo.app.ui.storage

import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

internal fun storePropertiesAtomically(
    storePath: Path,
    properties: Properties,
    comment: String,
) {
    val parent = storePath.parent
    if (parent != null) {
        Files.createDirectories(parent)
    }
    val tempPath = if (parent != null) {
        Files.createTempFile(parent, "${storePath.fileName}.", ".tmp")
    } else {
        Files.createTempFile("${storePath.fileName}.", ".tmp")
    }
    try {
        FileOutputStream(tempPath.toFile()).use { output ->
            properties.store(output, comment)
            output.fd.sync()
        }
        try {
            Files.move(
                tempPath,
                storePath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tempPath,
                storePath,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    } finally {
        Files.deleteIfExists(tempPath)
    }
}
