package com.mcgo.app.ui.model

import java.io.File

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
    runtimeLogs = (runtimeLogs + listOfNotNull(logLine)).takeLast(12),
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
    runtimeLogs = (runtimeLogs + logLine).takeLast(12),
)

fun ServerCardState.markLaunchFailed(error: String): ServerCardState = clearTunnelRuntimeBindings().copy(
    isOnline = false,
    onlinePlayers = 0,
    port = defaultPort,
    activeTunnelLabel = null,
    runtimeAddress = null,
    launchStatus = ServerLaunchStatus.Failed,
    launchProgress = 0,
    runtimeLogs = (runtimeLogs + "启动失败：$error").takeLast(12),
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
    runtimeLogs = (runtimeLogs + "导入整合包后同步失败：$error").takeLast(12),
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

fun buildPaperServerPropertiesEditorText(server: ServerCardState): String {
    val overrideLines = server.serverPropertiesOverride
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.filterNot(::isGeneratedServerPropertiesComment)
        ?.toList()
        .orEmpty()
    val overrideProperties = overrideLines
        .mapNotNull(::parseServerPropertyLine)
        .associate { it }
    val templateEntries = documentedServerPropertiesTemplate(server)
    val templateKeys = templateEntries.mapTo(mutableSetOf()) { it.key }
    val templateLines = buildList {
        add("# MC-GO server.properties 模板")
        add("# MC-GO: 说明 - 修改等号右侧的值；MC-GO 注释仅用于解释，不会写入高级覆盖。")
        var currentSection: String? = null
        templateEntries.forEach { entry ->
            if (entry.section != currentSection) {
                currentSection = entry.section
                add("")
                add("# MC-GO === ${entry.section} ===")
            }
            add("# MC-GO: ${entry.key} - ${entry.comment}")
            add("${entry.key}=${overrideProperties[entry.key] ?: entry.defaultValue}")
        }
    }
    val extraLines = overrideLines.filter { line ->
        line.startsWith("#") || parseServerPropertyLine(line)?.first !in templateKeys
    }
    return (templateLines + listOfNotNull(extraLines.takeIf { it.isNotEmpty() }?.joinToString("\n")))
        .joinToString(separator = "\n")
        .trim()
}

private data class ServerPropertyTemplateEntry(
    val section: String,
    val key: String,
    val defaultValue: String,
    val comment: String,
)

private fun documentedServerPropertiesTemplate(server: ServerCardState): List<ServerPropertyTemplateEntry> {
    val managedProperties = managedEditorPropertyMap(server)

    fun entry(section: String, key: String, defaultValue: String, comment: String): ServerPropertyTemplateEntry =
        ServerPropertyTemplateEntry(
            section = section,
            key = key,
            defaultValue = managedProperties[key] ?: defaultValue,
            comment = comment,
        )

    return listOf(
        entry("基础身份", "motd", server.name, "服务器列表中显示的名称"),
        entry("基础身份", "level-name", server.worldName, "世界存档目录名称"),
        entry("基础身份", "level-seed", "", "世界种子；留空表示随机生成"),
        entry("基础身份", "level-type", "minecraft:normal", "世界类型；常用 minecraft:normal / minecraft:flat"),
        entry("基础身份", "generator-settings", "{}", "自定义世界生成参数，普通世界保持 {}"),
        entry("基础身份", "initial-enabled-packs", "vanilla", "初始启用数据包"),
        entry("基础身份", "initial-disabled-packs", "", "初始禁用数据包"),
        entry("玩家与访问", "max-players", server.maxPlayers.toString(), "最大在线玩家数"),
        entry("玩家与访问", "online-mode", server.onlineMode.toString(), "正版验证；关闭后安全风险更高"),
        entry("玩家与访问", "white-list", "false", "是否启用白名单"),
        entry("玩家与访问", "enforce-whitelist", "false", "开启白名单后是否强制踢出未在白名单玩家"),
        entry("玩家与访问", "enforce-secure-profile", "true", "强制安全玩家档案；公网服建议保持 true"),
        entry("玩家与访问", "prevent-proxy-connections", "false", "是否阻止代理/VPN 连接"),
        entry("玩家与访问", "hide-online-players", "false", "是否在状态查询中隐藏在线玩家列表"),
        entry("玩家与访问", "player-idle-timeout", "0", "玩家空闲多少分钟后踢出，0 表示关闭"),
        entry("玩家与访问", "enable-code-of-conduct", "false", "是否要求玩家同意服务器行为准则"),
        entry("游戏规则", "gamemode", server.gameMode.propertyValue, "默认游戏模式"),
        entry("游戏规则", "difficulty", server.difficulty.propertyValue, "世界难度"),
        entry("游戏规则", "hardcore", "false", "是否启用极限模式"),
        entry("游戏规则", "pvp", server.pvpEnabled.toString(), "是否允许玩家互相伤害"),
        entry("游戏规则", "force-gamemode", "false", "玩家进入时是否强制使用默认游戏模式"),
        entry("游戏规则", "spawn-monsters", "true", "是否生成攻击型生物"),
        entry("游戏规则", "spawn-protection", "16", "出生点保护半径，0 表示关闭"),
        entry("游戏规则", "allow-nether", "true", "是否允许下界维度"),
        entry("游戏规则", "generate-structures", "true", "是否生成村庄、要塞等结构"),
        entry("性能与视距", "view-distance", "8", "客户端视距，数值越高越吃内存/CPU"),
        entry("性能与视距", "simulation-distance", "4", "实体/红石模拟距离，手机建议谨慎调高"),
        entry("性能与视距", "entity-broadcast-range-percentage", "100", "实体同步范围百分比"),
        entry("性能与视距", "max-tick-time", "60000", "单 tick 最大耗时毫秒；超过可能触发看门狗"),
        entry("性能与视距", "max-world-size", "29999984", "世界边界最大大小"),
        entry("性能与视距", "max-chained-neighbor-updates", "1000000", "连锁方块更新上限"),
        entry("性能与视距", "sync-chunk-writes", "true", "是否同步写区块；更安全但可能更慢"),
        entry("网络", "server-ip", "", "绑定 IP，安卓本机通常留空"),
        entry("网络", "server-port", server.defaultPort.toString(), "监听端口，需与启动端口/隧道配置匹配"),
        entry("网络", "network-compression-threshold", "256", "网络压缩阈值，-1 表示禁用压缩"),
        entry("网络", "enable-status", "true", "是否响应服务器列表 ping"),
        entry("网络", "status-heartbeat-interval", "0", "管理协议心跳通知间隔秒数，0 表示关闭"),
        entry("网络", "rate-limit", "0", "连接速率限制，0 表示关闭"),
        entry("网络", "use-native-transport", "true", "Linux/Android 原生网络传输优化"),
        entry("网络", "accepts-transfers", "false", "是否接受 transfer packet 跨服转移"),
        entry("查询与远控", "enable-query", "false", "是否启用 GameSpy4 查询"),
        entry("查询与远控", "query.port", server.defaultPort.toString(), "Query 查询端口"),
        entry("查询与远控", "enable-rcon", "false", "是否启用 RCON 远控"),
        entry("查询与远控", "rcon.port", "25575", "RCON 端口"),
        entry("查询与远控", "rcon.password", "", "RCON 密码；启用前务必设置强密码"),
        entry("查询与远控", "management-server-enabled", "false", "是否启用 Minecraft Server Management Protocol"),
        entry("查询与远控", "management-server-host", "localhost", "管理协议监听主机，公网暴露前需谨慎评估"),
        entry("查询与远控", "management-server-port", "0", "管理协议端口，0 表示由系统自动分配"),
        entry("查询与远控", "management-server-allowed-origins", "", "允许访问管理协议的来源白名单，留空表示不额外放行"),
        entry("查询与远控", "management-server-secret", "", "管理协议授权密钥，留空由服务端自动生成"),
        entry("查询与远控", "management-server-tls-enabled", "true", "管理协议是否启用 TLS 加密"),
        entry("查询与远控", "management-server-tls-keystore", "", "TLS keystore 文件路径，启用 TLS 且非自动证书时填写"),
        entry("查询与远控", "management-server-tls-keystore-password", "", "TLS keystore 密码，可改用环境变量传入"),
        entry("查询与远控", "broadcast-console-to-ops", "true", "是否向 OP 广播控制台消息"),
        entry("查询与远控", "broadcast-rcon-to-ops", "true", "是否向 OP 广播 RCON 消息"),
        entry("权限与命令", "enable-command-block", "true", "是否启用命令方块"),
        entry("权限与命令", "op-permission-level", "4", "OP 权限等级，1-4"),
        entry("权限与命令", "function-permission-level", "2", "数据包函数权限等级"),
        entry("权限与命令", "allow-flight", "true", "是否允许飞行；模组/创造服可开启"),
        entry("资源包", "resource-pack", "", "资源包下载 URL"),
        entry("资源包", "resource-pack-id", "", "资源包 UUID"),
        entry("资源包", "resource-pack-sha1", "", "资源包 SHA-1 校验"),
        entry("资源包", "require-resource-pack", "false", "是否强制客户端使用资源包"),
        entry("资源包", "resource-pack-prompt", "", "资源包提示文本"),
        entry("高级", "debug", "false", "是否输出调试日志"),
        entry("高级", "enable-jmx-monitoring", "false", "是否启用 JMX 监控"),
        entry("高级", "log-ips", "true", "日志中是否记录玩家 IP"),
        entry("高级", "pause-when-empty-seconds", "60", "无人在线多少秒后暂停模拟，0 表示关闭"),
        entry("高级", "region-file-compression", "deflate", "区域文件压缩算法"),
        entry("高级", "text-filtering-config", "", "聊天文本过滤配置"),
        entry("高级", "text-filtering-version", "0", "聊天文本过滤配置版本，0 表示使用默认/未配置"),
        entry("高级", "bug-report-link", "", "崩溃/问题反馈链接"),
    )
}

private fun isGeneratedServerPropertiesComment(line: String): Boolean = line.startsWith("# MC-GO")

fun sanitizeAdvancedServerPropertiesOverride(rawOverride: String?): String? {
    val managedKeys = ManagedEditorPropertyKeys
    return rawOverride
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.filterNot(::isGeneratedServerPropertiesComment)
        ?.filter { line ->
            line.startsWith("#") || parseServerPropertyLine(line)?.first !in managedKeys
        }
        ?.joinToString(separator = "\n")
        ?.trim()
        ?.ifBlank { null }
}

fun parsePaperServerPropertiesEditorText(server: ServerCardState, text: String): ServerCardState {
    var name = server.name
    var worldName = server.worldName
    var maxPlayers = server.maxPlayers
    var port = server.defaultPort
    var gameMode = server.gameMode
    var difficulty = server.difficulty
    var onlineMode = server.onlineMode
    var pvpEnabled = server.pvpEnabled

    val hasGeneratedTemplateComments = text.lineSequence().any { isGeneratedServerPropertiesComment(it.trim()) }
    val defaultTemplateProperties = documentedServerPropertiesTemplate(server).associate { it.key to it.defaultValue }
    val existingOverrideKeys = server.serverPropertiesOverride
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filterNot { it.isEmpty() || isGeneratedServerPropertiesComment(it) || it.startsWith("#") }
        ?.mapNotNull(::parseServerPropertyLine)
        ?.mapTo(mutableSetOf()) { it.first }
        .orEmpty()
    val preservedLines = mutableListOf<String>()
    text.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() -> Unit
            isGeneratedServerPropertiesComment(line) -> Unit
            line.startsWith("#") -> preservedLines += line
            else -> {
                val entry = parseServerPropertyLine(line)
                if (entry == null) {
                    preservedLines += line
                    return@forEach
                }
                val (key, value) = entry
                when (key) {
                    "motd" -> name = value.ifBlank { name }
                    "level-name" -> worldName = value.ifBlank { worldName }
                    "max-players" -> maxPlayers = value.toIntOrNull()?.coerceIn(1, 200) ?: maxPlayers
                    "server-port" -> port = value.toIntOrNull()?.coerceIn(1, 65535) ?: port
                    "gamemode" -> gameMode = parsePaperGameMode(value, gameMode)
                    "difficulty" -> difficulty = parsePaperDifficulty(value, difficulty)
                    "online-mode" -> onlineMode = parseBooleanServerProperty(value, onlineMode)
                    "pvp" -> pvpEnabled = parseBooleanServerProperty(value, pvpEnabled)
                    else -> {
                        val unchangedGeneratedTemplateDefault = hasGeneratedTemplateComments &&
                            key !in existingOverrideKeys &&
                            defaultTemplateProperties[key] == value
                        if (!unchangedGeneratedTemplateDefault) {
                            preservedLines += "$key=$value"
                        }
                    }
                }
            }
        }
    }

    val editedServer = applyPaperServerEdits(
        server = server,
        name = name,
        minecraftVersion = server.minecraftVersion,
        maxPlayers = maxPlayers,
        memoryMb = server.memoryMb,
        port = port,
        worldName = worldName,
        javaMajorVersion = server.javaMajorVersion,
        javaSelectionMode = server.javaSelectionMode,
        gameMode = gameMode,
        difficulty = difficulty,
        onlineMode = onlineMode,
        pvpEnabled = pvpEnabled,
    )
    val managedProperties = managedEditorPropertyMap(editedServer)
    val overrideText = preservedLines
        .filterNot { line ->
            parseServerPropertyLine(line)?.let { (key, value) -> managedProperties[key] == value } == true
        }
        .joinToString(separator = "\n")
        .trim()
        .ifBlank { null }
    return editedServer.copy(serverPropertiesOverride = overrideText)
}

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
        clearTunnelRuntimeBindings().copy(
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

private fun managedEditorPropertyMap(server: ServerCardState): LinkedHashMap<String, String> = linkedMapOf(
    "motd" to server.name,
    "level-name" to server.worldName,
    "max-players" to server.maxPlayers.toString(),
    "server-port" to server.defaultPort.toString(),
    "gamemode" to server.gameMode.propertyValue,
    "difficulty" to server.difficulty.propertyValue,
    "online-mode" to server.onlineMode.toString(),
    "pvp" to server.pvpEnabled.toString(),
)

private val ManagedEditorPropertyKeys = setOf(
    "motd",
    "level-name",
    "max-players",
    "server-port",
    "gamemode",
    "difficulty",
    "online-mode",
    "pvp",
)

private fun parseServerPropertyLine(line: String): Pair<String, String>? {
    val separatorIndex = line.indexOf('=')
    if (separatorIndex <= 0) return null
    val key = line.substring(0, separatorIndex).trim()
    val value = line.substring(separatorIndex + 1).trim()
    if (key.isEmpty()) return null
    return key to value
}

private fun parsePaperGameMode(value: String, fallback: PaperGameMode): PaperGameMode =
    PaperGameMode.entries.firstOrNull { it.propertyValue.equals(value.trim(), ignoreCase = true) } ?: fallback

private fun parsePaperDifficulty(value: String, fallback: PaperDifficulty): PaperDifficulty =
    PaperDifficulty.entries.firstOrNull { it.propertyValue.equals(value.trim(), ignoreCase = true) } ?: fallback

private fun parseBooleanServerProperty(value: String, fallback: Boolean): Boolean = when (value.trim().lowercase()) {
    "true", "1", "yes", "on" -> true
    "false", "0", "no", "off" -> false
    else -> fallback
}
