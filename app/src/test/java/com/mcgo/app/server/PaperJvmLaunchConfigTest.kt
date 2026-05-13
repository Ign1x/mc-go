package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.createFabricServer
import com.mcgo.app.ui.model.createForgeServer
import com.mcgo.app.ui.model.createNeoForgeServer
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.createQuiltServer
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
    fun buildManagedPaperLaunchConfig_prefersAuthorizedWorkspaceWhenProvided_butKeepsLogsInPrivateDirectory() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-authorized")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-authorized")
        val authorizedRoot = Files.createTempDirectory("mcgo-authorized-root")
        createRuntime(filesDir, majorVersion = 21)
        val server = createPaperServer("外置服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)
        val authorizedWorkDir = authorizedRoot.resolve("servers/${server.id}")
        Files.createDirectories(authorizedWorkDir)
        val authorizedJar = authorizedWorkDir.resolve("server.jar")
        Files.write(authorizedJar, "authorized-server".toByteArray())
        Files.write(paperJarSha256File(authorizedJar), (sha256Hex(authorizedJar) + "\n").toByteArray())

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
            serverWorkDirOverride = authorizedWorkDir,
        )

        assertThat(config.workingDirectory).isEqualTo(authorizedWorkDir)
        assertThat(config.arguments).contains(authorizedJar.toString())
        assertThat(config.logFile).isEqualTo(managedPaperServerLogFile(filesDir, server.id))
    }

    @Test
    fun buildManagedPaperLaunchConfig_usesImportedServerPayloadJarForDirectJarServerTypes() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-imported-payload")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-imported-payload")
        createRuntime(filesDir, majorVersion = 21)
        val server = createPaperServer("导入服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)
        val serverDir = managedPaperServerDirectory(filesDir, server.id)
        Files.createDirectories(serverDir)
        val importedJar = serverDir.resolve("server.jar")
        Files.write(importedJar, "imported-server".toByteArray())
        Files.write(paperJarSha256File(importedJar), (sha256Hex(importedJar) + "\n").toByteArray())

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).contains("-jar")
        assertThat(config.arguments).contains(importedJar.toString())
        assertThat(config.arguments).doesNotContain(config.jarPath.toString())
    }

    @Test
    fun buildManagedPaperLaunchConfig_prefersFabricServerLaunchJarForImportedWorkspace() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-imported-fabric")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-imported-fabric")
        createRuntime(filesDir, majorVersion = 21)
        val server = createFabricServer("Fabric整合服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)
        val serverDir = managedPaperServerDirectory(filesDir, server.id)
        Files.createDirectories(serverDir)
        val importedJar = serverDir.resolve("server.jar")
        val launchJar = serverDir.resolve("fabric-server-launch.jar")
        Files.write(importedJar, "vanilla-server".toByteArray())
        Files.write(launchJar, "fabric-launcher".toByteArray())
        Files.write(paperJarSha256File(launchJar), (sha256Hex(launchJar) + "\n").toByteArray())

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).contains("-jar")
        assertThat(config.arguments).contains(launchJar.toString())
        assertThat(config.arguments).doesNotContain(importedJar.toString())
    }

    @Test
    fun buildManagedPaperLaunchConfig_prefersQuiltServerLaunchJarForImportedWorkspace() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-imported-quilt")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-imported-quilt")
        createRuntime(filesDir, majorVersion = 21)
        val server = createQuiltServer("Quilt整合服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)
        val serverDir = managedPaperServerDirectory(filesDir, server.id)
        Files.createDirectories(serverDir)
        val importedJar = serverDir.resolve("server.jar")
        val launchJar = serverDir.resolve("quilt-server-launch.jar")
        Files.write(importedJar, "vanilla-server".toByteArray())
        Files.write(launchJar, "quilt-launcher".toByteArray())
        Files.write(paperJarSha256File(launchJar), (sha256Hex(launchJar) + "\n").toByteArray())

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).contains("-jar")
        assertThat(config.arguments).contains(launchJar.toString())
        assertThat(config.arguments).doesNotContain(importedJar.toString())
    }

    @Test
    fun buildManagedPaperLaunchConfig_supportsForgeArgfileLaunchAndQuiltCustomServerLaunchJar() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-modded")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-modded")
        createRuntime(filesDir, majorVersion = 21)
        val forgeServer = createForgeServer("Forge服", "1.21.4", maxPlayers = 20, memoryMb = 3072, port = 25569)
        val quiltServer = createQuiltServer("Quilt服", "1.21.4", maxPlayers = 20, memoryMb = 3072, port = 25570)
        val forgeDir = managedPaperServerDirectory(filesDir, forgeServer.id)
        val quiltDir = managedPaperServerDirectory(filesDir, quiltServer.id)
        Files.createDirectories(forgeDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.16"))
        Files.write(forgeDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.16/unix_args.txt"), "--launchTarget forgeserver\n".toByteArray())
        Files.write(forgeDir.resolve("user_jvm_args.txt"), "# user args\n".toByteArray())
        Files.createDirectories(quiltDir)
        Files.write(quiltDir.resolve("quilt-server-launch.jar"), byteArrayOf(1, 2, 3))

        val forgeConfig = buildManagedPaperLaunchConfig(
            server = forgeServer,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )
        val quiltConfig = buildManagedPaperLaunchConfig(
            server = quiltServer,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(forgeConfig.arguments).contains("@user_jvm_args.txt")
        assertThat(forgeConfig.arguments.any { it.contains("unix_args.txt") }).isTrue()
        assertThat(forgeConfig.arguments).doesNotContain("-jar")
        assertThat(quiltConfig.arguments).contains("-jar")
        assertThat(quiltConfig.arguments).contains(quiltDir.resolve("quilt-server-launch.jar").toString())
    }

    @Test
    fun buildManagedPaperLaunchConfig_createsMissingUserJvmArgsForForgeLaunches() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-user-args")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-user-args")
        createRuntime(filesDir, majorVersion = 21)
        val server = createForgeServer("Forge服", "1.21.4", maxPlayers = 20, memoryMb = 3072, port = 25565)
        val forgeDir = managedPaperServerDirectory(filesDir, server.id)
        Files.createDirectories(forgeDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.16"))
        Files.write(forgeDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.16/unix_args.txt"), "--launchTarget forge_server\n".toByteArray())

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).contains("@user_jvm_args.txt")
        assertThat(Files.isRegularFile(forgeDir.resolve("user_jvm_args.txt"))).isTrue()
        val generatedArgs = String(Files.readAllBytes(forgeDir.resolve("user_jvm_args.txt")))
        assertThat(generatedArgs).contains("-Xms1536M")
        assertThat(generatedArgs).contains("-Xmx3072M")
        assertThat(generatedArgs).doesNotContain("AlwaysPreTouch")
    }

    @Test
    fun buildManagedPaperLaunchConfig_preservesExistingSetupScriptLogOutput() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-setup-log")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-setup-log")
        createRuntime(filesDir, majorVersion = 21)
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)
        val serverDir = managedPaperServerDirectory(filesDir, server.id)
        Files.createDirectories(serverDir)
        val importedJar = serverDir.resolve("server.jar")
        Files.write(importedJar, "imported-server".toByteArray())
        Files.write(paperJarSha256File(importedJar), (sha256Hex(importedJar) + "\n").toByteArray())
        val logFile = managedPaperServerLogFile(filesDir, server.id)
        Files.createDirectories(logFile.parent)
        Files.write(logFile, "install-step-1\ninstall-step-2\n".toByteArray())

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.logFile).isEqualTo(logFile)
        val logText = String(Files.readAllBytes(logFile))
        assertThat(logText).contains("install-step-1")
        assertThat(logText).contains("install-step-2")
    }

    @Test
    fun buildManagedPaperLaunchConfig_sanitizesImportedNeoForgeUserJvmArgsForAndroid() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-neoforge-user-args")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-neoforge-user-args")
        createRuntime(filesDir, majorVersion = 21)
        val server = createNeoForgeServer("ATM10", "1.21.1", maxPlayers = 20, memoryMb = 2048, port = 25565)
        val serverDir = managedPaperServerDirectory(filesDir, server.id)
        Files.createDirectories(serverDir.resolve("libraries/net/neoforged/neoforge/21.1.224"))
        Files.write(serverDir.resolve("libraries/net/neoforged/neoforge/21.1.224/unix_args.txt"), "--fml.neoForgeVersion 21.1.224\n".toByteArray())
        Files.write(
            serverDir.resolve("user_jvm_args.txt"),
            """
            -Xms4G
            -Xmx8G
            -XX:+AlwaysPreTouch
            -XX:+UseG1GC
            """.trimIndent().toByteArray(),
        )

        val config = buildManagedPaperLaunchConfig(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).contains("@user_jvm_args.txt")
        val sanitized = String(Files.readAllBytes(serverDir.resolve("user_jvm_args.txt")))
        assertThat(sanitized).contains("-Xms1024M")
        assertThat(sanitized).contains("-Xmx2048M")
        assertThat(sanitized).contains("-XX:+UseG1GC")
        assertThat(sanitized).doesNotContain("-Xms4G")
        assertThat(sanitized).doesNotContain("-Xmx8G")
        assertThat(sanitized).doesNotContain("AlwaysPreTouch")
    }

    @Test
    fun buildManagedPaperLaunchConfig_prefersInstalledForgeArgfilePathWhenPresent() {
        val filesDir = Files.createTempDirectory("mcgo-launch-files-forge-installed")
        val cacheDir = Files.createTempDirectory("mcgo-launch-cache-forge-installed")
        createRuntime(filesDir, majorVersion = 21)
        val forgeServer = createForgeServer("Forge服", "1.21.4", maxPlayers = 20, memoryMb = 3072, port = 25569)
        val forgeDir = managedPaperServerDirectory(filesDir, forgeServer.id)
        Files.createDirectories(forgeDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.8"))
        Files.write(forgeDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.8/unix_args.txt"), "--launchTarget forgeserver\n".toByteArray())
        Files.write(forgeDir.resolve("user_jvm_args.txt"), "# user args\n".toByteArray())

        val config = buildManagedPaperLaunchConfig(
            server = forgeServer,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(config.arguments).contains("@libraries/net/minecraftforge/forge/1.21.4-54.1.8/unix_args.txt")
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

    @Test
    fun prepareManagedPaperRuntimeContext_exportsAppProcessLaunchMetadataWithoutPrivateExecutableWrapper() {
        val filesDir = Files.createTempDirectory("mcgo-runtime-context-wrapper-files")
        val cacheDir = Files.createTempDirectory("mcgo-runtime-context-wrapper-cache")
        createRuntime(filesDir, majorVersion = 21)
        val server = createNeoForgeServer("NeoForge整合服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val context = prepareManagedPaperRuntimeContext(
            server = server,
            filesDir = filesDir,
            cacheDir = cacheDir,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
            applicationSourceDir = "/data/app/com.mcgo.app/base.apk",
        )

        assertThat(context.javaBinary).isEqualTo("/system/bin/app_process")
        assertThat(context.environment).contains("MCGO_JAVA_APP_PROCESS=/system/bin/app_process")
        assertThat(context.environment).contains("MCGO_JAVA_MAIN_CLASS=com.mcgo.app.server.ManagedJavaCli")
        assertThat(context.environment).contains("MCGO_JAVA_CLASSPATH=/data/app/com.mcgo.app/base.apk")
        assertThat(context.environment).contains("MCGO_JAVA_HOME=${filesDir.resolve("jre/java-21")}")
        assertThat(context.environment).contains("MCGO_JAVA_NATIVE_LAUNCHER_LIB=/data/app/com.mcgo.app/lib/arm64/libpaper_jli_launcher.so")
        assertThat(context.environment.joinToString("\n")).doesNotContain("MCGO_JAVA_WRAPPER=")
        assertThat(context.environment.single { it.startsWith("PATH=") }).doesNotContain("runtime-tools/java-wrapper")
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
