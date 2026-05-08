package com.mcgo.app.ui.screens

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class ServersTunnelChipContractTest {
    private val source: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt")))

    @Test
    fun runningServerTunnelChip_rendersStoredLabelWithoutAppendingAddressAgain() {
        val tunnelChipSection = source.substringAfter("server.activeTunnelLabel?.let { tunnelLabel ->")
            .substringBefore("            }\n        }")

        assertThat(tunnelChipSection).contains("text = tunnelLabel")
        assertThat(tunnelChipSection).doesNotContain("connectionAddress")
        assertThat(tunnelChipSection).doesNotContain("runtimeAddress")
        assertThat(tunnelChipSection).doesNotContain("serverAddress")
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
