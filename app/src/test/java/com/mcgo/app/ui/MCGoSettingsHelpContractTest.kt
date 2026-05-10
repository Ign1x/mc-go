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
        assertThat(appSource).contains("exportDebugLogs(")
        assertThat(appSource).contains("Intent.ACTION_SEND")
        assertThat(appSource).contains("Intent.createChooser(")
        assertThat(appSource).contains("FileProvider.getUriForFile(")
        assertThat(appSource).contains("mcgo_debug_logs")
        assertThat(appSource).contains("FLAG_GRANT_READ_URI_PERMISSION")
        assertThat(appSource).contains("redactSensitiveLogExportText(")
        assertThat(appSource).contains("line.substringBefore('=')")
        assertThat(appSource).contains("key.endsWith(\"credentialValue\")")
        assertThat(appSource).contains("key.endsWith(\"rawConfigText\")")
        assertThat(appSource).contains("key.endsWith(\"rawConfigPreview\")")
        assertThat(appSource).contains("server_directory_uri")
        assertThat(settingsScreenSource).contains("默认会自动隐藏隧道凭据")
        assertThat(manifestSource).contains("androidx.core.content.FileProvider")
        assertThat(manifestSource).contains(".fileprovider")
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}