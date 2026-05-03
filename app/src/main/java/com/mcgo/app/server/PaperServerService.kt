package com.mcgo.app.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mcgo.app.R
import com.mcgo.app.network.TcpEndpoint
import com.mcgo.app.network.measureTcpLatency
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.TunnelConfigFormat
import com.mcgo.app.ui.model.TunnelKind
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.TunnelSource
import com.mcgo.app.ui.model.createPaperServer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

open class PaperServerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var currentServerId: String? = null
    @Volatile
    private var runtimeRunning = false
    @Volatile
    private var stopRequested = false
    @Volatile
    private var runtimeLaunchSubmitted = false
    @Volatile
    private var stopSignalDelivered = false
    @Volatile
    private var lastLaunchedJavaMajorVersion: Int? = null
    private var launchJob: Job? = null
    private var stopSignalRetryJob: Job? = null
    private var logTailJob: Job? = null
    private var portMonitorJob: Job? = null
    private var frpcProcess: Process? = null
    private var frpcWatchJob: Job? = null
    @Volatile
    private var currentActiveTunnelLabel: String? = null
    @Volatile
    private var currentRuntimeAddress: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStop -> stopRunningServer(intent)
            ActionStart -> startPaperServer(intent)
            ActionCommand -> submitConsoleCommand(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        launchJob?.cancel()
        stopSignalRetryJob?.cancel()
        logTailJob?.cancel()
        portMonitorJob?.cancel()
        frpcWatchJob?.cancel()
        stopFrpcProcess()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPaperServer(intent: Intent) {
        val server = intent.toServerCardState()
        val tunnel = intent.toTunnelProfile()
        if (currentServerId == server.id) {
            publish(
                server.id,
                if (runtimeRunning) PaperServerEventStatus.Running else PaperServerEventStatus.Launching,
                if (runtimeRunning) 100 else null,
                startConflictMessage(currentServerId = currentServerId, requestedServerId = server.id) ?: "该服务器已在启动或运行中，请稍候",
            )
            return
        }
        if (javaRuntimeMayRequireFreshProcess(lastLaunchedJavaMajorVersion, server.javaMajorVersion)) {
            publish(
                server.id,
                PaperServerEventStatus.Failed,
                0,
                "检测到切换 Java ${lastLaunchedJavaMajorVersion} → ${server.javaMajorVersion}；正在重置运行时进程后请重试启动",
            )
            stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
            return
        }
        startConflictMessage(currentServerId = currentServerId, requestedServerId = server.id)?.let { conflict ->
            publish(server.id, PaperServerEventStatus.Failed, 0, conflict)
            return
        }
        currentServerId = server.id
        currentActiveTunnelLabel = server.activeTunnelLabel
        currentRuntimeAddress = server.runtimeAddress
        runtimeRunning = false
        stopRequested = false
        runtimeLaunchSubmitted = false
        stopSignalDelivered = false
        PaperJvmLauncher.clearPendingStopRequest()
        startForeground(notificationId(), notification("正在启动 ${server.name}"))
        publish(server.id, PaperServerEventStatus.Launching, 8, "正在准备内置 Java ${server.javaMajorVersion} 运行时")
        launchJob = serviceScope.launch {
            fun ensureLaunchNotCancelled() {
                if (!isActive || (stopRequested && !runtimeLaunchSubmitted)) {
                    throw CancellationException("用户已取消启动")
                }
            }

            val result = runCatching {
                ensureLaunchNotCancelled()
                val config = buildManagedPaperLaunchConfig(
                    server = server,
                    filesDir = filesDir.toPath(),
                    cacheDir = cacheDir.toPath(),
                    nativeLibraryDir = applicationInfo.nativeLibraryDir,
                    is64BitProcess = android.os.Process.is64Bit(),
                )
                ensureLaunchNotCancelled()
                publish(server.id, PaperServerEventStatus.Launching, 26, "正在解析 Paper ${server.minecraftVersion} 下载信息")
                if (!shouldReusePaperJar(config.jarPath)) {
                    publish(server.id, PaperServerEventStatus.Launching, 42, "正在下载 Paper ${server.minecraftVersion}")
                    downloadLatestPaperJar(server.minecraftVersion, config.jarPath) { progress ->
                        ensureLaunchNotCancelled()
                        publish(
                            server.id,
                            PaperServerEventStatus.Launching,
                            42 + ((progress.coerceIn(0, 100) * 34) / 100),
                            "正在下载 Paper ${server.minecraftVersion} · ${progress.coerceIn(0, 100)}%",
                        )
                    }
                    ensureLaunchNotCancelled()
                } else {
                    publish(server.id, PaperServerEventStatus.Launching, 58, "复用本地 Paper 包：${config.jarPath.fileName}")
                }
                ensureLaunchNotCancelled()
                val tunnelPlan = tunnelRuntimePlanForStart(
                    filesDir = filesDir.toPath(),
                    nativeLibraryDir = java.io.File(applicationInfo.nativeLibraryDir).toPath(),
                    server = server,
                    tunnel = tunnel,
                    supportedAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                )
                tunnelPlan?.let { plan ->
                    publish(
                        server.id,
                        PaperServerEventStatus.Launching,
                        68,
                        "正在启动 ${tunnel?.name ?: "FRP"} 隧道",
                    )
                    startFrpcForPlan(server, plan)
                    publishEvent(
                        PaperServerEvent(
                            serverId = server.id,
                            status = PaperServerEventStatus.Launching,
                            progress = 72,
                            message = "FRP 隧道已启动，等待 Paper 绑定端口",
                            activeTunnelLabel = plan.displayLabel,
                            runtimeAddress = plan.runtimeAddress,
                        ),
                    )
                }
                runtimeLaunchSubmitted = true
                if (stopRequested) {
                    PaperJvmLauncher.queueStopRequest()
                }
                publish(server.id, PaperServerEventStatus.Launching, 78, "正在通过内置 HotSpot 启动 Paper")
                startRuntimeMonitors(server, config.logFile)
                val exitCode = PaperJvmLauncher.launch(config)
                lastLaunchedJavaMajorVersion = server.javaMajorVersion
                publishEvent(runtimeExitEvent(server.id, exitCode, stopRequested && stopSignalDelivered, config.logFile))
            }
            stopRuntimeMonitors()
            stopFrpcProcess()
            result.exceptionOrNull()?.let { error ->
                when {
                    error is CancellationException && stopRequested -> publishEvent(launchCancelledEvent(server.id))
                    error is CancellationException -> Unit
                    else -> publish(server.id, PaperServerEventStatus.Failed, 0, error.toUserFacingStartError(server.javaMajorVersion))
                }
            }
            launchJob = null
            stopSignalRetryJob?.cancel()
            stopSignalRetryJob = null
            currentServerId = null
            runtimeRunning = false
            stopRequested = false
            runtimeLaunchSubmitted = false
            stopSignalDelivered = false
            currentActiveTunnelLabel = null
            currentRuntimeAddress = null
            PaperJvmLauncher.clearPendingStopRequest()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun stopRunningServer(intent: Intent) {
        val requestedServerId = intent.getStringExtra("id")
        when (resolveStopTargetAction(currentServerId = currentServerId, requestedServerId = requestedServerId)) {
            StopTargetAction.NoActiveRuntime -> {
                requestedServerId?.let { publishEvent(noActiveRuntimeStopEvent(it)) }
                stopSelf()
                return
            }
            StopTargetAction.IgnoreMismatchedServer -> {
                requestedServerId?.let {
                    publish(it, PaperServerEventStatus.Failed, 0, "当前运行中的不是该服务器，已忽略停止请求")
                }
                return
            }
            StopTargetAction.HandleCurrentServer -> Unit
        }

        val serverId = currentServerId ?: requestedServerId ?: run {
            stopSelf()
            return
        }
        stopRequested = true
        publish(serverId, PaperServerEventStatus.Stopping, 0, stopRequestMessage())
        stopSignalDelivered = if (runtimeLaunchSubmitted) {
            PaperJvmLauncher.queueStopRequest()
            PaperJvmLauncher.requestStop()
        } else {
            false
        }
        when (resolveStopHandlingAction(runtimeLaunchSubmitted = runtimeLaunchSubmitted, stopSignalDelivered = stopSignalDelivered)) {
            StopHandlingAction.CancelPendingLaunch -> {
                PaperJvmLauncher.queueStopRequest()
                launchJob?.cancel(CancellationException("用户请求停止启动中的服务器"))
                publish(serverId, PaperServerEventStatus.Stopping, 0, "正在取消启动任务，等待当前步骤结束")
            }
            StopHandlingAction.AwaitStopSignalDelivery -> {
                publish(serverId, PaperServerEventStatus.Stopping, 0, queuedStopRequestMessage())
                ensureStopSignalDelivery(serverId)
            }
            StopHandlingAction.StopSignalAlreadyDelivered -> Unit
        }
    }

    private fun submitConsoleCommand(intent: Intent) {
        val requestedServerId = intent.getStringExtra("id")
        val rawCommand = intent.getStringExtra("command")?.trim().orEmpty()
        when (resolveCommandTargetAction(currentServerId = currentServerId, requestedServerId = requestedServerId)) {
            CommandTargetAction.NoActiveRuntime -> {
                requestedServerId?.let {
                    publishEvent(PaperServerEvent(it, null, null, "当前没有运行中的 Paper 进程，无法发送控制台指令"))
                }
                return
            }
            CommandTargetAction.IgnoreMismatchedServer -> {
                requestedServerId?.let {
                    publishEvent(PaperServerEvent(it, null, null, "当前运行中的不是该服务器，已忽略控制台指令"))
                }
                return
            }
            CommandTargetAction.HandleCurrentServer -> Unit
        }
        val serverId = currentServerId ?: requestedServerId ?: return
        if (rawCommand.isBlank()) {
            publishEvent(PaperServerEvent(serverId, null, null, "控制台指令不能为空"))
            return
        }
        if (PaperJvmLauncher.submitCommand(rawCommand + "\n")) {
            publish(serverId, runtimeMonitorEventStatus(runtimeRunning = runtimeRunning, stopRequested = stopRequested), if (runtimeRunning && !stopRequested) 100 else null, runtimeCommandMessage(rawCommand))
        } else {
            publishEvent(PaperServerEvent(serverId, null, null, "当前 Paper 进程尚未接收标准输入，请稍后再试"))
        }
    }

    private fun ensureStopSignalDelivery(serverId: String) {
        if (stopSignalDelivered || stopSignalRetryJob?.isActive == true) return
        stopSignalRetryJob = serviceScope.launch {
            while (isActive && shouldRetryQueuedStopSignal(currentServerId, serverId, stopRequested, stopSignalDelivered)) {
                if (PaperJvmLauncher.requestStop()) {
                    stopSignalDelivered = true
                    publish(serverId, PaperServerEventStatus.Stopping, 0, "已将 stop 指令送达内置 Paper 进程，等待安全退出")
                    break
                }
                delay(150)
            }
        }
    }

    private fun startRuntimeMonitors(server: ServerCardState, logFile: Path) {
        stopRuntimeMonitors()
        logTailJob = serviceScope.launch {
            var logOffset = 0L
            while (isActive) {
                val tail = readLastAppendedNonBlankLine(logFile, logOffset)
                logOffset = tail.nextOffset
                val line = tail.line
                if (!line.isNullOrBlank()) {
                    publish(
                        server.id,
                        runtimeMonitorEventStatus(runtimeRunning = runtimeRunning, stopRequested = stopRequested),
                        if (runtimeRunning && !stopRequested) 100 else null,
                        line.takeLast(280),
                    )
                }
                delay(1200)
            }
        }
        portMonitorJob = serviceScope.launch {
            val endpoint = TcpEndpoint(host = "127.0.0.1", port = server.port)
            while (isActive) {
                if (measureTcpLatency(endpoint, timeoutMillis = 400) != null) {
                    runtimeRunning = true
                    publishEvent(
                        PaperServerEvent(
                            serverId = server.id,
                            status = runtimeMonitorEventStatus(runtimeRunning = true, stopRequested = stopRequested),
                            progress = if (stopRequested) null else 100,
                            message = if (stopRequested) {
                                "Paper 正在安全停止；日志路径：$logFile"
                            } else {
                                "Paper 已监听 127.0.0.1:${server.port}；日志路径：$logFile"
                            },
                            activeTunnelLabel = currentActiveTunnelLabel,
                            runtimeAddress = currentRuntimeAddress,
                        ),
                    )
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.notify(notificationId(), notification(if (stopRequested) "${server.name} 停止中" else "${server.name} 运行中"))
                    if (!stopRequested) {
                        return@launch
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopRuntimeMonitors() {
        logTailJob?.cancel()
        portMonitorJob?.cancel()
        logTailJob = null
        portMonitorJob = null
    }

    private fun publish(serverId: String, status: PaperServerEventStatus, progress: Int?, message: String) {
        publishEvent(
            PaperServerEvent(
                serverId = serverId,
                status = status,
                progress = progress,
                message = message,
            ),
        )
    }

    private fun publishEvent(event: PaperServerEvent) {
        sendPaperRuntimeEvent(event)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ChannelId, "MC-GO 开服", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, ChannelId)
        .setSmallIcon(R.drawable.ic_mcgo)
        .setContentTitle("MC-GO")
        .setContentText(text)
        .setOngoing(true)
        .build()

    private fun notificationId(): Int = paperRuntimeNotificationId(serviceRuntimeSlot())

    private fun serviceRuntimeSlot(): Int = when (this) {
        is PaperServerServiceSlot2 -> 2
        is PaperServerServiceSlot3 -> 3
        is PaperServerServiceSlot4 -> 4
        else -> 1
    }

    private fun startFrpcForPlan(server: ServerCardState, plan: TunnelRuntimePlan) {
        Files.createDirectories(plan.configPath.parent)
        check(Files.exists(plan.binaryPath)) {
            "内置 FRP 组件缺失：${plan.binaryPath.fileName}"
        }
        Files.write(plan.configPath, plan.configText.toByteArray())
        val process = ProcessBuilder(plan.binaryPath.toString(), "-c", plan.configPath.toString())
            .directory(plan.configPath.parent.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(managedPaperServerLogFile(filesDir.toPath(), server.id).toFile()))
            .start()
        currentActiveTunnelLabel = plan.displayLabel
        currentRuntimeAddress = plan.runtimeAddress
        frpcProcess = process
        frpcWatchJob?.cancel()
        frpcWatchJob = serviceScope.launch {
            val exitCode = process.waitFor()
            if (!stopRequested) {
                currentActiveTunnelLabel = null
                currentRuntimeAddress = "127.0.0.1:${server.port}"
                publishEvent(
                    PaperServerEvent(
                        serverId = server.id,
                        message = "FRP 退出码 $exitCode；公网入口已断开",
                        activeTunnelLabel = null,
                        runtimeAddress = currentRuntimeAddress,
                    ),
                )
            }
        }
    }

    private fun stopFrpcProcess() {
        frpcWatchJob?.cancel()
        frpcWatchJob = null
        frpcProcess?.destroy()
        frpcProcess?.waitFor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (frpcProcess?.isAlive == true) {
            frpcProcess?.destroyForcibly()
        }
        frpcProcess = null
    }

    private fun Throwable.toUserFacingStartError(javaMajorVersion: Int): String = when (this) {
        is JavaRuntimeInstallException -> message ?: "Java $javaMajorVersion 托管运行时不可用"
        else -> message ?: "启动失败；请确认 Java $javaMajorVersion 托管 JRE 已安装"
    }

    companion object {
        private const val ChannelId = "mcgo_paper_server"
        private const val ActionStart = "com.mcgo.app.server.START_PAPER"
        private const val ActionStop = "com.mcgo.app.server.STOP_PAPER"
        private const val ActionCommand = "com.mcgo.app.server.COMMAND_PAPER"

        fun start(context: Context, server: ServerCardState, tunnel: TunnelProfile? = null) {
            val slot = server.runtimeSlot ?: 1
            val intent = Intent(context, paperRuntimeServiceClass(slot)).apply {
                action = ActionStart
                putExtra("id", server.id)
                putExtra("name", server.name)
                putExtra("minecraftVersion", server.minecraftVersion)
                putExtra("maxPlayers", server.maxPlayers)
                putExtra("memoryMb", server.memoryMb)
                putExtra("port", server.port)
                putExtra("worldName", server.worldName)
                putExtra("javaMajorVersion", server.javaMajorVersion)
                putExtra("runtimeSlot", slot)
                putExtra("selectedTunnelId", server.selectedTunnelId)
                putExtra("activeTunnelLabel", server.activeTunnelLabel)
                putExtra("runtimeAddress", server.runtimeAddress)
                tunnel?.let {
                    putExtra("tunnel.id", it.id)
                    putExtra("tunnel.name", it.name)
                    putExtra("tunnel.kind", it.kind.name)
                    putExtra("tunnel.source", it.source.name)
                    putExtra("tunnel.format", it.format?.name)
                    putExtra("tunnel.serverAddress", it.serverAddress)
                    putExtra("tunnel.remotePort", it.remotePort ?: -1)
                    putExtra("tunnel.localPort", it.localPort ?: -1)
                    putExtra("tunnel.credentialValue", it.credentialValue)
                    putExtra("tunnel.portRange", it.portRange)
                    putExtra("tunnel.detail", it.detail)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context, serverId: String, runtimeSlot: Int? = null) {
            val slot = runtimeSlot ?: 1
            context.startService(
                Intent(context, paperRuntimeServiceClass(slot)).apply {
                    action = ActionStop
                    putExtra("id", serverId)
                    putExtra("runtimeSlot", slot)
                },
            )
        }

        fun sendCommand(context: Context, serverId: String, command: String, runtimeSlot: Int? = null) {
            val slot = runtimeSlot ?: 1
            context.startService(
                Intent(context, paperRuntimeServiceClass(slot)).apply {
                    action = ActionCommand
                    putExtra("id", serverId)
                    putExtra("command", command)
                    putExtra("runtimeSlot", slot)
                },
            )
        }
    }
}

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

private fun Intent.toServerCardState(): ServerCardState = createPaperServer(
    name = getStringExtra("name").orEmpty(),
    minecraftVersion = getStringExtra("minecraftVersion") ?: "1.21.4",
    maxPlayers = getIntExtra("maxPlayers", 20),
    memoryMb = getIntExtra("memoryMb", 2048),
    port = getIntExtra("port", 25565),
    worldName = getStringExtra("worldName") ?: "world",
).let { server ->
    server.copy(
        id = getStringExtra("id") ?: "paper-server",
        javaMajorVersion = getIntExtra("javaMajorVersion", server.javaMajorVersion),
        selectedTunnelId = getStringExtra("selectedTunnelId"),
        activeTunnelLabel = getStringExtra("activeTunnelLabel"),
        runtimeAddress = getStringExtra("runtimeAddress"),
        runtimeSlot = getIntExtra("runtimeSlot", -1).takeIf { it > 0 },
    )
}

private fun Intent.toTunnelProfile(): TunnelProfile? {
    val tunnelId = getStringExtra("tunnel.id") ?: return null
    val kind = getStringExtra("tunnel.kind")?.let(TunnelKind::valueOf) ?: TunnelKind.Frp
    val source = getStringExtra("tunnel.source")?.let(TunnelSource::valueOf) ?: TunnelSource.ManualServer
    val format = getStringExtra("tunnel.format")?.let(TunnelConfigFormat::valueOf)
    return TunnelProfile(
        id = tunnelId,
        name = getStringExtra("tunnel.name") ?: "FRP",
        kind = kind,
        source = source,
        format = format,
        serverAddress = getStringExtra("tunnel.serverAddress").orEmpty(),
        remotePort = getIntExtra("tunnel.remotePort", -1).takeIf { it > 0 },
        localPort = getIntExtra("tunnel.localPort", -1).takeIf { it > 0 },
        credentialValue = getStringExtra("tunnel.credentialValue"),
        portRange = getStringExtra("tunnel.portRange"),
        rawConfigPreview = null,
        rawConfigText = null,
        detail = getStringExtra("tunnel.detail"),
    )
}
