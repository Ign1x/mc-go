package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.createPaperServer
import java.nio.file.Files
import kotlin.test.Test

class PaperServerRuntimeTest {

    @Test
    fun buildPaperDownloadUrl_usesLatestBuildAndDownloadNameFromApi() {
        val build = parseLatestPaperBuild(
            """
                {"project_id":"paper","project_name":"Paper","version":"1.21.4","builds":[1,2,227]}
            """.trimIndent(),
        )
        val download = parsePaperDownloadName(
            """
                {"downloads":{"application":{"name":"paper-1.21.4-227.jar","sha256":"abc"}}}
            """.trimIndent(),
        )

        assertThat(build).isEqualTo(227)
        assertThat(download).isEqualTo("paper-1.21.4-227.jar")
        assertThat(buildPaperDownloadUrl("1.21.4", build, download)).isEqualTo(
            "https://api.papermc.io/v2/projects/paper/versions/1.21.4/builds/227/downloads/paper-1.21.4-227.jar",
        )
    }

    @Test
    fun preparePaperServerFiles_writesEulaAndServerProperties() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime")
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25566)

        val prepared = preparePaperServerFiles(server, workDir)

        assertThat(String(Files.readAllBytes(prepared.eulaPath))).contains("eula=true")
        val properties = String(Files.readAllBytes(prepared.serverPropertiesPath))
        assertThat(properties).contains("server-port=25566")
        assertThat(properties).contains("max-players=20")
        assertThat(prepared.jarPath.fileName.toString()).isEqualTo("paper-1.21.4.jar")
    }

    @Test
    fun buildJavaLaunchCommand_usesManagedJavaPathAndPaperJar() {
        val workDir = Files.createTempDirectory("mcgo-paper-command")
        val javaHome = workDir.resolve("jre-21")
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)
        val prepared = preparePaperServerFiles(server, workDir)

        val command = buildJavaLaunchCommand(server, prepared, javaHome)

        assertThat(command.first()).isEqualTo(javaHome.resolve("bin/java").toString())
        assertThat(command).contains("-Xmx2048M")
        assertThat(command).contains("-jar")
        assertThat(command).contains(prepared.jarPath.toString())
        assertThat(command).contains("nogui")
    }

    @Test
    fun requireManagedJavaHome_returnsJavaHomeOnlyWhenBinaryExistsAndExecutable() {
        val filesDir = Files.createTempDirectory("mcgo-managed-java-ready")
        val javaHome = filesDir.resolve("jre/java-21")
        val javaBinary = javaHome.resolve("bin/java")
        Files.createDirectories(javaBinary.parent)
        Files.write(javaBinary, byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
        javaBinary.toFile().setExecutable(true, false)

        assertThat(requireManagedJavaHome(filesDir, 21)).isEqualTo(javaHome)
        assertThat(isRuntimeReady(filesDir, 21)).isTrue()
    }

    @Test
    fun requireManagedJavaHome_rejectsMissingJavaBinary() {
        val filesDir = Files.createTempDirectory("mcgo-managed-java-missing")

        val error = kotlin.runCatching { requireManagedJavaHome(filesDir, 17) }.exceptionOrNull()

        assertThat(error).isInstanceOf(JavaRuntimeInstallException::class.java)
        assertThat(error).hasMessageThat().contains("Java 17")
        assertThat(isRuntimeReady(filesDir, 17)).isFalse()
    }
    @Test
    fun paperDownloadUserAgentUsesCurrentVersion() {
        assertThat(PaperDownloadUserAgent).isEqualTo("MC-GO/0.2.9")
    }

    @Test
    fun scaledProgressReporterMapsInnerDownloadRange() {
        val events = mutableListOf<Int>()
        val reporter = scaledPaperDownloadProgressReporter(20, 80) { events += it }

        reporter(0)
        reporter(50)
        reporter(100)

        assertThat(events).containsExactly(20, 50, 80).inOrder()
    }

}
