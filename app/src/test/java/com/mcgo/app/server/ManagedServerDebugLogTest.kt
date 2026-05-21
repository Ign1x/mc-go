package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.Test

class ManagedServerDebugLogTest {
    @Test
    fun appDebugLog_usesSharedStructuredFormatAndVisibleTopLevelLogFile() {
        val filesDir = Files.createTempDirectory("mcgo-app-debug-log")

        val line = appendMcGoAppDebugLog(
            filesDir = filesDir,
            message = "用户打开日志系统",
            details = mapOf("source" to "settings", "empty" to null),
            timestamp = LocalDateTime.of(2026, 5, 18, 21, 30, 0),
        )

        assertThat(mcGoAppDebugLogFile(filesDir).toString()).endsWith("logs/mcgo-debug.log")
        assertThat(line).isEqualTo("[debug] 2026-05-18 21:30:00 用户打开日志系统 | source=settings")
        assertThat(String(Files.readAllBytes(mcGoAppDebugLogFile(filesDir)))).contains(line)
    }

    @Test
    fun structuredDebugLog_rendersDetailValuesAsSingleLine() {
        val line = buildManagedServerDebugLogLine(
            message = "开始导入整合包",
            details = mapOf(
                "archiveDisplayName" to "pack.zip\nmalicious=1",
                "notes" to "  has\tmultiple   spaces  ",
            ),
            timestamp = LocalDateTime.of(2026, 5, 18, 21, 31, 0),
        )

        assertThat(line).isEqualTo("[debug] 2026-05-18 21:31:00 开始导入整合包 | archiveDisplayName=pack.zip malicious=1 notes=has multiple spaces")
        assertThat(line).doesNotContain("\n")
    }

    @Test
    fun recentDebugLogPreview_combinesAppAndManagedServerLogsWithMostRecentLinesOnly() {
        val filesDir = Files.createTempDirectory("mcgo-recent-debug-preview")
        val appLog = mcGoAppDebugLogFile(filesDir)
        val serverLog = managedPaperServerLogFile(filesDir, "survival")
        Files.createDirectories(appLog.parent)
        Files.createDirectories(serverLog.parent)
        Files.write(
            appLog,
            listOf("app-old", "app-new").joinToString(separator = "\n", postfix = "\n").toByteArray(),
        )
        Files.write(
            serverLog,
            listOf("server-old", "server-new").joinToString(separator = "\n", postfix = "\n").toByteArray(),
        )

        val preview = readRecentDebugLogPreview(filesDir = filesDir, maxLinesPerFile = 1)

        assertThat(preview).contains("===== logs/mcgo-debug.log =====")
        assertThat(preview).contains("app-new")
        assertThat(preview).doesNotContain("app-old")
        assertThat(preview).contains("===== servers/survival/logs/mcgo-latest.log =====")
        assertThat(preview).contains("server-new")
        assertThat(preview).doesNotContain("server-old")
    }

    @Test
    fun recentDebugLogPreview_ignoresSymlinkedLogFiles() {
        val filesDir = Files.createTempDirectory("mcgo-recent-debug-preview-link")
        val serverLog = managedPaperServerLogFile(filesDir, "survival")
        val outsideLog = Files.createTempFile("mcgo-outside-log", ".log")
        Files.write(outsideLog, "outside-secret\n".toByteArray())
        Files.createDirectories(serverLog.parent)
        Files.createSymbolicLink(serverLog, outsideLog)

        val preview = readRecentDebugLogPreview(filesDir = filesDir)

        assertThat(preview).doesNotContain("outside-secret")
        assertThat(preview).doesNotContain("servers/survival/logs/mcgo-latest.log")
    }

    @Test
    fun recentDebugLogPreview_tailsLinesAcrossLargeChunksWithoutKeepingWholeFile() {
        val filesDir = Files.createTempDirectory("mcgo-recent-debug-preview-large")
        val appLog = mcGoAppDebugLogFile(filesDir)
        Files.createDirectories(appLog.parent)
        val oldLine = "old-" + "x".repeat(300_000)
        Files.write(
            appLog,
            listOf(oldLine, "new-1", "new-2").joinToString(separator = "\n").toByteArray(StandardCharsets.UTF_8),
        )

        val preview = readRecentDebugLogPreview(filesDir = filesDir, maxLinesPerFile = 2)

        assertThat(preview).contains("new-1")
        assertThat(preview).contains("new-2")
        assertThat(preview).doesNotContain("old-")
    }
}
