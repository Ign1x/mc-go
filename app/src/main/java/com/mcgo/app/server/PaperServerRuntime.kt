package com.mcgo.app.server

import com.mcgo.app.McGoUserAgent
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.recommendedJavaMajorVersion
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

private const val PaperApiBase = "https://api.papermc.io/v2/projects/paper"
private const val PaperDownloadsPageUrl = "https://papermc.io/downloads/paper"
private const val PurpurApiBase = "https://api.purpurmc.org/v2/purpur"
private const val FabricMetaBase = "https://meta.fabricmc.net/v2"
private const val VanillaVersionManifestUrl = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
private const val DefaultProvisionablePaperVersion = "1.21.11"
private const val BundledAndroidJnaVersion = "5.18.1"
private const val ManagedServerIconFileName = "server-icon.png"
private const val ManagedServerIconSizePx = 64
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
    "1.21.11",
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

fun paperServerJarFileName(version: String): String = "paper-${validatePaperVersion(version)}.jar"

fun vanillaServerJarFileName(version: String): String = "vanilla-${validatePaperVersion(version)}.jar"

fun purpurServerJarFileName(version: String): String = "purpur-${validatePaperVersion(version)}.jar"

fun fabricServerJarFileName(version: String): String = "fabric-${validatePaperVersion(version)}.jar"

fun paperJarSha256File(targetJar: Path): Path = targetJar.resolveSibling("${targetJar.fileName}.sha256")

fun filterProvisionablePaperVersions(versions: List<String>): List<String> = versions.filter { version ->
    validatePaperVersionOrNull(version) != null && recommendedJavaMajorVersion(version) in setOf(8, 11, 17, 21, 25)
}.distinct().sortedWith(::compareMinecraftVersions)

fun resolveProvisionablePaperVersionOptions(
    versions: List<String>,
    supportedProvisionableJavaVersions: Set<Int> = setOf(8, 11, 17, 21, 25),
): List<String> =
    filterProvisionablePaperVersions(versions)
        .filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
        .ifEmpty { listOf(DefaultProvisionablePaperVersion) }

fun initialProvisionablePaperVersion(
    versions: List<String>,
    supportedProvisionableJavaVersions: Set<Int> = setOf(8, 11, 17, 21, 25),
): String =
    resolveProvisionablePaperVersionOptions(versions, supportedProvisionableJavaVersions).last()

fun fetchPaperVersions(): List<String> = runCatching {
    val apiVersions = parsePaperVersions(httpGet(PaperApiBase))
    val pageLatest = runCatching {
        httpGet(PaperDownloadsPageUrl)
            .let(::parseLatestPaperDownloadsPageArtifact)
            ?.version
    }.getOrNull()
    val merged = (apiVersions.ifEmpty { fallbackPaperVersions() } + fallbackPaperVersions() + listOfNotNull(pageLatest)).distinct()
    filterProvisionablePaperVersions(merged)
}.getOrElse { filterProvisionablePaperVersions(fallbackPaperVersions()) }

fun fallbackVanillaVersions(): List<String> = listOf(
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
    "1.21.11",
    "26.1.2",
)

fun fetchVanillaVersions(): List<String> = runCatching {
    val manifest = httpGet(VanillaVersionManifestUrl)
    Regex("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"type\\\"\\s*:\\s*\\\"release\\\"")
        .findAll(manifest)
        .map { it.groupValues[1] }
        .toList()
        .let(::filterProvisionablePaperVersions)
        .ifEmpty { fallbackVanillaVersions() }
}.getOrElse { fallbackVanillaVersions() }

fun fallbackPurpurVersions(): List<String> = listOf(
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
    "1.21.11",
    "26.1.2",
)

fun fallbackFabricVersions(): List<String> = listOf(
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
    "1.21.11",
    "26.1.2",
)

fun filterProvisionablePurpurVersions(versions: List<String>): List<String> =
    filterProvisionablePaperVersions(versions).ifEmpty { fallbackPurpurVersions() }

fun fetchPurpurVersions(): List<String> = runCatching {
    val body = httpGet(PurpurApiBase)
    Regex("\\\"versions\\\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        .find(body)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { payload -> Regex("\\\"([^\\\"]+)\\\"").findAll(payload).map { it.groupValues[1] }.toList() }
        .orEmpty()
        .let(::filterProvisionablePurpurVersions)
}.getOrElse { fallbackPurpurVersions() }

fun fetchFabricVersions(): List<String> = runCatching {
    val versions = httpGet("$FabricMetaBase/versions/game")
        .lineSequence()
        .mapNotNull { line ->
            Regex("\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(line)?.groupValues?.getOrNull(1)
        }
        .filter { validatePaperVersionOrNull(it) != null }
        .filter { recommendedJavaMajorVersion(it) in setOf(8, 11, 17, 21, 25) }
        .toList()
        .distinct()
        .sortedWith(::compareMinecraftVersions)
    if (versions.isEmpty()) fallbackFabricVersions() else versions
}.getOrElse { fallbackFabricVersions() }

fun fetchProvisionableMinecraftVersions(): List<String> =
    (fetchVanillaVersions() + fetchPaperVersions() + fetchPurpurVersions() + fetchFabricVersions() + fallbackPaperVersions())
        .distinct()
        .let(::filterProvisionablePaperVersions)

fun preparePaperServerFiles(server: ServerCardState, rootDir: Path): PreparedPaperServerFiles {
    val workDir = rootDir.resolve(sanitizeManagedServerId(server.id))
    Files.createDirectories(workDir)
    val eulaPath = workDir.resolve("eula.txt")
    val propertiesPath = workDir.resolve("server.properties")
    val jarPath = workDir.resolve(
        when (server.serverType) {
            com.mcgo.app.ui.model.MinecraftServerType.Vanilla -> vanillaServerJarFileName(server.minecraftVersion)
            com.mcgo.app.ui.model.MinecraftServerType.Paper -> paperServerJarFileName(server.minecraftVersion)
            com.mcgo.app.ui.model.MinecraftServerType.Purpur -> purpurServerJarFileName(server.minecraftVersion)
            com.mcgo.app.ui.model.MinecraftServerType.Fabric -> fabricServerJarFileName(server.minecraftVersion)
        },
    )

    Files.write(eulaPath, buildPaperEula().toByteArray())
    Files.write(propertiesPath, buildServerProperties(server).toByteArray())
    prepareAndroidCompatibleSparkConfig(workDir, server)
    return PreparedPaperServerFiles(
        workDir = workDir,
        jarPath = jarPath,
        eulaPath = eulaPath,
        serverPropertiesPath = propertiesPath,
    )
}

fun buildPaperEula(): String = "eula=true\n"

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

private fun prepareAndroidCompatibleSparkConfig(workDir: Path, server: ServerCardState) {
    if (
        server.serverType == com.mcgo.app.ui.model.MinecraftServerType.Vanilla ||
        server.serverType == com.mcgo.app.ui.model.MinecraftServerType.Fabric
    ) return
    val sparkConfigPath = workDir.resolve("plugins/spark/config.json")
    Files.createDirectories(sparkConfigPath.parent)
    val existingConfig = sparkConfigPath
        .takeIf { Files.isRegularFile(it) }
        ?.let { String(Files.readAllBytes(it)) }
    Files.write(
        sparkConfigPath,
        mergeAndroidCompatibleSparkConfig(existingConfig).toByteArray(),
    )
}

private fun mergeAndroidCompatibleSparkConfig(existingConfig: String?): String {
    val normalized = existingConfig
        ?.takeIf { it.contains('{') && it.contains('}') }
        ?.trim()
        ?.ifBlank { null }
        ?: "{}"
    return normalized
        .let { upsertTopLevelJsonScalarProperty(it, "backgroundProfiler", "false") }
        .let { upsertTopLevelJsonScalarProperty(it, "backgroundProfilerEngine", "\"java\"") }
        .trimEnd()
        .plus("\n")
}

private fun upsertTopLevelJsonScalarProperty(json: String, key: String, rawValue: String): String {
    findTopLevelJsonValueRange(json, key)?.let { valueRange ->
        return json.replaceRange(valueRange.first, valueRange.last + 1, rawValue)
    }
    val closeBraceIndex = json.lastIndexOf('}')
    if (closeBraceIndex < 0) {
        return "{\n  \"$key\": $rawValue\n}"
    }
    val hasEntries = json.substring(0, closeBraceIndex).any { it != '{' && !it.isWhitespace() }
    val insertion = if (hasEntries) {
        ",\n  \"$key\": $rawValue\n"
    } else {
        "\n  \"$key\": $rawValue\n"
    }
    return json.replaceRange(closeBraceIndex, closeBraceIndex, insertion)
}

private fun findTopLevelJsonValueRange(json: String, key: String): IntRange? {
    var depth = 0
    var index = 0
    while (index < json.length) {
        when (val current = json[index]) {
            '{', '[' -> {
                depth += 1
                index += 1
            }
            '}', ']' -> {
                depth = (depth - 1).coerceAtLeast(0)
                index += 1
            }
            '\"' -> {
                val stringEnd = json.findJsonStringEnd(index)
                if (depth == 1) {
                    val token = json.substring(index + 1, stringEnd)
                    val afterKey = json.indexOfFirstNonWhitespace(stringEnd + 1)
                    if (token == key && afterKey in json.indices && json[afterKey] == ':') {
                        val valueStart = json.indexOfFirstNonWhitespace(afterKey + 1)
                        if (valueStart in json.indices) {
                            val valueEndExclusive = json.findJsonValueEnd(valueStart)
                            return valueStart until valueEndExclusive
                        }
                    }
                }
                index = stringEnd + 1
            }
            else -> index += 1
        }
    }
    return null
}

private fun String.indexOfFirstNonWhitespace(startIndex: Int): Int {
    for (index in startIndex until length) {
        if (!this[index].isWhitespace()) return index
    }
    return -1
}

private fun String.findJsonStringEnd(startIndex: Int): Int {
    var index = startIndex + 1
    var escaped = false
    while (index < length) {
        val current = this[index]
        if (escaped) {
            escaped = false
        } else if (current == '\\') {
            escaped = true
        } else if (current == '\"') {
            return index
        }
        index += 1
    }
    return lastIndex.coerceAtLeast(startIndex)
}

private fun String.findJsonValueEnd(startIndex: Int): Int {
    if (startIndex !in indices) return startIndex
    return when (this[startIndex]) {
        '\"' -> findJsonStringEnd(startIndex) + 1
        '{', '[' -> {
            var index = startIndex
            var depth = 0
            while (index < length) {
                when (this[index]) {
                    '\"' -> index = findJsonStringEnd(index)
                    '{', '[' -> depth += 1
                    '}', ']' -> {
                        depth -= 1
                        if (depth == 0) return index + 1
                    }
                }
                index += 1
            }
            length
        }
        else -> {
            var index = startIndex
            while (index < length && this[index] != ',' && this[index] != '}') {
                index += 1
            }
            index.coerceAtLeast(startIndex)
        }
    }
}

fun buildServerProperties(server: ServerCardState): String =
    mergeManagedServerProperties(
        managedProperties = buildManagedServerProperties(server),
        overrideProperties = server.serverPropertiesOverride,
    )

fun buildManagedServerProperties(server: ServerCardState): String = buildString {
    appendLine("server-port=${server.port}")
    appendLine("level-name=${server.worldName.asServerPropertyValue()}")
    appendLine("max-players=${server.maxPlayers}")
    appendLine("motd=${server.name.asServerPropertyValue()}")
    appendLine("gamemode=${server.gameMode.propertyValue}")
    appendLine("difficulty=${server.difficulty.propertyValue}")
    appendLine("online-mode=${server.onlineMode}")
    appendLine("pvp=${server.pvpEnabled}")
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

private val RuntimeOwnedServerPropertyKeys = setOf(
    "server-port",
)

private fun mergeManagedServerProperties(
    managedProperties: String,
    overrideProperties: String?,
): String {
    val overrides = overrideProperties
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !it.startsWith("#") }
        ?.mapNotNull { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return@mapNotNull null
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim()
            key to value
        }
        ?.filterNot { (key, _) -> key in RuntimeOwnedServerPropertyKeys }
        ?.toMap(linkedMapOf())
        .orEmpty()

    val merged = linkedMapOf<String, String>()
    managedProperties.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .forEach { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return@forEach
            val key = line.substring(0, separatorIndex).trim()
            val managedValue = line.substring(separatorIndex + 1).trim()
            merged[key] = overrides[key] ?: managedValue
        }
    overrides.forEach { (key, value) ->
        if (key !in merged) merged[key] = value
    }
    return merged.entries.joinToString(separator = "\n", postfix = "\n") { (key, value) -> "$key=$value" }
}

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
            isBundledAndroidJnaCompatibleWithServerJar(targetJar)
    }
}.getOrDefault(false)

fun detectServerJnaVersion(serverJar: Path): String? = runCatching {
    ZipFile(serverJar.toFile()).use { zip ->
        val librariesEntry = zip.getEntry("META-INF/libraries.list") ?: return@use null
        zip.getInputStream(librariesEntry)
            .bufferedReader()
            .lineSequence()
            .mapNotNull { line ->
                line.split('\t').getOrNull(1)
                    ?.takeIf { it.startsWith("net.java.dev.jna:jna:") }
                    ?.substringAfterLast(':')
            }
            .firstOrNull()
    }
}.getOrNull()

fun isBundledAndroidJnaCompatibleWithServerJar(serverJar: Path): Boolean {
    val serverJnaVersion = detectServerJnaVersion(serverJar) ?: return true
    return isBundledAndroidJnaVersionCompatible(serverJnaVersion)
}

fun validateBundledAndroidJnaCompatibility(server: ServerCardState, serverJar: Path) {
    val serverJnaVersion = detectServerJnaVersion(serverJar) ?: return
    if (!isBundledAndroidJnaVersionCompatible(serverJnaVersion)) {
        throw JavaRuntimeInstallException(
            "${server.name} 依赖 JNA $serverJnaVersion，但当前 MC-GO 内置 Android JNA 为 $BundledAndroidJnaVersion；请更新 MC-GO 后再启动该服务端",
        )
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

fun resolveLatestPaperDownload(version: String): PaperDownloadArtifact {
    val safeVersion = validatePaperVersion(version)
    val pageArtifact = runCatching {
        httpGet(PaperDownloadsPageUrl)
            .let(::parseLatestPaperDownloadsPageArtifact)
            ?.takeIf { it.version == safeVersion }
    }.getOrNull()
    if (pageArtifact != null) {
        return PaperDownloadArtifact(
            version = pageArtifact.version,
            build = pageArtifact.build,
            downloadName = pageArtifact.downloadName,
            sha256 = pageArtifact.sha256,
            downloadUrl = pageArtifact.downloadUrl,
        )
    }
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

fun resolveLatestVanillaServerDownload(version: String): Pair<String, String> {
    val safeVersion = validatePaperVersion(version)
    val manifest = httpGet(VanillaVersionManifestUrl)
    val versionMetadataUrl = Regex("\\\"id\\\"\\s*:\\s*\\\"${Regex.escape(safeVersion)}\\\"[^}]*\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", RegexOption.DOT_MATCHES_ALL)
        .find(manifest)
        ?.groupValues
        ?.getOrNull(1)
        ?: error("Vanilla 版本元数据缺失：$safeVersion")
    val versionMetadata = httpGet(versionMetadataUrl)
    val serverUrl = Regex("\\\"server\\\"\\s*:\\s*\\{[^}]*\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", RegexOption.DOT_MATCHES_ALL)
        .find(versionMetadata)
        ?.groupValues
        ?.getOrNull(1)
        ?: error("Vanilla 服务端下载地址缺失：$safeVersion")
    val serverSha1 = Regex("\\\"server\\\"\\s*:\\s*\\{[^}]*\\\"sha1\\\"\\s*:\\s*\\\"([A-Fa-f0-9]{40})\\\"", RegexOption.DOT_MATCHES_ALL)
        .find(versionMetadata)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase()
        ?: error("Vanilla 服务端 SHA-1 缺失：$safeVersion")
    return serverUrl to serverSha1
}

fun resolveLatestPurpurServerDownload(version: String): Pair<String, String> {
    val safeVersion = validatePaperVersion(version)
    val buildsBody = httpGet("$PurpurApiBase/$safeVersion")
    val latestBuild = Regex("\\\"latest\\\"\\s*:\\s*\\\"?(\\d+)\\\"?")
        .find(buildsBody)
        ?.groupValues
        ?.getOrNull(1)
        ?: error("Purpur build 缺失：$safeVersion")
    val buildDetail = httpGet("$PurpurApiBase/$safeVersion/$latestBuild")
    val md5 = Regex("\\\"md5\\\"\\s*:\\s*\\\"([A-Fa-f0-9]{32})\\\"")
        .find(buildDetail)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase()
        ?: error("Purpur MD5 缺失：$safeVersion#$latestBuild")
    return "$PurpurApiBase/$safeVersion/$latestBuild/download" to md5
}

fun downloadVanillaServerJar(
    version: String,
    targetJar: Path,
    onProgress: (Int) -> Unit = {},
) {
    onProgress(2)
    val (downloadUrl, expectedSha1) = resolveLatestVanillaServerDownload(version)
    onProgress(8)
    Files.createDirectories(targetJar.parent)
    val tempJar = targetJar.resolveSibling("${targetJar.fileName}.part")
    Files.deleteIfExists(tempJar)
    try {
        downloadFile(downloadUrl, tempJar, scaledPaperDownloadProgressReporter(12, 74, onProgress), flavorLabel = "Vanilla")
        if (!Files.isRegularFile(tempJar) || Files.size(tempJar) <= 0L) error("Vanilla 下载失败：文件为空")
        val actualSha1 = sha1Hex(tempJar)
        if (actualSha1 != expectedSha1.lowercase()) {
            error("Vanilla 下载校验失败：SHA-1 不匹配")
        }
        moveDownloadedPaperJar(tempJar, targetJar)
        Files.write(paperJarSha256File(targetJar), (sha256Hex(targetJar) + "\n").toByteArray())
        onProgress(76)
    } catch (error: Throwable) {
        Files.deleteIfExists(tempJar)
        throw error
    }
}

fun downloadPurpurServerJar(
    version: String,
    targetJar: Path,
    onProgress: (Int) -> Unit = {},
) {
    onProgress(2)
    val (downloadUrl, expectedMd5) = resolveLatestPurpurServerDownload(version)
    onProgress(8)
    Files.createDirectories(targetJar.parent)
    val tempJar = targetJar.resolveSibling("${targetJar.fileName}.part")
    Files.deleteIfExists(tempJar)
    try {
        downloadFile(downloadUrl, tempJar, scaledPaperDownloadProgressReporter(12, 74, onProgress), flavorLabel = "Purpur")
        if (!Files.isRegularFile(tempJar) || Files.size(tempJar) <= 0L) error("Purpur 下载失败：文件为空")
        val actualMd5 = md5Hex(tempJar)
        if (actualMd5 != expectedMd5.lowercase()) {
            error("Purpur 下载校验失败：MD5 不匹配")
        }
        moveDownloadedPaperJar(tempJar, targetJar)
        Files.write(paperJarSha256File(targetJar), (sha256Hex(targetJar) + "\n").toByteArray())
        onProgress(76)
    } catch (error: Throwable) {
        Files.deleteIfExists(tempJar)
        throw error
    }
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
        downloadFile(artifact.downloadUrl, tempJar, scaledPaperDownloadProgressReporter(12, 74, onProgress), flavorLabel = "Paper")
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

fun downloadFabricServerJar(
    version: String,
    targetJar: Path,
    onProgress: (Int) -> Unit = {},
) {
    val safeVersion = validatePaperVersion(version)
    onProgress(2)
    val loaderVersion = Regex("\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        .find(httpGet("$FabricMetaBase/versions/loader/$safeVersion"))
        ?.groupValues
        ?.getOrNull(1)
        ?: error("Fabric Loader 版本缺失：$safeVersion")
    onProgress(8)
    val installerVersion = Regex("\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        .find(httpGet("$FabricMetaBase/versions/installer"))
        ?.groupValues
        ?.getOrNull(1)
        ?: error("Fabric Installer 版本缺失")
    val downloadUrl = "$FabricMetaBase/versions/loader/$safeVersion/$loaderVersion/$installerVersion/server/jar"
    Files.createDirectories(targetJar.parent)
    val tempJar = targetJar.resolveSibling("${targetJar.fileName}.part")
    Files.deleteIfExists(tempJar)
    try {
        downloadFile(downloadUrl, tempJar, scaledPaperDownloadProgressReporter(12, 74, onProgress), flavorLabel = "Fabric")
        if (!Files.isRegularFile(tempJar) || Files.size(tempJar) <= 0L) error("Fabric 下载失败：文件为空")
        moveDownloadedPaperJar(tempJar, targetJar)
        Files.write(paperJarSha256File(targetJar), (sha256Hex(targetJar) + "\n").toByteArray())
        onProgress(76)
    } catch (error: Throwable) {
        Files.deleteIfExists(tempJar)
        throw error
    }
}

fun installManagedServerModFile(
    sourceFile: Path,
    serverWorkDir: Path,
    targetFileName: String = sourceFile.fileName.toString(),
): Path {
    require(Files.isRegularFile(sourceFile)) { "模组文件不存在：$sourceFile" }
    require(targetFileName.endsWith(".jar", ignoreCase = true)) { "模组文件必须是 .jar" }
    val modsDir = serverWorkDir.resolve("mods")
    Files.createDirectories(modsDir)
    val target = modsDir.resolve(targetFileName)
    Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING)
    return target
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

private fun downloadFile(url: String, target: Path, onProgress: (Int) -> Unit, flavorLabel: String = "Paper") {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 60_000
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", PaperDownloadUserAgent)
    try {
        val statusCode = connection.responseCode
        if (statusCode !in 200..299) error("${flavorLabel} 下载失败：HTTP $statusCode")
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

private fun compareMinecraftVersions(left: String, right: String): Int {
    val leftParts = left.split('.').map { it.toIntOrNull() ?: Int.MIN_VALUE }
    val rightParts = right.split('.').map { it.toIntOrNull() ?: Int.MIN_VALUE }
    val maxSize = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until maxSize) {
        val leftPart = leftParts.getOrElse(index) { -1 }
        val rightPart = rightParts.getOrElse(index) { -1 }
        if (leftPart != rightPart) return leftPart.compareTo(rightPart)
    }
    return left.compareTo(right)
}
