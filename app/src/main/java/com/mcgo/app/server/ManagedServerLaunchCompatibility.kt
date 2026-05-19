package com.mcgo.app.server

import com.mcgo.app.ui.model.ServerCardState
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile

private const val BundledAndroidJnaVersion = "5.18.1"
internal const val MaxServerLibrariesListProbeBytes = 64 * 1024

private fun String.shouldIgnorePaperJavaVersionGate(): Boolean {
    val parts = split('.').mapNotNull { it.toIntOrNull() }
    val minor = parts.getOrNull(1) ?: return false
    return minor in 12..16
}

private fun String.requiresPaperJavaVersionBypassForModernPaper(): Boolean {
    val parts = split('.').mapNotNull { it.toIntOrNull() }
    val minor = parts.getOrNull(1) ?: return false
    return minor >= 20
}

fun requireManagedJavaHome(filesDir: Path, majorVersion: Int): Path {
    val javaHome = managedJavaHome(filesDir, majorVersion)
    if (!isRuntimeReady(filesDir, majorVersion)) {
        throw JavaRuntimeInstallException(
            "Java $majorVersion 未安装或 bin/java 不可执行，请先在设置里的 Java 管理中导入托管 JRE",
        )
    }
    return javaHome
}

private fun managedRuntimeRequiresPaperJavaVersionBypass(javaHome: Path): Boolean {
    val releaseProperties = readReleaseProperties(javaHome)
    val javaVersion = releaseProperties["JAVA_VERSION"]
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val runtimeVersion = releaseProperties["JAVA_RUNTIME_VERSION"]
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return false

    if (!javaVersion.isNullOrBlank()) {
        if (runtimeVersion == javaVersion) return false
        if (runtimeVersion.startsWith("$javaVersion-")) return true
        if (runtimeVersion.startsWith("$javaVersion+")) return false
    }

    return runtimeVersion.substringBefore('+').contains('-')
}

fun buildPaperJvmArguments(server: ServerCardState, javaHome: Path? = null): List<String> = buildList {
    add("-Xms${(server.memoryMb / 2).coerceAtLeast(512)}M")
    add("-Xmx${server.memoryMb}M")
    if (server.javaMajorVersion >= 9) {
        add("-Djdk.lang.Process.launchMechanism=FORK")
    }
    if (server.javaMajorVersion >= 17 && server.minecraftVersion.shouldIgnorePaperJavaVersionGate()) {
        add("-DPaper.IgnoreJavaVersion=true")
    }
    if (
        server.javaMajorVersion >= 21 &&
            server.minecraftVersion.requiresPaperJavaVersionBypassForModernPaper() &&
            javaHome != null &&
            managedRuntimeRequiresPaperJavaVersionBypass(javaHome)
    ) {
        add("-DPaper.IgnoreJavaVersion=true")
    }
}

fun shouldReusePaperJar(targetJar: Path): Boolean = runCatching {
    if (!Files.isRegularFile(targetJar) || Files.size(targetJar) <= 0L) {
        false
    } else {
        val sha256File = paperJarSha256File(targetJar)
        val recordedSha256 = if (Files.isRegularFile(sha256File)) {
            String(Files.readAllBytes(sha256File)).lineSequence().firstOrNull()?.trim()?.lowercase()
        } else {
            null
        }
        !recordedSha256.isNullOrBlank() &&
            recordedSha256 == sha256Hex(targetJar) &&
            readServerJnaVersion(targetJar).let { serverJnaVersion ->
                serverJnaVersion == null || isBundledAndroidJnaVersionCompatible(serverJnaVersion)
            }
    }
}.getOrDefault(false)

fun shouldReuseInstalledServerPayload(serverWorkDir: Path, targetJar: Path): Boolean {
    val payloadJar = resolveInstalledPayloadJar(serverWorkDir, targetJar) ?: return false
    if (!hasManagedServerLaunchPrerequisites(serverWorkDir, targetJar)) return false
    return shouldReusePaperJar(payloadJar)
}

fun detectServerJnaVersion(serverJar: Path): String? = runCatching {
    readServerJnaVersion(serverJar)
}.getOrNull()

private fun readServerJnaVersion(serverJar: Path): String? = try {
    ZipFile(serverJar.toFile()).use { zip ->
        val librariesEntry = zip.getEntry("META-INF/libraries.list") ?: return@use null
        zip.getInputStream(librariesEntry).use { input ->
            parseServerJnaVersionFromLibrariesList(
                readZipEntryTextBounded(input, MaxServerLibrariesListProbeBytes),
            )
        }
    }
} catch (_: ZipException) {
    null
}

private fun parseServerJnaVersionFromLibrariesList(librariesList: String): String? {
    var lineStart = 0
    while (lineStart <= librariesList.length) {
        val newlineIndex = librariesList.indexOf('\n', startIndex = lineStart)
        val lineEnd = if (newlineIndex < 0) librariesList.length else newlineIndex
        extractServerJnaVersionFromLibrariesListLine(librariesList.substring(lineStart, lineEnd))?.let { return it }
        if (lineEnd >= librariesList.length) break
        lineStart = lineEnd + 1
    }
    return null
}

private fun extractServerJnaVersionFromLibrariesListLine(line: String): String? =
    line.trimEnd('\r')
        .split('\t')
        .getOrNull(1)
        ?.takeIf { it.startsWith("net.java.dev.jna:jna:") }
        ?.substringAfterLast(':')

private fun readZipEntryTextBounded(input: InputStream, maxBytes: Int): String {
    val limit = maxBytes.coerceAtLeast(1)
    val buffer = ByteArrayOutputStream(limit + 1)
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = limit + 1
    while (remaining > 0) {
        val read = input.read(chunk, 0, minOf(chunk.size, remaining))
        if (read <= 0) break
        buffer.write(chunk, 0, read)
        remaining -= read
    }
    if (buffer.size() > limit) {
        throw JavaRuntimeInstallException("服务端 libraries.list 元数据过大")
    }
    return buffer.toByteArray().toString(Charsets.UTF_8)
}

fun isBundledAndroidJnaCompatibleWithServerJar(serverJar: Path): Boolean {
    val serverJnaVersion = detectServerJnaVersion(serverJar) ?: return true
    return isBundledAndroidJnaVersionCompatible(serverJnaVersion)
}

fun validateBundledAndroidJnaCompatibility(server: ServerCardState, serverJar: Path) {
    val serverJnaVersion = readServerJnaVersion(serverJar) ?: return
    if (!isBundledAndroidJnaVersionCompatible(serverJnaVersion)) {
        throw JavaRuntimeInstallException(
            "${server.name} 依赖 JNA $serverJnaVersion，但当前 MC-GO 内置 Android JNA 为 $BundledAndroidJnaVersion；请更新 MC-GO 后再启动该服务端",
        )
    }
}

fun validateBundledAndroidJnaCompatibilityForLaunchTarget(server: ServerCardState, serverWorkDir: Path, targetJar: Path) {
    val payloadJar = resolveInstalledPayloadJar(serverWorkDir, targetJar) ?: targetJar
    validateBundledAndroidJnaCompatibility(server, payloadJar)
}

private fun hasManagedServerLaunchPrerequisites(serverWorkDir: Path, targetJar: Path): Boolean {
    val targetName = targetJar.fileName.toString().lowercase()
    return when {
        targetName.startsWith("forge-") -> resolveInstalledForgeUnixArgsRelativePath(serverWorkDir, targetName.removePrefix("forge-").removeSuffix(".jar")) != null
        targetName.startsWith("neoforge-") -> resolveInstalledNeoForgeUnixArgsRelativePath(serverWorkDir, targetName.removePrefix("neoforge-").removeSuffix(".jar")) != null
        targetName.startsWith("quilt-") -> Files.isRegularFile(serverWorkDir.resolve("quilt-server-launch.jar"))
        Files.isRegularFile(targetJar) && !isInstalledPayloadMarkerFile(targetJar) -> true
        targetName.startsWith("fabric-") -> Files.isRegularFile(serverWorkDir.resolve("fabric-server-launch.jar")) || Files.isRegularFile(serverWorkDir.resolve("server.jar"))
        else -> true
    }
}

private fun isBundledAndroidJnaVersionCompatible(serverJnaVersion: String): Boolean {
    val bundled = parseSemverLikeVersion(BundledAndroidJnaVersion) ?: return false
    val required = parseSemverLikeVersion(serverJnaVersion) ?: return false
    return bundled.first == required.first && bundled.second >= required.second
}

private fun parseSemverLikeVersion(version: String): Pair<Int, Int>? {
    val parts = version.trim().split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return major to minor
}
