package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.server.PaperServerEvent
import com.mcgo.app.server.PaperServerEventStatus
import com.mcgo.app.server.buildServerProperties
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
    }

    @Test
    fun createVanillaServer_buildsOfficialVanillaInstanceWithRecommendedJava() {
        val server = createVanillaServer(
            name = "原版服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
        )

        assertThat(server.name).isEqualTo("原版服")
        assertThat(server.serverType).isEqualTo(MinecraftServerType.Vanilla)
        assertThat(server.edition).isEqualTo("Vanilla 1.21.4")
        assertThat(server.minecraftVersion).isEqualTo("1.21.4")
        assertThat(server.javaMajorVersion).isEqualTo(21)
        assertThat(server.gameMode).isEqualTo(PaperGameMode.Survival)
        assertThat(server.difficulty).isEqualTo(PaperDifficulty.Normal)
        assertThat(server.onlineMode).isTrue()
        assertThat(server.pvpEnabled).isTrue()
        assertThat(server.worldName).isEqualTo("world")
        assertThat(server.defaultPort).isEqualTo(25565)
        assertThat(server.tunnelRemotePort).isNull()
        assertThat(server.maxPlayers).isEqualTo(20)
        assertThat(server.memoryLabel).isEqualTo("2.0 GB RAM")
        assertThat(server.memoryMb).isEqualTo(2048)
        assertThat(server.id).startsWith("server-")
        assertThat(server.isOnline).isFalse()
        assertThat(server.launchStatus).isEqualTo(ServerLaunchStatus.Ready)
    }

    @Test
    fun createPurpurServer_buildsPurpurInstanceWithRecommendedJava() {
        val server = createPurpurServer(
            name = "Purpur服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
        )

        assertThat(server.name).isEqualTo("Purpur服")
        assertThat(server.serverType).isEqualTo(MinecraftServerType.Purpur)
        assertThat(server.edition).isEqualTo("Purpur 1.21.4")
        assertThat(server.minecraftVersion).isEqualTo("1.21.4")
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
    fun startPaperServer_withTunnelUsesIndependentRemotePortForRuntimeAddress() {
        val tunnel = TunnelProfile.manualServer(
            name = "家庭 FRP",
            kind = TunnelKind.Frp,
            serverAddress = "frp.example.com:7000",
            credentialValue = "secret-token",
            portRange = "38000-38100",
        )
        val started = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
        ).copy(tunnelRemotePort = 38009)
            .startPaperServer(tunnel = tunnel, startupPort = 25577)

        assertThat(started.port).isEqualTo(25577)
        assertThat(started.runtimeAddress).isEqualTo("frp.example.com:38009")
        assertThat(started.runtimeLogs.last()).contains("远端端口 38009")
    }

    @Test
    fun applyPaperServerEdits_updatesStructuredGameRulesAndJavaSelection() {
        val edited = applyPaperServerEdits(
            server = createPaperServer("生存服", "1.21.4", 20, 2048),
            name = "创造服",
            minecraftVersion = "1.21.11",
            maxPlayers = 12,
            memoryMb = 3072,
            port = 25570,
            worldName = "creative_world",
            javaMajorVersion = 17,
            javaSelectionMode = JavaSelectionMode.Manual,
            gameMode = PaperGameMode.Creative,
            difficulty = PaperDifficulty.Peaceful,
            onlineMode = false,
            pvpEnabled = false,
        )

        assertThat(edited.name).isEqualTo("创造服")
        assertThat(edited.minecraftVersion).isEqualTo("1.21.11")
        assertThat(edited.maxPlayers).isEqualTo(12)
        assertThat(edited.memoryMb).isEqualTo(3072)
        assertThat(edited.defaultPort).isEqualTo(25570)
        assertThat(edited.worldName).isEqualTo("creative_world")
        assertThat(edited.javaMajorVersion).isEqualTo(17)
        assertThat(edited.javaSelectionMode).isEqualTo(JavaSelectionMode.Manual)
        assertThat(edited.gameMode).isEqualTo(PaperGameMode.Creative)
        assertThat(edited.difficulty).isEqualTo(PaperDifficulty.Peaceful)
        assertThat(edited.onlineMode).isFalse()
        assertThat(edited.pvpEnabled).isFalse()
    }

    @Test
    fun buildPaperServerPropertiesEditorText_outputsDocumentedFullTemplate() {
        val server = createPaperServer("生存服", "1.21.11", 20, 2048)
        val text = buildPaperServerPropertiesEditorText(server)

        assertThat(text).contains("# MC-GO server.properties 模板")
        assertThat(text).contains("# MC-GO: motd - 服务器列表中显示的名称")
        assertThat(text).contains("motd=生存服")
        assertThat(text).contains("# MC-GO: view-distance - 客户端视距，数值越高越吃内存/CPU")
        assertThat(text).contains("view-distance=8")
        assertThat(text).contains("simulation-distance=4")
        assertThat(text).contains("spawn-protection=16")
        assertThat(text).contains("allow-nether=true")
        assertThat(text).contains("enable-command-block=true")
        assertThat(text).contains("allow-flight=true")
        assertThat(text).contains("white-list=false")
        assertThat(text).contains("enforce-secure-profile=true")
        assertThat(text).contains("sync-chunk-writes=true")
        assertThat(text).contains("accepts-transfers=false")
        assertThat(text).contains("enable-code-of-conduct=false")
        assertThat(text).contains("management-server-enabled=false")
        assertThat(text).contains("management-server-tls-enabled=true")
        assertThat(text).contains("status-heartbeat-interval=0")
        assertThat(text).contains("text-filtering-version=0")
        assertThat(text).doesNotContain("accept-transfers=")
        assertThat(text.lineSequence().count { it.contains("=") }).isAtLeast(58)
    }

    @Test
    fun buildPaperServerPropertiesEditorText_coversCurrentJavaServerPropertiesDefaults() {
        val server = createPaperServer("生存服", "1.21.11", 20, 2048)
        val editorProperties = parsePropertyMap(buildPaperServerPropertiesEditorText(server))

        listOf(
            "accepts-transfers",
            "allow-flight",
            "broadcast-console-to-ops",
            "broadcast-rcon-to-ops",
            "bug-report-link",
            "difficulty",
            "enable-code-of-conduct",
            "enable-jmx-monitoring",
            "enable-query",
            "enable-rcon",
            "enable-status",
            "enforce-secure-profile",
            "enforce-whitelist",
            "entity-broadcast-range-percentage",
            "force-gamemode",
            "function-permission-level",
            "gamemode",
            "generate-structures",
            "generator-settings",
            "hardcore",
            "hide-online-players",
            "initial-disabled-packs",
            "initial-enabled-packs",
            "level-name",
            "level-seed",
            "level-type",
            "log-ips",
            "management-server-allowed-origins",
            "management-server-enabled",
            "management-server-host",
            "management-server-port",
            "management-server-secret",
            "management-server-tls-enabled",
            "management-server-tls-keystore",
            "management-server-tls-keystore-password",
            "max-chained-neighbor-updates",
            "max-players",
            "max-tick-time",
            "max-world-size",
            "motd",
            "network-compression-threshold",
            "online-mode",
            "op-permission-level",
            "pause-when-empty-seconds",
            "player-idle-timeout",
            "prevent-proxy-connections",
            "query.port",
            "rate-limit",
            "rcon.password",
            "rcon.port",
            "region-file-compression",
            "require-resource-pack",
            "resource-pack",
            "resource-pack-id",
            "resource-pack-prompt",
            "resource-pack-sha1",
            "server-ip",
            "server-port",
            "simulation-distance",
            "spawn-protection",
            "status-heartbeat-interval",
            "sync-chunk-writes",
            "text-filtering-config",
            "text-filtering-version",
            "use-native-transport",
            "view-distance",
            "white-list",
        ).forEach { key ->
            assertThat(editorProperties).containsKey(key)
        }
        assertThat(editorProperties).doesNotContainKey("accept-transfers")
    }

    @Test
    fun buildPaperServerPropertiesEditorText_usesRuntimeManagedDefaultsForDocumentedTemplate() {
        val server = createPaperServer("生存服", "1.21.11", 20, 2048)
        val editorProperties = parsePropertyMap(buildPaperServerPropertiesEditorText(server))
        val runtimeProperties = parsePropertyMap(buildServerProperties(server))

        listOf(
            "enable-command-block",
            "allow-flight",
            "view-distance",
            "simulation-distance",
        ).forEach { key ->
            assertThat(editorProperties[key]).isEqualTo(runtimeProperties[key])
        }
    }

    @Test
    fun parsePaperServerPropertiesEditorText_ignoresUnchangedGeneratedTemplateCommentsAndDefaults() {
        val server = createPaperServer("生存服", "1.21.11", 20, 2048)
        val parsed = parsePaperServerPropertiesEditorText(
            server = server,
            text = buildPaperServerPropertiesEditorText(server),
        )

        assertThat(parsed.name).isEqualTo(server.name)
        assertThat(parsed.worldName).isEqualTo(server.worldName)
        assertThat(parsed.maxPlayers).isEqualTo(server.maxPlayers)
        assertThat(parsed.defaultPort).isEqualTo(server.defaultPort)
        assertThat(parsed.serverPropertiesOverride).isNull()
    }

    @Test
    fun parsePaperServerPropertiesEditorText_preservesEditedTemplateDefaultsAsOverrides() {
        val server = createPaperServer("生存服", "1.21.11", 20, 2048)
        val editedText = buildPaperServerPropertiesEditorText(server)
            .replace("view-distance=8", "view-distance=12")
            .replace("enable-command-block=true", "enable-command-block=false")

        val parsed = parsePaperServerPropertiesEditorText(server, editedText)

        assertThat(parsed.serverPropertiesOverride).contains("view-distance=12")
        assertThat(parsed.serverPropertiesOverride).contains("enable-command-block=false")
        assertThat(parsed.serverPropertiesOverride).doesNotContain("# MC-GO")
    }

    @Test
    fun parsePaperServerPropertiesEditorText_preservesExistingRuntimeDivergentOverridesAfterTemplateRoundTrip() {
        val server = createPaperServer("生存服", "1.21.11", 20, 2048)
            .copy(
                serverPropertiesOverride = """
                    view-distance=10
                    simulation-distance=10
                    enable-command-block=false
                    allow-flight=false
                """.trimIndent(),
            )

        val parsed = parsePaperServerPropertiesEditorText(
            server = server,
            text = buildPaperServerPropertiesEditorText(server),
        )

        assertThat(parsed.serverPropertiesOverride).contains("view-distance=10")
        assertThat(parsed.serverPropertiesOverride).contains("simulation-distance=10")
        assertThat(parsed.serverPropertiesOverride).contains("enable-command-block=false")
        assertThat(parsed.serverPropertiesOverride).contains("allow-flight=false")
    }

    @Test
    fun parsePaperServerPropertiesEditorText_extractsManagedFieldsAndKeepsOnlyAdvancedOverrides() {
        val server = createPaperServer("默认服", "1.21.11", 20, 2048)
        val parsed = parsePaperServerPropertiesEditorText(
            server = server,
            text = """
                # 自定义 Paper 属性
                motd=极限生存服
                level-name=world_nether
                max-players=12
                server-port=25570
                gamemode=creative
                difficulty=hard
                online-mode=false
                pvp=false
                view-distance=10
                allow-nether=false
            """.trimIndent(),
        )

        assertThat(parsed.name).isEqualTo("极限生存服")
        assertThat(parsed.worldName).isEqualTo("world_nether")
        assertThat(parsed.maxPlayers).isEqualTo(12)
        assertThat(parsed.port).isEqualTo(25570)
        assertThat(parsed.gameMode).isEqualTo(PaperGameMode.Creative)
        assertThat(parsed.difficulty).isEqualTo(PaperDifficulty.Hard)
        assertThat(parsed.onlineMode).isFalse()
        assertThat(parsed.pvpEnabled).isFalse()
        assertThat(parsed.serverPropertiesOverride).isEqualTo(
            "# 自定义 Paper 属性\nview-distance=10\nallow-nether=false",
        )
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
    fun applyPaperServerEdits_preservesVanillaServerTypeAndEditionFamily() {
        val original = createVanillaServer(
            name = "原版服",
            minecraftVersion = "1.20.1",
            maxPlayers = 20,
            memoryMb = 2048,
        )

        val edited = applyPaperServerEdits(
            server = original,
            name = "原版新服",
            minecraftVersion = "1.21.4",
            maxPlayers = 12,
            memoryMb = 3072,
            port = 25570,
            worldName = "world_vanilla",
        )

        assertThat(edited.serverType).isEqualTo(MinecraftServerType.Vanilla)
        assertThat(edited.edition).isEqualTo("Vanilla 1.21.4")
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
    fun applyPaperServerEdits_keepsRecommendedJavaModeAcrossMinecraftVersionChanges() {
        val original = createPaperServer(
            name = "自动服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
        ).copy(
            javaMajorVersion = 21,
            javaSelectionMode = JavaSelectionMode.Recommended,
        )

        val edited = applyPaperServerEdits(
            server = original,
            name = "自动服",
            minecraftVersion = "26.1.1",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
            worldName = "world",
        )

        assertThat(edited.javaMajorVersion).isEqualTo(25)
        assertThat(edited.javaSelectionMode).isEqualTo(JavaSelectionMode.Recommended)
    }

    @Test
    fun applyPaperServerEdits_preservesManualSelectionEvenWhenItMatchesRecommendedJava() {
        val original = createPaperServer(
            name = "手动钉住推荐 Java",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
        ).copy(
            javaMajorVersion = 21,
            javaSelectionMode = JavaSelectionMode.Manual,
        )

        val edited = applyPaperServerEdits(
            server = original,
            name = "手动钉住推荐 Java",
            minecraftVersion = "26.1.1",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
            worldName = "world",
        )

        assertThat(edited.javaMajorVersion).isEqualTo(21)
        assertThat(edited.javaSelectionMode).isEqualTo(JavaSelectionMode.Manual)
    }

    @Test
    fun pickAvailableManagedServerPort_skipsConflictingPortsAndFindsNextFreeOne() {
        val servers = listOf(
            createPaperServer("A", "1.21.4", 20, 2048, port = 25565),
            createPaperServer("B", "1.21.4", 20, 2048, port = 25566),
            createPaperServer("C", "1.21.4", 20, 2048, port = 25568),
        )

        assertThat(pickAvailableManagedServerPort(servers)).isEqualTo(25567)
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

    private fun parsePropertyMap(text: String): Map<String, String> = text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return@mapNotNull null
            line.substring(0, separatorIndex).trim() to line.substring(separatorIndex + 1).trim()
        }
        .toMap()
}
