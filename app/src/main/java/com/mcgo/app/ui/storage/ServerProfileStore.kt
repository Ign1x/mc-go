package com.mcgo.app.ui.storage

import com.mcgo.app.ui.model.JavaSelectionMode
import com.mcgo.app.ui.model.MinecraftServerType
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.recommendedJavaMajorVersion
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class ServerProfileStore(
    private val storePath: Path,
) {
    fun load(): List<ServerCardState> {
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
            val selectedTunnelId = properties.getProperty(prefix + "selectedTunnelId")
            val activeTunnelLabel = properties.getProperty(prefix + "activeTunnelLabel")
            val launchProgress = properties.getProperty(prefix + "launchProgress")?.toIntOrNull()
                ?: if (isOnline) 100 else 0
            val runtimeLogPath = properties.getProperty(prefix + "runtimeLogPath")
            val runtimeSlot = properties.getProperty(prefix + "runtimeSlot")?.toIntOrNull()
            val pendingDeletion = properties.getProperty(prefix + "pendingDeletion")?.toBooleanStrictOrNull() ?: false
            val runtimeLogCount = properties.getProperty(prefix + "runtimeLogCount")?.toIntOrNull() ?: 0
            val runtimeLogs = (0 until runtimeLogCount).mapNotNull { logIndex ->
                properties.getProperty(prefix + "runtimeLog.$logIndex")
            }
            when (serverType) {
                MinecraftServerType.Paper -> createPaperServer(
                    name = name,
                    minecraftVersion = minecraftVersion,
                    maxPlayers = maxPlayers,
                    memoryMb = memoryMb,
                    port = defaultPort,
                    worldName = worldName,
                ).copy(
                    id = id,
                    javaMajorVersion = migrateManagedJavaMajorVersion(
                        minecraftVersion = minecraftVersion,
                        requestedJavaMajorVersion = requestedJavaMajorVersion,
                        javaSelectionMode = javaSelectionMode,
                    ),
                    javaSelectionMode = javaSelectionMode,
                    port = port,
                    isOnline = isOnline,
                    selectedTunnelId = selectedTunnelId,
                    activeTunnelLabel = activeTunnelLabel,
                    launchStatus = launchStatus,
                    launchPlan = null,
                    launchProgress = launchProgress,
                    runtimeLogs = runtimeLogs,
                    runtimeLogPath = runtimeLogPath,
                    runtimeSlot = runtimeSlot,
                    pendingDeletion = pendingDeletion,
                )
            }
        }
    }

    fun save(servers: List<ServerCardState>) {
        storePath.parent?.let { parent -> Files.createDirectories(parent) }
        val properties = Properties()
        properties.setProperty("version", "2")
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
            properties.setProperty(prefix + "worldName", server.worldName)
            properties.setProperty(prefix + "isOnline", server.isOnline.toString())
            properties.setProperty(prefix + "launchStatus", server.launchStatus.name)
            properties.setProperty(prefix + "launchProgress", server.launchProgress.toString())
            properties.setProperty(prefix + "runtimeLogCount", server.runtimeLogs.size.toString())
            server.selectedTunnelId?.let { properties.setProperty(prefix + "selectedTunnelId", it) }
            server.activeTunnelLabel?.let { properties.setProperty(prefix + "activeTunnelLabel", it) }
            server.runtimeLogPath?.let { properties.setProperty(prefix + "runtimeLogPath", it) }
            server.runtimeSlot?.let { properties.setProperty(prefix + "runtimeSlot", it.toString()) }
            properties.setProperty(prefix + "pendingDeletion", server.pendingDeletion.toString())
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
