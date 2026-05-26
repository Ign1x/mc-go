package com.mcgo.app.server

import com.mcgo.app.ui.model.ServerTunnelBinding
import java.nio.file.Path

enum class StopTargetAction {
    NoActiveRuntime,
    IgnoreMismatchedServer,
    HandleCurrentServer,
}

enum class CommandTargetAction {
    NoActiveRuntime,
    IgnoreMismatchedServer,
    HandleCurrentServer,
}

fun startConflictMessage(currentServerId: String?, requestedServerId: String): String? = when {
    currentServerId == null -> null
    currentServerId == requestedServerId -> "该服务器已在启动或运行中，请稍候"
    else -> "当前运行时槽位正忙，请稍后再试"
}

fun resolveStopTargetAction(currentServerId: String?, requestedServerId: String?): StopTargetAction = when {
    currentServerId == null -> StopTargetAction.NoActiveRuntime
    requestedServerId == null || requestedServerId == currentServerId -> StopTargetAction.HandleCurrentServer
    else -> StopTargetAction.IgnoreMismatchedServer
}

fun resolveCommandTargetAction(currentServerId: String?, requestedServerId: String?): CommandTargetAction = when {
    currentServerId == null -> CommandTargetAction.NoActiveRuntime
    requestedServerId == null || requestedServerId == currentServerId -> CommandTargetAction.HandleCurrentServer
    else -> CommandTargetAction.IgnoreMismatchedServer
}

enum class StopHandlingAction {
    CancelPendingLaunch,
    AwaitStopSignalDelivery,
    StopSignalAlreadyDelivered,
}

fun stopRequestMessage(): String = "已请求停止内置 Paper 进程，等待运行时退出"

fun runtimeCommandMessage(command: String): String = "已从控制台发送指令：$command"

fun queuedStopRequestMessage(): String = "已排队 stop 指令，等待内置 Paper 进程接收"

fun resolveStopHandlingAction(
    runtimeLaunchSubmitted: Boolean,
    stopSignalDelivered: Boolean,
): StopHandlingAction = when {
    !runtimeLaunchSubmitted -> StopHandlingAction.CancelPendingLaunch
    stopSignalDelivered -> StopHandlingAction.StopSignalAlreadyDelivered
    else -> StopHandlingAction.AwaitStopSignalDelivery
}

fun shouldRetryQueuedStopSignal(
    currentServerId: String?,
    serverId: String,
    stopRequested: Boolean,
    stopSignalDelivered: Boolean,
): Boolean = stopRequested && !stopSignalDelivered && currentServerId == serverId

fun javaRuntimeMayRequireFreshProcess(previousJavaMajorVersion: Int?, nextJavaMajorVersion: Int): Boolean =
    previousJavaMajorVersion != null && previousJavaMajorVersion != nextJavaMajorVersion

fun runtimeMonitorEventStatus(runtimeRunning: Boolean, stopRequested: Boolean): PaperServerEventStatus = when {
    stopRequested -> PaperServerEventStatus.Stopping
    runtimeRunning -> PaperServerEventStatus.Running
    else -> PaperServerEventStatus.Launching
}

fun updatedOnlinePlayersFromLogLine(currentOnlinePlayers: Int, logLine: String): Int? {
    val normalized = logLine.trim()
    val joinMatch = Regex("""^\[[^]]+]:\s+(?!<)(?!\[)(?![^\s:]+:).+ joined the game$""", RegexOption.IGNORE_CASE).matches(normalized)
    val leaveMatch = Regex("""^\[[^]]+]:\s+(?!<)(?!\[)(?![^\s:]+:).+ left the game$""", RegexOption.IGNORE_CASE).matches(normalized)
    return when {
        joinMatch -> currentOnlinePlayers + 1
        leaveMatch -> (currentOnlinePlayers - 1).coerceAtLeast(0)
        else -> null
    }
}

fun updatedOnlinePlayerNamesFromLogLine(currentOnlinePlayerNames: List<String>, logLine: String): List<String>? {
    val normalized = logLine.trim()
    val joinMatch = Regex("""^\[[^]]+]:\s+(?!<)(?!\[)(?![^\s:]+:)(.+) joined the game$""", RegexOption.IGNORE_CASE)
        .find(normalized)
    if (joinMatch != null) {
        val playerName = joinMatch.groupValues[1].trim()
        return (currentOnlinePlayerNames + playerName).distinct()
    }
    val leaveMatch = Regex("""^\[[^]]+]:\s+(?!<)(?!\[)(?![^\s:]+:)(.+) left the game$""", RegexOption.IGNORE_CASE)
        .find(normalized)
    if (leaveMatch != null) {
        val playerName = leaveMatch.groupValues[1].trim()
        return currentOnlinePlayerNames.filterNot { it.equals(playerName, ignoreCase = true) }
    }
    return null
}

fun launchCancelledEvent(serverId: String): PaperServerEvent = PaperServerEvent(
    serverId = serverId,
    status = PaperServerEventStatus.Stopped,
    progress = 0,
    message = "已取消启动；内置 Paper 进程尚未启动",
)

fun noActiveRuntimeStopEvent(serverId: String): PaperServerEvent = PaperServerEvent(
    serverId = serverId,
    status = PaperServerEventStatus.Stopped,
    progress = 0,
    message = "内置 Paper 进程当前未在运行，已清理残留状态",
)

fun runtimeExitEvent(
    serverId: String,
    exitCode: Int,
    stopRequested: Boolean,
    logFile: Path,
): PaperServerEvent = when {
    exitCode == 0 && stopRequested -> PaperServerEvent(
        serverId = serverId,
        status = PaperServerEventStatus.Stopped,
        progress = 0,
        message = "Paper 已安全停止；日志路径：$logFile",
    )
    exitCode == 0 -> PaperServerEvent(
        serverId = serverId,
        status = PaperServerEventStatus.Stopped,
        progress = 0,
        message = "Paper 已退出；日志路径：$logFile",
    )
    else -> PaperServerEvent(
        serverId = serverId,
        status = PaperServerEventStatus.Failed,
        progress = 0,
        message = "Paper 退出码 $exitCode；日志路径：$logFile",
    )
}

fun pendingTunnelBindingForFrpcPlan(plan: TunnelRuntimePlan): ServerTunnelBinding = ServerTunnelBinding(
    tunnelId = plan.tunnelId,
    remotePort = plan.remotePort,
    activeLabel = null,
    runtimeAddress = null,
)

fun readyTunnelBindingForFrpcPlan(plan: TunnelRuntimePlan): ServerTunnelBinding = ServerTunnelBinding(
    tunnelId = plan.tunnelId,
    remotePort = plan.remotePort,
    activeLabel = plan.displayLabel,
    runtimeAddress = plan.runtimeAddress,
)

enum class FrpcReadinessStatus {
    Ready,
    Failed,
}

data class FrpcReadinessSignal(
    val status: FrpcReadinessStatus,
    val line: String,
)

fun selectFrpcReadinessSignal(lines: List<String>): FrpcReadinessSignal? {
    val normalizedLines = lines.map(String::trim).filter(String::isNotBlank)
    val failureMatchers = listOf<(String) -> Boolean>(
        { line -> line.contains("token in login doesn't match token from configuration", ignoreCase = true) },
        { line -> line.contains("login to the server failed", ignoreCase = true) },
        { line -> line.contains("start proxy", ignoreCase = true) && line.contains("error", ignoreCase = true) },
        { line -> line.contains("port already", ignoreCase = true) || line.contains("port already used", ignoreCase = true) },
        { line -> line.contains("connect to server error", ignoreCase = true) },
    )
    val readyMatchers = listOf<(String) -> Boolean>(
        { line -> line.contains("start proxy success", ignoreCase = true) },
    )
    normalizedLines.forEach { line ->
        if (failureMatchers.any { matcher -> matcher(line) }) {
            return FrpcReadinessSignal(FrpcReadinessStatus.Failed, line)
        }
        if (readyMatchers.any { matcher -> matcher(line) }) {
            return FrpcReadinessSignal(FrpcReadinessStatus.Ready, line)
        }
    }
    return null
}

fun frpcReadinessMessage(label: String, runtimeAddress: String, signal: FrpcReadinessSignal): String {
    val normalizedLine = signal.line.trim()
    val prefix = if (label.isBlank()) "FRP" else "FRP 隧道 $label"
    val addressSuffix = runtimeAddress.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
    return when {
        signal.status == FrpcReadinessStatus.Ready -> "$prefix 已连接$addressSuffix"
        normalizedLine.contains("token in login doesn't match token from configuration", ignoreCase = true) -> {
            "$prefix token 不匹配，请检查隧道配置中的 token 是否与服务端一致"
        }
        normalizedLine.contains("port already", ignoreCase = true) || normalizedLine.contains("start proxy", ignoreCase = true) -> {
            "$prefix 启动失败，远端端口可能已被占用：${normalizedLine.takeLast(220)}"
        }
        normalizedLine.contains("connect to server error", ignoreCase = true) -> {
            "$prefix 无法连接服务端：${normalizedLine.takeLast(220)}"
        }
        else -> "$prefix 启动失败：${normalizedLine.takeLast(220)}"
    }
}

fun selectFrpcExitLogLine(lines: List<String>): String? {
    val normalizedLines = lines.map(String::trim).filter(String::isNotBlank)
    val matchers = listOf<(String) -> Boolean>(
        { line -> line.contains("token in login doesn't match token from configuration", ignoreCase = true) },
        { line -> line.contains("login to the server failed", ignoreCase = true) },
        { line -> line.contains("start proxy", ignoreCase = true) && line.contains("error", ignoreCase = true) },
        { line -> line.contains("port already", ignoreCase = true) || line.contains("port already used", ignoreCase = true) },
        { line -> line.contains("connect to server error", ignoreCase = true) },
        { line -> line.contains("frpc service", ignoreCase = true) },
    )
    return matchers.firstNotNullOfOrNull { matcher -> normalizedLines.lastOrNull(matcher) }
}

fun frpcExitMessage(exitCode: Int, lastLogLine: String?): String {
    val normalizedLine = lastLogLine?.trim().orEmpty()
    val readinessFailure = normalizedLine
        .takeIf { it.isNotBlank() }
        ?.let { selectFrpcReadinessSignal(listOf(it)) }
        ?.takeIf { it.status == FrpcReadinessStatus.Failed }
    return when {
        readinessFailure != null -> frpcReadinessMessage("", "", readinessFailure)
        normalizedLine.isNotBlank() -> "FRP 退出码 $exitCode；$normalizedLine"
        else -> "FRP 退出码 $exitCode；公网入口已断开"
    }
}
