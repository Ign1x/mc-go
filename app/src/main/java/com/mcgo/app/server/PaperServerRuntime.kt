package com.mcgo.app.server

import com.mcgo.app.ui.model.ServerCardState
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

private const val PaperApiBase = "https://api.papermc.io/v2/projects/paper"
const val PaperDownloadUserAgent = "MC-GO/0.2.9"

data class PreparedPaperServerFiles(
    val workDir: Path,
    val jarPath: Path,
    val eulaPath: Path,
    val serverPropertiesPath: Path,
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

fun buildPaperDownloadUrl(version: String, build: Int, downloadName: String): String =
    "$PaperApiBase/versions/$version/builds/$build/downloads/$downloadName"

fun fetchPaperVersions(): List<String> = runCatching {
    val response = httpGet(PaperApiBase)
    parsePaperVersions(response).ifEmpty { fallbackPaperVersions() }
}.getOrElse { fallbackPaperVersions() }

fun preparePaperServerFiles(server: ServerCardState, rootDir: Path): PreparedPaperServerFiles {
    val workDir = rootDir.resolve(server.id)
    Files.createDirectories(workDir)
    val eulaPath = workDir.resolve("eula.txt")
    val propertiesPath = workDir.resolve("server.properties")
    val jarPath = workDir.resolve("paper-${server.minecraftVersion}.jar")

    Files.write(eulaPath, "eula=true\n".toByteArray())
    Files.write(
        propertiesPath,
        buildString {
            appendLine("server-port=${server.port}")
            appendLine("max-players=${server.maxPlayers}")
            appendLine("motd=${server.name}")
            appendLine("online-mode=true")
            appendLine("enable-command-block=true")
            appendLine("allow-flight=true")
            appendLine("view-distance=8")
            appendLine("simulation-distance=4")
        }.toByteArray(),
    )
    return PreparedPaperServerFiles(
        workDir = workDir,
        jarPath = jarPath,
        eulaPath = eulaPath,
        serverPropertiesPath = propertiesPath,
    )
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

fun buildJavaLaunchCommand(
    server: ServerCardState,
    preparedFiles: PreparedPaperServerFiles,
    javaHome: Path,
): List<String> = listOf(
    javaHome.resolve("bin/java").toString(),
    "-Xms${(server.memoryMb / 2).coerceAtLeast(512)}M",
    "-Xmx${server.memoryMb}M",
    "-jar",
    preparedFiles.jarPath.toString(),
    "nogui",
)

fun downloadLatestPaperJar(
    version: String,
    targetJar: Path,
    onProgress: (Int) -> Unit = {},
) {
    onProgress(2)
    val buildsBody = httpGet("$PaperApiBase/versions/$version")
    val build = parseLatestPaperBuild(buildsBody)
    onProgress(8)
    val buildBody = httpGet("$PaperApiBase/versions/$version/builds/$build")
    val downloadName = parsePaperDownloadName(buildBody)
    val downloadUrl = buildPaperDownloadUrl(version, build, downloadName)
    Files.createDirectories(targetJar.parent)
    downloadFile(downloadUrl, targetJar, scaledPaperDownloadProgressReporter(12, 74, onProgress))
    onProgress(76)
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
