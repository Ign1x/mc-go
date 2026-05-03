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
}
