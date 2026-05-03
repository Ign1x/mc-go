package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.BuildConfig
import com.mcgo.app.McGoUserAgent
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.model.createPaperServer
import java.nio.file.Files
import kotlin.test.Test

class PaperServerRuntimeTest {

    @Test
    fun buildPaperDownloadUrl_usesLatestBuildNameAndSha256FromApi() {
        val build = parseLatestPaperBuild(
            """
                {"project_id":"paper","project_name":"Paper","version":"1.21.4","builds":[1,2,227]}
            """.trimIndent(),
        )
        val sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val download = parsePaperDownloadName(
            """
                {"downloads":{"application":{"name":"paper-1.21.4-227.jar","sha256":"$sha256"}}}
            """.trimIndent(),
        )
        val parsedSha256 = parsePaperDownloadSha256(
            """
                {"downloads":{"application":{"name":"paper-1.21.4-227.jar","sha256":"$sha256"}}}
            """.trimIndent(),
        )

        assertThat(build).isEqualTo(227)
        assertThat(download).isEqualTo("paper-1.21.4-227.jar")
        assertThat(parsedSha256).isEqualTo(sha256)
        val url = "https://api.papermc.io/v2/projects/paper/versions/1.21.4/builds/227/downloads/paper-1.21.4-227.jar"
        assertThat(buildPaperDownloadUrl("1.21.4", build, download)).isEqualTo(url)
        val artifact = PaperDownloadArtifact("1.21.4", build, download, sha256, url)
        assertThat(artifact.sha256).isEqualTo(sha256)
        assertThat(artifact.downloadUrl).isEqualTo(url)
    }

    @Test
    fun preparePaperServerFiles_writesEulaAndServerProperties() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime")
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25566)
            .copy(
                worldName = "world_nether",
                onlineMode = false,
                pvpEnabled = false,
                gameMode = PaperGameMode.Creative,
                difficulty = PaperDifficulty.Hard,
            )

        val prepared = preparePaperServerFiles(server, workDir)

        assertThat(String(Files.readAllBytes(prepared.eulaPath))).contains("eula=true")
        val properties = String(Files.readAllBytes(prepared.serverPropertiesPath))
        assertThat(properties).contains("server-port=25566")
        assertThat(properties).contains("max-players=20")
        assertThat(properties).contains("level-name=world_nether")
        assertThat(properties).contains("gamemode=creative")
        assertThat(properties).contains("difficulty=hard")
        assertThat(properties).contains("online-mode=false")
        assertThat(properties).contains("pvp=false")
        assertThat(prepared.jarPath.fileName.toString()).isEqualTo("paper-1.21.4.jar")
    }

    @Test
    fun preparePaperServerFiles_prefersExplicitServerPropertiesOverride() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime-override")
        val overrideText = "motd=Custom MOTD\nonline-mode=false\npvp=false\n"
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)
            .copy(serverPropertiesOverride = overrideText)

        val prepared = preparePaperServerFiles(server, workDir)
        val properties = String(Files.readAllBytes(prepared.serverPropertiesPath))

        assertThat(properties).contains("server-port=25565")
        assertThat(properties).contains("motd=Custom MOTD")
        assertThat(properties).contains("online-mode=false")
        assertThat(properties).contains("pvp=false")
    }

    @Test
    fun preparePaperServerFiles_mergesOverrideButKeepsManagedServerPort() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime-override-merge")
        val overrideText = "server-port=24444\nmotd=Custom MOTD\nonline-mode=false\n"
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25566)
            .copy(serverPropertiesOverride = overrideText)

        val prepared = preparePaperServerFiles(server, workDir)
        val properties = String(Files.readAllBytes(prepared.serverPropertiesPath))

        assertThat(properties).contains("server-port=25566")
        assertThat(properties).contains("motd=Custom MOTD")
        assertThat(properties).contains("online-mode=false")
        assertThat(properties).doesNotContain("server-port=24444")
    }

    @Test
    fun managedPaperServerPaths_useAppPrivateDirectoryAndLogFile() {
        val filesDir = Files.createTempDirectory("mcgo-paper-paths")

        val serverDir = managedPaperServerDirectory(filesDir, "server-demo")
        val logFile = managedPaperServerLogFile(filesDir, "server-demo")

        assertThat(serverDir).isEqualTo(filesDir.resolve("servers/server-demo"))
        assertThat(logFile).isEqualTo(filesDir.resolve("servers/server-demo/logs/mcgo-latest.log"))
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
        assertThat(McGoUserAgent).isEqualTo("MC-GO/${BuildConfig.VERSION_NAME}")
        assertThat(PaperDownloadUserAgent).isEqualTo(McGoUserAgent)
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

    @Test
    fun shouldReusePaperJar_requiresMatchingRecordedSha256() {
        val tempDir = Files.createTempDirectory("mcgo-paper-jar-reuse")
        val missing = tempDir.resolve("missing.jar")
        val empty = tempDir.resolve("empty.jar")
        val valid = tempDir.resolve("paper.jar")
        Files.write(empty, byteArrayOf())
        Files.write(valid, "verified-paper".toByteArray())

        assertThat(shouldReusePaperJar(missing)).isFalse()
        assertThat(shouldReusePaperJar(empty)).isFalse()
        assertThat(shouldReusePaperJar(valid)).isFalse()
        Files.write(paperJarSha256File(valid), "deadbeef\n".toByteArray())
        assertThat(shouldReusePaperJar(valid)).isFalse()
        Files.write(paperJarSha256File(valid), (sha256Hex(valid) + "\n").toByteArray())
        assertThat(shouldReusePaperJar(valid)).isTrue()
    }
}
