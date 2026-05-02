package com.mcgo.app.server

import com.mcgo.app.McGoUserAgent
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.recommendedJavaMajorVersion
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val PaperApiBase = "https://api.papermc.io/v2/projects/paper"
private const val DefaultProvisionablePaperVersion = "1.21.4"
val PaperDownloadUserAgent: String = McGoUserAgent

data class PreparedPaperServerFiles(
    val workDir: Path,
    val jarPath: Path,
    val eulaPath: Path,
    val serverPropertiesPath: Path,
)

data class PaperDownloadArtifact(
    val version: String,
    val build: Int,
    val downloadName: String,
    val sha256: String,
    val downloadUrl: String,
)


fun fallbackPaperVersions(): List<String> = listOf(
    "1.8.8",
    "1.9.4",
    "1.10.2",
    "1.11.2",
    "1.12.2",
    "1.13.2",
    "1.14.4",
    "1.15.2",
    "1.16.5",
    "1.17.1",
    "1.18.2",
    "1.19.4",
    "1.20.1",
    "1.20.4",
    "1.20.6",
    "1.21.1",
    "1.21.4",
)

fun parsePaperVersions(responseBody: String): List<String> =
    Regex("\\\"versions\\\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        .find(responseBody)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { body -> Regex("\\\"([^\\\"]+)\\\"").findAll(body).map { it.groupValues[1] }.toList() }
        .orEmpty()

fun parseLatestPaperBuild(responseBody: String): Int =
    Regex("\\\"builds\\\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        .find(responseBody)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { body -> Regex("\\d+").findAll(body).mapNotNull { it.value.toIntOrNull() }.maxOrNull() }
        ?: error("Paper build list is empty")

fun parsePaperDownloadName(responseBody: String): String =
    Regex("\\\"application\\\"\\s*:\\s*\\{[^}]*\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", RegexOption.DOT_MATCHES_ALL)
        .find(responseBody)
        ?.groupValues
        ?.getOrNull(1)
        ?: error("Paper download name is missing")

fun parsePaperDownloadSha256(responseBody: String): String =
    Regex("\\\"application\\\"\\s*:\\s*\\{[^}]*\\\"sha256\\\"\\s*:\\s*\\\"([A-Fa-f0-9]{64})\\\"", RegexOption.DOT_MATCHES_ALL)
        .find(responseBody)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase()
        ?: error("Paper download sha256 is missing")

fun buildPaperDownloadUrl(version: String, build: Int, downloadName: String): String {
    val safeVersion = validatePaperVersion(version)
    require(downloadName.matches(Regex("[A-Za-z0-9._-]+\\.jar"))) { "Paper download name is invalid" }
    return "$PaperApiBase/versions/$safeVersion/builds/$build/downloads/$downloadName"
}

fun validatePaperVersion(version: String): String {
    val trimmed = version.trim()
    require(trimmed.matches(Regex("[0-9]+\\.[0-9]+(\\.[0-9]+)?"))) { "Paper version is invalid: $version" }
    return trimmed
}

fun validatePaperVersionOrNull(version: String): String? = runCatching { validatePaperVersion(version) }.getOrNull()

fun paperJarFileName(version: String): String = "paper-${validatePaperVersion(version)}.jar"

fun paperJarSha256File(targetJar: Path): Path = targetJar.resolveSibling("${targetJar.fileName}.sha256")

fun filterProvisionablePaperVersions(versions: List<String>): List<String> = versions.filter { version ->
    validatePaperVersionOrNull(version) != null && recommendedJavaMajorVersion(version) in setOf(8, 11, 17, 21)
}

fun resolveProvisionablePaperVersionOptions(versions: List<String>): List<String> =
    filterProvisionablePaperVersions(versions).ifEmpty { listOf(DefaultProvisionablePaperVersion) }

fun initialProvisionablePaperVersion(versions: List<String>): String =
    resolveProvisionablePaperVersionOptions(versions).last()

fun fetchPaperVersions(): List<String> = runCatching {
    val response = httpGet(PaperApiBase)
    val versions = parsePaperVersions(response).ifEmpty { fallbackPaperVersions() }
    filterProvisionablePaperVersions(versions)
}.getOrElse { filterProvisionablePaperVersions(fallbackPaperVersions()) }

fun preparePaperServerFiles(server: ServerCardState, rootDir: Path): PreparedPaperServerFiles {
    val workDir = rootDir.resolve(server.id)
    Files.createDirectories(workDir)
    val eulaPath = workDir.resolve("eula.txt")
    val propertiesPath = workDir.resolve("server.properties")
    val jarPath = workDir.resolve(paperJarFileName(server.minecraftVersion))

    Files.write(eulaPath, buildPaperEula().toByteArray())
    Files.write(propertiesPath, buildServerProperties(server).toByteArray())
    return PreparedPaperServerFiles(
        workDir = workDir,
        jarPath = jarPath,
        eulaPath = eulaPath,
        serverPropertiesPath = propertiesPath,
    )
}

fun buildPaperEula(): String = "eula=true\n"

fun buildServerProperties(server: ServerCardState): String = buildString {
    appendLine("server-port=${server.port}")
    appendLine("level-name=${server.worldName.asServerPropertyValue()}")
    appendLine("max-players=${server.maxPlayers}")
    appendLine("motd=${server.name.asServerPropertyValue()}")
    appendLine("online-mode=true")
    appendLine("enable-command-block=true")
    appendLine("allow-flight=true")
    appendLine("view-distance=8")
    appendLine("simulation-distance=4")
}

private fun String.asServerPropertyValue(): String = trim()
    .replace('\\', '/')
    .replace('\r', ' ')
    .replace('\n', ' ')
    .replace('=', '-')
    .replace(':', '-')
    .ifBlank { "MC-GO Server" }

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
        !recordedSha256.isNullOrBlank() && recordedSha256 == sha256Hex(targetJar)
    }
}.getOrDefault(false)

fun resolveLatestPaperDownload(version: String): PaperDownloadArtifact {
    val safeVersion = validatePaperVersion(version)
    val buildsBody = httpGet("$PaperApiBase/versions/$safeVersion")
    val build = parseLatestPaperBuild(buildsBody)
    val buildBody = httpGet("$PaperApiBase/versions/$safeVersion/builds/$build")
    val downloadName = parsePaperDownloadName(buildBody)
    val sha256 = parsePaperDownloadSha256(buildBody)
    return PaperDownloadArtifact(
        version = safeVersion,
        build = build,
        downloadName = downloadName,
        sha256 = sha256,
        downloadUrl = buildPaperDownloadUrl(version, build, downloadName),
    )
}

fun downloadLatestPaperJar(
    version: String,
    targetJar: Path,
    onProgress: (Int) -> Unit = {},
) {
    onProgress(2)
    val artifact = resolveLatestPaperDownload(version)
    onProgress(8)
    Files.createDirectories(targetJar.parent)
    val tempJar = targetJar.resolveSibling("${targetJar.fileName}.part")
    Files.deleteIfExists(tempJar)
    try {
        downloadFile(artifact.downloadUrl, tempJar, scaledPaperDownloadProgressReporter(12, 74, onProgress))
        if (!Files.isRegularFile(tempJar) || Files.size(tempJar) <= 0L) error("Paper 下载失败：文件为空")
        val actualSha256 = sha256Hex(tempJar)
        if (actualSha256 != artifact.sha256.lowercase()) {
            error("Paper 下载校验失败：SHA-256 不匹配")
        }
        moveDownloadedPaperJar(tempJar, targetJar)
        Files.write(paperJarSha256File(targetJar), (artifact.sha256.lowercase() + "\n").toByteArray())
        onProgress(76)
    } catch (error: Throwable) {
        Files.deleteIfExists(tempJar)
        throw error
    }
}

fun scaledPaperDownloadProgressReporter(
    start: Int,
    end: Int,
    onProgress: (Int) -> Unit,
): (Int) -> Unit = { inner ->
    val normalized = inner.coerceIn(0, 100)
    val mapped = start + ((end - start) * normalized / 100)
    onProgress(mapped.coerceIn(start, end))
}

private fun moveDownloadedPaperJar(tempJar: Path, targetJar: Path) {
    try {
        Files.move(tempJar, targetJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(tempJar, targetJar, StandardCopyOption.REPLACE_EXISTING)
    }
}

private fun downloadFile(url: String, target: Path, onProgress: (Int) -> Unit) {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 60_000
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", PaperDownloadUserAgent)
    try {
        val statusCode = connection.responseCode
        if (statusCode !in 200..299) error("Paper 下载失败：HTTP $statusCode")
        val contentLength = connection.contentLengthLong.takeIf { it > 0L }
        connection.inputStream.use { input ->
            Files.newOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    contentLength?.let { onProgress(((copied * 100) / it).toInt()) }
                }
            }
        }
        if (contentLength == null) onProgress(100)
    } finally {
        connection.disconnect()
    }
}

private fun httpGet(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 20_000
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", PaperDownloadUserAgent)
    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
