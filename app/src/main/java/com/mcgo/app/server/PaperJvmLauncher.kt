package com.mcgo.app.server

import java.nio.file.Path

object PaperJvmLauncher {
    init {
        val absoluteLibPath = System.getProperty("mcgo.paperJvmLauncher.absoluteLibPath")
            ?.takeIf { it.isNotBlank() }
        if (absoluteLibPath != null) {
            System.load(absoluteLibPath)
        } else {
            System.loadLibrary("paper_jli_launcher")
        }
    }

    fun launch(config: ManagedPaperLaunchConfig): Int = nativeLaunchJvm(
        arguments = config.arguments.toTypedArray(),
        environment = config.environment.toTypedArray(),
        workingDirectory = config.workingDirectory.toString(),
        logFile = config.logFile.toString(),
        libjliPath = config.libjliPath.toString(),
        bootstrapLibraries = config.bootstrapLibraries.map(Path::toString).toTypedArray(),
        launcherFullVersion = config.launcherFullVersion,
        launcherDotVersion = config.launcherDotVersion,
    )

    fun requestStop(): Boolean = nativeRequestStop()

    fun queueStopRequest(): Boolean = nativeQueueStopRequest()

    fun submitCommand(command: String): Boolean = nativeSubmitCommand(command)

    fun clearPendingStopRequest() {
        nativeClearPendingStopRequest()
    }

    private external fun nativeLaunchJvm(
        arguments: Array<String>,
        environment: Array<String>,
        workingDirectory: String,
        logFile: String,
        libjliPath: String,
        bootstrapLibraries: Array<String>,
        launcherFullVersion: String,
        launcherDotVersion: String,
    ): Int

    private external fun nativeRequestStop(): Boolean
    private external fun nativeQueueStopRequest(): Boolean
    private external fun nativeSubmitCommand(command: String): Boolean
    private external fun nativeClearPendingStopRequest()
}
