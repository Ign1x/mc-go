package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.server.PaperServerEvent
import com.mcgo.app.server.PaperServerEventStatus
import com.mcgo.app.server.reducePaperRuntimeEvent
import kotlin.test.Test

class ServerModelsTest {

    @Test
    fun createPaperServer_buildsVanillaPaperInstanceWithRecommendedJava() {
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
        )

        assertThat(server.name).isEqualTo("生存服")
        assertThat(server.serverType).isEqualTo(MinecraftServerType.Paper)
        assertThat(server.edition).isEqualTo("Paper 1.21.4")
        assertThat(server.minecraftVersion).isEqualTo("1.21.4")
        assertThat(server.javaMajorVersion).isEqualTo(21)
        assertThat(server.worldName).isEqualTo("world")
        assertThat(server.defaultPort).isEqualTo(25565)
        assertThat(server.maxPlayers).isEqualTo(20)
        assertThat(server.memoryLabel).isEqualTo("2.0 GB RAM")
        assertThat(server.memoryMb).isEqualTo(2048)
        assertThat(server.id).startsWith("server-")
        assertThat(server.isOnline).isFalse()
        assertThat(server.launchStatus).isEqualTo(ServerLaunchStatus.Ready)
    }

    @Test
    fun startPaperServer_entersLaunchingStateWithProgressAndLogsBeforeOnline() {
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
        )

        val started = server.startPaperServer(tunnel = null, startupPort = 25566)

        assertThat(started.isOnline).isFalse()
        assertThat(started.port).isEqualTo(25566)
        assertThat(started.launchStatus).isEqualTo(ServerLaunchStatus.Launching)
        assertThat(started.launchProgress).isAtLeast(1)
        assertThat(started.runtimeLogs).isNotEmpty()
        assertThat(started.launchPlan?.serverJarName).isEqualTo("paper-1.21.4.jar")
        assertThat(started.launchPlan?.javaMajorVersion).isEqualTo(21)
        assertThat(started.launchPlan?.arguments).contains("-Xmx2048M")
        assertThat(started.launchPlan?.arguments).contains("nogui")
        assertThat(started.activeTunnelLabel).isNull()
    }

    @Test
    fun canStartServerFromUi_rejectsLaunchingOrRunningServerButAllowsReadyOrFailedState() {
        val ready = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
        )
        val launching = ready.startPaperServer(tunnel = null, startupPort = 25566)
        val running = launching.markLaunchRunning()
        val failed = ready.markLaunchFailed("boom")

        assertThat(canStartServerFromUi(ready)).isTrue()
        assertThat(canStartServerFromUi(launching)).isFalse()
        assertThat(canStartServerFromUi(running)).isFalse()
        assertThat(canStartServerFromUi(failed)).isTrue()
    }

    @Test
    fun recommendedJavaMajorVersion_matchesPaperCompatibilityTable() {
        assertThat(recommendedJavaMajorVersion("1.11")).isEqualTo(8)
        assertThat(recommendedJavaMajorVersion("1.12.2")).isEqualTo(11)
        assertThat(recommendedJavaMajorVersion("1.16.4")).isEqualTo(11)
        assertThat(recommendedJavaMajorVersion("1.16.5")).isEqualTo(11)
        assertThat(recommendedJavaMajorVersion("1.17.1")).isEqualTo(17)
        assertThat(recommendedJavaMajorVersion("1.19.4")).isEqualTo(17)
        assertThat(recommendedJavaMajorVersion("1.20.1")).isEqualTo(21)
        assertThat(recommendedJavaMajorVersion("1.21.4")).isEqualTo(21)
        assertThat(recommendedJavaMajorVersion("1.21.11")).isEqualTo(21)
        assertThat(recommendedJavaMajorVersion("26.1.2")).isEqualTo(25)
    }

    @Test
    fun reducePaperRuntimeEvent_failedClearsTransientPortAndTunnelState() {
        val tunnel = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.example.com:7000",
            credentialValue = "secret-token",
            portRange = "38000-38100",
        )
        val launching = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
        ).startPaperServer(tunnel = tunnel, startupPort = 25577)

        val reduced = reducePaperRuntimeEvent(
            launching,
            PaperServerEvent(
                serverId = launching.id,
                status = PaperServerEventStatus.Failed,
                progress = 0,
                message = "JLI_Launch failed",
            ),
        )

        assertThat(reduced.launchStatus).isEqualTo(ServerLaunchStatus.Failed)
        assertThat(reduced.port).isEqualTo(launching.defaultPort)
        assertThat(reduced.activeTunnelLabel).isNull()
    }

    @Test
    fun reducePaperRuntimeEvent_stoppedClearsTransientPortAndTunnelState() {
        val tunnel = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.example.com:7000",
            credentialValue = "secret-token",
            portRange = "38000-38100",
        )
        val launching = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
        ).startPaperServer(tunnel = tunnel, startupPort = 25577)

        val reduced = reducePaperRuntimeEvent(
            launching,
            PaperServerEvent(
                serverId = launching.id,
                status = PaperServerEventStatus.Stopped,
                progress = 0,
                message = "Paper 已退出",
            ),
        )

        assertThat(reduced.launchStatus).isEqualTo(ServerLaunchStatus.Stopped)
        assertThat(reduced.port).isEqualTo(launching.defaultPort)
        assertThat(reduced.activeTunnelLabel).isNull()
    }

    @Test
    fun reducePaperRuntimeEvent_stoppingPreservesRuntimeBindingUntilRuntimeActuallyExits() {
        val tunnel = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.example.com:7000",
            credentialValue = "secret-token",
            portRange = "38000-38100",
        )
        val launching = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
        ).startPaperServer(tunnel = tunnel, startupPort = 25577)
        val running = launching.markLaunchRunning("Paper 已监听 127.0.0.1:25577")

        val reduced = reducePaperRuntimeEvent(
            running,
            PaperServerEvent(
                serverId = running.id,
                status = PaperServerEventStatus.Stopping,
                progress = 0,
                message = "已请求停止内置 Paper 进程，等待运行时退出",
            ),
        )

        assertThat(reduced.launchStatus).isEqualTo(ServerLaunchStatus.Stopping)
        assertThat(reduced.isOnline).isTrue()
        assertThat(reduced.port).isEqualTo(running.port)
        assertThat(reduced.activeTunnelLabel).isEqualTo(running.activeTunnelLabel)
        assertThat(reduced.runtimeLogs.last()).contains("已请求停止")
    }

    @Test
    fun reducePaperRuntimeEvent_launchingStatusDoesNotFlipServerToFailedForConsoleFeedback() {
        val launching = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
        ).startPaperServer(tunnel = null, startupPort = 25565)

        val reduced = reducePaperRuntimeEvent(
            launching,
            PaperServerEvent(
                serverId = launching.id,
                status = null,
                progress = null,
                message = "当前 Paper 进程尚未接收标准输入，请稍后再试",
            ),
        )

        assertThat(reduced.launchStatus).isEqualTo(ServerLaunchStatus.Launching)
        assertThat(reduced.isOnline).isFalse()
        assertThat(reduced.runtimeLogs.last()).contains("标准输入")
    }

    @Test
    fun pendingDeletion_blocksRestartUntilTerminalCleanupCompletes() {
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
        )
        val pending = requestServerDeletion(server)
        val finalized = finalizePendingServerDeletion(listOf(pending))

        assertThat(pending.pendingDeletion).isTrue()
        assertThat(canStartServerFromUi(pending)).isFalse()
        assertThat(finalized).isEmpty()
    }

    @Test
    fun applyPaperServerEdits_updatesProfileButPreservesRuntimeState() {
        val original = createPaperServer(
            name = "旧生存服",
            minecraftVersion = "1.20.1",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
            worldName = "world",
        ).copy(
            id = "survival",
            isOnline = true,
            selectedTunnelId = "frp-home",
            activeTunnelLabel = "家庭 FRP · 38 ms",
            launchStatus = ServerLaunchStatus.Running,
            launchProgress = 100,
            runtimeLogs = listOf("Paper 已启动"),
            runtimeLogPath = "/tmp/mcgo.log",
        )

        val edited = applyPaperServerEdits(
            server = original,
            name = "新生存服",
            minecraftVersion = "1.16.5",
            maxPlayers = 12,
            memoryMb = 1024,
            port = 25570,
            worldName = "world_nether",
        )

        assertThat(edited.id).isEqualTo("survival")
        assertThat(edited.name).isEqualTo("新生存服")
        assertThat(edited.edition).isEqualTo("Paper 1.16.5")
        assertThat(edited.minecraftVersion).isEqualTo("1.16.5")
        assertThat(edited.javaMajorVersion).isEqualTo(11)
        assertThat(edited.javaSelectionMode).isEqualTo(JavaSelectionMode.Recommended)
        assertThat(edited.maxPlayers).isEqualTo(12)
        assertThat(edited.memoryMb).isEqualTo(1024)
        assertThat(edited.memoryLabel).isEqualTo("1.0 GB RAM")
        assertThat(edited.defaultPort).isEqualTo(25570)
        assertThat(edited.worldName).isEqualTo("world_nether")
        assertThat(edited.isOnline).isEqualTo(true)
        assertThat(edited.port).isEqualTo(original.port)
        assertThat(edited.selectedTunnelId).isEqualTo("frp-home")
        assertThat(edited.activeTunnelLabel).isEqualTo("家庭 FRP · 38 ms")
        assertThat(edited.launchStatus).isEqualTo(ServerLaunchStatus.Running)
        assertThat(edited.runtimeLogs).containsExactlyElementsIn(listOf("Paper 已启动"))
        assertThat(edited.runtimeLogPath).isEqualTo("/tmp/mcgo.log")
    }

    @Test
    fun applyPaperServerEdits_preservesManualJavaOverrideAcrossMinecraftVersionChanges() {
        val original = createPaperServer(
            name = "旧生存服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
        ).copy(
            javaMajorVersion = 17,
            javaSelectionMode = JavaSelectionMode.Manual,
        )

        val edited = applyPaperServerEdits(
            server = original,
            name = "新生存服",
            minecraftVersion = "26.1.1",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
            worldName = "world",
        )

        assertThat(edited.javaMajorVersion).isEqualTo(17)
        assertThat(edited.javaSelectionMode).isEqualTo(JavaSelectionMode.Manual)
    }

    @Test
    fun resolveServerConsoleText_prefersRuntimeLogFileAndFallsBackToInMemoryLogs() {
        val logFile = java.nio.file.Files.createTempFile("mcgo-console", ".log")
        java.nio.file.Files.write(logFile, "line-a\nline-b\n".toByteArray())
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
        ).copy(
            runtimeLogs = listOf("fallback-1", "fallback-2"),
            runtimeLogPath = logFile.toString(),
        )

        assertThat(resolveServerConsoleText(server)).contains("line-a")

        val missing = server.copy(runtimeLogPath = logFile.resolveSibling("missing.log").toString())
        assertThat(resolveServerConsoleText(missing)).isEqualTo("fallback-1\nfallback-2")
    }
}
