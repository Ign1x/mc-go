package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoSettingsHelpContractTest {
    private val settingsScreenSource: String = readSource("app/src/main/java/com/mcgo/app/ui/screens/SettingsScreen.kt")
    private val appSource: String = readSource("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")
    private val manifestSource: String = readSource("app/src/main/AndroidManifest.xml")

    @Test
    fun settingsHelpSection_offersSupportInfoAboutCardAndExportLogsAction() {
        assertThat(settingsScreenSource).contains("SettingsDestination.ServerDirectory")
        assertThat(settingsScreenSource).contains("onOpenServerDirectory")
        assertThat(settingsScreenSource).contains("ServerDirectoryDetailScreen(")
        assertThat(settingsScreenSource).contains("text = \"服务器目录\"")
        assertThat(settingsScreenSource).contains("Text(\"选择目录\")")
        assertThat(settingsScreenSource).contains("onRequestServerDirectory")
        assertThat(settingsScreenSource).contains("重新授权同一目录")
        assertThat(settingsScreenSource).contains("SettingsDestination.HelpAndDebug")
        assertThat(settingsScreenSource).contains("onOpenHelpAndDebug")
        assertThat(settingsScreenSource).contains("HelpAndDebugDetailScreen(")
        assertThat(settingsScreenSource).contains("title = \"帮助与调试\"")
        assertThat(settingsScreenSource).contains("Text(\"提取日志\")")
        assertThat(settingsScreenSource).contains("onExportLogs")
        assertThat(settingsScreenSource).contains("遇到问题时，可先提取日志")
        assertThat(settingsScreenSource).contains("问题反馈时建议附上日志")
        assertThat(settingsScreenSource).contains("关于")
        assertThat(settingsScreenSource).contains("当前版本")
        assertThat(settingsScreenSource).contains("BuildConfig.VERSION_NAME")
        assertThat(settingsScreenSource).contains("BuildConfig.VERSION_CODE")
    }

    @Test
    fun helpAndDebugScreen_showsRecentLogsPreviewAndRefreshAction() {
        assertThat(settingsScreenSource).contains("recentLogPreview: String")
        assertThat(settingsScreenSource).contains("onRefreshRecentLogs: () -> Unit")
        assertThat(settingsScreenSource).contains("text = \"最近日志\"")
        assertThat(settingsScreenSource).contains("Text(\"刷新日志\")")
        assertThat(settingsScreenSource).contains("recentLogPreview.ifBlank")
        assertThat(settingsScreenSource).contains("最近还没有日志")
        assertThat(appSource).contains("readRecentDebugLogPreview(")
        assertThat(appSource).contains("appendMcGoAppDebugLog(")
        assertThat(appSource).contains("\"打开帮助与调试页面\"")
    }

    @Test
    fun appDebugLog_recordsStartAndDirectoryEvents() {
        assertThat(appSource).contains("\"提交服务器启动\"")
        assertThat(appSource).contains("\"服务器启动任务已派发\"")
        assertThat(appSource).contains("\"服务器目录已授权\"")
        assertThat(appSource).contains("\"整合包脚本已确认\"")
    }

    @Test
    fun logExport_usesFileProviderAndSystemShareSheet() {
        val debugExportSource = readSource("app/src/main/java/com/mcgo/app/ui/DebugLogExport.kt")
        val settingsExportCallback = appSource.substringAfter("onExportLogs = {").substringBefore("                        },")

        assertThat(settingsExportCallback).contains("exportDebugLogs(appContext)")
        assertThat(appSource).doesNotContain("private fun exportDebugLogs(")
        assertThat(appSource).doesNotContain("private fun readLogExportSection(")
        assertThat(appSource).doesNotContain("private fun readRuntimePrefsExportSection(")
        assertThat(appSource).doesNotContain("private fun redactSensitiveLogExportText(")
        assertThat(debugExportSource).contains("internal fun exportDebugLogs(")
        assertThat(debugExportSource).contains("Intent.ACTION_SEND")
        assertThat(debugExportSource).contains("Intent.createChooser(")
        assertThat(debugExportSource).contains("FileProvider.getUriForFile(")
        assertThat(debugExportSource).contains("mcgo_debug_logs")
        assertThat(debugExportSource).contains("isReadableLogExportFile")
        assertThat(debugExportSource).contains("readNoFollowLogExportText")
        assertThat(debugExportSource).contains("Files.newByteChannel")
        assertThat(debugExportSource).contains("StandardOpenOption.READ")
        assertThat(debugExportSource).doesNotContain("Files.readAllBytes(path)")
        assertThat(debugExportSource).contains("LinkOption.NOFOLLOW_LINKS")
        assertThat(debugExportSource).contains("Files.isSymbolicLink")
        assertThat(debugExportSource).contains("Files.isDirectory(serversRoot, LinkOption.NOFOLLOW_LINKS)")
        assertThat(debugExportSource).contains("===== export_metadata =====")
        assertThat(debugExportSource).contains("supportedAbis=")
        assertThat(debugExportSource).contains("[debug] 行会写入托管运行日志")
        assertThat(debugExportSource).contains("FLAG_GRANT_READ_URI_PERMISSION")
        assertThat(debugExportSource).contains("redactSensitiveLogExportLine")
        assertThat(debugExportSource).contains("SensitiveLogExportExactKeys")
        assertThat(debugExportSource).contains("SensitiveLogExportKeySuffixes")
        assertThat(debugExportSource).contains("\"credentialvalue\"")
        assertThat(debugExportSource).contains("\"rawconfigtext\"")
        assertThat(debugExportSource).contains("\"rawconfigpreview\"")
        assertThat(debugExportSource).contains("\"auth.token\"")
        assertThat(debugExportSource).contains("\"rcon.password\"")
        assertThat(debugExportSource).contains("server_directory_uri")
        assertThat(settingsScreenSource).contains("默认会自动隐藏隧道凭据")
        assertThat(manifestSource).contains("androidx.core.content.FileProvider")
        assertThat(manifestSource).contains(".fileprovider")
    }

    @Test
    fun logExportFileReadability_rejectsSymlinkedLogs() {
        val tempDir = Files.createTempDirectory("mcgo-log-export-symlink")
        val regularLog = tempDir.resolve("mcgo-latest.log")
        val externalSecret = Files.createTempFile("mcgo-external-log", ".txt")
        val symlinkLog = tempDir.resolve("linked.log")
        try {
            Files.write(regularLog, "safe log".toByteArray())
            Files.write(externalSecret, "secret outside log".toByteArray())
            Files.createSymbolicLink(symlinkLog, externalSecret)

            assertThat(isReadableLogExportFile(regularLog)).isTrue()
            assertThat(readNoFollowLogExportText(regularLog)).isEqualTo("safe log")
            assertThat(isReadableLogExportFile(symlinkLog)).isFalse()
        } finally {
            Files.deleteIfExists(symlinkLog)
            Files.deleteIfExists(regularLog)
            Files.deleteIfExists(externalSecret)
            Files.deleteIfExists(tempDir)
        }
    }

    @Test
    fun logExportRedaction_hidesCredentialsRawFrpConfigAndCommonSecretKeysOnly() {
        val redacted = redactSensitiveLogExportText(
            """
            server.0.name=creative
            server.0.credentialValue=secret-token
            tunnel.0.rawConfigText=[common]\ntoken = secret
            tunnel.0.rawConfigPreview=token = secret
            auth.token = provider-token
            token: provider-token
            vkey=provider-vkey
            secret_key = provider-secret
            rcon.password=op-password
            management-server-secret = management-secret
            tunnel.0.rawConfigFormat=toml
            server.0.credentialValueSuffix=visible
            frpc.error=login to the server failed: token in login doesn't match token from configuration
            [debug] 2026-05-19 18:00:00 提交服务器启动 | server.id=demo tunnel.credentialValue=secret-token auth.token = provider-token rcon.password=op-password frpc.error=token in login doesn't match
            auth.token = provider token with spaces
            rcon.password=op password with spaces
            [debug] 2026-05-19 18:01:00 提交服务器启动 | server.id=demo auth.token=provider token with spaces rcon.password=op password with spaces frpc.error=token in login doesn't match
            """.trimIndent(),
        )

        assertThat(redacted).isEqualTo(
            """
            server.0.name=creative
            server.0.credentialValue=<redacted>
            tunnel.0.rawConfigText=<redacted>
            tunnel.0.rawConfigPreview=<redacted>
            auth.token=<redacted>
            token=<redacted>
            vkey=<redacted>
            secret_key=<redacted>
            rcon.password=<redacted>
            management-server-secret=<redacted>
            tunnel.0.rawConfigFormat=toml
            server.0.credentialValueSuffix=visible
            frpc.error=login to the server failed: token in login doesn't match token from configuration
            [debug] 2026-05-19 18:00:00 提交服务器启动 | server.id=demo tunnel.credentialValue=<redacted> auth.token=<redacted> rcon.password=<redacted> frpc.error=token in login doesn't match
            auth.token=<redacted>
            rcon.password=<redacted>
            [debug] 2026-05-19 18:01:00 提交服务器启动 | server.id=demo auth.token=<redacted> rcon.password=<redacted> frpc.error=token in login doesn't match
            """.trimIndent(),
        )
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}