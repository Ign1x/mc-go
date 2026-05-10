package com.mcgo.app.ui.storage

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.JavaSelectionMode
import com.mcgo.app.ui.model.ServerTunnelBinding
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.effectiveTunnelBindings
import com.mcgo.app.ui.model.withTunnelBindings
import java.nio.file.Files
import kotlin.test.Test

class ServerProfileStoreTest {

    @Test
    fun saveAndLoad_preservesManualJavaOverrideSelection() {
        val storeFile = Files.createTempFile("mcgo-server-store", ".properties")
        val store = ServerProfileStore(storeFile)
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
        ).copy(
            javaMajorVersion = 17,
            javaSelectionMode = JavaSelectionMode.Manual,
        )

        store.save(listOf(server))
        val loaded = store.load().single()

        assertThat(loaded.javaMajorVersion).isEqualTo(17)
        assertThat(loaded.javaSelectionMode).isEqualTo(JavaSelectionMode.Manual)
    }

    @Test
    fun saveAndLoad_preservesMultipleTunnelBindingsAndLegacyPrimaryMirror() {
        val storeFile = Files.createTempFile("mcgo-server-store-multi-tunnel", ".properties")
        val store = ServerProfileStore(storeFile)
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
        ).withTunnelBindings(
            listOf(
                ServerTunnelBinding(
                    tunnelId = "frp-home",
                    remotePort = 39001,
                    activeLabel = "家庭 FRP · frp.home:39001",
                    runtimeAddress = "frp.home:39001",
                ),
                ServerTunnelBinding(
                    tunnelId = "frp-aliyun",
                    remotePort = 39002,
                    activeLabel = "阿里云 FRP · frp.aliyun:39002",
                    runtimeAddress = "frp.aliyun:39002",
                ),
            ),
        ).copy(runtimeSlot = 2)

        store.save(listOf(server))
        val loaded = store.load().single()

        val loadedBindings = loaded.effectiveTunnelBindings()
        assertThat(loadedBindings).hasSize(2)
        assertThat(loadedBindings.map { it.tunnelId }).containsExactly("frp-home", "frp-aliyun").inOrder()
        assertThat(loaded.selectedTunnelId).isEqualTo("frp-home")
        assertThat(loaded.tunnelRemotePort).isEqualTo(39001)
        assertThat(loaded.activeTunnelLabel).isEqualTo("家庭 FRP · frp.home:39001")
        assertThat(loaded.runtimeAddress).isEqualTo("frp.home:39001")
    }

    @Test
    fun saveAndLoad_preservesReservedTunnelRemotePort() {
        val storeFile = Files.createTempFile("mcgo-server-store-remote-port", ".properties")
        val store = ServerProfileStore(storeFile)
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "1.21.11",
            maxPlayers = 20,
            memoryMb = 2048,
        ).copy(
            selectedTunnelId = "frp-home",
            tunnelRemotePort = 39001,
            activeTunnelLabel = "家庭 FRP · frp.home:39001",
            runtimeAddress = "frp.home:39001",
            runtimeSlot = 2,
            onlineMode = false,
            pvpEnabled = false,
        )

        store.save(listOf(server))
        val loaded = store.load().single()

        assertThat(loaded.selectedTunnelId).isEqualTo("frp-home")
        assertThat(loaded.tunnelRemotePort).isEqualTo(39001)
        assertThat(loaded.activeTunnelLabel).isEqualTo("家庭 FRP · frp.home:39001")
        assertThat(loaded.runtimeAddress).isEqualTo("frp.home:39001")
        assertThat(loaded.runtimeSlot).isEqualTo(2)
        assertThat(loaded.onlineMode).isFalse()
        assertThat(loaded.pvpEnabled).isFalse()
    }

    @Test
    fun saveAndLoad_preservesServerIconVersionForUiRefreshAndWorkspaceSync() {
        val storeFile = Files.createTempFile("mcgo-server-store-icon-version", ".properties")
        val store = ServerProfileStore(storeFile)
        val server = createPaperServer(
            name = "生存服",
            minecraftVersion = "26.1.2",
            maxPlayers = 20,
            memoryMb = 2048,
        ).copy(serverIconVersion = 1_778_252_410_628L)

        store.save(listOf(server))
        val loaded = store.load().single()

        assertThat(loaded.serverIconVersion).isEqualTo(1_778_252_410_628L)
    }
}
