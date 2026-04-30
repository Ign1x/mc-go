package com.mcgo.app.server

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.mcgo.app.ui.model.ServerCardState

const val TermuxPackageName = "com.termux"
const val TermuxRunCommandPermission = "com.termux.permission.RUN_COMMAND"

private const val TermuxRunCommandService = "com.termux.app.RunCommandService"
private const val TermuxRunCommandAction = "com.termux.RUN_COMMAND"
private const val ExtraCommandPath = "com.termux.RUN_COMMAND_PATH"
private const val ExtraArguments = "com.termux.RUN_COMMAND_ARGUMENTS"
private const val ExtraWorkDir = "com.termux.RUN_COMMAND_WORKDIR"
private const val ExtraRunner = "com.termux.RUN_COMMAND_RUNNER"
private const val ExtraBackground = "com.termux.RUN_COMMAND_BACKGROUND"
private const val ExtraShellName = "com.termux.RUN_COMMAND_SHELL_NAME"
private const val ExtraShellCreateMode = "com.termux.RUN_COMMAND_SHELL_CREATE_MODE"
private const val ExtraCommandLabel = "com.termux.RUN_COMMAND_COMMAND_LABEL"
private const val ExtraCommandDescription = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
private const val ExtraPendingIntent = "com.termux.RUN_COMMAND_PENDING_INTENT"

private const val TermuxBashPath = "/data/data/com.termux/files/usr/bin/bash"
private const val TermuxHomePath = "/data/data/com.termux/files/home"

object TermuxRunCommandBridge {
    fun isTermuxInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(TermuxPackageName, 0)
        true
    }.getOrDefault(false)

    fun hasRunCommandPermission(context: Context): Boolean =
        context.checkSelfPermission(TermuxRunCommandPermission) == PackageManager.PERMISSION_GRANTED

    fun startPaperServer(
        context: Context,
        server: ServerCardState,
        artifact: PaperDownloadArtifact,
    ) {
        val script = buildTermuxPaperLaunchScript(server, artifact)
        context.startService(
            buildTermuxRunCommandIntent(
                context = context,
                serverId = server.id,
                script = script,
                shellName = "MC-GO ${server.name}",
                label = "MC-GO 启动 ${server.name}",
                description = "MC-GO 通过 Termux 运行 Paper ${server.minecraftVersion}",
                withResult = true,
            ),
        )
    }

    fun stopPaperServer(context: Context, serverId: String) {
        context.startService(
            buildTermuxRunCommandIntent(
                context = context,
                serverId = serverId,
                script = buildTermuxStopScript(serverId),
                shellName = "MC-GO Stop $serverId",
                label = "MC-GO 停止服务器",
                description = "MC-GO 通过 Termux 停止 Paper 进程",
                withResult = false,
            ),
        )
    }
}

fun buildTermuxRunCommandIntent(
    context: Context,
    serverId: String,
    script: String,
    shellName: String,
    label: String,
    description: String,
    withResult: Boolean,
): Intent = Intent(TermuxRunCommandAction).apply {
    setClassName(TermuxPackageName, TermuxRunCommandService)
    putExtra(ExtraCommandPath, TermuxBashPath)
    putExtra(ExtraArguments, arrayOf("-lc", script))
    putExtra(ExtraWorkDir, TermuxHomePath)
    putExtra(ExtraRunner, "app-shell")
    putExtra(ExtraBackground, true)
    putExtra(ExtraShellName, shellName)
    putExtra(ExtraShellCreateMode, "always")
    putExtra(ExtraCommandLabel, label)
    putExtra(ExtraCommandDescription, description)
    if (withResult) putExtra(ExtraPendingIntent, termuxResultPendingIntent(context, serverId))
}

private fun termuxResultPendingIntent(context: Context, serverId: String): PendingIntent {
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_MUTABLE
    } else {
        0
    }
    val intent = Intent(context, TermuxCommandResultReceiver::class.java).apply {
        data = Uri.parse("mcgo://termux-result/${Uri.encode(serverId)}")
        putExtra("serverId", serverId)
    }
    return PendingIntent.getBroadcast(context, serverId.hashCode(), intent, flags)
}
