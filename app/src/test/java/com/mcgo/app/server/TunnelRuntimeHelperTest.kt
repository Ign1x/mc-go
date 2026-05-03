package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.TunnelKind
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.createPaperServer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TunnelRuntimeHelperTest {
    @Test
    fun nativeLibraryExecutableNameForAsset_mapsBundledFrpcToLibfrpcSo() {
        assertThat(nativeLibraryExecutableNameForAsset("frp/android_arm64/frpc")).isEqualTo("libfrpc.so")
        assertThat(nativeLibraryExecutableNameForAsset("libalready.so")).isEqualTo("libalready.so")
    }

    @Test
    fun tunnelRuntimePlanForStart_buildsRealFrpcPlanForSelectedFrpTunnel() {
        val filesDir = Files.createTempDirectory("mcgo-frpc-plan")
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25577,
        )
        val tunnel = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.example.com:7000",
            credentialValue = "secret-token",
            portRange = "38000-38100",
        ).copy(remotePort = 38001)

        val nativeLibraryDir = Path.of("/data/app/com.mcgo.app/lib/arm64")
        val plan = tunnelRuntimePlanForStart(
            filesDir = filesDir,
            nativeLibraryDir = nativeLibraryDir,
            server = server,
            tunnel = tunnel,
            supportedAbi = "arm64-v8a",
        )

        assertThat(plan).isNotNull()
        assertThat(plan!!.displayLabel).isEqualTo("家庭 FRP · frp.example.com:38001")
        assertThat(plan.binaryPath).isEqualTo(nativeLibraryDir.resolve("libfrpc.so"))
        assertThat(plan.extractedBinaryPath).isEqualTo(filesDir.resolve("servers/${server.id}/frp/bin/frpc"))
        assertThat(plan.configPath).isEqualTo(filesDir.resolve("servers/${server.id}/frp/frpc.toml"))
        assertThat(plan.configText).contains("remotePort = 38001")
    }

    @Test
    fun tunnelRuntimePlanForStart_rejectsUnsupportedTunnelKindsInsteadOfPretendingSuccess() {
        val filesDir = Files.createTempDirectory("mcgo-frpc-plan")
        val server = createPaperServer("生存服", "1.21.11", 20, 2048)
        val tunnel = TunnelProfile.manualServer(
            name = "Playit",
            kind = TunnelKind.Playit,
            serverAddress = "playit.gg:443",
            credentialValue = "agent-key",
            portRange = "25565-25585",
        )

        val error = assertFailsWith<JavaRuntimeInstallException> {
            tunnelRuntimePlanForStart(
                filesDir = filesDir,
                nativeLibraryDir = Path.of("/data/app/com.mcgo.app/lib/arm64"),
                server = server,
                tunnel = tunnel,
                supportedAbi = "arm64-v8a",
            )
        }

        assertThat(error).hasMessageThat().contains("当前仅支持 FRP")
    }
}
