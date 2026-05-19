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
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

private const val PaperApiBase = "https://api.papermc.io/v2/projects/paper"
private const val PaperDownloadsPageUrl = "https://papermc.io/downloads/paper"
private const val PurpurApiBase = "https://api.purpurmc.org/v2/purpur"
private const val FabricMetaBase = "https://meta.fabricmc.net/v2"
private const val QuiltMetaBase = "https://meta.quiltmc.org/v3"
private const val ForgeMavenMetadataUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"
private const val NeoForgeMavenMetadataUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
private const val VanillaVersionManifestUrl = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
private const val DefaultProvisionablePaperVersion = "1.21.11"
private const val BundledAndroidJnaVersion = "5.18.1"
private const val ManagedServerIconFileName = "server-icon.png"
private const val ManagedServerIconSizePx = 64
private val ReservedManagedServerImportEntries = setOf(
    ".mcgo-modpack-setup-approved",
    ".mcgo-modpack-setup-complete",
)
val PaperDownloadUserAgent: String = McGoUserAgent
private val ManagedServerSetupDebugTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

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

fun forgeServerJarFileName(version: String): String = "forge-${validatePaperVersion(version)}.jar"

fun neoForgeServerJarFileName(version: String): String = "neoforge-${validatePaperVersion(version)}.jar"

fun quiltServerJarFileName(version: String): String = "quilt-${validatePaperVersion(version)}.jar"

fun paperJarSha256File(targetJar: Path): Path = targetJar.resolveSibling("${targetJar.fileName}.sha256")

internal fun writeManagedServerPayloadSha(serverWorkDir: Path, targetJar: Path) {
    resolveInstalledPayloadJar(serverWorkDir, targetJar)
        ?.takeIf { Files.isRegularFile(it) }
        ?.let { payload ->
            Files.deleteIfExists(serverWorkDir.resolve("server.jar.sha256"))
            Files.write(paperJarSha256File(payload), (sha256Hex(payload) + "\n").toByteArray())
        }
}

internal fun managedServerTargetJarPath(serverWorkDir: Path, serverTypeName: String, minecraftVersion: String): Path =
    serverWorkDir.resolve(
        when (serverTypeName) {
            "Vanilla" -> vanillaServerJarFileName(minecraftVersion)
            "Paper" -> paperServerJarFileName(minecraftVersion)
            "Purpur" -> purpurServerJarFileName(minecraftVersion)
            "Fabric" -> fabricServerJarFileName(minecraftVersion)
            "Forge" -> forgeServerJarFileName(minecraftVersion)
            "NeoForge" -> neoForgeServerJarFileName(minecraftVersion)
            "Quilt" -> quiltServerJarFileName(minecraftVersion)
            else -> paperServerJarFileName(minecraftVersion)
        },
    )

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

fun fallbackVanillaVersions(): List<String> = fallbackLegacyToModernMinecraftVersions()

fun fallbackLegacyToModernMinecraftVersions(): List<String> = listOf(
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

fun fetchVanillaVersions(): List<String> = runCatching {
    val manifest = httpGet(VanillaVersionManifestUrl)
    Regex("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"type\\\"\\s*:\\s*\\\"release\\\"")
        .findAll(manifest)
        .map { it.groupValues[1] }
        .toList()
        .let(::filterProvisionablePaperVersions)
        .ifEmpty { fallbackVanillaVersions() }
}.getOrElse { fallbackVanillaVersions() }

fun fallbackPurpurVersions(): List<String> = fallbackModernMinecraftVersionsSince114()

fun fallbackModernMinecraftVersionsSince114(): List<String> = listOf(
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

fun fallbackFabricVersions(): List<String> = fallbackModernMinecraftVersionsSince114()

fun fallbackForgeVersions(): List<String> = fallbackFabricVersions()

fun fallbackNeoForgeVersions(): List<String> = fallbackFabricVersions()

fun fallbackQuiltVersions(): List<String> = fallbackFabricVersions()

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
    val candidates = Regex("\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        .findAll(httpGet("$FabricMetaBase/versions/game"))
        .map { it.groupValues[1] }
        .filter { validatePaperVersionOrNull(it) != null }
        .filter { recommendedJavaMajorVersion(it) in setOf(8, 11, 17, 21, 25) }
        .toList()
        .distinct()
    val versions = candidates.filter { version ->
        runCatching { httpGet("$FabricMetaBase/versions/loader/$version") }
            .getOrNull()
            ?.contains("\"loader\"") == true
    }.sortedWith(::compareMinecraftVersions)
    if (versions.isEmpty()) fallbackFabricVersions() else versions
}.getOrElse { fallbackFabricVersions() }

fun fetchForgeVersions(): List<String> = runCatching {
    val versions = Regex("<version>([^<]+)</version>")
        .findAll(httpGet(ForgeMavenMetadataUrl))
        .map { it.groupValues[1] }
        .mapNotNull { it.substringBefore('-').takeIf { version -> validatePaperVersionOrNull(version) != null } }
        .filter { recommendedJavaMajorVersion(it) in setOf(8, 11, 17, 21, 25) }
        .distinct()
        .sortedWith(::compareMinecraftVersions)
        .toList()
    if (versions.isEmpty()) fallbackForgeVersions() else versions
}.getOrElse { fallbackForgeVersions() }

fun fetchNeoForgeVersions(): List<String> = runCatching {
    val versions = resolveNeoForgeMinecraftVersions(
        metadataXml = httpGet(NeoForgeMavenMetadataUrl),
        availableMinecraftVersions = fetchVanillaVersions(),
    )
    if (versions.isEmpty()) fallbackNeoForgeVersions() else versions
}.getOrElse { fallbackNeoForgeVersions() }

fun fetchQuiltVersions(): List<String> = runCatching {
    val candidates = Regex("\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        .findAll(httpGet("$QuiltMetaBase/versions"))
        .map { it.groupValues[1] }
        .filter { validatePaperVersionOrNull(it) != null }
        .filter { recommendedJavaMajorVersion(it) in setOf(8, 11, 17, 21, 25) }
        .distinct()
        .toList()
    val versions = candidates.filter { version ->
        runCatching { httpGet("$QuiltMetaBase/versions/loader/$version") }
            .getOrNull()
            ?.contains("\"loader\"") == true
    }.sortedWith(::compareMinecraftVersions)
    if (versions.isEmpty()) fallbackQuiltVersions() else versions
}.getOrElse { fallbackQuiltVersions() }

fun resolveSupportedModLoaderMinecraftVersions(): Map<com.mcgo.app.ui.model.MinecraftServerType, List<String>> = mapOf(
    com.mcgo.app.ui.model.MinecraftServerType.Fabric to fetchFabricVersions(),
    com.mcgo.app.ui.model.MinecraftServerType.Forge to fetchForgeVersions(),
    com.mcgo.app.ui.model.MinecraftServerType.NeoForge to fetchNeoForgeVersions(),
    com.mcgo.app.ui.model.MinecraftServerType.Quilt to fetchQuiltVersions(),
)

fun fetchProvisionableMinecraftVersions(): List<String> =
    (fetchVanillaVersions() + fetchPaperVersions() + fetchPurpurVersions() + fetchFabricVersions() + fallbackPaperVersions())
        .distinct()
        .let(::filterProvisionablePaperVersions)

fun preparePaperServerFiles(server: ServerCardState, rootDir: Path, workDirOverride: Path? = null): PreparedPaperServerFiles {
    val workDir = workDirOverride ?: rootDir.resolve(sanitizeManagedServerId(server.id))
    Files.createDirectories(workDir)
    writeManagedServerWorkspaceReadyMarker(workDir)
    val eulaPath = workDir.resolve("eula.txt")
    val propertiesPath = workDir.resolve("server.properties")
    val jarPath = workDir.resolve(
        when (server.serverType) {
            com.mcgo.app.ui.model.MinecraftServerType.Vanilla -> vanillaServerJarFileName(server.minecraftVersion)
            com.mcgo.app.ui.model.MinecraftServerType.Paper -> paperServerJarFileName(server.minecraftVersion)
            com.mcgo.app.ui.model.MinecraftServerType.Purpur -> purpurServerJarFileName(server.minecraftVersion)
            com.mcgo.app.ui.model.MinecraftServerType.Fabric -> fabricServerJarFileName(server.minecraftVersion)
            com.mcgo.app.ui.model.MinecraftServerType.Forge -> forgeServerJarFileName(server.minecraftVersion)
            com.mcgo.app.ui.model.MinecraftServerType.NeoForge -> neoForgeServerJarFileName(server.minecraftVersion)
            com.mcgo.app.ui.model.MinecraftServerType.Quilt -> quiltServerJarFileName(server.minecraftVersion)
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

fun shouldReuseInstalledServerPayload(serverWorkDir: Path, targetJar: Path): Boolean {
    val payloadJar = resolveInstalledPayloadJar(serverWorkDir, targetJar) ?: return false
    if (!hasManagedServerLaunchPrerequisites(serverWorkDir, targetJar)) return false
    return shouldReusePaperJar(payloadJar)
}

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

fun installForgeServer(
    version: String,
    serverWorkDir: Path,
    targetJar: Path,
    javaBinary: String = "java",
    environment: List<String> = emptyList(),
    onProgress: (Int) -> Unit = {},
) {
    val artifactVersion = resolveLatestForgeArtifactVersion(version)
    val installerUrl = resolveForgeInstallerDownloadUrl(artifactVersion)
    val expectedInstallerSha256 = httpGet(resolveForgeInstallerSha256Url(artifactVersion)).lineSequence().first().trim().lowercase()
    installModdedServerViaInstaller(
        version = version,
        installerUrl = installerUrl,
        expectedInstallerSha256 = expectedInstallerSha256,
        installerFlavor = "Forge",
        serverWorkDir = serverWorkDir,
        targetJar = targetJar,
        argFileRelativePath = resolveForgeUnixArgsRelativePath(artifactVersion),
        launchJarRelativePath = null,
        javaBinary = javaBinary,
        environment = environment,
        onProgress = onProgress,
    )
}

fun installNeoForgeServer(
    version: String,
    serverWorkDir: Path,
    targetJar: Path,
    javaBinary: String = "java",
    environment: List<String> = emptyList(),
    onProgress: (Int) -> Unit = {},
) {
    val artifactVersion = resolveLatestNeoForgeArtifactVersion(version)
    val installerUrl = resolveNeoForgeInstallerDownloadUrl(artifactVersion)
    val checksumUrl = resolveNeoForgeInstallerChecksumUrl(installerUrl, httpGet(installerUrl.substringBeforeLast('/') + "/"), algorithm = "sha256")
    val expectedInstallerSha256 = httpGet(checksumUrl).lineSequence().first().trim().lowercase()
    installModdedServerViaInstaller(
        version = version,
        installerUrl = installerUrl,
        expectedInstallerSha256 = expectedInstallerSha256,
        installerFlavor = "NeoForge",
        serverWorkDir = serverWorkDir,
        targetJar = targetJar,
        argFileRelativePath = "libraries/net/neoforged/neoforge/$artifactVersion/unix_args.txt",
        launchJarRelativePath = null,
        javaBinary = javaBinary,
        environment = environment,
        onProgress = onProgress,
    )
}

fun installQuiltServer(
    version: String,
    serverWorkDir: Path,
    targetJar: Path,
    javaBinary: String = "java",
    environment: List<String> = emptyList(),
    onProgress: (Int) -> Unit = {},
) {
    val installerMetadata = httpGet("$QuiltMetaBase/versions/installer")
    val installerVersion = Regex("\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        .find(installerMetadata)
        ?.groupValues
        ?.getOrNull(1)
        ?: error("Quilt Installer 版本缺失")
    val installerUrl = Regex("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        .find(installerMetadata)
        ?.groupValues
        ?.getOrNull(1)
        ?: "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/$installerVersion/quilt-installer-$installerVersion.jar"
    val expectedInstallerSha256 = parseQuiltInstallerSha256(installerMetadata, installerVersion)
    installModdedServerViaInstaller(
        version = version,
        installerUrl = installerUrl,
        expectedInstallerSha256 = expectedInstallerSha256,
        installerFlavor = "Quilt",
        serverWorkDir = serverWorkDir,
        targetJar = targetJar,
        argFileRelativePath = null,
        launchJarRelativePath = "quilt-server-launch.jar",
        javaBinary = javaBinary,
        environment = environment,
        onProgress = onProgress,
        customInstallCommand = listOf("install", "server", version, "--install-dir=${serverWorkDir}", "--create-scripts", "--download-server"),
    )
}

fun importManagedServerModpackArchive(
    archiveFile: Path,
    serverWorkDir: Path,
    targetJar: Path = serverWorkDir.resolve("server.jar"),
    onProgress: ((Int, String) -> Unit)? = null,
): Path {
    require(Files.isRegularFile(archiveFile)) { "整合包文件不存在：$archiveFile" }
    fun reportProgress(progress: Int, message: String) {
        onProgress?.invoke(progress.coerceIn(1, 100), message)
    }
    Files.createDirectories(serverWorkDir.parent ?: serverWorkDir)
    if (shouldImportModpackDirectlyIntoTarget(serverWorkDir)) {
        try {
            reportProgress(5, "正在解压整合包到目标目录")
            unzipManagedServerArchive(archiveFile, serverWorkDir)
            writeManagedServerPayloadSha(serverWorkDir, targetJar)
            reportProgress(100, "整合包导入完成")
            return serverWorkDir
        } catch (error: Exception) {
            clearManagedServerImportTarget(serverWorkDir)
            Files.deleteIfExists(serverWorkDir)
            throw error
        }
    }

    val stagingDir = Files.createTempDirectory(serverWorkDir.parent ?: serverWorkDir, "mcgo-modpack-stage-")
    try {
        reportProgress(5, "正在解压整合包")
        unzipManagedServerArchive(archiveFile, stagingDir)
        reportProgress(35, "整合包解压完成，正在准备目标目录")
        clearManagedServerImportTarget(serverWorkDir)
        Files.createDirectories(serverWorkDir)
        val stagedPaths = Files.walk(stagingDir).use { paths ->
            paths.sorted().iterator().asSequence().toList()
        }
        val stagedEntries = stagedPaths.filter { it != stagingDir }
        val stagedFileCount = stagedEntries.count { Files.isRegularFile(it) }.coerceAtLeast(1)
        var copiedFileCount = 0
        reportProgress(45, "正在复制整合包文件")
        stagedEntries.forEach { path ->
            val relative = stagingDir.relativize(path)
            val target = serverWorkDir.resolve(relative.toString())
            if (Files.isDirectory(path)) {
                Files.createDirectories(target)
            } else {
                Files.createDirectories(target.parent)
                Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
                if (target.fileName.toString().endsWith(".sh", ignoreCase = true)) {
                    target.toFile().setExecutable(true, false)
                }
                copiedFileCount += 1
                val fileProgress = 45 + ((copiedFileCount * 50) / stagedFileCount)
                reportProgress(fileProgress, "正在复制整合包文件 · ${copiedFileCount}/$stagedFileCount")
            }
        }
        writeManagedServerPayloadSha(serverWorkDir, targetJar)
        reportProgress(100, "整合包导入完成")
        return serverWorkDir
    } finally {
        clearManagedServerImportTarget(stagingDir)
        Files.deleteIfExists(stagingDir)
    }
}

private fun shouldImportModpackDirectlyIntoTarget(serverWorkDir: Path): Boolean {
    if (!Files.exists(serverWorkDir)) return true
    if (!Files.isDirectory(serverWorkDir)) return false
    return Files.list(serverWorkDir).use { children -> !children.findAny().isPresent }
}

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
    val contentPrefix = runCatching {
        Files.newInputStream(script).use { input ->
            val buffer = ByteArray(512)
            val read = input.read(buffer)
            if (read <= 0) "" else String(buffer, 0, read)
        }
    }.getOrDefault("")
    return contentPrefix.startsWith("#!") && contentPrefix.contains("sh", ignoreCase = true)
}

internal fun isInstallerBootstrapScript(script: Path, serverWorkDir: Path): Boolean {
    if (!isManagedServerRegularFile(script)) return false
    val content = runCatching { String(Files.readAllBytes(script)) }.getOrDefault("")
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
    if (!Files.isRegularFile(marker)) return null
    val values = Files.readAllLines(marker)
        .map(String::trim)
        .filter(String::isNotBlank)
    val relativePath = values.getOrNull(0) ?: return null
    val sha256 = values.getOrNull(1)?.lowercase() ?: return null
    return ManagedServerSetupApprovalRecord(relativePath, sha256)
}

internal fun approvedManagedServerSetupScript(serverWorkDir: Path): Path? {
    val approval = readManagedServerSetupApprovalRecord(serverWorkDir) ?: return null
    val script = runCatching {
        resolveManagedServerSetupScript(serverWorkDir, approval.scriptRelativePath)
    }.getOrNull() ?: return null
    return script.takeIf { approval.scriptSha256 == sha256Hex(it) }
}

private fun matchesManagedServerSetupApproval(serverWorkDir: Path, script: Path): Boolean {
    val approval = readManagedServerSetupApprovalRecord(serverWorkDir) ?: return false
    val relativePath = serverWorkDir.toAbsolutePath().normalize()
        .relativize(script.toAbsolutePath().normalize())
        .toString()
        .replace('\\', '/')
    return approval.scriptRelativePath == relativePath && approval.scriptSha256 == sha256Hex(script)
}

internal fun requiresManagedServerSetupApproval(serverWorkDir: Path): Path? {
    if (Files.isRegularFile(managedServerSetupCompletionMarker(serverWorkDir))) return null
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
    Files.write(
        managedServerSetupApprovalMarker(serverWorkDir),
        "$relativePath\n${sha256Hex(script)}\n".toByteArray(),
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
    if (Files.isRegularFile(marker)) return false
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
    val effectiveScript = rewriteManagedInstallerBootstrapScriptForAndroid(script, scriptEnvironment) ?: script
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
    Files.write(marker, "done\n".toByteArray())
    return true
}

fun installManagedServerModFile(
    sourceFile: Path,
    serverWorkDir: Path,
    targetFileName: String = sourceFile.fileName.toString(),
): Path {
    require(Files.isRegularFile(sourceFile)) { "模组文件不存在：$sourceFile" }
    require(targetFileName.endsWith(".jar", ignoreCase = true)) { "模组文件必须是 .jar" }
    require(targetFileName.isNotBlank()) { "模组文件名不能为空" }
    require('/' !in targetFileName && '\\' !in targetFileName) { "模组文件名不能包含路径：$targetFileName" }
    require(targetFileName != "." && targetFileName != "..") { "模组文件名无效：$targetFileName" }
    val modsDir = serverWorkDir.resolve("mods").normalize()
    Files.createDirectories(modsDir)
    val target = modsDir.resolve(targetFileName).normalize()
    require(target.parent == modsDir) { "模组文件名不能越界：$targetFileName" }
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

private fun unzipManagedServerArchive(archiveFile: Path, targetDir: Path) {
    Files.createDirectories(targetDir)
    Files.newInputStream(archiveFile).use { input ->
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val normalized = entry.name.replace('\\', '/').trimStart('/')
                if (normalized.isBlank()) continue
                val target = targetDir.resolve(normalized).normalize()
                require(target.startsWith(targetDir)) { "整合包包含越界路径：${entry.name}" }
                if (normalized.substringAfterLast('/') in ReservedManagedServerImportEntries) continue
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.newOutputStream(target).use { output -> zip.copyTo(output) }
                    if (normalized.endsWith(".sh", ignoreCase = true)) {
                        target.toFile().setExecutable(true, false)
                    }
                }
                zip.closeEntry()
            }
        }
    }
}

internal fun resolveNeoForgeMinecraftVersions(
    metadataXml: String,
    availableMinecraftVersions: List<String>,
): List<String> {
    val prefixToMinecraft = availableMinecraftVersions.associateBy(::neoforgeArtifactPrefixForMinecraftVersion)
    return Regex("<version>([^<]+)</version>")
        .findAll(metadataXml)
        .map { it.groupValues[1] }
        .mapNotNull { artifact ->
            val numericPrefix = Regex("\\d+(?:\\.\\d+){1,2}").find(artifact)?.value ?: return@mapNotNull null
            generateSequence(numericPrefix) { current -> current.substringBeforeLast('.', missingDelimiterValue = "") }
                .takeWhile { it.isNotBlank() }
                .mapNotNull(prefixToMinecraft::get)
                .firstOrNull()
        }
        .distinct()
        .sortedWith(::compareMinecraftVersions)
        .toList()
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

internal fun compareArtifactVersions(left: String, right: String): Int {
    fun numericTokens(value: String): List<Int> = Regex("\\d+").findAll(value).map { it.value.toInt() }.toList()
    val leftTokens = numericTokens(left)
    val rightTokens = numericTokens(right)
    val maxSize = maxOf(leftTokens.size, rightTokens.size)
    for (index in 0 until maxSize) {
        val leftPart = leftTokens.getOrElse(index) { -1 }
        val rightPart = rightTokens.getOrElse(index) { -1 }
        if (leftPart != rightPart) return leftPart.compareTo(rightPart)
    }
    return left.compareTo(right)
}

internal fun neoforgeArtifactPrefixForMinecraftVersion(version: String): String {
    val safe = validatePaperVersion(version)
    val parts = safe.split('.')
    return when {
        parts.size >= 3 && parts[0] == "1" -> "${parts[1]}.${parts[2]}"
        parts.size >= 3 -> "${parts[0]}.${parts[1]}.${parts[2]}"
        parts.size >= 2 -> "${parts[0]}.${parts[1]}"
        else -> safe
    }
}

private fun resolveForgeInstallerDownloadUrl(artifactVersion: String): String =
    "https://maven.minecraftforge.net/net/minecraftforge/forge/$artifactVersion/forge-$artifactVersion-installer.jar"

internal fun resolveForgeInstallerSha256Url(artifactVersion: String): String =
    "https://maven.minecraftforge.net/net/minecraftforge/forge/$artifactVersion/forge-$artifactVersion-installer.jar.sha256"

private fun resolveForgeUnixArgsRelativePath(artifactVersion: String): String =
    "libraries/net/minecraftforge/forge/$artifactVersion/unix_args.txt"

internal fun resolveInstalledForgeUnixArgsRelativePath(serverWorkDir: Path, version: String): String? =
    Files.walk(serverWorkDir).use { paths ->
        paths.filter { path ->
            Files.isRegularFile(path) &&
                path.fileName.toString() == "unix_args.txt" &&
                path.toString().contains("/net/minecraftforge/forge/") &&
                path.toString().contains(validatePaperVersion(version))
        }.findFirst().orElse(null)
    }?.let(serverWorkDir::relativize)?.toString()?.replace('\\', '/')

internal fun resolveInstalledNeoForgeUnixArgsRelativePath(serverWorkDir: Path, version: String): String? =
    Files.walk(serverWorkDir).use { paths ->
        paths.filter { path ->
            Files.isRegularFile(path) &&
                path.fileName.toString() == "unix_args.txt" &&
                path.toString().contains("/net/neoforged/neoforge/") &&
                path.toString().contains(neoforgeArtifactPrefixForMinecraftVersion(version))
        }.findFirst().orElse(null)
    }?.let(serverWorkDir::relativize)?.toString()?.replace('\\', '/')

internal fun resolveLatestNeoForgeArtifactVersion(version: String): String =
    Regex("<version>(${Regex.escape(neoforgeArtifactPrefixForMinecraftVersion(version))}(?:\\.[^<]+)?)</version>")
        .findAll(httpGet(NeoForgeMavenMetadataUrl))
        .map { it.groupValues[1] }
        .maxWithOrNull(::compareArtifactVersions)
        ?: error("NeoForge 安装器版本缺失：$version")

internal fun resolveLatestForgeArtifactVersion(version: String): String =
    Regex("<version>(${Regex.escape(validatePaperVersion(version))}-[^<]+)</version>")
        .findAll(httpGet(ForgeMavenMetadataUrl))
        .map { it.groupValues[1] }
        .maxWithOrNull(::compareArtifactVersions)
        ?: error("Forge 安装器版本缺失：$version")

internal fun detectImportedModpackServerMetadata(serverWorkDir: Path): ImportedModpackServerMetadata {
    detectInstallerPackMetadata(serverWorkDir)?.let { return it }

    fun build(serverType: com.mcgo.app.ui.model.MinecraftServerType, minecraftVersion: String): ImportedModpackServerMetadata =
        ImportedModpackServerMetadata(
            serverType = serverType,
            minecraftVersion = minecraftVersion,
            javaMajorVersion = com.mcgo.app.ui.model.recommendedJavaMajorVersion(minecraftVersion),
        )

    fun findVersionFromPaths(): String? {
        val versionRegex = Regex("""/(?:server|minecraftforge/forge|neoforge)/((?:1\.)?\d+\.\d+(?:\.\d+)?)(?:-|/)""")
        val jarVersions = Files.walk(serverWorkDir).use { paths ->
            paths.iterator().asSequence()
                .map { path -> path.toString().replace('\\', '/') }
                .mapNotNull { rawPath ->
                    versionRegex.find(rawPath)
                        ?.groupValues
                        ?.getOrNull(1)
                }
                .filter { version -> validatePaperVersionOrNull(version) != null }
                .toList()
        }
        return jarVersions.maxWithOrNull(::compareMinecraftVersions)
    }

    if (Files.isRegularFile(serverWorkDir.resolve("fabric-server-launch.jar"))) {
        return build(com.mcgo.app.ui.model.MinecraftServerType.Fabric, findVersionFromPaths() ?: "1.21.4")
    }
    if (Files.isRegularFile(serverWorkDir.resolve("quilt-server-launch.jar"))) {
        return build(com.mcgo.app.ui.model.MinecraftServerType.Quilt, findVersionFromPaths() ?: "1.21.4")
    }
    Files.walk(serverWorkDir).use { paths ->
        paths.filter { path ->
            Files.isRegularFile(path) && path.fileName.toString() == "unix_args.txt" && path.toString().contains("/net/minecraftforge/forge/")
        }.findFirst().orElse(null)
    }?.let { argsPath ->
        val version = Regex("""/forge/((?:1\.)?\d+\.\d+(?:\.\d+)?)-""")
            .find(argsPath.toString().replace('\\', '/'))
            ?.groupValues
            ?.getOrNull(1)
            ?: "1.20.1"
        return build(com.mcgo.app.ui.model.MinecraftServerType.Forge, version)
    }
    Files.walk(serverWorkDir).use { paths ->
        paths.filter { path ->
            Files.isRegularFile(path) && path.fileName.toString() == "unix_args.txt" && path.toString().contains("/net/neoforged/neoforge/")
        }.findFirst().orElse(null)
    }?.let { argsPath ->
        val rawVersion = Regex("""/neoforge/((?:1\.)?\d+\.\d+(?:\.\d+)?)(?:[./])""")
            .find(argsPath.toString().replace('\\', '/'))
            ?.groupValues
            ?.getOrNull(1)
            ?: "1.21.4"
        val version = rawVersion.split('.').let { parts ->
            when {
                rawVersion.startsWith("1.") -> rawVersion
                parts.size >= 3 && parts[0].toIntOrNull() ?: 0 < 26 -> "1.${parts[0]}.${parts[1]}"
                else -> rawVersion
            }
        }
        return build(com.mcgo.app.ui.model.MinecraftServerType.NeoForge, version)
    }
    return build(com.mcgo.app.ui.model.MinecraftServerType.Paper, findVersionFromPaths() ?: "1.21.4")
}

fun resolveInstalledPayloadJar(serverWorkDir: Path, targetJar: Path): Path? {
    val targetName = targetJar.fileName.toString().lowercase()
    val targetKind = when {
        targetName.startsWith("fabric-") -> "fabric"
        targetName.startsWith("forge-") -> "forge"
        targetName.startsWith("neoforge-") -> "neoforge"
        targetName.startsWith("quilt-") -> "quilt"
        else -> "generic"
    }
    if (Files.isRegularFile(targetJar) && !isInstalledPayloadMarkerFile(targetJar) && targetJar.fileName.toString() != "server.jar") {
        if (targetKind !in setOf("forge", "neoforge", "quilt")) {
            return targetJar
        }
    }
    val candidates = Files.walk(serverWorkDir).use { paths ->
        paths.filter { path ->
            Files.isRegularFile(path) && when {
                path.fileName.toString() == "fabric-server-launch.jar" -> true
                path.fileName.toString() == "server.jar" -> true
                path.fileName.toString() == "quilt-server-launch.jar" -> true
                path.fileName.toString().endsWith("-server.jar") -> true
                path.fileName.toString().endsWith("-universal.jar") -> true
                path.fileName.toString().endsWith("-shim.jar") -> true
                else -> false
            }
        }.iterator().asSequence().toList()
    }
    fun rank(path: Path): Int {
        val name = path.fileName.toString()
        return when (targetKind) {
            "fabric" -> when {
                name == "fabric-server-launch.jar" -> 0
                name == "server.jar" -> 1
                else -> 9
            }
            "quilt" -> when {
                name == "quilt-server-launch.jar" -> 0
                name == "server.jar" -> 1
                else -> 9
            }
            "forge" -> when {
                name.endsWith("-server.jar") && path.toString().contains("/net/minecraftforge/forge/") -> 0
                name == "server.jar" -> 1
                name.endsWith("-universal.jar") -> 2
                name.endsWith("-shim.jar") -> 3
                else -> 9
            }
            "neoforge" -> when {
                name.endsWith("-server.jar") && path.toString().contains("/net/neoforged/neoforge/") -> 0
                name.endsWith("-universal.jar") && path.toString().contains("/net/neoforged/neoforge/") -> 1
                name.endsWith("-shim.jar") && path.toString().contains("/net/neoforged/neoforge/") -> 2
                name == "server.jar" -> 3
                else -> 9
            }
            else -> when {
                name == "fabric-server-launch.jar" -> 0
                name == "quilt-server-launch.jar" -> 1
                name == "server.jar" -> 2
                name.endsWith("-server.jar") -> 3
                name.endsWith("-universal.jar") -> 4
                name.endsWith("-shim.jar") -> 5
                else -> 9
            }
        }
    }
    return candidates.sortedWith(compareBy<Path>({ rank(it) }, { it.toString().length })).firstOrNull { rank(it) < 9 }
}

internal fun isInstalledPayloadMarkerFile(path: Path): Boolean {
    if (!Files.isRegularFile(path)) return false
    val prefix = ByteArray(16)
    Files.newInputStream(path).use { input ->
        val read = input.read(prefix)
        if (read <= 0) return false
        val header = String(prefix, 0, read)
        return header.startsWith("installed") || header.startsWith("launcher=")
    }
}

private fun resolveNeoForgeInstallerDownloadUrl(artifactVersion: String): String =
    "https://maven.neoforged.net/releases/net/neoforged/neoforge/$artifactVersion/neoforge-$artifactVersion-installer.jar"

internal fun resolveNeoForgeInstallerChecksumUrl(installerUrl: String, indexHtml: String, algorithm: String): String {
    val normalized = algorithm.lowercase()
    require(normalized == "sha1" || normalized == "sha256") { "unsupported checksum algorithm: $algorithm" }
    val fileName = installerUrl.substringAfterLast('/')
    val relative = Regex("href=\"(\\.?/?${Regex.escape(fileName)}\\.${normalized})\"", RegexOption.IGNORE_CASE)
        .find(indexHtml)
        ?.groupValues
        ?.getOrNull(1)
        ?.removePrefix("./")
        ?.removePrefix("/")
        ?: error("NeoForge installer ${normalized} 校验文件缺失")
    return installerUrl.substringBeforeLast('/') + "/" + relative
}

internal fun parseQuiltInstallerSha256(installerMetadataJson: String, version: String): String =
    Regex("\"version\"\\s*:\\s*\"${Regex.escape(version)}\"[\\s\\S]*?\"sha256\"\\s*:\\s*\"([A-Fa-f0-9]{64})\"")
        .find(installerMetadataJson)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase()
        ?: error("Quilt installer sha256 缺失：$version")

internal fun parseQuiltInstallerSha1(installerMetadataJson: String, version: String): String =
    Regex("\"version\"\\s*:\\s*\"${Regex.escape(version)}\"[\\s\\S]*?\"sha1\"\\s*:\\s*\"([A-Fa-f0-9]{40})\"")
        .find(installerMetadataJson)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase()
        ?: error("Quilt installer sha1 缺失：$version")

private fun downloadVerifiedInstallerFile(
    installerUrl: String,
    expectedSha256: String,
    targetPath: Path,
    onProgress: (Int) -> Unit,
    flavorLabel: String,
) {
    downloadFile(installerUrl, targetPath, onProgress, flavorLabel)
    val actualSha256 = sha256Hex(targetPath)
    require(actualSha256 == expectedSha256.lowercase()) { "${flavorLabel} 安装器校验失败：SHA-256 不匹配" }
}

private fun installModdedServerViaInstaller(
    version: String,
    installerUrl: String,
    expectedInstallerSha256: String,
    installerFlavor: String,
    serverWorkDir: Path,
    targetJar: Path,
    argFileRelativePath: String?,
    launchJarRelativePath: String?,
    javaBinary: String,
    environment: List<String>,
    onProgress: (Int) -> Unit,
    customInstallCommand: List<String>? = null,
) {
    onProgress(2)
    Files.createDirectories(serverWorkDir)
    val installerJar = serverWorkDir.resolve("${installerFlavor.lowercase()}-installer-${validatePaperVersion(version)}.jar")
    val tempJar = installerJar.resolveSibling("${installerJar.fileName}.part")
    Files.deleteIfExists(tempJar)
    downloadVerifiedInstallerFile(
        installerUrl = installerUrl,
        expectedSha256 = expectedInstallerSha256,
        targetPath = tempJar,
        onProgress = scaledPaperDownloadProgressReporter(8, 28, onProgress),
        flavorLabel = installerFlavor,
    )
    moveDownloadedPaperJar(tempJar, installerJar)
    onProgress(32)
    val command = buildManagedJavaProcessCommand(
        fallbackJavaBinary = javaBinary,
        environment = environment,
        javaArguments = buildList {
            add("-jar")
            add(installerJar.toString())
            addAll(customInstallCommand ?: listOf("--installServer", serverWorkDir.toString()))
        },
    )
    val process = ProcessBuilder(command)
        .directory(serverWorkDir.toFile())
        .redirectErrorStream(true)
        .apply {
            environment().putAll(environmentMap(environment))
        }
        .start()
    process.inputStream.bufferedReader().useLines { lines ->
        lines.forEach { _ -> }
    }
    val exitCode = process.waitFor()
    if (exitCode != 0) error("${installerFlavor} 安装失败：退出码 $exitCode")
    onProgress(72)
    argFileRelativePath?.let { relative ->
        val argsFile = serverWorkDir.resolve(relative)
        require(Files.isRegularFile(argsFile)) { "${installerFlavor} 参数文件缺失：$relative" }
    }
    launchJarRelativePath?.let { relative ->
        val launchJar = serverWorkDir.resolve(relative)
        require(Files.isRegularFile(launchJar)) { "${installerFlavor} 启动 Jar 缺失：$relative" }
        Files.write(targetJar, "launcher=${relative}\n".toByteArray())
    }
    if (!Files.exists(targetJar)) {
        Files.write(targetJar, "installed\n".toByteArray())
    }
    val payloadJar = resolveInstalledPayloadJar(serverWorkDir, targetJar) ?: targetJar
    Files.write(paperJarSha256File(payloadJar), (sha256Hex(payloadJar) + "\n").toByteArray())
    onProgress(76)
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
    val original = String(Files.readAllBytes(script))
    val managedJavaCommand = buildString {
        append("CLASSPATH=")
        append(shellSingleQuote(classpath))
        append(' ')
        append(shellSingleQuote(appProcess))
        append(" -Dmcgo.paperJvmLauncher.absoluteLibPath=")
        append(shellSingleQuote(launcherLib))
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

private fun clearManagedServerImportTarget(targetDir: Path) {
    if (!Files.exists(targetDir)) return
    Files.walk(targetDir)
        .sorted(Comparator.reverseOrder())
        .forEach { path -> if (path != targetDir) Files.deleteIfExists(path) }
}
