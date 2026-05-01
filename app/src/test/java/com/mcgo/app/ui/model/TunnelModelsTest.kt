package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class TunnelModelsTest {

    @Test
    fun importTunnelProfile_detectsFrpTomlAndBuildsServiceEndpointFromServerPort() {
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
        assertThat(imported.serverAddress).isEqualTo("frp.example.com:7000")
        assertThat(imported.localPort).isEqualTo(25565)
        assertThat(imported.remotePort).isEqualTo(37001)
        assertThat(imported.rawConfigText).isEqualTo(rawConfig)
        assertThat(imported.currentLatencyMs).isEqualTo(0)
        assertThat(imported.healthLabel).isEqualTo("检测中")
        assertThat(imported.supportsCustomPortOnStart()).isFalse()
    }

    @Test
    fun manualServerTunnel_usesEndpointWithPortAndStartsWithPendingRealLatency() {
        val profile = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.home:7000",
            credentialValue = "secret-token",
            portRange = "38000-38100",
        )

        assertThat(profile.credentialValue).isEqualTo("secret-token")
        assertThat(profile.portRange).isEqualTo("38000-38100")
        assertThat(profile.connectionSummary()).isEqualTo("frp.home:7000 · 端口范围 38000-38100")
        assertThat(profile.currentLatencyMs).isEqualTo(0)
        assertThat(profile.healthLabel).isEqualTo("检测中")
        assertThat(profile.detailSummary()).contains("Token")
        assertThat(profile.supportsCustomPortOnStart()).isTrue()
        assertThat(profile.resolveStartupPort(serverPort = 25565, customPort = 25577)).isEqualTo(25577)
    }

    @Test
    fun manualTunnelFieldSpec_requiresEndpointWithPortForDifferentTunnelKinds() {
        val frpSpec = manualTunnelFieldSpec(TunnelKind.Frp)
        val npsSpec = manualTunnelFieldSpec(TunnelKind.Nps)

        assertThat(frpSpec.addressLabel).isEqualTo("服务端地址（IP/域名:端口）")
        assertThat(frpSpec.addressHint).contains("frp.example.com:7000")
        assertThat(frpSpec.credentialLabel).isEqualTo("Token")
        assertThat(frpSpec.portRangeLabel).isEqualTo("可分配端口范围")
        assertThat(npsSpec.addressLabel).isEqualTo("服务端地址（IP/域名:端口）")
        assertThat(npsSpec.credentialLabel).isEqualTo("VKey")
        assertThat(npsSpec.portRangeLabel).contains("端口")
    }

    @Test
    fun latencyProbeResult_updatesHealthLabelsWithoutFakeSimulation() {
        val profile = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.home:7000",
            credentialValue = "token-1",
            portRange = "39001-39020",
        )

        val reachable = profile.withLatencyResult(42)
        val unreachable = profile.withLatencyResult(null)

        assertThat(reachable.currentLatencyMs).isEqualTo(42)
        assertThat(reachable.latencyLabel()).isEqualTo("42 ms")
        assertThat(reachable.healthLabel).isEqualTo("稳定")
        assertThat(unreachable.currentLatencyMs).isEqualTo(-1)
        assertThat(unreachable.latencyLabel()).isEqualTo("不可达")
        assertThat(unreachable.healthLabel).isEqualTo("不可达")
        assertThat(unreachable.latencyBadgeLines()).containsExactly("不可达")
        assertThat(reachable.latencyBadgeLines()).containsExactly("42 ms", "稳定").inOrder()
    }

    @Test
    fun pastedTunnel_ignoresCustomPortAndUsesParsedLocalPort() {
        val profile = TunnelProfile(
            id = "cfg-1",
            name = "单隧道配置",
            kind = TunnelKind.Frp,
            source = TunnelSource.PastedConfig,
            format = TunnelConfigFormat.Json,
            serverAddress = "frp.cloud:7000",
            remotePort = 39002,
            localPort = 25590,
            baseLatencyMs = 0,
            currentLatencyMs = 0,
            healthLabel = "检测中",
            rawConfigPreview = "{...}",
            rawConfigText = "{...}",
            detail = "来自粘贴配置",
        )

        assertThat(profile.resolveStartupPort(serverPort = 25565, customPort = 25580)).isEqualTo(25590)
        assertThat(profile.startupModeLabel()).isEqualTo("单隧道")
    }

    @Test
    fun latencyResults_mergeIntoLatestTunnelStateWithoutOverwritingEdits() {
        val original = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.home:7000",
            credentialValue = "old-token",
            portRange = "38000-38100",
        ).copy(id = "frp-home")
        val edited = original.copy(
            name = "家庭 FRP 主线",
            credentialValue = "new-token",
            portRange = "39000-39100",
        )

        val merged = applyTunnelLatencyResults(
            profiles = listOf(edited),
            results = listOf(TunnelLatencyResult(tunnelId = original.id, serverAddress = original.serverAddress, latencyMs = 36)),
        )
        val ignoredStaleEndpoint = applyTunnelLatencyResults(
            profiles = listOf(edited.copy(serverAddress = "frp-new.home:7000")),
            results = listOf(TunnelLatencyResult(tunnelId = original.id, serverAddress = original.serverAddress, latencyMs = 36)),
        )

        assertThat(merged.single().name).isEqualTo("家庭 FRP 主线")
        assertThat(merged.single().credentialValue).isEqualTo("new-token")
        assertThat(merged.single().portRange).isEqualTo("39000-39100")
        assertThat(merged.single().currentLatencyMs).isEqualTo(36)
        assertThat(ignoredStaleEndpoint.single().currentLatencyMs).isEqualTo(0)
    }

    @Test
    fun upsertAndDeleteTunnelProfile_supportEditingAndClearingServerSelections() {
        val original = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.home:7000",
            credentialValue = "old-token",
            portRange = "38000-38100",
        ).copy(id = "frp-home")
        val updated = original.copy(name = "家庭 FRP 主线", credentialValue = "new-token")
        val server = ServerCardState(
            name = "Creative Plot",
            edition = "Java 1.20.6",
            worldName = "Sky Blocks",
            port = 25566,
            defaultPort = 25566,
            onlinePlayers = 2,
            maxPlayers = 10,
            memoryLabel = "1.5 GB RAM",
            isOnline = true,
            selectedTunnelId = original.id,
            activeTunnelLabel = "家庭 FRP · 38 ms",
        )

        val upserted = upsertTunnelProfile(listOf(original), updated)
        val remaining = removeTunnelProfile(upserted, original.id)
        val detachedServers = detachDeletedTunnel(listOf(server), original.id)

        assertThat(upserted).hasSize(1)
        assertThat(upserted.single().name).isEqualTo("家庭 FRP 主线")
        assertThat(upserted.single().credentialValue).isEqualTo("new-token")
        assertThat(remaining).isEmpty()
        assertThat(detachedServers.single().selectedTunnelId).isNull()
        assertThat(detachedServers.single().activeTunnelLabel).isNull()
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
            serverAddress = "frp.home:7000",
            credentialValue = "token-1",
            portRange = "39001-39020",
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

    @Test
    fun launchingServer_isRuntimeBusyAndStopResetsBusyState() {
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
        )

        val launching = server.startWithTunnel(tunnel = null, startupPort = 25566)
        val stopped = launching.stopServer()

        assertThat(canStartServerFromUi(launching)).isFalse()
        assertThat(launching.isRuntimeBusy()).isTrue()
        assertThat(canStartServerFromUi(stopped)).isTrue()
        assertThat(stopped.isRuntimeBusy()).isFalse()
    }
}
