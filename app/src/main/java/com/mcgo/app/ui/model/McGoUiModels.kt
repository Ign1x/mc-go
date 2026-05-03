package com.mcgo.app.ui.model

import java.io.File

enum class MetricAccent {
    Blue,
    Green,
    Gold,
    Violet,
}

enum class SettingsCategoryIcon {
    Appearance,
    JavaRuntime,
    RuntimePermissions,
    Notifications,
    Storage,
    Diagnostics,
    Labs,
}

enum class MinecraftServerType(val label: String) {
    Paper("Paper"),
}

enum class ServerLaunchStatus(val label: String) {
    Ready("就绪"),
    Launching("启动中"),
    Stopping("停止中"),
    Running("运行中"),
    Failed("启动失败"),
    Stopped("已停止"),
}

enum class JavaSelectionMode {
    Recommended,
    Manual,
}

enum class PaperGameMode(val propertyValue: String) {
    Survival("survival"),
    Creative("creative"),
    Adventure("adventure"),
    Spectator("spectator"),
}

enum class PaperDifficulty(val propertyValue: String) {
    Peaceful("peaceful"),
    Easy("easy"),
    Normal("normal"),
    Hard("hard"),
}

data class PaperLaunchPlan(
    val serverJarName: String,
    val javaMajorVersion: Int,
    val arguments: List<String>,
)

data class DashboardMetric(
    val title: String,
    val valueLabel: String,
    val detailLabel: String,
    val trendValues: List<Float>,
    val accent: MetricAccent,
)

data class ServerCardState(
    val name: String,
    val id: String = createServerId(name),
    val edition: String,
    val worldName: String,
    val port: Int,
    val defaultPort: Int = port,
    val tunnelRemotePort: Int? = null,
    val onlinePlayers: Int,
    val maxPlayers: Int,
    val gameMode: PaperGameMode = PaperGameMode.Survival,
    val difficulty: PaperDifficulty = PaperDifficulty.Normal,
    val onlineMode: Boolean = true,
    val pvpEnabled: Boolean = true,
    val serverPropertiesOverride: String? = null,
    val memoryLabel: String,
    val memoryMb: Int = parseMemoryMb(memoryLabel),
    val isOnline: Boolean,
    val selectedTunnelId: String? = null,
    val activeTunnelLabel: String? = null,
    val runtimeAddress: String? = null,
    val serverType: MinecraftServerType = MinecraftServerType.Paper,
    val minecraftVersion: String = edition.substringAfter(' ', "1.21.4"),
    val javaMajorVersion: Int = recommendedJavaMajorVersion(edition.substringAfter(' ', "1.21.4")),
    val javaSelectionMode: JavaSelectionMode = JavaSelectionMode.Recommended,
    val launchStatus: ServerLaunchStatus = if (isOnline) ServerLaunchStatus.Running else ServerLaunchStatus.Ready,
    val launchPlan: PaperLaunchPlan? = null,
    val launchProgress: Int = if (isOnline) 100 else 0,
    val runtimeLogs: List<String> = emptyList(),
    val runtimeLogPath: String? = null,
    val runtimeSlot: Int? = null,
    val pendingDeletion: Boolean = false,
)

data class SettingsSectionState(
    val title: String,
    val subtitle: String,
    val highlight: String,
    val icon: SettingsCategoryIcon,
)

data class AppearanceToggleState(
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
)

data class AppearanceSettingsState(
    val themeModes: List<String>,
    val selectedThemeMode: String,
    val accentOptions: List<String>,
    val selectedAccent: String,
    val fontScaleOptions: List<String>,
    val selectedFontScale: String,
    val cardTransparencyPercent: Int,
    val toggles: List<AppearanceToggleState>,
)

fun recommendedJavaMajorVersion(minecraftVersion: String): Int {
    val parts = minecraftVersion.split('.').mapNotNull { it.toIntOrNull() }
    val major = parts.getOrNull(0) ?: return 21
    val minor = parts.getOrNull(1) ?: return if (major >= 26) 25 else 21
    return when {
        major >= 26 -> 25
        minor <= 11 -> 8
        minor in 12..16 -> 11
        minor in 17..19 -> 17
        else -> 21
    }
}

fun formatMemoryMb(memoryMb: Int): String = if (memoryMb % 1024 == 0) {
    "${memoryMb / 1024}.0 GB RAM"
} else {
    "${memoryMb} MB RAM"
}

fun createPaperServer(
    name: String,
    minecraftVersion: String,
    maxPlayers: Int,
    memoryMb: Int,
    port: Int = 25565,
    worldName: String = "world",
    javaMajorVersion: Int = recommendedJavaMajorVersion(minecraftVersion),
    javaSelectionMode: JavaSelectionMode = JavaSelectionMode.Recommended,
    tunnelRemotePort: Int? = null,
    gameMode: PaperGameMode = PaperGameMode.Survival,
    difficulty: PaperDifficulty = PaperDifficulty.Normal,
    onlineMode: Boolean = true,
    pvpEnabled: Boolean = true,
    serverPropertiesOverride: String? = null,
): ServerCardState = ServerCardState(
    id = createServerId(name.ifBlank { "Paper 服务器" }),
    name = name.ifBlank { "Paper 服务器" },
    edition = "Paper $minecraftVersion",
    worldName = worldName.ifBlank { "world" },
    port = port,
    defaultPort = port,
    tunnelRemotePort = tunnelRemotePort,
    onlinePlayers = 0,
    maxPlayers = maxPlayers,
    gameMode = gameMode,
    difficulty = difficulty,
    onlineMode = onlineMode,
    pvpEnabled = pvpEnabled,
    serverPropertiesOverride = serverPropertiesOverride,
    memoryLabel = formatMemoryMb(memoryMb),
    memoryMb = memoryMb,
    isOnline = false,
    serverType = MinecraftServerType.Paper,
    minecraftVersion = minecraftVersion,
    javaMajorVersion = javaMajorVersion,
    javaSelectionMode = javaSelectionMode,
    launchStatus = ServerLaunchStatus.Ready,
)

fun ServerCardState.withLaunchProgress(
    progress: Int,
    logLine: String? = null,
    status: ServerLaunchStatus = ServerLaunchStatus.Launching,
    online: Boolean = false,
): ServerCardState = copy(
    isOnline = online,
    launchStatus = status,
    launchProgress = progress.coerceIn(0, 100),
    runtimeLogs = (runtimeLogs + listOfNotNull(logLine)).takeLast(12),
)

fun ServerCardState.markLaunchRunning(logLine: String = "服务端进程已进入运行状态"): ServerCardState = copy(
    isOnline = true,
    launchStatus = ServerLaunchStatus.Running,
    launchProgress = 100,
    runtimeLogs = (runtimeLogs + logLine).takeLast(12),
)

fun ServerCardState.markLaunchFailed(error: String): ServerCardState = copy(
    isOnline = false,
    port = defaultPort,
    activeTunnelLabel = null,
    runtimeAddress = null,
    launchStatus = ServerLaunchStatus.Failed,
    launchProgress = 0,
    runtimeLogs = (runtimeLogs + "启动失败：$error").takeLast(12),
    runtimeSlot = null,
)

fun ServerCardState.isRuntimeBusy(): Boolean =
    isOnline || launchStatus == ServerLaunchStatus.Launching || launchStatus == ServerLaunchStatus.Stopping || launchStatus == ServerLaunchStatus.Running

fun canStartServerFromUi(server: ServerCardState): Boolean = !server.isRuntimeBusy() && !server.pendingDeletion

fun applyPaperServerEdits(
    server: ServerCardState,
    name: String,
    minecraftVersion: String,
    maxPlayers: Int,
    memoryMb: Int,
    port: Int,
    worldName: String,
    javaMajorVersion: Int = if (server.javaSelectionMode == JavaSelectionMode.Manual) server.javaMajorVersion else recommendedJavaMajorVersion(minecraftVersion),
    javaSelectionMode: JavaSelectionMode = server.javaSelectionMode,
    gameMode: PaperGameMode = server.gameMode,
    difficulty: PaperDifficulty = server.difficulty,
    onlineMode: Boolean = server.onlineMode,
    pvpEnabled: Boolean = server.pvpEnabled,
    serverPropertiesOverride: String? = server.serverPropertiesOverride,
): ServerCardState = server.copy(
    name = name.ifBlank { server.name },
    edition = "Paper $minecraftVersion",
    worldName = worldName.ifBlank { "world" },
    port = if (server.isRuntimeBusy()) server.port else port,
    defaultPort = port,
    maxPlayers = maxPlayers,
    memoryLabel = formatMemoryMb(memoryMb),
    memoryMb = memoryMb,
    minecraftVersion = minecraftVersion,
    javaMajorVersion = javaMajorVersion,
    javaSelectionMode = javaSelectionMode,
    gameMode = gameMode,
    difficulty = difficulty,
    onlineMode = onlineMode,
    pvpEnabled = pvpEnabled,
    serverPropertiesOverride = serverPropertiesOverride,
)

fun resolveServerConsoleText(server: ServerCardState): String =
    server.runtimeLogPath
        ?.let(::File)
        ?.takeIf { it.isFile }
        ?.readText()
        ?.takeIf { it.isNotBlank() }
        ?: server.runtimeLogs.joinToString(separator = "\n")

fun requestServerDeletion(server: ServerCardState): ServerCardState = server.copy(
    pendingDeletion = true,
    runtimeLogs = (server.runtimeLogs + "已请求删除，待服务停止后自动移除").takeLast(12),
)

fun finalizePendingServerDeletion(servers: List<ServerCardState>): List<ServerCardState> =
    servers.filterNot { it.pendingDeletion && !it.isRuntimeBusy() }

fun isManagedRuntimeProvisioningAvailable(majorVersion: Int, supportedProvisionableVersions: Set<Int> = setOf(8, 11, 17, 21, 25)): Boolean = majorVersion in supportedProvisionableVersions

fun unsupportedManagedRuntimeReason(
    majorVersion: Int,
    supportedProvisionableVersions: Set<Int> = setOf(8, 11, 17, 21, 25),
): String? = if (isManagedRuntimeProvisioningAvailable(majorVersion, supportedProvisionableVersions)) {
    null
} else {
    "当前版本暂不提供 Java $majorVersion 托管运行时；该 Minecraft 版本暂不支持一键开服"
}

fun ServerCardState.markUnsupportedManagedRuntime(supportedProvisionableVersions: Set<Int> = setOf(8, 11, 17, 21, 25)): ServerCardState = unsupportedManagedRuntimeReason(javaMajorVersion, supportedProvisionableVersions)
    ?.let { reason ->
        copy(
            isOnline = false,
            port = defaultPort,
            activeTunnelLabel = null,
            runtimeAddress = null,
            launchStatus = ServerLaunchStatus.Failed,
            launchProgress = 0,
            runtimeLogs = (runtimeLogs + reason).distinct().takeLast(12),
            runtimeSlot = null,
        )
    }
    ?: this

private fun parseMemoryMb(memoryLabel: String): Int {
    val value = memoryLabel.substringBefore(' ').toFloatOrNull() ?: return 1024
    return if (memoryLabel.contains("GB")) (value * 1024).toInt() else value.toInt()
}

private fun createServerId(name: String): String {
    val slug = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "paper-server" }
    return "server-$slug-${System.currentTimeMillis()}"
}
