package com.mcgo.app.ui.screens

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class ServersTunnelChipContractTest {
    private val source: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt")))

    @Test
    fun runningServerTunnelChip_rendersStoredLabelWithoutAppendingAddressAgain() {
        val tunnelChipSection = source.substringAfter("tunnelLabels.forEach { tunnelLabel: String ->")
            .substringBefore("                        }\n                    }")

        assertThat(tunnelChipSection).contains("text = tunnelLabel")
        assertThat(tunnelChipSection).doesNotContain("connectionAddress")
        assertThat(tunnelChipSection).doesNotContain("runtimeAddress")
        assertThat(tunnelChipSection).doesNotContain("serverAddress")
    }

    @Test
    fun serverStatusBadge_reservesSpaceFromTunnelChipsAndBreathingRoomFromRightEdge() {
        val serverHeaderSection = source.substringAfter("        Row(\n            modifier = Modifier.fillMaxWidth(),")
            .substringBefore("        Spacer(modifier = Modifier.height(14.dp))")

        assertThat(serverHeaderSection).contains("modifier = Modifier.weight(1f)")
        assertThat(serverHeaderSection).contains("modifier = Modifier.padding(start = 12.dp, end = 4.dp)")
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
