package com.mcgo.app.ui.storage

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.createPaperServer
import java.nio.file.Files
import kotlin.test.Test

class ServerProfileStoreTest {

    @Test
    fun load_returnsEmptyListWhenStoreFileDoesNotExist() {
        val storePath = Files.createTempDirectory("mcgo-server-store-empty").resolve("servers.properties")
        val store = ServerProfileStore(storePath)

        assertThat(store.load()).isEmpty()
    }

    @Test
    fun saveAndLoad_roundTripsPaperServerAndPreservesRuntimeStateForRecovery() {
        val storePath = Files.createTempDirectory("mcgo-server-store-roundtrip").resolve("servers.properties")
        val store = ServerProfileStore(storePath)
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
            selectedTunnelId = "frp-home",
            activeTunnelLabel = "家庭 FRP · 38 ms",
            port = 25577,
            launchProgress = 100,
            runtimeLogs = listOf("Paper 已启动", "Done (1.234s)!"),
            runtimeLogPath = "/data/user/0/com.mcgo.app/files/servers/survival/logs/mcgo-latest.log",
        )

        store.save(listOf(server))
        val loaded = store.load()

        assertThat(loaded).hasSize(1)
        assertThat(loaded.single().id).isEqualTo("survival")
        assertThat(loaded.single().name).isEqualTo("生存服")
        assertThat(loaded.single().edition).isEqualTo("Paper 1.21.4")
        assertThat(loaded.single().minecraftVersion).isEqualTo("1.21.4")
        assertThat(loaded.single().javaMajorVersion).isEqualTo(21)
        assertThat(loaded.single().memoryMb).isEqualTo(2048)
        assertThat(loaded.single().port).isEqualTo(25577)
        assertThat(loaded.single().isOnline).isTrue()
        assertThat(loaded.single().launchStatus).isEqualTo(ServerLaunchStatus.Running)
        assertThat(loaded.single().selectedTunnelId).isEqualTo("frp-home")
        assertThat(loaded.single().activeTunnelLabel).isEqualTo("家庭 FRP · 38 ms")
        assertThat(loaded.single().launchProgress).isEqualTo(100)
        assertThat(loaded.single().runtimeLogs).containsExactly("Paper 已启动", "Done (1.234s)!").inOrder()
        assertThat(loaded.single().runtimeLogPath).isEqualTo("/data/user/0/com.mcgo.app/files/servers/survival/logs/mcgo-latest.log")
        assertThat(loaded.single().pendingDeletion).isFalse()
        assertThat(loaded.single().launchPlan).isNull()
    }

    @Test
    fun saveAndLoad_preservesPendingDeletionFlagForBusyServer() {
        val storePath = Files.createTempDirectory("mcgo-server-store-pending-delete").resolve("servers.properties")
        val store = ServerProfileStore(storePath)
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.4",
            maxPlayers = 20,
            memoryMb = 2048,
            port = 25565,
        ).copy(
            id = "survival",
            launchStatus = ServerLaunchStatus.Stopping,
            pendingDeletion = true,
            runtimeLogs = listOf("已请求删除，待服务停止后自动移除"),
        )

        store.save(listOf(server))
        val loaded = store.load().single()

        assertThat(loaded.pendingDeletion).isTrue()
        assertThat(loaded.launchStatus).isEqualTo(ServerLaunchStatus.Stopping)
        assertThat(loaded.runtimeLogs.last()).contains("待服务停止后自动移除")
    }
}
