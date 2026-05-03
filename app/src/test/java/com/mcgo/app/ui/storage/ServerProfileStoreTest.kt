package com.mcgo.app.ui.storage

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.JavaSelectionMode
import com.mcgo.app.ui.model.createPaperServer
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
}
