package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoBottomOverlayClearanceContractTest {
    private val appSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")))
    private val statusScreenSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/StatusScreen.kt")))
    private val settingsScreenSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/SettingsScreen.kt")))
    private val serversScreenSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt")))
    private val tunnelsScreenSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/TunnelScreen.kt")))

    @Test
    fun floatingBottomMenuUsesRealBottomInsetForScrollableScreenClearance() {
        assertThat(appSource).contains("val bottomContentPadding = innerPadding.calculateBottomPadding()")
        assertThat(appSource).contains("bottomContentPadding = bottomContentPadding")

        assertThat(statusScreenSource).contains("bottomContentPadding: Dp = 0.dp")
        assertThat(statusScreenSource).contains("item { Spacer(modifier = Modifier.height(24.dp + bottomContentPadding)) }")

        assertThat(settingsScreenSource).contains("bottomContentPadding: Dp = 0.dp")
        assertThat(settingsScreenSource).contains("item { Spacer(modifier = Modifier.height(24.dp + bottomContentPadding)) }")

        assertThat(serversScreenSource).contains("bottomContentPadding: Dp = 0.dp")
        assertThat(serversScreenSource).contains("item { Spacer(modifier = Modifier.height(96.dp + bottomContentPadding)) }")

        assertThat(tunnelsScreenSource).contains("bottomContentPadding: Dp = 0.dp")
        assertThat(tunnelsScreenSource).contains("item { Spacer(modifier = Modifier.height(96.dp + bottomContentPadding)) }")
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
