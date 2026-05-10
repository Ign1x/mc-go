package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.model.TunnelConfigFormat
import com.mcgo.app.ui.model.TunnelKind
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.TunnelSource
import kotlin.test.Test

class PaperCommandRoutingTest {
    @Test
    fun runtimeCommandMessage_formatsUserFacingAuditLine() {
        assertThat(runtimeCommandMessage("list")).contains("list")
        assertThat(runtimeCommandMessage("list")).contains("控制台")
    }

    @Test
    fun serviceIntentRoundTrip_preservesTunnelRemotePortAndPaperProperties() {
        val server = decodeServerCardStateExtrasForTest(
            mapOf(
                "id" to "server-demo",
                "name" to "生存服",
                "minecraftVersion" to "1.21.11",
                "maxPlayers" to 20,
                "memoryMb" to 2048,
                "port" to 25577,
                "worldName" to "creative_world",
                "javaMajorVersion" to 21,
                "runtimeSlot" to 2,
                "selectedTunnelId" to "frp-home",
                "activeTunnelLabel" to "家庭 FRP · frp.home:39001",
                "runtimeAddress" to "frp.home:39001",
                "tunnelRemotePort" to 39001,
                "gameMode" to PaperGameMode.Creative.name,
                "difficulty" to PaperDifficulty.Hard.name,
                "onlineMode" to false,
                "pvpEnabled" to false,
                "serverPropertiesOverride" to "motd=custom",
            ),
        )

        assertThat(server.id).isEqualTo("server-demo")
        assertThat(server.tunnelRemotePort).isEqualTo(39001)
        assertThat(server.gameMode).isEqualTo(PaperGameMode.Creative)
        assertThat(server.difficulty).isEqualTo(PaperDifficulty.Hard)
        assertThat(server.onlineMode).isFalse()
        assertThat(server.pvpEnabled).isFalse()
        assertThat(server.serverPropertiesOverride).isEqualTo("motd=custom")
        assertThat(server.runtimeSlot).isEqualTo(2)
    }

    @Test
    fun serviceIntentRoundTrip_supportsFabricServerType() {
        val server = decodeServerCardStateExtrasForTest(
            mapOf(
                "id" to "server-fabric",
                "name" to "Fabric服",
                "serverType" to "Fabric",
                "minecraftVersion" to "1.21.4",
                "maxPlayers" to 20,
                "memoryMb" to 2048,
                "port" to 25568,
                "worldName" to "fabric_world",
                "javaMajorVersion" to 21,
            ),
        )

        assertThat(server.id).isEqualTo("server-fabric")
        assertThat(server.serverType.name).isEqualTo("Fabric")
        assertThat(server.edition).isEqualTo("Fabric 1.21.4")
        assertThat(server.worldName).isEqualTo("fabric_world")
    }

    @Test
    fun tunnelIntentRoundTrip_preservesPastedConfigFixedPorts() {
        val tunnel = decodeTunnelProfileExtrasForTest(
            mapOf(
                "tunnel.id" to "frp-pasted",
                "tunnel.name" to "单隧道 FRP",
                "tunnel.kind" to TunnelKind.Frp.name,
                "tunnel.source" to TunnelSource.PastedConfig.name,
                "tunnel.format" to TunnelConfigFormat.Toml.name,
                "tunnel.serverAddress" to "frp.example.com:7000",
                "tunnel.remotePort" to 39001,
                "tunnel.localPort" to 25565,
                "tunnel.credentialValue" to "secret-token",
                "tunnel.portRange" to "",
                "tunnel.detail" to "固定映射",
            ),
        )

        assertThat(tunnel).isNotNull()
        assertThat(tunnel!!.source).isEqualTo(TunnelSource.PastedConfig)
        assertThat(tunnel.remotePort).isEqualTo(39001)
        assertThat(tunnel.localPort).isEqualTo(25565)
    }

    @Test
    fun tunnelIntentRoundTrip_preservesMultipleTunnelProfilesForRuntimeLaunch() {
        val tunnels = decodeTunnelProfilesExtrasForTest(
            mapOf(
                "tunnelCount" to 2,
                "tunnels.0.id" to "frp-home",
                "tunnels.0.name" to "家庭 FRP",
                "tunnels.0.kind" to TunnelKind.Frp.name,
                "tunnels.0.source" to TunnelSource.ManualServer.name,
                "tunnels.0.serverAddress" to "frp.home:7000",
                "tunnels.0.remotePort" to 39001,
                "tunnels.0.localPort" to 25565,
                "tunnels.0.credentialValue" to "token-home",
                "tunnels.0.portRange" to "39001-39099",
                "tunnels.0.detail" to "家庭映射",
                "tunnels.1.id" to "frp-ali",
                "tunnels.1.name" to "阿里云 FRP",
                "tunnels.1.kind" to TunnelKind.Frp.name,
                "tunnels.1.source" to TunnelSource.PastedConfig.name,
                "tunnels.1.format" to TunnelConfigFormat.Toml.name,
                "tunnels.1.serverAddress" to "frp.ali:7001",
                "tunnels.1.remotePort" to 40001,
                "tunnels.1.localPort" to 25565,
                "tunnels.1.credentialValue" to "token-ali",
                "tunnels.1.portRange" to "",
                "tunnels.1.detail" to "固定映射",
            ),
        )

        assertThat(tunnels.map(TunnelProfile::id)).containsExactly("frp-home", "frp-ali").inOrder()
        assertThat(tunnels.map(TunnelProfile::remotePort)).containsExactly(39001, 40001).inOrder()
        assertThat(tunnels.map(TunnelProfile::source)).containsExactly(TunnelSource.ManualServer, TunnelSource.PastedConfig).inOrder()
    }

    @Test
    fun hydrateLaunchTunnelProfiles_recoversStoredCredentialWithoutLosingRuntimePortOverride() {
        val stored = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.home:7000",
            credentialValue = "secret-token",
            portRange = "39001-39099",
        ).copy(id = "frp-home")
        val launch = stored.copy(remotePort = 39008, credentialValue = null, detail = "运行时覆写")

        val hydrated = hydrateLaunchTunnelProfilesForTest(
            storedProfiles = listOf(stored),
            launchProfiles = listOf(launch),
        ).single()

        assertThat(hydrated.id).isEqualTo("frp-home")
        assertThat(hydrated.remotePort).isEqualTo(39008)
        assertThat(hydrated.credentialValue).isEqualTo("secret-token")
        assertThat(hydrated.detail).isEqualTo("运行时覆写")
    }
}
