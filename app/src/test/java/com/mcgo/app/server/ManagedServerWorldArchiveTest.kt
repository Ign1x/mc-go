package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlin.test.Test

class ManagedServerWorldArchiveTest {
    @Test
    fun detectImportedWorldDirectory_prefersSingleTopLevelWorldFolder() {
        val root = Files.createTempDirectory("mcgo-world-import")
        val worldDir = root.resolve("my_world")
        Files.createDirectories(worldDir)
        Files.write(worldDir.resolve("level.dat"), byteArrayOf(1, 2, 3))

        val detected = detectImportedWorldDirectory(root)

        assertThat(detected).isEqualTo(worldDir)
    }

    @Test
    fun detectImportedWorldDirectory_acceptsRootLevelWorldContents() {
        val root = Files.createTempDirectory("mcgo-world-import-flat")
        Files.write(root.resolve("level.dat"), byteArrayOf(1, 2, 3))

        val detected = detectImportedWorldDirectory(root)

        assertThat(detected).isEqualTo(root)
    }
}
