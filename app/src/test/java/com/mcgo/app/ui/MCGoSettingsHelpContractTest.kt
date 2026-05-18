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
        assertThat(debugExportSource).contains("===== export_metadata =====")
        assertThat(debugExportSource).contains("supportedAbis=")
        assertThat(debugExportSource).contains("[debug] 行会写入托管运行日志")
        assertThat(debugExportSource).contains("FLAG_GRANT_READ_URI_PERMISSION")
        assertThat(debugExportSource).contains("redactSensitiveLogExportText(")
        assertThat(debugExportSource).contains("line.substringBefore('=')")
        assertThat(debugExportSource).contains("key.endsWith(\"credentialValue\")")
        assertThat(debugExportSource).contains("key.endsWith(\"rawConfigText\")")
        assertThat(debugExportSource).contains("key.endsWith(\"rawConfigPreview\")")
        assertThat(debugExportSource).contains("server_directory_uri")
        assertThat(settingsScreenSource).contains("默认会自动隐藏隧道凭据")
        assertThat(manifestSource).contains("androidx.core.content.FileProvider")
        assertThat(manifestSource).contains(".fileprovider")
    }

    @Test
    fun logExportRedaction_hidesCredentialsAndRawFrpConfigValuesOnly() {
        val redacted = redactSensitiveLogExportText(
            """
            server.0.name=creative
            server.0.credentialValue=secret-token
            tunnel.0.rawConfigText=[common]\ntoken = secret
            tunnel.0.rawConfigPreview=token = secret
            tunnel.0.rawConfigFormat=toml
            server.0.credentialValueSuffix=visible
            """.trimIndent(),
        )

        assertThat(redacted).isEqualTo(
            """
            server.0.name=creative
            server.0.credentialValue=<redacted>
            tunnel.0.rawConfigText=<redacted>
            tunnel.0.rawConfigPreview=<redacted>
            tunnel.0.rawConfigFormat=toml
            server.0.credentialValueSuffix=visible
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