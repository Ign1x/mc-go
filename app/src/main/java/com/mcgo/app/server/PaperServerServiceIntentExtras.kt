package com.mcgo.app.server

import android.content.Intent
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.TunnelConfigFormat
import com.mcgo.app.ui.model.TunnelKind
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.TunnelSource
import com.mcgo.app.ui.model.createFabricServer
import com.mcgo.app.ui.model.createForgeServer
import com.mcgo.app.ui.model.createNeoForgeServer
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.createPurpurServer
import com.mcgo.app.ui.model.createQuiltServer
import com.mcgo.app.ui.model.createVanillaServer

internal fun decodeServerCardStateExtrasForTest(extras: Map<String, Any?>): ServerCardState = decodeServerCardStateExtras(extras)
internal fun decodeTunnelProfileExtrasForTest(extras: Map<String, Any?>): TunnelProfile? = decodeTunnelProfileExtras(extras)
internal fun decodeTunnelProfilesExtrasForTest(extras: Map<String, Any?>): List<TunnelProfile> = decodeTunnelProfilesExtras(extras)
internal fun hydrateLaunchTunnelProfilesForTest(
    storedProfiles: List<TunnelProfile>,
    launchProfiles: List<TunnelProfile>,
): List<TunnelProfile> = hydrateLaunchTunnelProfiles(storedProfiles, launchProfiles)

internal fun Intent.toServerCardState(): ServerCardState = decodeServerCardStateExtras(
    mapOf(
        "id" to getStringExtra("id"),
        "name" to getStringExtra("name"),
        "serverType" to getStringExtra("serverType"),
        "minecraftVersion" to getStringExtra("minecraftVersion"),
        "maxPlayers" to getIntExtra("maxPlayers", 20),
        "memoryMb" to getIntExtra("memoryMb", 2048),
        "port" to getIntExtra("port", 25565),
        "worldName" to getStringExtra("worldName"),
        "javaMajorVersion" to getIntExtra("javaMajorVersion", 0),
        "runtimeSlot" to getIntExtra("runtimeSlot", -1),
        "selectedTunnelId" to getStringExtra("selectedTunnelId"),
        "activeTunnelLabel" to getStringExtra("activeTunnelLabel"),
        "runtimeAddress" to getStringExtra("runtimeAddress"),
        "tunnelRemotePort" to getIntExtra("tunnelRemotePort", -1),
        "gameMode" to getStringExtra("gameMode"),
        "difficulty" to getStringExtra("difficulty"),
        "onlineMode" to getBooleanExtra("onlineMode", true),
        "pvpEnabled" to getBooleanExtra("pvpEnabled", true),
        "serverPropertiesOverride" to getStringExtra("serverPropertiesOverride"),
    ),
)

private fun decodeServerCardStateExtras(extras: Map<String, Any?>): ServerCardState {
    val serverType = (extras["serverType"] as? String)
        ?.let { runCatching { enumValueOf<com.mcgo.app.ui.model.MinecraftServerType>(it) }.getOrNull() }
        ?: com.mcgo.app.ui.model.MinecraftServerType.Paper
    val baseServer = when (serverType) {
        com.mcgo.app.ui.model.MinecraftServerType.Vanilla -> createVanillaServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.Paper -> createPaperServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.Purpur -> createPurpurServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.Fabric -> createFabricServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.Forge -> createForgeServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.NeoForge -> createNeoForgeServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.Quilt -> createQuiltServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
    }
    return baseServer.let { server ->
        server.copy(
            id = extras["id"] as? String ?: "paper-server",
            javaMajorVersion = (extras["javaMajorVersion"] as? Int)?.takeIf { it > 0 } ?: server.javaMajorVersion,
            selectedTunnelId = extras["selectedTunnelId"] as? String,
            activeTunnelLabel = extras["activeTunnelLabel"] as? String,
            runtimeAddress = extras["runtimeAddress"] as? String,
            runtimeSlot = (extras["runtimeSlot"] as? Int)?.takeIf { it > 0 },
        )
    }
}

internal fun Intent.toTunnelProfiles(): List<TunnelProfile> = decodeTunnelProfilesExtras(
    buildMap {
        put("tunnelCount", getIntExtra("tunnelCount", 0))
        val tunnelCount = getIntExtra("tunnelCount", 0)
        repeat(tunnelCount) { index ->
            put("tunnels.$index.id", getStringExtra("tunnels.$index.id"))
            put("tunnels.$index.name", getStringExtra("tunnels.$index.name"))
            put("tunnels.$index.kind", getStringExtra("tunnels.$index.kind"))
            put("tunnels.$index.source", getStringExtra("tunnels.$index.source"))
            put("tunnels.$index.format", getStringExtra("tunnels.$index.format"))
            put("tunnels.$index.serverAddress", getStringExtra("tunnels.$index.serverAddress"))
            put("tunnels.$index.remotePort", getIntExtra("tunnels.$index.remotePort", -1))
            put("tunnels.$index.localPort", getIntExtra("tunnels.$index.localPort", -1))
            put("tunnels.$index.credentialValue", getStringExtra("tunnels.$index.credentialValue"))
            put("tunnels.$index.rawConfigPreview", getStringExtra("tunnels.$index.rawConfigPreview"))
            put("tunnels.$index.rawConfigText", getStringExtra("tunnels.$index.rawConfigText"))
            put("tunnels.$index.portRange", getStringExtra("tunnels.$index.portRange"))
            put("tunnels.$index.detail", getStringExtra("tunnels.$index.detail"))
        }
    },
)

private fun decodeTunnelProfilesExtras(extras: Map<String, Any?>): List<TunnelProfile> {
    val tunnelCount = extras["tunnelCount"] as? Int ?: 0
    return (0 until tunnelCount).mapNotNull { index ->
        decodeTunnelProfileExtras(
            mapOf(
                "tunnel.id" to extras["tunnels.$index.id"],
                "tunnel.name" to extras["tunnels.$index.name"],
                "tunnel.kind" to extras["tunnels.$index.kind"],
                "tunnel.source" to extras["tunnels.$index.source"],
                "tunnel.format" to extras["tunnels.$index.format"],
                "tunnel.serverAddress" to extras["tunnels.$index.serverAddress"],
                "tunnel.remotePort" to extras["tunnels.$index.remotePort"],
                "tunnel.localPort" to extras["tunnels.$index.localPort"],
                "tunnel.credentialValue" to extras["tunnels.$index.credentialValue"],
                "tunnel.rawConfigPreview" to extras["tunnels.$index.rawConfigPreview"],
                "tunnel.rawConfigText" to extras["tunnels.$index.rawConfigText"],
                "tunnel.portRange" to extras["tunnels.$index.portRange"],
                "tunnel.detail" to extras["tunnels.$index.detail"],
            ),
        )
    }
}

internal fun hydrateLaunchTunnelProfiles(
    storedProfiles: List<TunnelProfile>,
    launchProfiles: List<TunnelProfile>,
): List<TunnelProfile> {
    val storedById = storedProfiles.associateBy { it.id }
    return launchProfiles.map { launch ->
        val stored = storedById[launch.id] ?: return@map launch
        stored.copy(
            name = launch.name.ifBlank { stored.name },
            kind = launch.kind,
            source = launch.source,
            format = launch.format ?: stored.format,
            serverAddress = launch.serverAddress.ifBlank { stored.serverAddress },
            remotePort = launch.remotePort ?: stored.remotePort,
            localPort = launch.localPort ?: stored.localPort,
            credentialValue = launch.credentialValue ?: stored.credentialValue,
            portRange = launch.portRange ?: stored.portRange,
            rawConfigPreview = launch.rawConfigPreview ?: stored.rawConfigPreview,
            rawConfigText = launch.rawConfigText ?: stored.rawConfigText,
            detail = launch.detail ?: stored.detail,
        )
    }
}

internal fun Intent.toTunnelProfile(): TunnelProfile? = decodeTunnelProfileExtras(
    mapOf(
        "tunnel.id" to getStringExtra("tunnel.id"),
        "tunnel.name" to getStringExtra("tunnel.name"),
        "tunnel.kind" to getStringExtra("tunnel.kind"),
        "tunnel.source" to getStringExtra("tunnel.source"),
        "tunnel.format" to getStringExtra("tunnel.format"),
        "tunnel.serverAddress" to getStringExtra("tunnel.serverAddress"),
        "tunnel.remotePort" to getIntExtra("tunnel.remotePort", -1),
        "tunnel.localPort" to getIntExtra("tunnel.localPort", -1),
        "tunnel.credentialValue" to getStringExtra("tunnel.credentialValue"),
        "tunnel.rawConfigPreview" to getStringExtra("tunnel.rawConfigPreview"),
        "tunnel.rawConfigText" to getStringExtra("tunnel.rawConfigText"),
        "tunnel.portRange" to getStringExtra("tunnel.portRange"),
        "tunnel.detail" to getStringExtra("tunnel.detail"),
    ),
)

private fun decodeTunnelProfileExtras(extras: Map<String, Any?>): TunnelProfile? {
    val tunnelId = extras["tunnel.id"] as? String ?: return null
    val kind = (extras["tunnel.kind"] as? String)?.let(TunnelKind::valueOf) ?: TunnelKind.Frp
    val source = (extras["tunnel.source"] as? String)?.let(TunnelSource::valueOf) ?: TunnelSource.ManualServer
    val format = (extras["tunnel.format"] as? String)?.let(TunnelConfigFormat::valueOf)
    return TunnelProfile(
        id = tunnelId,
        name = extras["tunnel.name"] as? String ?: "FRP",
        kind = kind,
        source = source,
        format = format,
        serverAddress = extras["tunnel.serverAddress"] as? String ?: "",
        remotePort = (extras["tunnel.remotePort"] as? Int)?.takeIf { it > 0 },
        localPort = (extras["tunnel.localPort"] as? Int)?.takeIf { it > 0 },
        credentialValue = extras["tunnel.credentialValue"] as? String,
        portRange = extras["tunnel.portRange"] as? String,
        rawConfigPreview = extras["tunnel.rawConfigPreview"] as? String,
        rawConfigText = extras["tunnel.rawConfigText"] as? String,
        detail = extras["tunnel.detail"] as? String,
    )
}
