package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.JavaSelectionMode
import com.mcgo.app.ui.model.TunnelKind
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.startPaperServer
import kotlin.test.Test

class MultiServerRuntimePlanningTest {

    @Test
    fun allocateRuntimeSlot_picksFirstFreeSlotForAnotherBusyServer() {
        val running = createPaperServer("A服", "1.21.11", 20, 2048).copy(runtimeSlot = 1)
            .startPaperServer(tunnel = null, startupPort = 25565)
        val candidate = createPaperServer("B服", "1.21.11", 20, 2048)

        val slot = allocateRuntimeSlot(
            servers = listOf(running, candidate),
            targetServerId = candidate.id,
            maxSlots = 4,
        )

        assertThat(slot).isEqualTo(2)
    }

    @Test
    fun allocateRuntimeSlot_returnsNullWhenAllSlotsBusy() {
        val servers = (1..4).map { index ->
            createPaperServer("服$index", "1.21.11", 20, 2048).copy(runtimeSlot = index)
                .startPaperServer(tunnel = null, startupPort = 25564 + index)
        }
        val candidate = createPaperServer("新服", "1.21.11", 20, 2048)

        val slot = allocateRuntimeSlot(
            servers = servers + candidate,
            targetServerId = candidate.id,
            maxSlots = 4,
        )

        assertThat(slot).isNull()
    }

    @Test
    fun buildFrpcConfigForManualTunnel_bindsSelectedServerPortAndRemotePort() {
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 38001,
            javaSelectionMode = JavaSelectionMode.Recommended,
        )
        val tunnel = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.example.com:7000",
            credentialValue = "my-secret-token",
            portRange = "38000-38100",
        )

        val toml = buildFrpcConfigForTunnel(server, tunnel)

        assertThat(toml).contains("serverAddr = \"frp.example.com\"")
        assertThat(toml).contains("serverPort = 7000")
        assertThat(toml).contains("token = \"my-secret-token\"")
        assertThat(toml).contains("localPort = 38001")
        assertThat(toml).contains("remotePort = 38001")
        assertThat(toml).contains("type = \"tcp\"")
    }

    @Test
    fun buildFrpcConfigForManualTunnel_trimsCredentialBeforeWritingToml() {
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 38001,
            javaSelectionMode = JavaSelectionMode.Recommended,
        )
        val tunnel = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.example.com:7000",
            credentialValue = "  my-secret-token\n",
            portRange = "38000-38100",
        )

        val toml = buildFrpcConfigForTunnel(server, tunnel)

        assertThat(toml).contains("auth.token = \"my-secret-token\"")
        assertThat(toml).doesNotContain("auth.token = \"  my-secret-token")
    }

    @Test
    fun buildFrpcConfigForLoadedTunnel_trimsCredentialBeforeWritingToml() {
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 38001,
            javaSelectionMode = JavaSelectionMode.Recommended,
        )
        val tunnel = TunnelProfile(
            id = "frp-home",
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            source = com.mcgo.app.ui.model.TunnelSource.ManualServer,
            serverAddress = "frp.example.com:7000",
            credentialValue = "  my-secret-token\n",
            portRange = "38000-38100",
        )

        val toml = buildFrpcConfigForTunnel(server, tunnel)

        assertThat(toml).contains("auth.token = \"my-secret-token\"")
        assertThat(toml).doesNotContain("auth.token = \"  my-secret-token")
    }

    @Test
    fun extractBundledFrpcBinaryName_returnsArm64AssetPath() {
        assertThat(defaultBundledFrpcAssetRelativePath("arm64-v8a")).isEqualTo("frp/android_arm64/frpc")
    }
}
