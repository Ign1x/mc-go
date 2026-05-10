package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class PaperRuntimeEventReceiverContractTest {
    private val receiverSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperRuntimeEventReceiver.kt")))

    @Test
    fun runtimeEventReceiver_preservesOnlinePlayerNamesAcrossBroadcastSerialization() {
        assertThat(receiverSource).contains("event.onlinePlayerNames?.let { onlinePlayerNames ->")
        assertThat(receiverSource).contains("putExtra(ExtraOnlinePlayerNameCount, onlinePlayerNames.size)")
        assertThat(receiverSource).contains("putExtra(\"onlinePlayerName.\$index\", playerName)")
        assertThat(receiverSource).contains("val onlinePlayerNames = intent.getIntExtra(ExtraOnlinePlayerNameCount, -1)")
        assertThat(receiverSource).contains("takeIf { it >= 0 }")
        assertThat(receiverSource).contains("onlinePlayerNames = onlinePlayerNames,")
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
