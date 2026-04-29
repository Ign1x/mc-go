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
    fun saveAndLoad_roundTripsPaperServerButResetsRuntimeState() {
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
        assertThat(loaded.single().isOnline).isFalse()
        assertThat(loaded.single().launchStatus).isEqualTo(ServerLaunchStatus.Ready)
        assertThat(loaded.single().launchPlan).isNull()
    }
}
