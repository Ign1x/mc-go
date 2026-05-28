package com.mcgo.app.ui.model

import com.mcgo.app.server.isMinecraftClassListingLogNoise
import com.mcgo.app.server.minecraftClassListingSummaryLine
import com.mcgo.app.server.summarizeMinecraftClassListingLogLines
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

enum class MetricAccent {
    Blue,
    Green,
    Gold,
    Violet,
    Coral,
    Teal,
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
    Vanilla("Vanilla"),
    Paper("Paper"),
    Purpur("Purpur"),
    Fabric("Fabric"),
    Forge("Forge"),
    NeoForge("NeoForge"),
    Quilt("Quilt"),
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

data class MetricTrendSample(
    val elapsedMillis: Long,
    val value: Float?,
)

data class DashboardMetric(
    val title: String,
    val valueLabel: String,
    val detailLabel: String,
    val trendValues: List<Float>,
    val accent: MetricAccent,
    val trendSamples: List<MetricTrendSample> = trendValues.mapIndexed { index, value ->
        MetricTrendSample(elapsedMillis = index * 2_000L, value = value)
    },
)

data class ServerTunnelBinding(
    val tunnelId: String,
    val remotePort: Int? = null,
    val activeLabel: String? = null,
    val runtimeAddress: String? = null,
)

const val MaxServerRuntimeLogEntries = 80

data class ServerCardState(
    val name: String,
    val id: String = createServerId(name),
    val edition: String,
    val worldName: String,
    val port: Int,
    val defaultPort: Int = port,
    val tunnelRemotePort: Int? = null,
    val onlinePlayers: Int,
    val onlinePlayerNames: List<String> = emptyList(),
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
    val tunnelBindings: List<ServerTunnelBinding> = emptyList(),
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
    val serverIconVersion: Long = 0L,
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
): ServerCardState = createManagedServer(
    serverType = MinecraftServerType.Paper,
    defaultName = "Paper 服务器",
    editionLabel = "Paper",
    name = name,
    minecraftVersion = minecraftVersion,
    maxPlayers = maxPlayers,
    memoryMb = memoryMb,
    port = port,
    worldName = worldName,
    javaMajorVersion = javaMajorVersion,
    javaSelectionMode = javaSelectionMode,
    tunnelRemotePort = tunnelRemotePort,
    gameMode = gameMode,
    difficulty = difficulty,
    onlineMode = onlineMode,
    pvpEnabled = pvpEnabled,
    serverPropertiesOverride = serverPropertiesOverride,
)

fun createVanillaServer(
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
): ServerCardState = createManagedServer(
    serverType = MinecraftServerType.Vanilla,
    defaultName = "Vanilla 服务器",
    editionLabel = "Vanilla",
    name = name,
    minecraftVersion = minecraftVersion,
    maxPlayers = maxPlayers,
    memoryMb = memoryMb,
    port = port,
    worldName = worldName,
    javaMajorVersion = javaMajorVersion,
    javaSelectionMode = javaSelectionMode,
    tunnelRemotePort = tunnelRemotePort,
    gameMode = gameMode,
    difficulty = difficulty,
    onlineMode = onlineMode,
    pvpEnabled = pvpEnabled,
    serverPropertiesOverride = serverPropertiesOverride,
)

fun createPurpurServer(
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
): ServerCardState = createManagedServer(
    serverType = MinecraftServerType.Purpur,
    defaultName = "Purpur 服务器",
    editionLabel = "Purpur",
    name = name,
    minecraftVersion = minecraftVersion,
    maxPlayers = maxPlayers,
    memoryMb = memoryMb,
    port = port,
    worldName = worldName,
    javaMajorVersion = javaMajorVersion,
    javaSelectionMode = javaSelectionMode,
    tunnelRemotePort = tunnelRemotePort,
    gameMode = gameMode,
    difficulty = difficulty,
    onlineMode = onlineMode,
    pvpEnabled = pvpEnabled,
    serverPropertiesOverride = serverPropertiesOverride,
)

fun createFabricServer(
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
): ServerCardState = createManagedServer(
    serverType = MinecraftServerType.Fabric,
    defaultName = "Fabric 服务器",
    editionLabel = "Fabric",
    name = name,
    minecraftVersion = minecraftVersion,
    maxPlayers = maxPlayers,
    memoryMb = memoryMb,
    port = port,
    worldName = worldName,
    javaMajorVersion = javaMajorVersion,
    javaSelectionMode = javaSelectionMode,
    tunnelRemotePort = tunnelRemotePort,
    gameMode = gameMode,
    difficulty = difficulty,
    onlineMode = onlineMode,
    pvpEnabled = pvpEnabled,
    serverPropertiesOverride = serverPropertiesOverride,
)

fun createForgeServer(
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
): ServerCardState = createManagedServer(
    serverType = MinecraftServerType.Forge,
    defaultName = "Forge 服务器",
    editionLabel = "Forge",
    name = name,
    minecraftVersion = minecraftVersion,
    maxPlayers = maxPlayers,
    memoryMb = memoryMb,
    port = port,
    worldName = worldName,
    javaMajorVersion = javaMajorVersion,
    javaSelectionMode = javaSelectionMode,
    tunnelRemotePort = tunnelRemotePort,
    gameMode = gameMode,
    difficulty = difficulty,
    onlineMode = onlineMode,
    pvpEnabled = pvpEnabled,
    serverPropertiesOverride = serverPropertiesOverride,
)

fun createNeoForgeServer(
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
): ServerCardState = createManagedServer(
    serverType = MinecraftServerType.NeoForge,
    defaultName = "NeoForge 服务器",
    editionLabel = "NeoForge",
    name = name,
    minecraftVersion = minecraftVersion,
    maxPlayers = maxPlayers,
    memoryMb = memoryMb,
    port = port,
    worldName = worldName,
    javaMajorVersion = javaMajorVersion,
    javaSelectionMode = javaSelectionMode,
    tunnelRemotePort = tunnelRemotePort,
    gameMode = gameMode,
    difficulty = difficulty,
    onlineMode = onlineMode,
    pvpEnabled = pvpEnabled,
    serverPropertiesOverride = serverPropertiesOverride,
)

fun createQuiltServer(
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
): ServerCardState = createManagedServer(
    serverType = MinecraftServerType.Quilt,
    defaultName = "Quilt 服务器",
    editionLabel = "Quilt",
    name = name,
    minecraftVersion = minecraftVersion,
    maxPlayers = maxPlayers,
    memoryMb = memoryMb,
    port = port,
    worldName = worldName,
    javaMajorVersion = javaMajorVersion,
    javaSelectionMode = javaSelectionMode,
    tunnelRemotePort = tunnelRemotePort,
    gameMode = gameMode,
    difficulty = difficulty,
    onlineMode = onlineMode,
    pvpEnabled = pvpEnabled,
    serverPropertiesOverride = serverPropertiesOverride,
)

fun pickAvailableManagedServerPort(
    servers: List<ServerCardState>,
    preferredPort: Int = 25565,
    maxPort: Int = 65535,
): Int {
    val occupiedPorts = servers.map { it.defaultPort }.toSet()
    val startPort = preferredPort.coerceIn(1, maxPort)
    for (candidate in startPort..maxPort) {
        if (candidate !in occupiedPorts) return candidate
    }
    for (candidate in 1 until startPort) {
        if (candidate !in occupiedPorts) return candidate
    }
    return startPort
}

private fun createManagedServer(
    serverType: MinecraftServerType,
    defaultName: String,
    editionLabel: String,
    name: String,
    minecraftVersion: String,
    maxPlayers: Int,
    memoryMb: Int,
    port: Int,
    worldName: String,
    javaMajorVersion: Int,
    javaSelectionMode: JavaSelectionMode,
    tunnelRemotePort: Int?,
    gameMode: PaperGameMode,
    difficulty: PaperDifficulty,
    onlineMode: Boolean,
    pvpEnabled: Boolean,
    serverPropertiesOverride: String?,
): ServerCardState = ServerCardState(
    id = createServerId(name.ifBlank { defaultName }),
    name = name.ifBlank { defaultName },
    edition = "$editionLabel $minecraftVersion",
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
    serverType = serverType,
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
    runtimeLogs = appendRuntimeLogEntries(runtimeLogs, listOfNotNull(logLine)),
)

fun ServerCardState.markAwaitingManagedRuntimeInstall(majorVersion: Int): ServerCardState =
    withLaunchProgress(
        progress = 2,
        logLine = "未检测到 Java $majorVersion，正在自动安装托管 JRE",
    )

fun ServerCardState.effectiveTunnelBindings(): List<ServerTunnelBinding> = when {
    tunnelBindings.isNotEmpty() -> tunnelBindings
    selectedTunnelId != null || tunnelRemotePort != null || activeTunnelLabel != null || runtimeAddress != null -> listOf(
        ServerTunnelBinding(
            tunnelId = selectedTunnelId ?: "primary",
            remotePort = tunnelRemotePort,
            activeLabel = activeTunnelLabel,
            runtimeAddress = runtimeAddress,
        ),
    )
    else -> emptyList()
}

fun ServerCardState.withTunnelBindings(bindings: List<ServerTunnelBinding>): ServerCardState {
    val primary = bindings.firstOrNull()
    return copy(
        tunnelBindings = bindings,
        selectedTunnelId = primary?.tunnelId,
        tunnelRemotePort = primary?.remotePort,
        activeTunnelLabel = primary?.activeLabel,
        runtimeAddress = primary?.runtimeAddress,
    )
}

fun ServerCardState.clearTunnelRuntimeBindings(): ServerCardState = withTunnelBindings(
    effectiveTunnelBindings().map { it.copy(activeLabel = null, runtimeAddress = null) },
)

fun ServerCardState.usesTunnel(tunnelId: String): Boolean =
    effectiveTunnelBindings().any { it.tunnelId == tunnelId }

fun ServerCardState.remotePortForTunnel(tunnelId: String): Int? =
    effectiveTunnelBindings().firstOrNull { it.tunnelId == tunnelId }?.remotePort

fun ServerCardState.connectionAddresses(): List<String> =
    effectiveTunnelBindings().mapNotNull { it.runtimeAddress }.ifEmpty { listOf(runtimeAddress ?: "127.0.0.1:$port") }

fun ServerCardState.activeTunnelLabels(): List<String> =
    effectiveTunnelBindings().mapNotNull { it.activeLabel }.ifEmpty { listOfNotNull(activeTunnelLabel) }

fun ServerCardState.markLaunchRunning(logLine: String = "服务端进程已进入运行状态"): ServerCardState = copy(
    isOnline = true,
    launchStatus = ServerLaunchStatus.Running,
    launchProgress = 100,
    runtimeLogs = appendRuntimeLogEntries(runtimeLogs, listOf(logLine)),
)

fun ServerCardState.markLaunchFailed(error: String): ServerCardState = clearTunnelRuntimeBindings().copy(
    isOnline = false,
    onlinePlayers = 0,
    port = defaultPort,
    activeTunnelLabel = null,
    runtimeAddress = null,
    launchStatus = ServerLaunchStatus.Failed,
    launchProgress = 0,
    runtimeLogs = appendRuntimeLogEntries(runtimeLogs, listOf("启动失败：$error")),
    runtimeSlot = null,
)

fun ServerCardState.markModpackImportRecoveredAfterSyncFailure(error: String): ServerCardState = clearTunnelRuntimeBindings().copy(
    isOnline = false,
    onlinePlayers = 0,
    port = defaultPort,
    activeTunnelLabel = null,
    runtimeAddress = null,
    launchStatus = ServerLaunchStatus.Failed,
    launchProgress = 0,
    runtimeLogs = appendRuntimeLogEntries(runtimeLogs, listOf("导入整合包后同步失败：$error")),
    runtimeSlot = null,
)

fun ServerCardState.markModpackImportInProgress(progress: Int, logLine: String): ServerCardState = copy(
    isOnline = false,
    launchStatus = ServerLaunchStatus.Launching,
    launchProgress = progress.coerceIn(1, 99),
    runtimeLogs = appendRuntimeLogEntries(runtimeLogs, listOf(logLine)),
)

fun sanitizedRuntimeLogEntries(runtimeLogs: List<String>): List<String> =
    summarizeMinecraftClassListingLogLines(runtimeLogs).takeLast(MaxServerRuntimeLogEntries)

internal fun appendRuntimeLogEntries(existingLogs: List<String>, newLogs: List<String>): List<String> =
    sanitizedRuntimeLogEntries(existingLogs + newLogs)

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
): ServerCardState {
    val editedBase = when (server.serverType) {
        MinecraftServerType.Vanilla -> createVanillaServer(
            name = name.ifBlank { server.name },
            minecraftVersion = minecraftVersion,
            maxPlayers = maxPlayers,
            memoryMb = memoryMb,
            port = port,
            worldName = worldName.ifBlank { "world" },
            javaMajorVersion = javaMajorVersion,
            javaSelectionMode = javaSelectionMode,
            tunnelRemotePort = server.tunnelRemotePort,
            gameMode = gameMode,
            difficulty = difficulty,
            onlineMode = onlineMode,
            pvpEnabled = pvpEnabled,
            serverPropertiesOverride = serverPropertiesOverride,
        )
        MinecraftServerType.Paper -> createPaperServer(
            name = name.ifBlank { server.name },
            minecraftVersion = minecraftVersion,
            maxPlayers = maxPlayers,
            memoryMb = memoryMb,
            port = port,
            worldName = worldName.ifBlank { "world" },
            javaMajorVersion = javaMajorVersion,
            javaSelectionMode = javaSelectionMode,
            tunnelRemotePort = server.tunnelRemotePort,
            gameMode = gameMode,
            difficulty = difficulty,
            onlineMode = onlineMode,
            pvpEnabled = pvpEnabled,
            serverPropertiesOverride = serverPropertiesOverride,
        )
        MinecraftServerType.Purpur -> createPurpurServer(
            name = name.ifBlank { server.name },
            minecraftVersion = minecraftVersion,
            maxPlayers = maxPlayers,
            memoryMb = memoryMb,
            port = port,
            worldName = worldName.ifBlank { "world" },
            javaMajorVersion = javaMajorVersion,
            javaSelectionMode = javaSelectionMode,
            tunnelRemotePort = server.tunnelRemotePort,
            gameMode = gameMode,
            difficulty = difficulty,
            onlineMode = onlineMode,
            pvpEnabled = pvpEnabled,
            serverPropertiesOverride = serverPropertiesOverride,
        )
        MinecraftServerType.Fabric -> createFabricServer(
            name = name.ifBlank { server.name },
            minecraftVersion = minecraftVersion,
            maxPlayers = maxPlayers,
            memoryMb = memoryMb,
            port = port,
            worldName = worldName.ifBlank { "world" },
            javaMajorVersion = javaMajorVersion,
            javaSelectionMode = javaSelectionMode,
            tunnelRemotePort = server.tunnelRemotePort,
            gameMode = gameMode,
            difficulty = difficulty,
            onlineMode = onlineMode,
            pvpEnabled = pvpEnabled,
            serverPropertiesOverride = serverPropertiesOverride,
        )
        MinecraftServerType.Forge -> createForgeServer(
            name = name.ifBlank { server.name },
            minecraftVersion = minecraftVersion,
            maxPlayers = maxPlayers,
            memoryMb = memoryMb,
            port = port,
            worldName = worldName.ifBlank { "world" },
            javaMajorVersion = javaMajorVersion,
            javaSelectionMode = javaSelectionMode,
            tunnelRemotePort = server.tunnelRemotePort,
            gameMode = gameMode,
            difficulty = difficulty,
            onlineMode = onlineMode,
            pvpEnabled = pvpEnabled,
            serverPropertiesOverride = serverPropertiesOverride,
        )
        MinecraftServerType.NeoForge -> createNeoForgeServer(
            name = name.ifBlank { server.name },
            minecraftVersion = minecraftVersion,
            maxPlayers = maxPlayers,
            memoryMb = memoryMb,
            port = port,
            worldName = worldName.ifBlank { "world" },
            javaMajorVersion = javaMajorVersion,
            javaSelectionMode = javaSelectionMode,
            tunnelRemotePort = server.tunnelRemotePort,
            gameMode = gameMode,
            difficulty = difficulty,
            onlineMode = onlineMode,
            pvpEnabled = pvpEnabled,
            serverPropertiesOverride = serverPropertiesOverride,
        )
        MinecraftServerType.Quilt -> createQuiltServer(
            name = name.ifBlank { server.name },
            minecraftVersion = minecraftVersion,
            maxPlayers = maxPlayers,
            memoryMb = memoryMb,
            port = port,
            worldName = worldName.ifBlank { "world" },
            javaMajorVersion = javaMajorVersion,
            javaSelectionMode = javaSelectionMode,
            tunnelRemotePort = server.tunnelRemotePort,
            gameMode = gameMode,
            difficulty = difficulty,
            onlineMode = onlineMode,
            pvpEnabled = pvpEnabled,
            serverPropertiesOverride = serverPropertiesOverride,
        )
    }
    return editedBase.copy(
        id = server.id,
        onlinePlayers = server.onlinePlayers,
        onlinePlayerNames = server.onlinePlayerNames,
        isOnline = server.isOnline,
        selectedTunnelId = server.selectedTunnelId,
        activeTunnelLabel = server.activeTunnelLabel,
        runtimeAddress = server.runtimeAddress,
        tunnelBindings = server.tunnelBindings,
        launchStatus = server.launchStatus,
        launchPlan = server.launchPlan,
        launchProgress = server.launchProgress,
        runtimeLogs = server.runtimeLogs,
        runtimeLogPath = server.runtimeLogPath,
        runtimeSlot = server.runtimeSlot,
        pendingDeletion = server.pendingDeletion,
        serverIconVersion = server.serverIconVersion,
        port = if (server.isRuntimeBusy()) server.port else port,
        defaultPort = port,
    )
}

const val MaxServerConsoleLogReadBytes = 256 * 1024

fun resolveServerConsoleText(server: ServerCardState): String =
    server.runtimeLogPath
        ?.let(::readServerConsoleRuntimeLogTextOrNull)
        ?.takeIf { it.isNotBlank() }
        ?: sanitizedRuntimeLogEntries(server.runtimeLogs).joinToString(separator = "\n")

private fun readServerConsoleRuntimeLogTextOrNull(rawPath: String): String? = runCatching {
    readServerConsoleRuntimeLogTextOrNull(Paths.get(rawPath))
}.getOrNull()

private fun readServerConsoleRuntimeLogTextOrNull(path: Path): String? = runCatching {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) return@runCatching null
    Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
        val fileSize = channel.size()
        val bytesToRead = minOf(fileSize, MaxServerConsoleLogReadBytes.toLong()).toInt()
        val startOffset = (fileSize - bytesToRead).coerceAtLeast(0L)
        channel.position(startOffset)
        val text = Channels.newInputStream(channel).use { input ->
            val buffer = ByteArray(bytesToRead)
            var totalRead = 0
            while (totalRead < bytesToRead) {
                val read = input.read(buffer, totalRead, bytesToRead - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            buffer.copyOf(totalRead).toString(Charsets.UTF_8)
        }
        if (startOffset > 0L) {
            val trimmed = text.substringAfter('\n', missingDelimiterValue = text)
            summarizeMinecraftClassListingNoise(
                fullPath = path,
                tailText = trimmed,
                marker = "===== 仅显示最后 ${MaxServerConsoleLogReadBytes / 1024} KiB 日志 =====",
            )
        } else {
            summarizeMinecraftClassListingNoise(
                fullPath = path,
                tailText = text,
                marker = null,
            )
        }
    }
}.getOrNull()

private fun summarizeMinecraftClassListingNoise(fullPath: Path, tailText: String, marker: String?): String {
    val preservedLines = tailText
        .lineSequence()
        .filterNot { isMinecraftClassListingLogNoise(it) }
        .toMutableList()
    val removedFromTail = tailText
        .lineSequence()
        .count { isMinecraftClassListingLogNoise(it) }
    if (removedFromTail == 0) {
        return listOfNotNull(marker, tailText).joinToString(separator = "\n")
    }
    val debugMarkers = readManagedConsoleDebugMarkers(fullPath)
    val bodyLines = buildList {
        marker?.let(::add)
        add("===== 已过滤 Minecraft class 清单噪声：${minecraftClassListingSummaryLine(removedFromTail)} =====")
        debugMarkers.forEach { markerLine ->
            if (markerLine !in preservedLines) add(markerLine)
        }
        addAll(preservedLines)
    }
    return bodyLines.joinToString(separator = "\n").trimEnd()
}

private fun readManagedConsoleDebugMarkers(path: Path): List<String> = runCatching {
    Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
        val bytesToRead = minOf(channel.size(), MaxServerConsoleDebugMarkerProbeBytes.toLong()).toInt()
        val buffer = ByteArray(bytesToRead)
        val input = Channels.newInputStream(channel)
        var totalRead = 0
        while (totalRead < bytesToRead) {
            val read = input.read(buffer, totalRead, bytesToRead - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        String(buffer, 0, totalRead, Charsets.UTF_8)
            .lineSequence()
            .filter { line -> line.startsWith("[debug]") || line.startsWith("[MC-GO]") }
            .take(MaxServerConsoleDebugMarkerLines)
            .toList()
    }
}.getOrDefault(emptyList())

private const val MaxServerConsoleDebugMarkerLines = 24
private const val MaxServerConsoleDebugMarkerProbeBytes = 64 * 1024

fun requestServerDeletion(server: ServerCardState): ServerCardState {
    val runtimeBusy = server.isRuntimeBusy()
    val deleteMessage = if (runtimeBusy) {
        "已请求删除，正在停止服务，退出后会自动移除"
    } else {
        "已请求删除，待服务停止后自动移除"
    }
    return server.copy(
        pendingDeletion = true,
        launchStatus = if (runtimeBusy) ServerLaunchStatus.Stopping else server.launchStatus,
        launchProgress = if (runtimeBusy) 1 else server.launchProgress,
        runtimeLogs = appendRuntimeLogEntries(server.runtimeLogs, listOf(deleteMessage)),
    )
}

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
        clearTunnelRuntimeBindings().copy(
            isOnline = false,
            port = defaultPort,
            activeTunnelLabel = null,
            runtimeAddress = null,
            launchStatus = ServerLaunchStatus.Failed,
            launchProgress = 0,
            runtimeLogs = appendRuntimeLogEntries(runtimeLogs, listOf(reason)).distinct(),
            runtimeSlot = null,
        )
    }
    ?: this

private fun parseMemoryMb(memoryLabel: String): Int {
    val value = memoryLabel.substringBefore(' ').toFloatOrNull() ?: return 1024
    return if (memoryLabel.contains("GB")) (value * 1024).toInt() else value.toInt()
}

private fun createServerId(name: String): String {
    val slug = readableSlug(name)
        .ifBlank { "paper-server" }
    return "server-$slug-${System.currentTimeMillis()}"
}

internal fun readableSlug(raw: String): String = raw
    .trim()
    .lowercase()
    .replace(Regex("[\\s_]+"), "-")
    .replace(Regex("[^\\p{L}\\p{N}.-]+"), "-")
    .replace(Regex("-+"), "-")
    .trim('-', '.')
