package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.model.TunnelConfigFormat
import com.mcgo.app.ui.model.TunnelKind
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
}
