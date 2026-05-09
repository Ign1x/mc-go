package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoAnimationContractTest {
    private val appSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")))
    private val glassCardSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/components/GlassCard.kt")))
    private val serversScreenSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt")))

    @Test
    fun glassCards_and_serverCards_use_lowRisk_compose_animations() {
        assertThat(glassCardSource).contains("animateContentSize()")
        assertThat(serversScreenSource).contains("AnimatedContent(")
        assertThat(serversScreenSource).contains("AnimatedVisibility(")
        assertThat(serversScreenSource).contains("animateColorAsState(")
    }

    @Test
    fun appShell_usesAnimatedTransitionsForPageSwitchingAndEditOverlay() {
        val scaffoldSource = appSource.substringBetween(
            start = "private fun MCGoAppScaffold(",
            end = "@Composable\nprivate fun RequestRuntimePermissions(",
        )

        assertThat(scaffoldSource).contains("AnimatedContent(")
        assertThat(scaffoldSource).contains("targetState = destination")
        assertThat(scaffoldSource).contains("AnimatedVisibility(")
        assertThat(scaffoldSource).contains("visible = activeEditingServer != null")
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")

    private fun String.substringBetween(start: String, end: String): String =
        substringAfter(start).substringBefore(end)
}
