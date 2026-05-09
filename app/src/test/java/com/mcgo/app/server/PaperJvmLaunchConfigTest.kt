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
        createRuntime(filesDir, majorVersion = 21)
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
    fun buildManagedPaperLaunchConfig_injectsAndroidCompatibilityFlagsBeforeJarLaunch() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-android-compat")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-android-compat")
        createRuntime(filesDir, majorVersion = 25, javaVersion = "25.0.3")
        val server = createPaperServer("兼容服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        val jarIndex = config.arguments.indexOf("-jar")
        assertThat(jarIndex).isGreaterThan(0)
        assertThat(config.arguments).contains("-Dterminal.jline=false")
        assertThat(config.arguments).contains("-Dorg.jline.terminal.ffm=false")
        assertThat(config.arguments).contains("-Dorg.jline.terminal.jni=false")
        assertThat(config.arguments).contains("-Dorg.jline.terminal.jna=false")
        assertThat(config.arguments).contains("-Dorg.jline.terminal.jansi=false")
        assertThat(config.arguments).contains("-Doshi.os.linux.allowudev=false")
        assertThat(config.arguments).contains("-Djna.boot.library.path=/data/app/com.mcgo.app/lib/arm64")
        assertThat(config.arguments).contains("-Djna.noclasspath=true")
        assertThat(config.arguments.indexOf("-Dterminal.jline=false")).isLessThan(jarIndex)
        assertThat(config.arguments.indexOf("-Dorg.jline.terminal.ffm=false")).isLessThan(jarIndex)
        assertThat(config.arguments.indexOf("-Dorg.jline.terminal.jni=false")).isLessThan(jarIndex)
        assertThat(config.arguments.indexOf("-Dorg.jline.terminal.jna=false")).isLessThan(jarIndex)
        assertThat(config.arguments.indexOf("-Dorg.jline.terminal.jansi=false")).isLessThan(jarIndex)
        assertThat(config.arguments.indexOf("-Doshi.os.linux.allowudev=false")).isLessThan(jarIndex)
        assertThat(config.arguments.indexOf("-Djna.boot.library.path=/data/app/com.mcgo.app/lib/arm64")).isLessThan(jarIndex)
        assertThat(config.arguments.indexOf("-Djna.noclasspath=true")).isLessThan(jarIndex)
    }

    @Test
    fun buildManagedPaperLaunchConfig_prefersJvmLibDirAtFrontOfLdLibraryPathToAvoidAndroidReexecFailures() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-jvm-first")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-jvm-first")
        createRuntime(filesDir, majorVersion = 21)
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        val ldLibraryPath = config.environment
            .single { it.startsWith("LD_LIBRARY_PATH=") }
            .removePrefix("LD_LIBRARY_PATH=")
            .split(':')

        assertThat(ldLibraryPath.first()).endsWith("/lib/server")
        assertThat(ldLibraryPath).containsAtLeast(
            filesDir.resolve("jre/java-21/lib").toString(),
            filesDir.resolve("jre/java-21/lib/server").toString(),
        )
    }

    @Test
    fun buildManagedPaperLaunchConfig_forcesForkLaunchMechanismOnModernJavaToAvoidAndroidJspawnhelperExec() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-fork")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-fork")
        createRuntime(filesDir, majorVersion = 17, javaVersion = "17.0.14")
        val server = createPaperServer("生存服", "1.18.2", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).contains("-Djdk.lang.Process.launchMechanism=FORK")
        assertThat(config.arguments).doesNotContain("-DPaper.IgnoreJavaVersion=true")
    }

    @Test
    fun buildManagedPaperLaunchConfig_addsPaperIgnoreJavaVersionForPaper12Through16WhenUsingOnlineJava17() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-ignore-gate")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-ignore-gate")
        createRuntime(filesDir, majorVersion = 17, javaVersion = "17.0.14")
        val server = createPaperServer("生存服", "1.16.5", maxPlayers = 20, memoryMb = 2048, port = 25565).copy(javaMajorVersion = 17)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).contains("-Djdk.lang.Process.launchMechanism=FORK")
        assertThat(config.arguments).contains("-DPaper.IgnoreJavaVersion=true")
    }

    @Test
    fun buildManagedPaperLaunchConfig_doesNotAddPaperIgnoreJavaVersionForPaper16WhenUsingJava11() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-java11-paper16")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-java11-paper16")
        createRuntime(filesDir, majorVersion = 11, javaVersion = "11.0.31")
        val server = createPaperServer("生存服", "1.16.5", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).contains("-Djdk.lang.Process.launchMechanism=FORK")
        assertThat(config.arguments).doesNotContain("-DPaper.IgnoreJavaVersion=true")
    }

    @Test
    fun buildManagedPaperLaunchConfig_addsPaperIgnoreJavaVersionForPaper112WhenUsingOnlineJava17() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-ignore-gate-112")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-ignore-gate-112")
        createRuntime(filesDir, majorVersion = 17, javaVersion = "17.0.14")
        val server = createPaperServer("经典服", "1.12.2", maxPlayers = 20, memoryMb = 1024, port = 25565).copy(javaMajorVersion = 17)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).contains("-Djdk.lang.Process.launchMechanism=FORK")
        assertThat(config.arguments).contains("-DPaper.IgnoreJavaVersion=true")
    }

    @Test
    fun buildManagedPaperLaunchConfig_usesLauncherVersionMetadataFromRuntimeReleaseFile() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache")
        createRuntime(filesDir, majorVersion = 21)
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
        createRuntime(filesDir, majorVersion = 8, javaVersion = "1.8.0_402")
        val server = createPaperServer("经典服", "1.11.2", maxPlayers = 20, memoryMb = 1024, port = 25565)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.launcherFullVersion).isEqualTo("1.8.0_402")
        assertThat(config.launcherDotVersion).isEqualTo("1.8")
        assertThat(config.arguments).doesNotContain("-Djdk.lang.Process.launchMechanism=FORK")
    }

    @Test
    fun buildManagedPaperLaunchConfig_doesNotAddPaperIgnoreJavaVersionForTrustedOnlineJava21EvenWhenRuntimeVersionContainsBuildMetadata() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-java21-online-paper121")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-java21-online-paper121")
        createRuntime(
            filesDir,
            majorVersion = 21,
            javaVersion = "21.0.5",
            javaRuntimeVersion = "21.0.5+-adhoc.runner.openjdk",
        )
        val server = createPaperServer("生存服", "1.21.11", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).doesNotContain("-DPaper.IgnoreJavaVersion=true")
    }

    @Test
    fun buildManagedPaperLaunchConfig_addsPaperIgnoreJavaVersionForPojavJava21InternalBuildOnPaper121() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-java21-pojav-paper121")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-java21-pojav-paper121")
        createRuntime(
            filesDir,
            majorVersion = 21,
            javaVersion = "21.0.1",
            javaRuntimeVersion = "21.0.1-internal-adhoc.runner.openjdk-21",
        )
        val server = createPaperServer("生存服", "1.21.11", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).contains("-DPaper.IgnoreJavaVersion=true")
    }

    @Test
    fun buildManagedPaperLaunchConfig_doesNotAddPaperIgnoreJavaVersionForGaJava21OnPaper121() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-java21-ga-paper121")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-java21-ga-paper121")
        createRuntime(filesDir, majorVersion = 21, javaVersion = "21.0.5", javaRuntimeVersion = "21.0.5+13")
        val server = createPaperServer("生存服", "1.21.11", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).doesNotContain("-DPaper.IgnoreJavaVersion=true")
    }

    private fun createRuntime(
        filesDir: java.nio.file.Path,
        majorVersion: Int,
        javaVersion: String = "21.0.6",
        javaRuntimeVersion: String? = null,
    ) {
        val javaHome = filesDir.resolve("jre/java-$majorVersion")
        Files.createDirectories(javaHome.resolve("bin"))
        Files.write(javaHome.resolve("bin/java"), byteArrayOf(1))
        javaHome.resolve("bin/java").toFile().setExecutable(true, false)
        Files.write(
            javaHome.resolve("release"),
            buildString {
                appendLine("OS_ARCH=\"aarch64\"")
                appendLine("JAVA_VERSION=\"$javaVersion\"")
                javaRuntimeVersion?.let { appendLine("JAVA_RUNTIME_VERSION=\"$it\"") }
            }.toByteArray(),
        )
        val javaLibDir = javaHome.resolve("lib")
        Files.createDirectories(javaLibDir.resolve("server"))
        Files.write(javaLibDir.resolve("libjli.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("server/libjvm.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libverify.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libjava.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libnet.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libnio.so"), byteArrayOf(1))
    }
}
