package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class TunnelModelsTest {

    @Test
    fun importTunnelProfile_detectsFrpTomlAndExtractsSingleTunnelPorts() {
        val rawConfig = """
            serverAddr = "frp.example.com"
            serverPort = 7000

            [[proxies]]
            name = "creative-plot"
            type = "tcp"
            localPort = 25565
            remotePort = 37001
        """.trimIndent()

        val imported = importTunnelProfile(
            rawConfig = rawConfig,
            fallbackName = "Creative Plot Config",
        )

        assertThat(imported.kind).isEqualTo(TunnelKind.Frp)
        assertThat(imported.source).isEqualTo(TunnelSource.PastedConfig)
        assertThat(imported.format).isEqualTo(TunnelConfigFormat.Toml)
        assertThat(imported.name).isEqualTo("creative-plot")
        assertThat(imported.localPort).isEqualTo(25565)
        assertThat(imported.remotePort).isEqualTo(37001)
        assertThat(imported.supportsCustomPortOnStart()).isFalse()
    }

    @Test
    fun manualServerTunnel_supportsCustomPortDuringStartup() {
        val profile = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.home",
            remotePort = 39001,
            baseLatencyMs = 42,
        )

        assertThat(profile.supportsCustomPortOnStart()).isTrue()
        assertThat(profile.resolveStartupPort(serverPort = 25565, customPort = 25577)).isEqualTo(25577)
    }

    @Test
    fun pastedTunnel_ignoresCustomPortAndUsesParsedLocalPort() {
        val profile = TunnelProfile(
            id = "cfg-1",
            name = "单隧道配置",
            kind = TunnelKind.Frp,
            source = TunnelSource.PastedConfig,
            format = TunnelConfigFormat.Json,
            protocol = TunnelProtocol.Tcp,
            serverAddress = "frp.cloud",
            remotePort = 39002,
            localPort = 25590,
            baseLatencyMs = 56,
            currentLatencyMs = 56,
            healthLabel = "稳定",
            rawConfigPreview = "{...}",
            detail = "来自粘贴配置",
        )

        assertThat(profile.resolveStartupPort(serverPort = 25565, customPort = 25580)).isEqualTo(25590)
        assertThat(profile.startupModeLabel()).isEqualTo("单隧道")
    }

    @Test
    fun serverRuntimeState_restoresDefaultPortAndClearsActiveTunnelWhenStopped() {
        val server = ServerCardState(
            name = "Modpack Test",
            edition = "Forge 1.20.1",
            worldName = "Redstone Lab",
            port = 25567,
            defaultPort = 25567,
            onlinePlayers = 0,
            maxPlayers = 8,
            memoryLabel = "3.0 GB RAM",
            isOnline = false,
        )
        val tunnel = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.home",
            remotePort = 39001,
            baseLatencyMs = 42,
        )

        val started = server.startWithTunnel(tunnel = tunnel, startupPort = 25579)
        val stopped = started.stopServer()
        val directRestart = stopped.startWithTunnel(tunnel = null, startupPort = null)

        assertThat(started.port).isEqualTo(25579)
        assertThat(stopped.port).isEqualTo(25567)
        assertThat(stopped.activeTunnelLabel).isNull()
        assertThat(stopped.selectedTunnelId).isEqualTo(tunnel.id)
        assertThat(directRestart.port).isEqualTo(25567)
    }
}
