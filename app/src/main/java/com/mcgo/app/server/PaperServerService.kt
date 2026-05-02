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
import com.mcgo.app.ui.model.createPaperServer
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PaperServerService : Service() {
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStop -> stopRunningServer(intent)
            ActionStart -> startPaperServer(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        launchJob?.cancel()
        stopSignalRetryJob?.cancel()
        logTailJob?.cancel()
        portMonitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPaperServer(intent: Intent) {
        val server = intent.toServerCardState()
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
        runtimeRunning = false
        stopRequested = false
        runtimeLaunchSubmitted = false
        stopSignalDelivered = false
        PaperJvmLauncher.clearPendingStopRequest()
        startForeground(NotificationId, notification("正在启动 ${server.name}"))
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
                    publish(
                        server.id,
                        runtimeMonitorEventStatus(runtimeRunning = true, stopRequested = stopRequested),
                        if (stopRequested) null else 100,
                        if (stopRequested) {
                            "Paper 正在安全停止；日志路径：$logFile"
                        } else {
                            "Paper 已监听 127.0.0.1:${server.port}；日志路径：$logFile"
                        },
                    )
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.notify(NotificationId, notification(if (stopRequested) "${server.name} 停止中" else "${server.name} 运行中"))
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

    private fun Throwable.toUserFacingStartError(javaMajorVersion: Int): String = when (this) {
        is JavaRuntimeInstallException -> message ?: "Java $javaMajorVersion 托管运行时不可用"
        else -> message ?: "启动失败；请确认 Java $javaMajorVersion 托管 JRE 已安装"
    }

    companion object {
        private const val ChannelId = "mcgo_paper_server"
        private const val NotificationId = 2001
        private const val ActionStart = "com.mcgo.app.server.START_PAPER"
        private const val ActionStop = "com.mcgo.app.server.STOP_PAPER"

        fun start(context: Context, server: ServerCardState) {
            val intent = Intent(context, PaperServerService::class.java).apply {
                action = ActionStart
                putExtra("id", server.id)
                putExtra("name", server.name)
                putExtra("minecraftVersion", server.minecraftVersion)
                putExtra("maxPlayers", server.maxPlayers)
                putExtra("memoryMb", server.memoryMb)
                putExtra("port", server.port)
                putExtra("worldName", server.worldName)
                putExtra("javaMajorVersion", server.javaMajorVersion)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context, serverId: String) {
            context.startService(
                Intent(context, PaperServerService::class.java).apply {
                    action = ActionStop
                    putExtra("id", serverId)
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

fun startConflictMessage(currentServerId: String?, requestedServerId: String): String? = when {
    currentServerId == null -> null
    currentServerId == requestedServerId -> "该服务器已在启动或运行中，请稍候"
    else -> "当前版本先支持单服运行，请先停止其他服务器"
}

fun resolveStopTargetAction(currentServerId: String?, requestedServerId: String?): StopTargetAction = when {
    currentServerId == null -> StopTargetAction.NoActiveRuntime
    requestedServerId == null || requestedServerId == currentServerId -> StopTargetAction.HandleCurrentServer
    else -> StopTargetAction.IgnoreMismatchedServer
}

enum class StopHandlingAction {
    CancelPendingLaunch,
    AwaitStopSignalDelivery,
    StopSignalAlreadyDelivered,
}

fun stopRequestMessage(): String = "已请求停止内置 Paper 进程，等待运行时退出"

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
    )
}
