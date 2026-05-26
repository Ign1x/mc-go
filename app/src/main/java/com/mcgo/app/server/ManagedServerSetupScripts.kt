package com.mcgo.app.server

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal val ReservedManagedServerImportEntries = setOf(
    ".mcgo-modpack-setup-approved",
    ".mcgo-modpack-setup-complete",
)

private val ManagedServerSetupDebugTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private const val MaxManagedServerSetupScriptProbeBytes = 64 * 1024
private const val MaxManagedServerSetupScriptRewriteBytes = 1024 * 1024
private const val MaxManagedServerSetupApprovalMarkerBytes = 4 * 1024

private fun isManagedServerRegularFile(path: Path): Boolean =
    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)

internal fun discoverManagedServerSetupScripts(serverWorkDir: Path): List<Path> {
    if (!Files.isDirectory(serverWorkDir, LinkOption.NOFOLLOW_LINKS)) return emptyList()
    return Files.walk(serverWorkDir, 3).use { paths ->
        paths
            .filter { path -> isManagedServerRegularFile(path) }
            .filter { path -> isManagedServerSetupScriptCandidate(path, serverWorkDir) }
            .sorted(compareBy<Path> { serverWorkDir.relativize(it).toString().replace('\\', '/') })
            .iterator()
            .asSequence()
            .toList()
    }
}

internal fun findManagedServerSetupScript(serverWorkDir: Path): Path? =
    discoverManagedServerSetupScripts(serverWorkDir).firstOrNull()

internal fun resolveManagedServerSetupScript(serverWorkDir: Path, scriptRelativePath: String): Path {
    val normalizedWorkDir = serverWorkDir.toAbsolutePath().normalize()
    val requestedRelativePath = scriptRelativePath.trim().replace('\\', '/')
    require(requestedRelativePath.isNotBlank()) { "整合包安装脚本路径不能为空" }
    require(!serverWorkDir.fileSystem.getPath(requestedRelativePath).isAbsolute) { "整合包安装脚本路径必须是服务器目录内的相对路径" }
    require(
        requestedRelativePath.split('/').none { segment -> segment.isBlank() || segment == "." || segment == ".." },
    ) { "整合包安装脚本路径不能包含空目录、. 或 .." }
    val script = normalizedWorkDir.resolve(requestedRelativePath).normalize()
    require(script.startsWith(normalizedWorkDir)) { "整合包安装脚本路径不能越界" }
    require(!hasSymbolicLinkComponent(normalizedWorkDir, requestedRelativePath)) { "整合包安装脚本路径不能包含符号链接" }
    require(isManagedServerRegularFile(script)) { "整合包安装脚本不存在：$requestedRelativePath" }
    require(isManagedServerSetupScriptCandidate(script, normalizedWorkDir)) { "不是可识别的整合包安装脚本：$requestedRelativePath" }
    return script
}

private fun hasSymbolicLinkComponent(baseDir: Path, relativePath: String): Boolean {
    var current = baseDir
    relativePath.split('/').forEach { segment ->
        current = current.resolve(segment).normalize()
        if (Files.isSymbolicLink(current)) return true
    }
    return false
}

private fun isManagedServerSetupScriptCandidate(script: Path, serverWorkDir: Path): Boolean {
    if (!isManagedServerRegularFile(script)) return false
    val relativeName = serverWorkDir.toAbsolutePath().normalize()
        .relativize(script.toAbsolutePath().normalize())
        .toString()
        .replace('\\', '/')
    if (relativeName.substringAfterLast('/') in ReservedManagedServerImportEntries) return false
    val fileName = script.fileName.toString()
    if (fileName.startsWith(".mcgo-") || fileName.contains(".mcgo-android-")) return false
    if (fileName.endsWith(".sh", ignoreCase = true)) return true
    val contentPrefix = readManagedServerSetupFilePrefix(script, 512).orEmpty()
    return contentPrefix.startsWith("#!") && contentPrefix.contains("sh", ignoreCase = true)
}

private fun readManagedServerSetupFilePrefix(path: Path, maxBytes: Int): String? = runCatching {
    if (!isManagedServerRegularFile(path)) return@runCatching null
    Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
        readManagedServerSetupText(channel, minOf(channel.size(), maxBytes.toLong()).toInt())
    }
}.getOrNull()

private fun readManagedServerSetupFileBounded(path: Path, maxBytes: Int): String? = runCatching {
    if (!isManagedServerRegularFile(path)) return@runCatching null
    Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
        if (channel.size() > maxBytes.toLong()) return@runCatching null
        readManagedServerSetupText(channel, channel.size().toInt())
    }
}.getOrNull()

private fun readManagedServerSetupText(channel: java.nio.channels.SeekableByteChannel, bytesToRead: Int): String {
    val buffer = ByteBuffer.allocate(bytesToRead)
    while (buffer.hasRemaining() && channel.read(buffer) > 0) {
        // Keep reading until the bounded buffer is full or EOF is reached.
    }
    return String(buffer.array(), 0, buffer.position())
}

private fun managedServerSetupScriptSha256(script: Path): String =
    Files.newInputStream(script, LinkOption.NOFOLLOW_LINKS).use(::sha256Hex)

private fun writeManagedServerMarker(marker: Path, content: String) {
    require(!Files.isSymbolicLink(marker)) { "整合包安装标记不能是符号链接：${marker.fileName}" }
    Files.createDirectories(marker.parent)
    Files.newByteChannel(
        marker,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE,
        LinkOption.NOFOLLOW_LINKS,
    ).use { channel ->
        val bytes = ByteBuffer.wrap(content.toByteArray())
        while (bytes.hasRemaining()) {
            channel.write(bytes)
        }
    }
}

internal fun isInstallerBootstrapScript(script: Path, serverWorkDir: Path): Boolean {
    if (!isManagedServerRegularFile(script)) return false
    val content = readManagedServerSetupFilePrefix(script, MaxManagedServerSetupScriptProbeBytes).orEmpty()
    if (!content.contains("-installServer", ignoreCase = true)) return false
    if (!content.contains("installer", ignoreCase = true) && !content.contains("libraries", ignoreCase = true)) return false
    return detectInstallerPackMetadata(serverWorkDir) != null
}

internal fun detectInstallerPackMetadata(serverWorkDir: Path): ImportedModpackServerMetadata? {
    val installerPattern = Regex("neoforge-(\\d+\\.\\d+\\.\\d+)-installer\\.jar", RegexOption.IGNORE_CASE)
    val neoforgeInstaller = Files.list(serverWorkDir).use { children ->
        children.iterator().asSequence().firstOrNull { child ->
            Files.isRegularFile(child) && installerPattern.matches(child.fileName.toString())
        }
    } ?: return null
    val artifactVersion = installerPattern
        .find(neoforgeInstaller.fileName.toString())
        ?.groupValues
        ?.getOrNull(1)
        ?: return null
    val minecraftVersion = artifactVersion.split('.').let { parts ->
        when {
            artifactVersion.startsWith("1.") -> artifactVersion
            parts.size >= 3 && parts[0].toIntOrNull() ?: 0 < 26 -> "1.${parts[0]}.${parts[1]}"
            else -> artifactVersion
        }
    }
    return ImportedModpackServerMetadata(
        serverType = com.mcgo.app.ui.model.MinecraftServerType.NeoForge,
        minecraftVersion = minecraftVersion,
        javaMajorVersion = com.mcgo.app.ui.model.recommendedJavaMajorVersion(minecraftVersion),
    )
}

internal fun managedServerSetupApprovalMarker(serverWorkDir: Path): Path =
    serverWorkDir.resolve(".mcgo-modpack-setup-approved")

internal fun managedServerSetupCompletionMarker(serverWorkDir: Path): Path =
    serverWorkDir.resolve(".mcgo-modpack-setup-complete")

private data class ManagedServerSetupApprovalRecord(
    val scriptRelativePath: String,
    val scriptSha256: String,
)

internal data class ImportedModpackServerMetadata(
    val serverType: com.mcgo.app.ui.model.MinecraftServerType,
    val minecraftVersion: String,
    val javaMajorVersion: Int,
)

private fun readManagedServerSetupApprovalRecord(serverWorkDir: Path): ManagedServerSetupApprovalRecord? {
    val marker = managedServerSetupApprovalMarker(serverWorkDir)
    val values = readManagedServerSetupFileBounded(marker, MaxManagedServerSetupApprovalMarkerBytes)
        ?.lineSequence()
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.toList()
        ?: return null
    val relativePath = values.getOrNull(0) ?: return null
    val sha256 = values.getOrNull(1)?.lowercase() ?: return null
    return ManagedServerSetupApprovalRecord(relativePath, sha256)
}

internal fun approvedManagedServerSetupScript(serverWorkDir: Path): Path? {
    val approval = readManagedServerSetupApprovalRecord(serverWorkDir) ?: return null
    val script = runCatching {
        resolveManagedServerSetupScript(serverWorkDir, approval.scriptRelativePath)
    }.getOrNull() ?: return null
    return script.takeIf { approval.scriptSha256 == managedServerSetupScriptSha256(it) }
}

private fun matchesManagedServerSetupApproval(serverWorkDir: Path, script: Path): Boolean {
    val approval = readManagedServerSetupApprovalRecord(serverWorkDir) ?: return false
    val relativePath = serverWorkDir.toAbsolutePath().normalize()
        .relativize(script.toAbsolutePath().normalize())
        .toString()
        .replace('\\', '/')
    return approval.scriptRelativePath == relativePath && approval.scriptSha256 == managedServerSetupScriptSha256(script)
}

internal fun requiresManagedServerSetupApproval(serverWorkDir: Path): Path? {
    val completionMarker = managedServerSetupCompletionMarker(serverWorkDir)
    if (!Files.isSymbolicLink(completionMarker) &&
        Files.isRegularFile(completionMarker, LinkOption.NOFOLLOW_LINKS)
    ) return null
    readManagedServerSetupApprovalRecord(serverWorkDir)?.let { approval ->
        val script = runCatching {
            resolveManagedServerSetupScript(serverWorkDir, approval.scriptRelativePath)
        }.getOrNull()
        if (script != null) {
            return script.takeUnless { matchesManagedServerSetupApproval(serverWorkDir, it) }
        }
    }
    return discoverManagedServerSetupScripts(serverWorkDir).firstOrNull()
}

internal fun approveManagedServerSetupScript(serverWorkDir: Path, scriptRelativePath: String) {
    val script = resolveManagedServerSetupScript(serverWorkDir, scriptRelativePath)
    val relativePath = serverWorkDir.toAbsolutePath().normalize()
        .relativize(script.toAbsolutePath().normalize())
        .toString()
        .replace('\\', '/')
    writeManagedServerMarker(
        managedServerSetupApprovalMarker(serverWorkDir),
        "$relativePath\n${managedServerSetupScriptSha256(script)}\n",
    )
}

fun runManagedServerSetupScriptIfNeeded(
    serverWorkDir: Path,
    targetJar: Path = serverWorkDir.resolve("server.jar"),
    shellBinary: String = "/system/bin/sh",
    environment: List<String> = emptyList(),
    logFile: Path = serverWorkDir.resolve("logs/mcgo-latest.log"),
    onOutputLine: ((String) -> Unit)? = null,
): Boolean {
    fun appendSetupDebugLine(message: String, details: Map<String, Any?> = emptyMap()) {
        val normalizedDetails = details.entries
            .asSequence()
            .filter { (_, value) -> value != null }
            .joinToString(separator = " ") { (key, value) -> "$key=$value" }
            .trim()
        val prefix = "[debug] ${ManagedServerSetupDebugTimestampFormatter.format(LocalDateTime.now())} $message"
        val rendered = if (normalizedDetails.isBlank()) prefix else "$prefix | $normalizedDetails"
        Files.createDirectories(logFile.parent)
        Files.write(
            logFile,
            "$rendered\n".toByteArray(),
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
    }
    val marker = managedServerSetupCompletionMarker(serverWorkDir)
    check(!Files.isSymbolicLink(marker)) { "整合包安装标记不能是符号链接：${marker.fileName}" }
    if (Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) return false
    val script = approvedManagedServerSetupScript(serverWorkDir)
        ?: discoverManagedServerSetupScripts(serverWorkDir).firstOrNull()?.let { detectedScript ->
            check(matchesManagedServerSetupApproval(serverWorkDir, detectedScript)) {
                "检测到整合包安装脚本 ${serverWorkDir.toAbsolutePath().normalize().relativize(detectedScript.toAbsolutePath().normalize()).toString().replace('\\', '/')}，请先输入并确认整合包安装脚本后再启动"
            }
            detectedScript
        }
        ?: return false
    script.toFile().setExecutable(true, false)
    val baseEnvironment = environment.associate { entry ->
        val separator = entry.indexOf('=')
        if (separator <= 0) entry to "" else entry.substring(0, separator) to entry.substring(separator + 1)
    }
    val scriptEnvironment = baseEnvironment.toMutableMap().apply {
        if (isInstallerBootstrapScript(script, serverWorkDir)) {
            put("ATM10_INSTALL_ONLY", "true")
            put("ATM10_RESTART", "false")
        }
    }
    val generatedBootstrapScript = rewriteManagedInstallerBootstrapScriptForAndroid(script, scriptEnvironment)
    val effectiveScript = generatedBootstrapScript ?: script
    try {
        appendSetupDebugLine(
            "准备执行整合包安装脚本",
            mapOf(
                "script" to script.fileName,
                "effectiveScript" to effectiveScript.fileName,
                "workingDirectory" to serverWorkDir,
                "environmentSize" to scriptEnvironment.size,
            ),
        )
        Files.createDirectories(logFile.parent)
        val process = ProcessBuilder(shellBinary, effectiveScript.toString())
            .directory(serverWorkDir.toFile())
            .redirectErrorStream(true)
            .apply {
                environment().putAll(scriptEnvironment)
            }
            .start()
        Files.newOutputStream(
            logFile,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        ).bufferedWriter().use { logWriter ->
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine.trimEnd()
                    if (line.isBlank()) return@forEach
                    logWriter.appendLine(line)
                    logWriter.flush()
                    onOutputLine?.invoke(line)
                }
            }
        }
        val exitCode = process.waitFor()
        appendSetupDebugLine(
            "整合包安装脚本执行完成",
            mapOf(
                "script" to script.fileName,
                "exitCode" to exitCode,
                "targetJar" to targetJar.fileName,
            ),
        )
        require(exitCode == 0) { "整合包安装脚本执行失败：${script.fileName} (exit=$exitCode)" }
        writeManagedServerPayloadSha(serverWorkDir, targetJar)
        writeManagedServerMarker(marker, "done\n")
        return true
    } finally {
        if (generatedBootstrapScript != null) {
            runCatching { Files.deleteIfExists(generatedBootstrapScript) }
        }
    }
}


private fun rewriteManagedInstallerBootstrapScriptForAndroid(
    script: Path,
    environment: Map<String, String>,
): Path? {
    val appProcess = environment["MCGO_JAVA_APP_PROCESS"]?.takeIf { it.isNotBlank() } ?: return null
    val mainClass = environment["MCGO_JAVA_MAIN_CLASS"]?.takeIf { it.isNotBlank() } ?: return null
    val classpath = environment["MCGO_JAVA_CLASSPATH"]?.takeIf { it.isNotBlank() }
        ?: environment["CLASSPATH"]?.takeIf { it.isNotBlank() }
        ?: return null
    val javaHome = environment["MCGO_JAVA_HOME"]?.takeIf { it.isNotBlank() } ?: return null
    val launcherLib = environment["MCGO_JAVA_NATIVE_LAUNCHER_LIB"]?.takeIf { it.isNotBlank() } ?: return null
    val original = readManagedServerSetupFileBounded(script, MaxManagedServerSetupScriptRewriteBytes) ?: return null
    val tmpDir = environment["TMPDIR"]?.takeIf { it.isNotBlank() }
    val userHome = environment["HOME"]?.takeIf { it.isNotBlank() }
    val managedJavaCommand = buildString {
        append("CLASSPATH=")
        append(shellSingleQuote(classpath))
        append(' ')
        append(shellSingleQuote(appProcess))
        append(" -Dmcgo.paperJvmLauncher.absoluteLibPath=")
        append(shellSingleQuote(launcherLib))
        tmpDir?.let { value ->
            append(" -Djava.io.tmpdir=")
            append(shellSingleQuote(value))
        }
        userHome?.let { value ->
            append(" -Duser.home=")
            append(shellSingleQuote(value))
        }
        append(" /system/bin ")
        append(shellSingleQuote(mainClass))
        append(' ')
        append(shellSingleQuote(javaHome))
    }
    val commandProbe = "command -v \"${'$'}{ATM10_JAVA:-java}\" >/dev/null 2>&1"
    val invocationToken = "\"${'$'}{ATM10_JAVA:-java}\""
    val rewritten = original
        .replace(commandProbe, "command -v ${shellSingleQuote(appProcess)} >/dev/null 2>&1")
        .replace(invocationToken, managedJavaCommand)
    if (rewritten == original) return null
    val tempScript = Files.createTempFile(script.parent, script.fileName.toString() + ".mcgo-android-", ".sh")
    Files.write(tempScript, rewritten.toByteArray())
    return tempScript
}

private fun shellSingleQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
