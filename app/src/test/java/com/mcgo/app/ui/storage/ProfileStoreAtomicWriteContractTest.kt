package com.mcgo.app.ui.storage

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class ProfileStoreAtomicWriteContractTest {
    @Test
    fun profileStores_writePropertiesThroughSharedAtomicMoveHelper() {
        val serverStoreSource = readSource("app/src/main/java/com/mcgo/app/ui/storage/ServerProfileStore.kt")
        val tunnelStoreSource = readSource("app/src/main/java/com/mcgo/app/ui/storage/TunnelProfileStore.kt")
        val helperSource = readSource("app/src/main/java/com/mcgo/app/ui/storage/AtomicPropertiesStore.kt")

        assertThat(serverStoreSource).contains("storePropertiesAtomically(storePath, properties, \"MC-GO server profiles\")")
        assertThat(tunnelStoreSource).contains("storePropertiesAtomically(storePath, properties, \"MC-GO tunnel profiles\")")
        assertThat(helperSource).contains("StandardCopyOption.ATOMIC_MOVE")
        assertThat(helperSource).contains("StandardCopyOption.REPLACE_EXISTING")
        assertThat(helperSource).contains("AtomicMoveNotSupportedException")
        assertThat(helperSource).contains("Files.deleteIfExists(tempPath)")
    }

    @Test
    fun storePropertiesAtomically_roundTripsPropertiesAndRemovesTempFile() {
        val storePath = Files.createTempDirectory("mcgo-atomic-properties").resolve("profiles.properties")
        val properties = java.util.Properties().apply {
            setProperty("count", "1")
            setProperty("profile.0.name", "生存服")
        }

        storePropertiesAtomically(storePath, properties, "MC-GO atomic properties test")

        val loaded = java.util.Properties()
        Files.newInputStream(storePath).use(loaded::load)
        assertThat(loaded.getProperty("count")).isEqualTo("1")
        assertThat(loaded.getProperty("profile.0.name")).isEqualTo("生存服")
        assertThat(Files.exists(storePath.resolveSibling("${storePath.fileName}.tmp"))).isFalse()
    }

    private fun readSource(relativePath: String): String = String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path = generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
        .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
        ?: error("project root not found")
}
