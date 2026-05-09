package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.BuildConfig
import com.mcgo.app.McGoUserAgent
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.createPurpurServer
import com.mcgo.app.ui.model.createVanillaServer
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
    fun preparePaperServerFiles_writesSparkConfigThatDisablesBackgroundNativeProfiler() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime-spark")
        val server = createPaperServer("性能服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25566)

        val prepared = preparePaperServerFiles(server, workDir)
        val sparkConfigPath = prepared.workDir.resolve("plugins/spark/config.json")

        assertThat(Files.isRegularFile(sparkConfigPath)).isTrue()
        val sparkConfig = String(Files.readAllBytes(sparkConfigPath))
        assertThat(sparkConfig).contains("\"backgroundProfiler\": false")
        assertThat(sparkConfig).contains("\"backgroundProfilerEngine\": \"java\"")
    }

    @Test
    fun preparePaperServerFiles_preservesExistingSparkSettingsWhileForcingAndroidSafeProfilerMode() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime-spark-merge")
        val server = createPaperServer("性能服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25566)
        val serverDir = workDir.resolve(server.id)
        val sparkConfigPath = serverDir.resolve("plugins/spark/config.json")
        Files.createDirectories(sparkConfigPath.parent)
        Files.write(
            sparkConfigPath,
            """
            {
              "backgroundProfiler": true,
              "backgroundProfilerEngine": "async",
              "extraMetric": true
            }
            """.trimIndent().toByteArray(),
        )

        val prepared = preparePaperServerFiles(server, workDir)
        val sparkConfig = String(Files.readAllBytes(prepared.workDir.resolve("plugins/spark/config.json")))

        assertThat(sparkConfig).contains("\"backgroundProfiler\": false")
        assertThat(sparkConfig).contains("\"backgroundProfilerEngine\": \"java\"")
        assertThat(sparkConfig).contains("\"extraMetric\": true")
    }

    @Test
    fun preparePaperServerFiles_onlyOverridesTopLevelSparkProfilerKeys_notNestedLookalikes() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime-spark-nested")
        val server = createPaperServer("性能服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25566)
        val serverDir = workDir.resolve(server.id)
        val sparkConfigPath = serverDir.resolve("plugins/spark/config.json")
        Files.createDirectories(sparkConfigPath.parent)
        Files.write(
            sparkConfigPath,
            """
            {
              "nested": {
                "backgroundProfiler": true,
                "backgroundProfilerEngine": "async"
              },
              "backgroundProfiler": true,
              "backgroundProfilerEngine": "async",
              "extraMetric": true
            }
            """.trimIndent().toByteArray(),
        )

        val prepared = preparePaperServerFiles(server, workDir)
        val sparkConfig = String(Files.readAllBytes(prepared.workDir.resolve("plugins/spark/config.json")))

        assertThat(sparkConfig).contains("\"nested\": {")
        val nestedProfilerTrueIndex = sparkConfig.indexOf("\"backgroundProfiler\": true")
        val nestedProfilerEngineAsyncIndex = sparkConfig.indexOf("\"backgroundProfilerEngine\": \"async\"")
        val topLevelProfilerFalseIndex = sparkConfig.lastIndexOf("\"backgroundProfiler\": false")
        val topLevelProfilerEngineJavaIndex = sparkConfig.lastIndexOf("\"backgroundProfilerEngine\": \"java\"")
        assertThat(nestedProfilerTrueIndex).isAtLeast(0)
        assertThat(nestedProfilerEngineAsyncIndex).isAtLeast(0)
        assertThat(topLevelProfilerFalseIndex).isAtLeast(0)
        assertThat(topLevelProfilerEngineJavaIndex).isAtLeast(0)
        assertThat(nestedProfilerTrueIndex).isLessThan(topLevelProfilerFalseIndex)
        assertThat(nestedProfilerEngineAsyncIndex).isLessThan(topLevelProfilerEngineJavaIndex)
        assertThat(sparkConfig).contains("\"extraMetric\": true")
    }

    @Test
    fun preparePaperServerFiles_doesNotWriteSparkConfigForVanillaServer() {
        val workDir = Files.createTempDirectory("mcgo-vanilla-runtime-spark")
        val server = createVanillaServer("原版服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25566)

        val prepared = preparePaperServerFiles(server, workDir)

        assertThat(Files.exists(prepared.workDir.resolve("plugins/spark/config.json"))).isFalse()
    }

    @Test
    fun preparePaperServerFiles_usesVanillaJarNameForVanillaServerType() {
        val workDir = Files.createTempDirectory("mcgo-vanilla-runtime")
        val server = createVanillaServer("原版服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25566)

        val prepared = preparePaperServerFiles(server, workDir)

        assertThat(prepared.jarPath.fileName.toString()).isEqualTo("vanilla-1.21.4.jar")
        assertThat(String(Files.readAllBytes(prepared.serverPropertiesPath))).contains("server-port=25566")
    }

    @Test
    fun preparePaperServerFiles_usesPurpurJarNameForPurpurServerType() {
        val workDir = Files.createTempDirectory("mcgo-purpur-runtime")
        val server = createPurpurServer("Purpur服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25567)

        val prepared = preparePaperServerFiles(server, workDir)

        assertThat(prepared.jarPath.fileName.toString()).isEqualTo("purpur-1.21.4.jar")
        assertThat(String(Files.readAllBytes(prepared.serverPropertiesPath))).contains("server-port=25567")
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

    @Test
    fun shouldReusePaperJar_rejectsRecordedJarWhenBundledAndroidJnaIsTooOldForServer() {
        val jarPath = createServerJarWithLibrariesList(
            """
            deadbeef\tnet.java.dev.jna:jna:5.19.0\tnet/java/dev/jna/jna/5.19.0/jna-5.19.0.jar
            """.trimIndent(),
        )
        Files.write(paperJarSha256File(jarPath), (sha256Hex(jarPath) + "\n").toByteArray())

        assertThat(shouldReusePaperJar(jarPath)).isFalse()
    }

    @Test
    fun detectServerJnaVersion_readsVersionFromPaperLibrariesList() {
        val jarPath = createServerJarWithLibrariesList(
            """
            deadbeef\tnet.java.dev.jna:jna:5.18.1\tnet/java/dev/jna/jna/5.18.1/jna-5.18.1.jar
            cafebabe\tcom.github.oshi:oshi-core:6.9.0\tcom/github/oshi/oshi-core/6.9.0/oshi-core-6.9.0.jar
            """.trimIndent(),
        )

        assertThat(detectServerJnaVersion(jarPath)).isEqualTo("5.18.1")
    }

    @Test
    fun validateBundledAndroidJnaCompatibility_allowsSameOrOlderServerJnaMinor() {
        val jarPath = createServerJarWithLibrariesList(
            """
            deadbeef\tnet.java.dev.jna:jna:5.17.0\tnet/java/dev/jna/jna/5.17.0/jna-5.17.0.jar
            """.trimIndent(),
        )
        val server = createPaperServer("兼容服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25565)

        validateBundledAndroidJnaCompatibility(server, jarPath)
    }

    @Test
    fun validateBundledAndroidJnaCompatibility_rejectsNewerServerJnaMinorWithActionableMessage() {
        val jarPath = createServerJarWithLibrariesList(
            """
            deadbeef\tnet.java.dev.jna:jna:5.19.0\tnet/java/dev/jna/jna/5.19.0/jna-5.19.0.jar
            """.trimIndent(),
        )
        val server = createPaperServer("兼容服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val error = assertFailsWith<JavaRuntimeInstallException> {
            validateBundledAndroidJnaCompatibility(server, jarPath)
        }

        assertThat(error).hasMessageThat().contains("JNA 5.19.0")
        assertThat(error).hasMessageThat().contains("5.18.1")
        assertThat(error).hasMessageThat().contains("更新 MC-GO")
    }

    private fun createServerJarWithLibrariesList(librariesList: String): java.nio.file.Path {
        val jarPath = Files.createTempFile("mcgo-paper-libraries", ".jar")
        ZipOutputStream(Files.newOutputStream(jarPath)).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/libraries.list"))
            zip.write(librariesList.replace("\\t", "\t").toByteArray())
            zip.closeEntry()
        }
        return jarPath
    }
}
