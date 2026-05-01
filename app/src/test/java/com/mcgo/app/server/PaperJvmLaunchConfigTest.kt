package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.createPaperServer
import java.nio.file.Files
import kotlin.test.Test

class PaperJvmLaunchConfigTest {

    @Test
    fun buildManagedPaperLaunchConfig_usesHeadlessManagedRuntimeAndAppPrivatePaths() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache")
        createRuntime(filesDir)
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.workingDirectory).isEqualTo(managedPaperServerDirectory(filesDir, server.id))
        assertThat(config.logFile).isEqualTo(managedPaperServerLogFile(filesDir, server.id))
        assertThat(config.arguments.first()).endsWith("/bin/java")
        assertThat(config.arguments).contains("-Djava.awt.headless=true")
        assertThat(config.arguments).contains("-jar")
        assertThat(config.arguments).contains("nogui")
        assertThat(config.arguments).contains(config.jarPath.toString())
        assertThat(config.environment).contains("JAVA_HOME=${filesDir.resolve("jre/java-21")}")
        assertThat(config.environment).contains("TMPDIR=$cacheDir")
        assertThat(config.environment.any { it.startsWith("LD_LIBRARY_PATH=") }).isTrue()
        assertThat(config.environment.joinToString("\n")).doesNotContain("Ter" + "mux")
    }

    @Test
    fun buildManagedPaperLaunchConfig_usesLauncherVersionMetadataFromRuntimeReleaseFile() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache")
        createRuntime(filesDir)
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.launcherFullVersion).isEqualTo("21.0.6")
        assertThat(config.launcherDotVersion).isEqualTo("21")
    }

    @Test
    fun buildManagedPaperLaunchConfig_preservesJava8DotVersion() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-java8")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-java8")
        createRuntime(filesDir, javaVersion = "1.8.0_402")
        val server = createPaperServer("经典服", "1.21.4", maxPlayers = 20, memoryMb = 1024, port = 25565)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.launcherFullVersion).isEqualTo("1.8.0_402")
        assertThat(config.launcherDotVersion).isEqualTo("1.8")
    }

    private fun createRuntime(filesDir: java.nio.file.Path, javaVersion: String = "21.0.6") {
        val javaHome = filesDir.resolve("jre/java-21")
        Files.createDirectories(javaHome.resolve("bin"))
        Files.write(javaHome.resolve("bin/java"), byteArrayOf(1))
        javaHome.resolve("bin/java").toFile().setExecutable(true, false)
        Files.write(javaHome.resolve("release"), "OS_ARCH=\"aarch64\"\nJAVA_VERSION=\"$javaVersion\"\n".toByteArray())
        val javaLibDir = javaHome.resolve("lib/aarch64")
        Files.createDirectories(javaLibDir.resolve("jli"))
        Files.createDirectories(javaLibDir.resolve("server"))
        Files.write(javaLibDir.resolve("jli/libjli.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("server/libjvm.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libverify.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libjava.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libnet.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libnio.so"), byteArrayOf(1))
    }
}
