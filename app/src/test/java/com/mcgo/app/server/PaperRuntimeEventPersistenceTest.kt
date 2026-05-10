package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.finalizePendingServerDeletion
import com.mcgo.app.ui.model.requestServerDeletion
import com.mcgo.app.ui.storage.ServerProfileStore
import java.nio.file.Files
import kotlin.test.Test

class PaperRuntimeEventPersistenceTest {

    @Test
    fun syncPaperRuntimeEvent_persistsRunningStateForRecovery() {
        val filesDir = Files.createTempDirectory("mcgo-runtime-event-store")
        val store = ServerProfileStore(filesDir.resolve("server_profiles.properties"))
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
        ).copy(id = "survival")
        store.save(listOf(server))

        syncPaperRuntimeEvent(
            filesDir = filesDir,
            event = PaperServerEvent(
                serverId = "survival",
                status = PaperServerEventStatus.Running,
                progress = 100,
                message = "Paper 已监听 127.0.0.1:25565",
                onlinePlayers = 1,
            ),
        )

        val updated = store.load().single()
        assertThat(updated.launchStatus).isEqualTo(ServerLaunchStatus.Running)
        assertThat(updated.isOnline).isTrue()
        assertThat(updated.launchProgress).isEqualTo(100)
        assertThat(updated.onlinePlayers).isEqualTo(1)
        assertThat(updated.runtimeLogs).contains("Paper 已监听 127.0.0.1:25565")
    }

    @Test
    fun syncPaperRuntimeEvent_removesPendingDeletionServerAfterTerminalStoppedEvent() {
        val filesDir = Files.createTempDirectory("mcgo-runtime-event-store")
        val store = ServerProfileStore(filesDir.resolve("server_profiles.properties"))
        val server = requestServerDeletion(
            createPaperServer(
                name = "生存服",
                minecraftVersion = "1.21.4",
                maxPlayers = 20,
                memoryMb = 2048,
                port = 25565,
            ).copy(
                id = "survival",
                launchStatus = ServerLaunchStatus.Stopping,
            ),
        )
        store.save(listOf(server))

        syncPaperRuntimeEvent(
            filesDir = filesDir,
            event = PaperServerEvent(
                serverId = "survival",
                status = PaperServerEventStatus.Stopped,
                progress = 0,
                message = "Paper 已安全停止",
            ),
        )

        assertThat(store.load()).isEmpty()
    }

    @Test
    fun syncPaperRuntimeEvent_stoppingPreservesTransientRuntimeStateUntilExit() {
        val filesDir = Files.createTempDirectory("mcgo-runtime-event-store")
        val store = ServerProfileStore(filesDir.resolve("server_profiles.properties"))
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
        ).copy(
            id = "survival",
            isOnline = true,
            launchStatus = ServerLaunchStatus.Running,
            launchProgress = 100,
            activeTunnelLabel = "家庭 FRP · 38 ms",
            port = 25577,
            runtimeLogs = listOf("Paper 已监听 127.0.0.1:25577"),
        )
        store.save(listOf(server))

        syncPaperRuntimeEvent(
            filesDir = filesDir,
            event = PaperServerEvent(
                serverId = "survival",
                status = PaperServerEventStatus.Stopping,
                progress = 0,
                message = "已请求停止内置 Paper 进程，等待运行时退出",
            ),
        )

        val updated = store.load().single()
        assertThat(updated.launchStatus).isEqualTo(ServerLaunchStatus.Stopping)
        assertThat(updated.isOnline).isTrue()
        assertThat(updated.port).isEqualTo(25577)
        assertThat(updated.activeTunnelLabel).isEqualTo("家庭 FRP · 38 ms")
        assertThat(updated.runtimeLogs.last()).contains("已请求停止")
    }

    @Test
    fun reconcilePersistedRuntimeState_keepsRunningStateWhenRuntimeProcessIsAlive() {
        val running = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
        ).copy(
            id = "survival",
            isOnline = true,
            launchStatus = ServerLaunchStatus.Running,
            launchProgress = 100,
            activeTunnelLabel = "家庭 FRP · 38 ms",
            runtimeLogs = listOf("Paper 已监听 127.0.0.1:25565"),
        )

        val reconciled = reconcilePersistedRuntimeState(listOf(running), runtimeAlive = true).single()

        assertThat(reconciled.launchStatus).isEqualTo(ServerLaunchStatus.Running)
        assertThat(reconciled.isOnline).isTrue()
        assertThat(reconciled.activeTunnelLabel).isEqualTo("家庭 FRP · 38 ms")
    }

    @Test
    fun reconcilePersistedRuntimeState_clearsStoppingStateWhenRuntimeProcessIsGone() {
        val stopping = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
        ).copy(
            id = "survival",
            isOnline = false,
            launchStatus = ServerLaunchStatus.Stopping,
            launchProgress = 0,
            activeTunnelLabel = "家庭 FRP · 38 ms",
            port = 25577,
            runtimeLogs = listOf("已请求停止内置 Paper 进程，等待运行时退出"),
        )

        val reconciled = reconcilePersistedRuntimeState(listOf(stopping), runtimeAlive = false).single()

        assertThat(reconciled.launchStatus).isEqualTo(ServerLaunchStatus.Stopped)
        assertThat(reconciled.isOnline).isFalse()
        assertThat(reconciled.port).isEqualTo(25565)
        assertThat(reconciled.activeTunnelLabel).isNull()
        assertThat(reconciled.runtimeLogs.last()).contains("运行时进程已结束")
    }

    @Test
    fun finalizePendingServerDeletion_removesRecoveredStoppedServerOnColdStart() {
        val stopping = requestServerDeletion(
            createPaperServer(
                name = "生存服",
                minecraftVersion = "1.21.4",
                maxPlayers = 20,
                memoryMb = 2048,
                port = 25565,
            ).copy(
                id = "survival",
                launchStatus = ServerLaunchStatus.Stopping,
                runtimeLogs = listOf("已请求删除，待服务停止后自动移除"),
            ),
        )

        val reconciled = reconcilePersistedRuntimeState(listOf(stopping), runtimeAlive = false)
        val finalized = finalizePendingServerDeletion(reconciled)

        assertThat(finalized).isEmpty()
    }

    @Test
    fun reconcilePersistedRuntimeState_keepsOnlyServersWhoseRuntimeSlotIsStillAlive() {
        val alpha = createPaperServer(
            name = "A服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
        ).copy(
            id = "alpha",
            isOnline = true,
            launchStatus = ServerLaunchStatus.Running,
            runtimeSlot = 1,
            port = 25571,
        )
        val beta = createPaperServer(
            name = "B服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25566,
        ).copy(
            id = "beta",
            isOnline = true,
            launchStatus = ServerLaunchStatus.Running,
            runtimeSlot = 2,
            port = 25572,
            activeTunnelLabel = "家庭 FRP · frp.example.com:38002",
        )

        val reconciled = reconcilePersistedRuntimeState(
            servers = listOf(alpha, beta),
            activeRuntimeSlots = setOf(2),
        )

        assertThat(reconciled.single { it.id == "alpha" }.launchStatus).isEqualTo(ServerLaunchStatus.Stopped)
        assertThat(reconciled.single { it.id == "alpha" }.runtimeSlot).isNull()
        assertThat(reconciled.single { it.id == "beta" }.launchStatus).isEqualTo(ServerLaunchStatus.Running)
        assertThat(reconciled.single { it.id == "beta" }.runtimeSlot).isEqualTo(2)
        assertThat(reconciled.single { it.id == "beta" }.activeTunnelLabel).isEqualTo("家庭 FRP · frp.example.com:38002")
    }
}
