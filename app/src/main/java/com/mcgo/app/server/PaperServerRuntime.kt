package com.mcgo.app.server

import com.mcgo.app.ui.model.ServerCardState
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

private const val PaperApiBase = "https://api.papermc.io/v2/projects/paper"
const val PaperDownloadUserAgent = "MC-GO/0.2.10"

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

fun paperJarFileName(version: String): String = "paper-${validatePaperVersion(version)}.jar"

fun fetchPaperVersions(): List<String> = runCatching {
    val response = httpGet(PaperApiBase)
    parsePaperVersions(response).ifEmpty { fallbackPaperVersions() }
}.getOrElse { fallbackPaperVersions() }

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

fun requireManagedJavaHome(filesDir: Path, majorVersion: Int): Path {
    val javaHome = managedJavaHome(filesDir, majorVersion)
    if (!isRuntimeReady(filesDir, majorVersion)) {
        throw JavaRuntimeInstallException(
            "Java $majorVersion 未安装或 bin/java 不可执行，请先在设置里的 Java 管理中导入托管 JRE",
        )
    }
    return javaHome
}

fun buildPaperJvmArguments(server: ServerCardState): List<String> = listOf(
    "-Xms${(server.memoryMb / 2).coerceAtLeast(512)}M",
    "-Xmx${server.memoryMb}M",
)

fun termuxServerDirectory(serverId: String): String = "\$HOME/mc-go/servers/${sanitizeTermuxServerId(serverId)}"

fun sanitizeTermuxServerId(serverId: String): String = serverId
    .replace(Regex("[^A-Za-z0-9._-]+"), "-")
    .trim('-', '.')
    .ifBlank { "paper-server" }

fun termuxJavaInstallHint(javaMajorVersion: Int): String {
    val packageName = if (javaMajorVersion >= 21) "openjdk-21" else "openjdk-17"
    return "pkg update && pkg install $packageName"
}

fun buildTermuxPaperLaunchScript(
    server: ServerCardState,
    artifact: PaperDownloadArtifact,
): String {
    val safeServerId = sanitizeTermuxServerId(server.id)
    val jarName = paperJarFileName(server.minecraftVersion)
    val installHint = termuxJavaInstallHint(server.javaMajorVersion)
    val jvmArgs = buildPaperJvmArguments(server).joinToString(" ") { shellQuote(it) }
    return buildString {
        appendLine("set -Eeuo pipefail")
        appendLine("export PATH=\"/data/data/com.termux/files/usr/bin:\$PATH\"")
        appendLine("SERVER_ID=${shellQuote(safeServerId)}")
        appendLine("SERVER_NAME=${shellQuote(server.name)}")
        appendLine("SERVER_DIR=\"\$HOME/mc-go/servers/\$SERVER_ID\"")
        appendLine("LOG_FILE=\"\$SERVER_DIR/mcgo-latest.log\"")
        appendLine("PID_FILE=\"\$SERVER_DIR/mcgo.pid\"")
        appendLine("JAR_FILE=\"\$SERVER_DIR/$jarName\"")
        appendLine("mkdir -p \"\$SERVER_DIR\"")
        appendLine("cd \"\$SERVER_DIR\"")
        appendLine(": > \"\$LOG_FILE\"")
        appendLine("exec > >(tee -a \"\$LOG_FILE\") 2>&1")
        appendLine("echo '[MC-GO] Termux 桥接启动，避开 Android 私有目录执行限制'")
        appendLine("echo \"[MC-GO] 服务器：\$SERVER_NAME\"")
        appendLine("echo \"[MC-GO] 工作目录：\$SERVER_DIR\"")
        appendLine("if ! command -v java >/dev/null 2>&1; then")
        appendLine("  echo ${shellQuote("[MC-GO] 未找到 Termux Java，请先在 Termux 执行：$installHint")}")
        appendLine("  exit 127")
        appendLine("fi")
        appendLine("java -version || true")
        appendLine("cat > eula.txt <<'MCGO_EULA'")
        append(buildPaperEula())
        appendLine("MCGO_EULA")
        appendLine("cat > server.properties <<'MCGO_PROPERTIES'")
        append(buildServerProperties(server))
        appendLine("MCGO_PROPERTIES")
        appendLine("if [ ! -s \"\$JAR_FILE\" ]; then")
        appendLine("  echo ${shellQuote("[MC-GO] 正在下载 Paper ${artifact.version} build ${artifact.build}")}")
        appendLine("  rm -f \"\$JAR_FILE.tmp\"")
        appendLine("  if command -v curl >/dev/null 2>&1; then")
        appendLine("    curl -L --fail --connect-timeout 15 -o \"\$JAR_FILE.tmp\" ${shellQuote(artifact.downloadUrl)}")
        appendLine("  elif command -v wget >/dev/null 2>&1; then")
        appendLine("    wget -O \"\$JAR_FILE.tmp\" ${shellQuote(artifact.downloadUrl)}")
        appendLine("  else")
        appendLine("    echo '[MC-GO] Termux 缺少 curl/wget，无法下载 Paper。请在 Termux 执行：pkg update && pkg install curl'")
        appendLine("    exit 126")
        appendLine("  fi")
        appendLine("  mv \"\$JAR_FILE.tmp\" \"\$JAR_FILE\"")
        appendLine("fi")
        appendLine("echo '[MC-GO] 启动 Paper：java $jvmArgs -jar ... nogui'")
        appendLine("java $jvmArgs -jar \"\$JAR_FILE\" nogui &")
        appendLine("JAVA_PID=\$!")
        appendLine("echo \"\$JAVA_PID\" > \"\$PID_FILE\"")
        appendLine("set +e")
        appendLine("wait \"\$JAVA_PID\"")
        appendLine("EXIT_CODE=\$?")
        appendLine("set -e")
        appendLine("rm -f \"\$PID_FILE\"")
        appendLine("echo \"[MC-GO] 服务器进程已退出：\$EXIT_CODE\"")
        appendLine("exit \"\$EXIT_CODE\"")
    }
}

fun buildTermuxStopScript(serverId: String): String {
    val safeServerId = sanitizeTermuxServerId(serverId)
    return buildString {
        appendLine("set -Eeuo pipefail")
        appendLine("SERVER_DIR=\"\$HOME/mc-go/servers/$safeServerId\"")
        appendLine("PID_FILE=\"\$SERVER_DIR/mcgo.pid\"")
        appendLine("if [ -s \"\$PID_FILE\" ]; then")
        appendLine("  PID=\$(cat \"\$PID_FILE\")")
        appendLine("  echo \"[MC-GO] 正在停止服务器进程：\$PID\"")
        appendLine("  kill \"\$PID\" 2>/dev/null || true")
        appendLine("else")
        appendLine("  echo '[MC-GO] 没有找到运行中的 MC-GO 服务器 PID'")
        appendLine("fi")
    }
}

private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

fun resolveLatestPaperDownload(version: String): PaperDownloadArtifact {
    val safeVersion = validatePaperVersion(version)
    val buildsBody = httpGet("$PaperApiBase/versions/$safeVersion")
    val build = parseLatestPaperBuild(buildsBody)
    val buildBody = httpGet("$PaperApiBase/versions/$safeVersion/builds/$build")
    val downloadName = parsePaperDownloadName(buildBody)
    return PaperDownloadArtifact(
        version = safeVersion,
        build = build,
        downloadName = downloadName,
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
    downloadFile(artifact.downloadUrl, targetJar, scaledPaperDownloadProgressReporter(12, 74, onProgress))
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
