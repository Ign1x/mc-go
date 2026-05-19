package com.mcgo.app.server

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

fun detectImportedWorldDirectory(root: Path): Path {
    if (Files.isRegularFile(root.resolve("level.dat"))) return root
    val childDirectories = Files.list(root).use { stream ->
        val results = mutableListOf<Path>()
        stream.forEach { child ->
            if (Files.isDirectory(child)) {
                results.add(child)
            }
        }
        results
    }
    val matchingChild = childDirectories.firstOrNull { Files.isRegularFile(it.resolve("level.dat")) }
    return matchingChild ?: error("导入的压缩包里没有找到可识别的世界存档（缺少 level.dat）")
}

fun exportManagedServerWorldArchive(
    context: Context,
    sourceWorldDir: Path,
    targetUri: Uri,
) {
    require(Files.isDirectory(sourceWorldDir, LinkOption.NOFOLLOW_LINKS)) { "世界存档目录不存在：$sourceWorldDir" }
    context.contentResolver.openOutputStream(targetUri, "wt")?.use { output ->
        exportManagedServerWorldArchiveToStream(sourceWorldDir, output)
    } ?: error("无法写入导出目标")
}

internal fun exportManagedServerWorldArchiveToStream(
    sourceWorldDir: Path,
    output: OutputStream,
) {
    require(Files.isDirectory(sourceWorldDir, LinkOption.NOFOLLOW_LINKS)) { "世界存档目录不存在：$sourceWorldDir" }
    ZipOutputStream(BufferedOutputStream(output)).use { zip ->
        val rootName = sourceWorldDir.fileName.toString()
        Files.walk(sourceWorldDir).use { paths ->
            paths.forEach { path ->
                if (Files.isSymbolicLink(path)) return@forEach
                val relative = sourceWorldDir.relativize(path).toString().replace('\\', '/')
                val entryName = if (relative.isBlank()) "$rootName/" else "$rootName/$relative"
                val zipEntry = ZipEntry(if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) "$entryName/" else entryName)
                zip.putNextEntry(zipEntry)
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input -> input.copyTo(zip) }
                }
                zip.closeEntry()
            }
        }
    }
}

fun importManagedServerWorldArchive(
    context: Context,
    archiveUri: Uri,
    targetWorldDir: Path,
) {
    val extractRoot = Files.createTempDirectory(context.cacheDir.toPath(), "mcgo-world-import-")
    try {
        context.contentResolver.openInputStream(archiveUri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val normalized = entry.name.replace('\\', '/').trimStart('/')
                    if (normalized.isNotBlank()) {
                        val target = extractRoot.resolve(normalized).normalize()
                        require(target.startsWith(extractRoot)) { "压缩包包含非法路径：${entry.name}" }
                        if (entry.isDirectory) {
                            Files.createDirectories(target)
                        } else {
                            target.parent?.let(Files::createDirectories)
                            Files.newOutputStream(target).use { output -> zip.copyTo(output) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("无法读取导入压缩包")
        val detectedWorldDir = detectImportedWorldDirectory(extractRoot)
        targetWorldDir.parent?.let(Files::createDirectories)
        clearDirectoryRecursively(targetWorldDir)
        copyDirectoryRecursively(detectedWorldDir, targetWorldDir)
    } finally {
        clearDirectoryRecursively(extractRoot)
    }
}

private fun clearDirectoryRecursively(targetDir: Path) {
    if (!Files.exists(targetDir)) return
    Files.walk(targetDir)
        .sorted(compareByDescending<Path> { it.nameCount })
        .forEach { path -> Files.deleteIfExists(path) }
}

private fun copyDirectoryRecursively(sourceDir: Path, targetDir: Path) {
    Files.walk(sourceDir).use { paths ->
        paths.forEach { source ->
            val relative = sourceDir.relativize(source)
            val target = targetDir.resolve(relative.toString())
            if (Files.isDirectory(source)) {
                Files.createDirectories(target)
            } else {
                target.parent?.let(Files::createDirectories)
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
