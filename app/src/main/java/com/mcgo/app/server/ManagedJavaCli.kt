package com.mcgo.app.server

import android.os.Process
import java.nio.file.Paths
import kotlin.system.exitProcess

object ManagedJavaCli {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("[MC-GO] managed java cli requires JAVA_HOME")
            exitProcess(2)
        }
        val javaHome = Paths.get(args[0])
        val forwardedArgs = args.drop(1)
        val nativeLibraryDir = System.getProperty("java.library.path")
            ?.split(':')
            ?.firstOrNull()
            .orEmpty()
        val runtimeLayout = resolveManagedJavaRuntimeLayout(
            javaHome = javaHome,
            nativeLibraryDir = nativeLibraryDir,
            is64BitProcess = Process.is64Bit(),
        )
        val launcherFullVersion = readReleaseProperties(javaHome)["JAVA_VERSION"]
            ?.takeIf { it.isNotBlank() }
            ?: throw JavaRuntimeInstallException("托管 JRE 缺少 JAVA_VERSION：$javaHome/release")
        val launcherDotVersion = if (launcherFullVersion.startsWith("1.8")) "1.8" else launcherFullVersion.substringBefore('.')
        val tmpDir = System.getProperty("java.io.tmpdir")
        val userHome = System.getProperty("user.home")
        val exitCode = PaperJvmLauncher.launch(
            ManagedPaperLaunchConfig(
                workingDirectory = Paths.get(System.getProperty("user.dir")),
                jarPath = Paths.get(System.getProperty("user.dir")).resolve("server.jar"),
                logFile = Paths.get(""),
                arguments = buildList {
                    add(runtimeLayout.javaBinary.toString())
                    if (!tmpDir.isNullOrBlank()) {
                        add("-Djava.io.tmpdir=$tmpDir")
                    }
                    if (!userHome.isNullOrBlank()) {
                        add("-Duser.home=$userHome")
                    }
                    addAll(forwardedArgs)
                },
                environment = buildList {
                    System.getenv("JAVA_HOME")?.let { add("JAVA_HOME=$it") }
                    System.getenv("HOME")?.let { add("HOME=$it") }
                    System.getenv("TMPDIR")?.let { add("TMPDIR=$it") }
                    System.getenv("PATH")?.let { add("PATH=$it") }
                    System.getenv("LD_LIBRARY_PATH")?.let { add("LD_LIBRARY_PATH=$it") }
                },
                bootstrapLibraries = runtimeLayout.bootstrapLibraries,
                libjliPath = runtimeLayout.libjliPath,
                launcherFullVersion = launcherFullVersion,
                launcherDotVersion = launcherDotVersion,
            ),
        )
        exitProcess(exitCode)
    }
}
