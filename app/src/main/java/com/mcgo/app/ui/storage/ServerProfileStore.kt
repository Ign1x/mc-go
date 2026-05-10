package com.mcgo.app.ui.storage

import com.mcgo.app.ui.model.JavaSelectionMode
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.model.MinecraftServerType
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.ServerTunnelBinding
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.createPurpurServer
import com.mcgo.app.ui.model.createVanillaServer
import com.mcgo.app.ui.model.effectiveTunnelBindings
import com.mcgo.app.ui.model.recommendedJavaMajorVersion
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

internal val ServerProfileStoreGlobalLock = Any()

class ServerProfileStore(
    private val storePath: Path,
) {
    fun load(): List<ServerCardState> = synchronized(ServerProfileStoreGlobalLock) {
        if (!Files.exists(storePath)) return emptyList()

        val properties = Properties()
        Files.newInputStream(storePath).use { input -> properties.load(input) }
        val count = properties.getProperty("count")?.toIntOrNull() ?: return emptyList()

        return (0 until count).mapNotNull { index ->
            val prefix = "server.$index."
            val id = properties.getProperty(prefix + "id") ?: return@mapNotNull null
            val name = properties.getProperty(prefix + "name") ?: return@mapNotNull null
            val minecraftVersion = properties.getProperty(prefix + "minecraftVersion") ?: "1.21.4"
            val maxPlayers = properties.getProperty(prefix + "maxPlayers")?.toIntOrNull() ?: 20
            val memoryMb = properties.getProperty(prefix + "memoryMb")?.toIntOrNull() ?: 2048
            val defaultPort = properties.getProperty(prefix + "defaultPort")?.toIntOrNull() ?: 25565
            val worldName = properties.getProperty(prefix + "worldName") ?: "world"
            val serverType = enumValueOrNull<MinecraftServerType>(properties.getProperty(prefix + "serverType"))
                ?: MinecraftServerType.Paper
            val requestedJavaMajorVersion = properties.getProperty(prefix + "javaMajorVersion")?.toIntOrNull()
            val javaSelectionMode = enumValueOrNull<JavaSelectionMode>(properties.getProperty(prefix + "javaSelectionMode"))
                ?: JavaSelectionMode.Recommended
            val launchStatus = enumValueOrNull<ServerLaunchStatus>(properties.getProperty(prefix + "launchStatus"))
                ?: ServerLaunchStatus.Ready
            val isOnline = properties.getProperty(prefix + "isOnline")?.toBooleanStrictOrNull()
                ?: (launchStatus == ServerLaunchStatus.Running)
            val port = properties.getProperty(prefix + "port")?.toIntOrNull() ?: defaultPort
            val onlinePlayers = properties.getProperty(prefix + "onlinePlayers")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val onlinePlayerNameCount = properties.getProperty(prefix + "onlinePlayerNameCount")?.toIntOrNull() ?: 0
            val onlinePlayerNames = (0 until onlinePlayerNameCount).mapNotNull { playerIndex ->
                properties.getProperty(prefix + "onlinePlayerName.$playerIndex")
            }
            val tunnelRemotePort = properties.getProperty(prefix + "tunnelRemotePort")?.toIntOrNull()
            val selectedTunnelId = properties.getProperty(prefix + "selectedTunnelId")
            val activeTunnelLabel = properties.getProperty(prefix + "activeTunnelLabel")
            val runtimeAddress = properties.getProperty(prefix + "runtimeAddress")
            val tunnelBindingCount = properties.getProperty(prefix + "tunnelBindingCount")?.toIntOrNull() ?: 0
            val tunnelBindings = if (tunnelBindingCount > 0) {
                (0 until tunnelBindingCount).mapNotNull { bindingIndex ->
                    val bindingPrefix = prefix + "tunnelBinding.$bindingIndex."
                    val tunnelId = properties.getProperty(bindingPrefix + "tunnelId") ?: return@mapNotNull null
                    ServerTunnelBinding(
                        tunnelId = tunnelId,
                        remotePort = properties.getProperty(bindingPrefix + "remotePort")?.toIntOrNull(),
                        activeLabel = properties.getProperty(bindingPrefix + "activeLabel"),
                        runtimeAddress = properties.getProperty(bindingPrefix + "runtimeAddress"),
                    )
                }
            } else {
                listOfNotNull(
                    selectedTunnelId?.let { tunnelId ->
                        ServerTunnelBinding(
                            tunnelId = tunnelId,
                            remotePort = tunnelRemotePort,
                            activeLabel = activeTunnelLabel,
                            runtimeAddress = runtimeAddress,
                        )
                    },
                )
            }
            val gameMode = enumValueOrNull<PaperGameMode>(properties.getProperty(prefix + "gameMode")) ?: PaperGameMode.Survival
            val difficulty = enumValueOrNull<PaperDifficulty>(properties.getProperty(prefix + "difficulty")) ?: PaperDifficulty.Normal
            val onlineMode = properties.getProperty(prefix + "onlineMode")?.toBooleanStrictOrNull() ?: true
            val pvpEnabled = properties.getProperty(prefix + "pvpEnabled")?.toBooleanStrictOrNull() ?: true
            val serverPropertiesOverride = properties.getProperty(prefix + "serverPropertiesOverride")
            val launchProgress = properties.getProperty(prefix + "launchProgress")?.toIntOrNull()
                ?: if (isOnline) 100 else 0
            val runtimeLogPath = properties.getProperty(prefix + "runtimeLogPath")
            val runtimeSlot = properties.getProperty(prefix + "runtimeSlot")?.toIntOrNull()
            val pendingDeletion = properties.getProperty(prefix + "pendingDeletion")?.toBooleanStrictOrNull() ?: false
            val serverIconVersion = properties.getProperty(prefix + "serverIconVersion")?.toLongOrNull() ?: 0L
            val runtimeLogCount = properties.getProperty(prefix + "runtimeLogCount")?.toIntOrNull() ?: 0
            val runtimeLogs = (0 until runtimeLogCount).mapNotNull { logIndex ->
                properties.getProperty(prefix + "runtimeLog.$logIndex")
            }

            when (serverType) {
                MinecraftServerType.Vanilla -> createVanillaServer(
                    name = name,
                    minecraftVersion = minecraftVersion,
                    maxPlayers = maxPlayers,
                    memoryMb = memoryMb,
                    port = defaultPort,
                    worldName = worldName,
                    tunnelRemotePort = tunnelRemotePort,
                    gameMode = gameMode,
                    difficulty = difficulty,
                    onlineMode = onlineMode,
                    pvpEnabled = pvpEnabled,
                    serverPropertiesOverride = serverPropertiesOverride,
                ).copy(
                    id = id,
                    onlinePlayers = onlinePlayers,
                    onlinePlayerNames = onlinePlayerNames,
                    javaMajorVersion = migrateManagedJavaMajorVersion(
                        minecraftVersion = minecraftVersion,
                        requestedJavaMajorVersion = requestedJavaMajorVersion,
                        javaSelectionMode = javaSelectionMode,
                    ),
                    javaSelectionMode = javaSelectionMode,
                    port = port,
                    tunnelRemotePort = tunnelRemotePort,
                    isOnline = isOnline,
                    selectedTunnelId = selectedTunnelId,
                    activeTunnelLabel = activeTunnelLabel,
                    runtimeAddress = runtimeAddress,
                    tunnelBindings = tunnelBindings,
                    launchStatus = launchStatus,
                    launchPlan = null,
                    launchProgress = launchProgress,
                    runtimeLogs = runtimeLogs,
                    runtimeLogPath = runtimeLogPath,
                    runtimeSlot = runtimeSlot,
                    pendingDeletion = pendingDeletion,
                    serverIconVersion = serverIconVersion,
                )
                MinecraftServerType.Paper -> createPaperServer(
                    name = name,
                    minecraftVersion = minecraftVersion,
                    maxPlayers = maxPlayers,
                    memoryMb = memoryMb,
                    port = defaultPort,
                    worldName = worldName,
                    tunnelRemotePort = tunnelRemotePort,
                    gameMode = gameMode,
                    difficulty = difficulty,
                    onlineMode = onlineMode,
                    pvpEnabled = pvpEnabled,
                    serverPropertiesOverride = serverPropertiesOverride,
                ).copy(
                    id = id,
                    onlinePlayers = onlinePlayers,
                    onlinePlayerNames = onlinePlayerNames,
                    javaMajorVersion = migrateManagedJavaMajorVersion(
                        minecraftVersion = minecraftVersion,
                        requestedJavaMajorVersion = requestedJavaMajorVersion,
                        javaSelectionMode = javaSelectionMode,
                    ),
                    javaSelectionMode = javaSelectionMode,
                    port = port,
                    tunnelRemotePort = tunnelRemotePort,
                    isOnline = isOnline,
                    selectedTunnelId = selectedTunnelId,
                    activeTunnelLabel = activeTunnelLabel,
                    runtimeAddress = runtimeAddress,
                    tunnelBindings = tunnelBindings,
                    launchStatus = launchStatus,
                    launchPlan = null,
                    launchProgress = launchProgress,
                    runtimeLogs = runtimeLogs,
                    runtimeLogPath = runtimeLogPath,
                    runtimeSlot = runtimeSlot,
                    pendingDeletion = pendingDeletion,
                    serverIconVersion = serverIconVersion,
                )
                MinecraftServerType.Purpur -> createPurpurServer(
                    name = name,
                    minecraftVersion = minecraftVersion,
                    maxPlayers = maxPlayers,
                    memoryMb = memoryMb,
                    port = defaultPort,
                    worldName = worldName,
                    tunnelRemotePort = tunnelRemotePort,
                    gameMode = gameMode,
                    difficulty = difficulty,
                    onlineMode = onlineMode,
                    pvpEnabled = pvpEnabled,
                    serverPropertiesOverride = serverPropertiesOverride,
                ).copy(
                    id = id,
                    onlinePlayers = onlinePlayers,
                    onlinePlayerNames = onlinePlayerNames,
                    javaMajorVersion = migrateManagedJavaMajorVersion(
                        minecraftVersion = minecraftVersion,
                        requestedJavaMajorVersion = requestedJavaMajorVersion,
                        javaSelectionMode = javaSelectionMode,
                    ),
                    javaSelectionMode = javaSelectionMode,
                    port = port,
                    tunnelRemotePort = tunnelRemotePort,
                    isOnline = isOnline,
                    selectedTunnelId = selectedTunnelId,
                    activeTunnelLabel = activeTunnelLabel,
                    runtimeAddress = runtimeAddress,
                    tunnelBindings = tunnelBindings,
                    launchStatus = launchStatus,
                    launchPlan = null,
                    launchProgress = launchProgress,
                    runtimeLogs = runtimeLogs,
                    runtimeLogPath = runtimeLogPath,
                    runtimeSlot = runtimeSlot,
                    pendingDeletion = pendingDeletion,
                    serverIconVersion = serverIconVersion,
                )
            }
        }
    }

    fun save(servers: List<ServerCardState>) = synchronized(ServerProfileStoreGlobalLock) {
        storePath.parent?.let { parent -> Files.createDirectories(parent) }
        val properties = Properties()
        properties.setProperty("version", "3")
        properties.setProperty("count", servers.size.toString())
        servers.forEachIndexed { index, server ->
            val prefix = "server.$index."
            properties.setProperty(prefix + "id", server.id)
            properties.setProperty(prefix + "name", server.name)
            properties.setProperty(prefix + "serverType", server.serverType.name)
            properties.setProperty(prefix + "minecraftVersion", server.minecraftVersion)
            properties.setProperty(prefix + "javaMajorVersion", server.javaMajorVersion.toString())
            properties.setProperty(prefix + "javaSelectionMode", server.javaSelectionMode.name)
            properties.setProperty(prefix + "maxPlayers", server.maxPlayers.toString())
            properties.setProperty(prefix + "memoryMb", server.memoryMb.toString())
            properties.setProperty(prefix + "defaultPort", server.defaultPort.toString())
            properties.setProperty(prefix + "port", server.port.toString())
            properties.setProperty(prefix + "onlinePlayers", server.onlinePlayers.toString())
            properties.setProperty(prefix + "onlinePlayerNameCount", server.onlinePlayerNames.size.toString())
            server.onlinePlayerNames.forEachIndexed { playerIndex, playerName ->
                properties.setProperty(prefix + "onlinePlayerName.$playerIndex", playerName)
            }
            server.tunnelRemotePort?.let { properties.setProperty(prefix + "tunnelRemotePort", it.toString()) }
            server.effectiveTunnelBindings().takeIf { it.isNotEmpty() }?.let { bindings ->
                properties.setProperty(prefix + "tunnelBindingCount", bindings.size.toString())
                bindings.forEachIndexed { bindingIndex, binding ->
                    val bindingPrefix = prefix + "tunnelBinding.$bindingIndex."
                    properties.setProperty(bindingPrefix + "tunnelId", binding.tunnelId)
                    binding.remotePort?.let { properties.setProperty(bindingPrefix + "remotePort", it.toString()) }
                    binding.activeLabel?.let { properties.setProperty(bindingPrefix + "activeLabel", it) }
                    binding.runtimeAddress?.let { properties.setProperty(bindingPrefix + "runtimeAddress", it) }
                }
            }
            properties.setProperty(prefix + "worldName", server.worldName)
            properties.setProperty(prefix + "gameMode", server.gameMode.name)
            properties.setProperty(prefix + "difficulty", server.difficulty.name)
            properties.setProperty(prefix + "onlineMode", server.onlineMode.toString())
            properties.setProperty(prefix + "pvpEnabled", server.pvpEnabled.toString())
            server.serverPropertiesOverride?.let { properties.setProperty(prefix + "serverPropertiesOverride", it) }
            properties.setProperty(prefix + "isOnline", server.isOnline.toString())
            properties.setProperty(prefix + "launchStatus", server.launchStatus.name)
            properties.setProperty(prefix + "launchProgress", server.launchProgress.toString())
            properties.setProperty(prefix + "runtimeLogCount", server.runtimeLogs.size.toString())
            server.selectedTunnelId?.let { properties.setProperty(prefix + "selectedTunnelId", it) }
            server.activeTunnelLabel?.let { properties.setProperty(prefix + "activeTunnelLabel", it) }
            server.runtimeAddress?.let { properties.setProperty(prefix + "runtimeAddress", it) }
            server.runtimeLogPath?.let { properties.setProperty(prefix + "runtimeLogPath", it) }
            server.runtimeSlot?.let { properties.setProperty(prefix + "runtimeSlot", it.toString()) }
            properties.setProperty(prefix + "pendingDeletion", server.pendingDeletion.toString())
            server.serverIconVersion.takeIf { it > 0L }?.let { properties.setProperty(prefix + "serverIconVersion", it.toString()) }
            server.runtimeLogs.forEachIndexed { logIndex, logLine ->
                properties.setProperty(prefix + "runtimeLog.$logIndex", logLine)
            }
        }
        Files.newOutputStream(storePath).use { output ->
            properties.store(output, "MC-GO server profiles")
        }
    }
}

private fun migrateManagedJavaMajorVersion(
    minecraftVersion: String,
    requestedJavaMajorVersion: Int?,
    javaSelectionMode: JavaSelectionMode,
): Int {
    val recommended = recommendedJavaMajorVersion(minecraftVersion)
    return when (javaSelectionMode) {
        JavaSelectionMode.Recommended -> recommended
        JavaSelectionMode.Manual -> requestedJavaMajorVersion ?: recommended
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
    enumValues<T>().firstOrNull { it.name == value }
