package com.mcgo.app.ui.storage

import com.mcgo.app.ui.model.MinecraftServerType
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.createPaperServer
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
            val serverType = enumValueOrNull<MinecraftServerType>(properties.getProperty(prefix + "serverType")) ?: MinecraftServerType.Paper
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
                    isOnline = false,
                    launchStatus = ServerLaunchStatus.Ready,
                    launchPlan = null,
                )
            }
        }
    }

    fun save(servers: List<ServerCardState>) {
        storePath.parent?.let { parent -> Files.createDirectories(parent) }
        val properties = Properties()
        properties.setProperty("version", "1")
        properties.setProperty("count", servers.size.toString())
        servers.forEachIndexed { index, server ->
            val prefix = "server.$index."
            properties.setProperty(prefix + "id", server.id)
            properties.setProperty(prefix + "name", server.name)
            properties.setProperty(prefix + "serverType", server.serverType.name)
            properties.setProperty(prefix + "minecraftVersion", server.minecraftVersion)
            properties.setProperty(prefix + "maxPlayers", server.maxPlayers.toString())
            properties.setProperty(prefix + "memoryMb", server.memoryMb.toString())
            properties.setProperty(prefix + "defaultPort", server.defaultPort.toString())
            properties.setProperty(prefix + "worldName", server.worldName)
        }
        Files.newOutputStream(storePath).use { output ->
            properties.store(output, "MC-GO server profiles")
        }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
    enumValues<T>().firstOrNull { it.name == value }
