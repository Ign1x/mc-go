package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoAutoJavaInstallContractTest {
    private val source: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")))

    @Test
    fun startServerFlow_autoInstallsMissingManagedJavaAndResumesLaunch() {
        val scaffold = source.substringBetween(
            start = "private fun MCGoAppScaffold(",
            end = "@Composable\nprivate fun RequestRuntimePermissions(",
        )

        assertThat(source).contains("private data class PendingManagedRuntimeStart(")
        assertThat(scaffold).contains("var pendingManagedRuntimeStarts by remember")
        assertThat(scaffold).contains("pendingManagedRuntimeStarts = pendingManagedRuntimeStarts + PendingManagedRuntimeStart(request, targetServer.javaMajorVersion)")
        assertThat(scaffold).contains("markAwaitingManagedRuntimeInstall(targetServer.javaMajorVersion)")
        assertThat(scaffold).contains("未检测到 Java \${targetServer.javaMajorVersion}，已开始自动安装")
        assertThat(scaffold).contains("LaunchedEffect(installedJavaVersions, pendingManagedRuntimeStarts)")
        assertThat(scaffold).contains("pendingManagedRuntimeStarts.filter")
        assertThat(scaffold).contains("pendingStartRequest = completedPending.request")
        assertThat(scaffold).doesNotContain("请先安装 Java \${targetServer.javaMajorVersion} 托管 JRE")
    }

    @Test
    fun autoInstallQueue_clearsCanceledServerRequestsBeforeResume() {
        val scaffold = source.substringBetween(
            start = "private fun MCGoAppScaffold(",
            end = "@Composable\nprivate fun RequestRuntimePermissions(",
        )

        assertThat(scaffold).contains("pendingManagedRuntimeStarts = pendingManagedRuntimeStarts.filterNot { it.request.serverId == serverId }")
        assertThat(scaffold).contains("pendingStartRequest = pendingStartRequest?.takeUnless { it.serverId == serverId }")
    }

    private fun String.substringBetween(start: String, end: String): String {
        val startIndex = indexOf(start)
        val endIndex = indexOf(end, startIndex.coerceAtLeast(0))
        require(startIndex >= 0) { "Missing start marker: $start" }
        require(endIndex > startIndex) { "Missing end marker after $start: $end" }
        return substring(startIndex, endIndex)
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
