package com.mcgo.app.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mcgo.app.MainActivity
import com.mcgo.app.R
import com.mcgo.app.server.appendManagedServerDebugLog
import com.mcgo.app.network.TcpEndpoint
import com.mcgo.app.network.measureTcpLatency
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.ServerTunnelBinding
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.storage.ServerProfileStore
import com.mcgo.app.ui.storage.TunnelProfileStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
    private var completedInstallerBootstrapOnly = false
    @Volatile
    private var lastLaunchedJavaMajorVersion: Int? = null
    private var launchJob: Job? = null
    private var stopSignalRetryJob: Job? = null
    private var logTailJob: Job? = null
    private var portMonitorJob: Job? = null
    private var workspaceSyncJob: Job? = null
    private var runtimeWakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var currentWorkspacePreparedFromAuthorizedDirectory = false
    @Volatile
    private var currentWorkspacePath: Path? = null
    @Volatile
    private var currentWorkspaceMode: ManagedServerWorkspaceMode = ManagedServerWorkspaceMode.PrivatePersistentFallback
    private val frpcProcesses = mutableMapOf<String, Process>()
    private val frpcWatchJobs = mutableMapOf<String, Job>()
    private val tunnelRuntimeStateLock = Any()
    @Volatile
    private var currentActiveTunnelLabel: String? = null
    @Volatile
    private var currentRuntimeAddress: String? = null
    @Volatile
    private var currentTunnelBindings: List<ServerTunnelBinding> = emptyList()

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
        workspaceSyncJob?.cancel()
        stopFrpcProcesses()
        releaseRuntimeWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPaperServer(intent: Intent) {
        val server = intent.toServerCardState()
        val tunnels = hydrateLaunchTunnelProfiles(
            storedProfiles = TunnelProfileStore(filesDir.toPath().resolve("tunnel_profiles.properties")).load(),
            launchProfiles = intent.toTunnelProfiles(),
        )
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
        currentWorkspacePreparedFromAuthorizedDirectory = intent.getBooleanExtra("workspacePrepared", false)
        currentWorkspacePath = intent.getStringExtra("workspacePath")?.let(Paths::get)
        currentWorkspaceMode = intent.getStringExtra("workspaceMode")
            ?.let { runCatching { enumValueOf<ManagedServerWorkspaceMode>(it) }.getOrNull() }
            ?: ManagedServerWorkspaceMode.PrivatePersistentFallback
        if (currentWorkspacePath == null) {
            val workspaceAccess = prepareManagedServerWorkspaceAccess(
                context = this,
                authorizedDirectoryUri = runtimePrefsServerDirectoryUri(this),
                filesDir = filesDir.toPath(),
                serverId = server.id,
            )
            currentWorkspacePath = workspaceAccess.path
            currentWorkspaceMode = workspaceAccess.mode
            currentWorkspacePreparedFromAuthorizedDirectory = true
        }
        currentActiveTunnelLabel = server.activeTunnelLabel
        currentRuntimeAddress = server.runtimeAddress
        val runtimeLogFile = managedPaperServerLogFile(filesDir.toPath(), server.id)
        appendManagedServerDebugLog(
            runtimeLogFile,
            "启动请求已接收",
            mapOf(
                "serverId" to server.id,
                "serverName" to server.name,
                "serverType" to server.serverType.name,
                "minecraftVersion" to server.minecraftVersion,
                "javaMajorVersion" to server.javaMajorVersion,
                "workspaceMode" to currentWorkspaceMode.name,
                "requestedWorkspacePath" to currentWorkspacePath,
                "tunnelCount" to tunnels.size,
            ),
        )
        runtimeRunning = false
        stopRequested = false
        runtimeLaunchSubmitted = false
        stopSignalDelivered = false
        completedInstallerBootstrapOnly = false
        PaperJvmLauncher.clearPendingStopRequest()
        startForeground(notificationId(), notification("正在启动 ${server.name}"))
        acquireRuntimeWakeLock(server.id)
        publish(server.id, PaperServerEventStatus.Launching, 8, "正在准备内置 Java ${server.javaMajorVersion} 运行时")
        launchJob = serviceScope.launch {
            fun ensureLaunchNotCancelled() {
                if (!isActive || (stopRequested && !runtimeLaunchSubmitted)) {
                    throw CancellationException("用户已取消启动")
                }
            }

            try {
                val result = runCatching {
                    ensureLaunchNotCancelled()
                    if (currentWorkspacePreparedFromAuthorizedDirectory) {
                        Files.createDirectories(currentWorkspacePath ?: managedPaperServerDirectory(filesDir.toPath(), server.id))
                    } else {
                        val preparedWorkspace = prepareManagedServerWorkspaceForForegroundAccess(
                            context = this@PaperServerService,
                            authorizedDirectoryUri = runtimePrefsServerDirectoryUri(this@PaperServerService),
                            filesDir = filesDir.toPath(),
                            serverId = server.id,
                        )
                        currentWorkspacePath = preparedWorkspace
                        currentWorkspacePreparedFromAuthorizedDirectory = true
                    }
                    appendManagedServerDebugLog(
                        runtimeLogFile,
                        "工作目录已就绪",
                        mapOf(
                            "workspacePath" to currentWorkspacePath,
                            "workspacePreparedFromAuthorizedDirectory" to currentWorkspacePreparedFromAuthorizedDirectory,
                            "workspaceMode" to currentWorkspaceMode.name,
                        ),
                    )
                    val runtimeContext = prepareManagedPaperRuntimeContext(
                        server = server,
                        filesDir = filesDir.toPath(),
                        cacheDir = cacheDir.toPath(),
                        nativeLibraryDir = applicationInfo.nativeLibraryDir,
                        is64BitProcess = android.os.Process.is64Bit(),
                        applicationSourceDir = applicationInfo.sourceDir,
                        serverWorkDirOverride = currentWorkspacePath,
                    )
                    appendManagedServerDebugLog(
                        runtimeLogFile,
                        "运行时上下文已准备",
                        mapOf(
                            "workingDirectory" to runtimeContext.workingDirectory,
                            "jarPath" to runtimeContext.jarPath,
                            "javaBinary" to runtimeContext.javaBinary,
                            "environmentSize" to runtimeContext.environment.size,
                        ),
                    )
                    ensureLaunchNotCancelled()
                    val serverFlavorLabel = when (server.serverType) {
                        com.mcgo.app.ui.model.MinecraftServerType.Vanilla -> "Vanilla"
                        com.mcgo.app.ui.model.MinecraftServerType.Paper -> "Paper"
                        com.mcgo.app.ui.model.MinecraftServerType.Purpur -> "Purpur"
                        com.mcgo.app.ui.model.MinecraftServerType.Fabric -> "Fabric"
                        com.mcgo.app.ui.model.MinecraftServerType.Forge -> "Forge"
                        com.mcgo.app.ui.model.MinecraftServerType.NeoForge -> "NeoForge"
                        com.mcgo.app.ui.model.MinecraftServerType.Quilt -> "Quilt"
                    }
                    publish(server.id, PaperServerEventStatus.Launching, 26, "正在解析 ${serverFlavorLabel} ${server.minecraftVersion} 下载信息")
                    val setupScriptExecuted = runManagedServerSetupScriptIfNeeded(
                        serverWorkDir = runtimeContext.workingDirectory,
                        targetJar = runtimeContext.jarPath,
                        environment = runtimeContext.environment,
                        logFile = managedPaperServerLogFile(filesDir.toPath(), server.id),
                        onOutputLine = { line ->
                            publish(
                                server.id,
                                PaperServerEventStatus.Launching,
                                30,
                                line.takeLast(280),
                            )
                        },
                    )
                    if (setupScriptExecuted) {
                        if (approvedManagedServerSetupScript(runtimeContext.workingDirectory)?.let { script ->
                                isInstallerBootstrapScript(script, runtimeContext.workingDirectory)
                            } == true
                        ) {
                            completedInstallerBootstrapOnly = true
                        }
                        publish(server.id, PaperServerEventStatus.Launching, 34, "已执行整合包安装脚本，继续准备 ${serverFlavorLabel}")
                    }
                    if (!shouldReuseInstalledServerPayload(runtimeContext.workingDirectory, runtimeContext.jarPath)) {
                        publish(server.id, PaperServerEventStatus.Launching, 42, "正在下载 ${serverFlavorLabel} ${server.minecraftVersion}")
                        when (server.serverType) {
                            com.mcgo.app.ui.model.MinecraftServerType.Vanilla -> downloadVanillaServerJar(server.minecraftVersion, runtimeContext.jarPath) { progress ->
                                ensureLaunchNotCancelled()
                                publish(
                                    server.id,
                                    PaperServerEventStatus.Launching,
                                    42 + ((progress.coerceIn(0, 100) * 34) / 100),
                                    "正在下载 ${serverFlavorLabel} ${server.minecraftVersion} · ${progress.coerceIn(0, 100)}%",
                                )
                            }
                            com.mcgo.app.ui.model.MinecraftServerType.Paper -> downloadLatestPaperJar(server.minecraftVersion, runtimeContext.jarPath) { progress ->
                                ensureLaunchNotCancelled()
                                publish(
                                    server.id,
                                    PaperServerEventStatus.Launching,
                                    42 + ((progress.coerceIn(0, 100) * 34) / 100),
                                    "正在下载 ${serverFlavorLabel} ${server.minecraftVersion} · ${progress.coerceIn(0, 100)}%",
                                )
                            }
                            com.mcgo.app.ui.model.MinecraftServerType.Purpur -> downloadPurpurServerJar(server.minecraftVersion, runtimeContext.jarPath) { progress ->
                                ensureLaunchNotCancelled()
                                publish(
                                    server.id,
                                    PaperServerEventStatus.Launching,
                                    42 + ((progress.coerceIn(0, 100) * 34) / 100),
                                    "正在下载 ${serverFlavorLabel} ${server.minecraftVersion} · ${progress.coerceIn(0, 100)}%",
                                )
                            }
                            com.mcgo.app.ui.model.MinecraftServerType.Fabric -> downloadFabricServerJar(server.minecraftVersion, runtimeContext.jarPath) { progress ->
                                ensureLaunchNotCancelled()
                                publish(
                                    server.id,
                                    PaperServerEventStatus.Launching,
                                    42 + ((progress.coerceIn(0, 100) * 34) / 100),
                                    "正在下载 ${serverFlavorLabel} ${server.minecraftVersion} · ${progress.coerceIn(0, 100)}%",
                                )
                            }
                            com.mcgo.app.ui.model.MinecraftServerType.Forge -> installForgeServer(
                                version = server.minecraftVersion,
                                serverWorkDir = runtimeContext.workingDirectory,
                                targetJar = runtimeContext.jarPath,
                                javaBinary = runtimeContext.javaBinary,
                                environment = runtimeContext.environment,
                            ) { progress ->
                                ensureLaunchNotCancelled()
                                publish(server.id, PaperServerEventStatus.Launching, 42 + ((progress.coerceIn(0, 100) * 34) / 100), "正在安装 ${serverFlavorLabel} ${server.minecraftVersion} · ${progress.coerceIn(0, 100)}%")
                            }
                            com.mcgo.app.ui.model.MinecraftServerType.NeoForge -> installNeoForgeServer(
                                version = server.minecraftVersion,
                                serverWorkDir = runtimeContext.workingDirectory,
                                targetJar = runtimeContext.jarPath,
                                javaBinary = runtimeContext.javaBinary,
                                environment = runtimeContext.environment,
                            ) { progress ->
                                ensureLaunchNotCancelled()
                                publish(server.id, PaperServerEventStatus.Launching, 42 + ((progress.coerceIn(0, 100) * 34) / 100), "正在安装 ${serverFlavorLabel} ${server.minecraftVersion} · ${progress.coerceIn(0, 100)}%")
                            }
                            com.mcgo.app.ui.model.MinecraftServerType.Quilt -> installQuiltServer(
                                version = server.minecraftVersion,
                                serverWorkDir = runtimeContext.workingDirectory,
                                targetJar = runtimeContext.jarPath,
                                javaBinary = runtimeContext.javaBinary,
                                environment = runtimeContext.environment,
                            ) { progress ->
                                ensureLaunchNotCancelled()
                                publish(server.id, PaperServerEventStatus.Launching, 42 + ((progress.coerceIn(0, 100) * 34) / 100), "正在安装 ${serverFlavorLabel} ${server.minecraftVersion} · ${progress.coerceIn(0, 100)}%")
                            }
                        }
                        ensureLaunchNotCancelled()
                    } else {
                        publish(server.id, PaperServerEventStatus.Launching, 58, "复用本地 ${serverFlavorLabel} 包：${runtimeContext.jarPath.fileName}")
                    }
                    validateBundledAndroidJnaCompatibilityForLaunchTarget(server, runtimeContext.workingDirectory, runtimeContext.jarPath)
                    ensureLaunchNotCancelled()
                    val tunnelPlans = tunnelRuntimePlansForStart(
                        filesDir = filesDir.toPath(),
                        nativeLibraryDir = java.io.File(applicationInfo.nativeLibraryDir).toPath(),
                        server = server,
                        tunnels = tunnels,
                        supportedAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                    )
                    if (tunnelPlans.isNotEmpty()) {
                        currentTunnelBindings = tunnelPlans.map(::pendingTunnelBindingForFrpcPlan)
                        currentActiveTunnelLabel = null
                        currentRuntimeAddress = null
                        publish(
                            server.id,
                            PaperServerEventStatus.Launching,
                            68,
                            "正在启动 ${tunnelPlans.joinToString("、") { it.displayLabel }} 隧道",
                        )
                        startFrpcForPlans(server, tunnelPlans)
                        publishEvent(
                            PaperServerEvent(
                                serverId = server.id,
                                status = PaperServerEventStatus.Launching,
                                progress = 72,
                                message = "FRP 隧道正在连接，等待日志确认",
                                activeTunnelLabel = currentActiveTunnelLabel,
                                runtimeAddress = currentRuntimeAddress,
                                tunnelBindings = currentTunnelBindings,
                            ),
                        )
                    } else {
                        currentTunnelBindings = emptyList()
                    }
                    runtimeLaunchSubmitted = true
                    if (stopRequested) {
                        PaperJvmLauncher.queueStopRequest()
                    }
                    val launchConfig = buildManagedPaperLaunchConfig(
                        server = server,
                        filesDir = filesDir.toPath(),
                        cacheDir = cacheDir.toPath(),
                        nativeLibraryDir = applicationInfo.nativeLibraryDir,
                        is64BitProcess = android.os.Process.is64Bit(),
                        serverWorkDirOverride = currentWorkspacePath,
                    )
                    appendManagedServerDebugLog(
                        runtimeLogFile,
                        "JVM 启动参数已生成",
                        mapOf(
                            "logFile" to launchConfig.logFile,
                            "argumentCount" to launchConfig.arguments.size,
                            "environmentSize" to launchConfig.environment.size,
                            "bootstrapLibraryCount" to launchConfig.bootstrapLibraries.size,
                        ),
                    )
                    publish(server.id, PaperServerEventStatus.Launching, 78, "正在通过内置 HotSpot 启动 ${serverFlavorLabel}")
                    startRuntimeMonitors(server, launchConfig.logFile)
                    val exitCode = PaperJvmLauncher.launch(launchConfig)
                    appendManagedServerDebugLog(
                        runtimeLogFile,
                        "运行时已退出",
                        mapOf(
                            "exitCode" to exitCode,
                            "stopRequested" to stopRequested,
                            "stopSignalDelivered" to stopSignalDelivered,
                            "logFile" to launchConfig.logFile,
                        ),
                    )
                    lastLaunchedJavaMajorVersion = server.javaMajorVersion
                    publishEvent(runtimeExitEvent(server.id, exitCode, stopRequested && stopSignalDelivered, launchConfig.logFile))
                }
                stopRuntimeMonitors()
                stopFrpcProcesses()
                result.exceptionOrNull()?.let { error ->
                    when {
                        error is CancellationException && stopRequested -> publishEvent(launchCancelledEvent(server.id))
                        error is CancellationException -> Unit
                        else -> publish(server.id, PaperServerEventStatus.Failed, 0, error.toUserFacingStartError(server.javaMajorVersion))
                    }
                }
            } finally {
                runCatching {
                    val persistedServer = ServerProfileStore(filesDir.toPath().resolve("server_profiles.properties"))
                        .load()
                        .firstOrNull { persisted -> persisted.id == server.id }
                    val serverPendingDeletion = persistedServer?.pendingDeletion == true
                    if (!serverPendingDeletion && shouldPersistManagedServerWorkspaceAfterLaunchAttempt(currentWorkspaceMode, runtimeLaunchSubmitted, completedInstallerBootstrapOnly)) {
                        check(
                            releaseManagedServerWorkspaceAfterForegroundAccess(
                                context = this@PaperServerService,
                                authorizedDirectoryUri = runtimePrefsServerDirectoryUri(this@PaperServerService),
                                filesDir = filesDir.toPath(),
                                serverId = server.id,
                                workspaceMode = currentWorkspaceMode,
                            ),
                        ) { "停止时清理临时服务器目录失败" }
                    }
                }.onFailure { error ->
                    publishEvent(PaperServerEvent(server.id, null, null, error.message ?: "停止后同步服务器目录失败"))
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
            completedInstallerBootstrapOnly = false
            currentActiveTunnelLabel = null
            currentRuntimeAddress = null
            currentTunnelBindings = emptyList()
            currentWorkspacePreparedFromAuthorizedDirectory = false
            currentWorkspacePath = null
            currentWorkspaceMode = ManagedServerWorkspaceMode.PrivatePersistentFallback
            PaperJvmLauncher.clearPendingStopRequest()
            releaseRuntimeWakeLock()
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
        appendRuntimeDebugLog(
            serverId,
            "停止请求已接收",
            mapOf(
                "requestedServerId" to requestedServerId,
                "runtimeLaunchSubmitted" to runtimeLaunchSubmitted,
                "runtimeRunning" to runtimeRunning,
            ),
        )
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
                    publishEvent(PaperServerEvent(it, null, null, "当前没有运行中的服务器进程，无法发送控制台指令"))
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
            appendRuntimeDebugLog(serverId, "控制台指令发送失败", mapOf("reason" to "blank"))
            publishEvent(PaperServerEvent(serverId, null, null, "控制台指令不能为空"))
            return
        }
        if (PaperJvmLauncher.submitCommand(rawCommand + "\n")) {
            appendRuntimeDebugLog(
                serverId,
                "控制台指令已提交",
                mapOf("commandLength" to rawCommand.length),
            )
            publish(serverId, runtimeMonitorEventStatus(runtimeRunning = runtimeRunning, stopRequested = stopRequested), if (runtimeRunning && !stopRequested) 100 else null, runtimeCommandMessage(rawCommand))
        } else {
            appendRuntimeDebugLog(serverId, "控制台指令发送失败", mapOf("reason" to "stdin_not_ready"))
            publishEvent(PaperServerEvent(serverId, null, null, "当前服务器进程尚未接收标准输入，请稍后再试"))
        }
    }

    private fun ensureStopSignalDelivery(serverId: String) {
        if (stopSignalDelivered || stopSignalRetryJob?.isActive == true) return
        stopSignalRetryJob = serviceScope.launch {
            while (isActive && shouldRetryQueuedStopSignal(currentServerId, serverId, stopRequested, stopSignalDelivered)) {
                if (PaperJvmLauncher.requestStop()) {
                    stopSignalDelivered = true
                    publish(serverId, PaperServerEventStatus.Stopping, 0, "已将 stop 指令送达内置服务器进程，等待安全退出")
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
            var onlinePlayers = server.onlinePlayers
            var onlinePlayerNames = server.onlinePlayerNames
            while (isActive) {
                val tail = readAppendedNonBlankLinesWithOffset(logFile, logOffset)
                logOffset = tail.nextOffset
                tail.lines.forEach { line ->
                    val updatedOnlinePlayers = updatedOnlinePlayersFromLogLine(onlinePlayers, line)
                    if (updatedOnlinePlayers != null) {
                        onlinePlayers = updatedOnlinePlayers
                    }
                    val updatedOnlinePlayerNames = updatedOnlinePlayerNamesFromLogLine(onlinePlayerNames, line)
                    if (updatedOnlinePlayerNames != null) {
                        onlinePlayerNames = updatedOnlinePlayerNames
                    }
                    publishEvent(
                        PaperServerEvent(
                            serverId = server.id,
                            status = runtimeMonitorEventStatus(runtimeRunning = runtimeRunning, stopRequested = stopRequested),
                            progress = if (runtimeRunning && !stopRequested) 100 else null,
                            message = line.takeLast(280),
                            onlinePlayers = updatedOnlinePlayers,
                            onlinePlayerNames = updatedOnlinePlayerNames,
                            activeTunnelLabel = currentActiveTunnelLabel,
                            runtimeAddress = currentRuntimeAddress,
                            tunnelBindings = currentTunnelBindings,
                        ),
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
                            tunnelBindings = currentTunnelBindings,
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
        workspaceSyncJob = serviceScope.launch {
            while (isActive) {
                if (!currentWorkspaceMode.shouldSyncBack) {
                    delay(15_000)
                    continue
                }
                runCatching {
                    val persistedServer = ServerProfileStore(filesDir.toPath().resolve("server_profiles.properties"))
                        .load()
                        .firstOrNull { persisted -> persisted.id == server.id }
                    if (persistedServer?.pendingDeletion != true) {
                        currentWorkspacePath?.let { workspacePath ->
                            check(
                                syncManagedServerWorkspaceToAuthorizedDirectory(
                                    context = this@PaperServerService,
                                    authorizedDirectoryUri = runtimePrefsServerDirectoryUri(this@PaperServerService),
                                    serverId = server.id,
                                    sourceWorkspaceDir = workspacePath,
                                ),
                            ) { "运行中同步服务器目录失败" }
                        }
                    }
                }.onFailure { error ->
                    publishEvent(PaperServerEvent(server.id, null, null, error.message ?: "运行中同步服务器目录失败"))
                }
                delay(15_000)
            }
        }
    }

    private fun stopRuntimeMonitors() {
        logTailJob?.cancel()
        portMonitorJob?.cancel()
        workspaceSyncJob?.cancel()
        logTailJob = null
        portMonitorJob = null
        workspaceSyncJob = null
    }

    private fun appendRuntimeDebugLog(serverId: String, message: String, details: Map<String, Any?> = emptyMap()) {
        runCatching {
            appendManagedServerDebugLog(
                managedPaperServerLogFile(filesDir.toPath(), serverId),
                message,
                details,
            )
        }
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

    private fun acquireRuntimeWakeLock(serverId: String) {
        if (runtimeWakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        runtimeWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MC-GO:paper-runtime-$serverId",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseRuntimeWakeLock() {
        runtimeWakeLock?.let { wakeLock ->
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
        runtimeWakeLock = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ChannelId, "MC-GO 开服", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            notificationId(),
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_mcgo)
            .setContentTitle("MC-GO")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }

    private fun notificationId(): Int = paperRuntimeNotificationId(serviceRuntimeSlot())

    private fun serviceRuntimeSlot(): Int = when (this) {
        is PaperServerServiceSlot2 -> 2
        is PaperServerServiceSlot3 -> 3
        is PaperServerServiceSlot4 -> 4
        else -> 1
    }

    private fun startFrpcForPlans(server: ServerCardState, plans: List<TunnelRuntimePlan>) {
        currentActiveTunnelLabel = plans.firstOrNull()?.displayLabel
        currentRuntimeAddress = plans.firstOrNull()?.runtimeAddress
        plans.forEach { plan ->
            Files.createDirectories(plan.configPath.parent)
            val executablePath = resolveExecutableFrpcPath(plan)
            Files.write(plan.configPath, plan.configText.toByteArray())
            val frpcLogFile = managedPaperServerFrpcLogFile(filesDir.toPath(), server.id, plan.tunnelId)
            val frpcLogStartOffset = frpcLogFile
                .takeIf { Files.isRegularFile(it) }
                ?.let(Files::size)
                ?: 0L
            val process = ProcessBuilder(executablePath.toString(), "-c", plan.configPath.toString())
                .directory(plan.configPath.parent.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(frpcLogFile.toFile()))
                .start()
            frpcProcesses[plan.tunnelId] = process
            frpcWatchJobs.remove(plan.tunnelId)?.cancel()
            frpcWatchJobs[plan.tunnelId] = serviceScope.launch {
                var readinessOffset = frpcLogStartOffset
                var readinessDelivered = false
                while (isActive && process.isAlive && !readinessDelivered) {
                    val tail = readAppendedNonBlankLinesWithOffset(frpcLogFile, readinessOffset)
                    readinessOffset = tail.nextOffset
                    selectFrpcReadinessSignal(tail.lines)?.let { signal ->
                        readinessDelivered = true
                        if (signal.status == FrpcReadinessStatus.Ready) {
                            markFrpcTunnelReady(server, plan, signal)
                        } else if (!stopRequested) {
                            publishEvent(
                                PaperServerEvent(
                                    serverId = server.id,
                                    status = PaperServerEventStatus.Failed,
                                    progress = 0,
                                    message = frpcReadinessMessage(plan.displayLabel, plan.runtimeAddress, signal),
                                    activeTunnelLabel = currentActiveTunnelLabel,
                                    runtimeAddress = currentRuntimeAddress,
                                    tunnelBindings = currentTunnelBindings,
                                ),
                            )
                        }
                    }
                    if (!readinessDelivered) {
                        delay(250)
                    }
                }
                val exitCode = process.waitFor()
                if (!isActive) return@launch
                val frpcLines = readAppendedNonBlankLines(
                    frpcLogFile,
                    readinessOffset,
                )
                val lastFrpcLine = selectFrpcExitLogLine(frpcLines)
                if (!stopRequested) {
                    synchronized(tunnelRuntimeStateLock) {
                        currentTunnelBindings = currentTunnelBindings.map { binding ->
                            if (binding.tunnelId == plan.tunnelId) {
                                binding.copy(activeLabel = null, runtimeAddress = "127.0.0.1:${server.port}")
                            } else {
                                binding
                            }
                        }
                        val primaryBinding = currentTunnelBindings.firstOrNull()
                        currentActiveTunnelLabel = primaryBinding?.activeLabel
                        currentRuntimeAddress = primaryBinding?.runtimeAddress ?: "127.0.0.1:${server.port}"
                    }
                    publishEvent(
                        PaperServerEvent(
                            serverId = server.id,
                            message = frpcExitMessage(exitCode, lastFrpcLine),
                            activeTunnelLabel = currentActiveTunnelLabel,
                            runtimeAddress = currentRuntimeAddress,
                            tunnelBindings = currentTunnelBindings,
                        ),
                    )
                }
            }
        }
    }

    private fun markFrpcTunnelReady(server: ServerCardState, plan: TunnelRuntimePlan, signal: FrpcReadinessSignal) {
        synchronized(tunnelRuntimeStateLock) {
            currentTunnelBindings = currentTunnelBindings.map { binding ->
                if (binding.tunnelId == plan.tunnelId) readyTunnelBindingForFrpcPlan(plan) else binding
            }
            val primaryBinding = currentTunnelBindings.firstOrNull()
            currentActiveTunnelLabel = primaryBinding?.activeLabel
            currentRuntimeAddress = primaryBinding?.runtimeAddress
        }
        if (!stopRequested) {
            publishEvent(
                PaperServerEvent(
                    serverId = server.id,
                    status = PaperServerEventStatus.Launching,
                    progress = 72,
                    message = frpcReadinessMessage(plan.displayLabel, plan.runtimeAddress, signal),
                    activeTunnelLabel = currentActiveTunnelLabel,
                    runtimeAddress = currentRuntimeAddress,
                    tunnelBindings = currentTunnelBindings,
                ),
            )
        }
    }

    private fun stopFrpcProcesses() {
        frpcWatchJobs.values.forEach { it.cancel() }
        frpcWatchJobs.clear()
        frpcProcesses.values.forEach { process ->
            process.destroy()
            process.waitFor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
        frpcProcesses.clear()
    }

    private fun resolveExecutableFrpcPath(plan: TunnelRuntimePlan): Path {
        if (Files.exists(plan.binaryPath)) {
            return plan.binaryPath
        }
        Files.createDirectories(plan.extractedBinaryPath.parent)
        java.util.zip.ZipFile(applicationInfo.sourceDir).use { zip ->
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            val entry = zip.getEntry("lib/$abi/${plan.binaryPath.fileName}")
                ?: error("内置 FRP 组件缺失：${plan.binaryPath.fileName}")
            zip.getInputStream(entry).use { input ->
                Files.copy(input, plan.extractedBinaryPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        plan.extractedBinaryPath.toFile().setExecutable(true, false)
        return plan.extractedBinaryPath
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
        private const val RuntimePrefsName = "mcgo_runtime_permissions"
        private const val ServerDirectoryUriKey = "server_directory_uri"

        fun start(context: Context, server: ServerCardState, tunnels: List<TunnelProfile> = emptyList()) {
            start(
                context = context,
                server = server,
                tunnels = tunnels,
                workspacePath = null,
                workspaceMode = null,
            )
        }

        fun start(
            context: Context,
            server: ServerCardState,
            tunnels: List<TunnelProfile> = emptyList(),
            workspacePath: String? = null,
            workspaceMode: ManagedServerWorkspaceMode? = null,
        ) {
            val slot = server.runtimeSlot ?: 1
            val intent = Intent(context, paperRuntimeServiceClass(slot)).apply {
                action = ActionStart
                putExtra("id", server.id)
                putExtra("name", server.name)
                putExtra("serverType", server.serverType.name)
                putExtra("minecraftVersion", server.minecraftVersion)
                putExtra("maxPlayers", server.maxPlayers)
                putExtra("memoryMb", server.memoryMb)
                putExtra("port", server.port)
                putExtra("worldName", server.worldName)
                putExtra("javaMajorVersion", server.javaMajorVersion)
                putExtra("tunnelRemotePort", server.tunnelRemotePort ?: -1)
                putExtra("gameMode", server.gameMode.name)
                putExtra("difficulty", server.difficulty.name)
                putExtra("onlineMode", server.onlineMode)
                putExtra("pvpEnabled", server.pvpEnabled)
                putExtra("serverPropertiesOverride", server.serverPropertiesOverride)
                putExtra("runtimeSlot", slot)
                putExtra("selectedTunnelId", server.selectedTunnelId)
                putExtra("activeTunnelLabel", server.activeTunnelLabel)
                putExtra("runtimeAddress", server.runtimeAddress)
                putExtra("workspacePrepared", workspacePath != null)
                putExtra("workspacePath", workspacePath)
                putExtra("workspaceMode", workspaceMode?.name)
                putExtra("tunnelCount", tunnels.size)
                tunnels.forEachIndexed { index, tunnel ->
                    putExtra("tunnels.$index.id", tunnel.id)
                    putExtra("tunnels.$index.name", tunnel.name)
                    putExtra("tunnels.$index.kind", tunnel.kind.name)
                    putExtra("tunnels.$index.source", tunnel.source.name)
                    putExtra("tunnels.$index.format", tunnel.format?.name)
                    putExtra("tunnels.$index.serverAddress", tunnel.serverAddress)
                    putExtra("tunnels.$index.remotePort", tunnel.remotePort ?: -1)
                    putExtra("tunnels.$index.localPort", tunnel.localPort ?: -1)
                    putExtra("tunnels.$index.portRange", tunnel.portRange)
                    putExtra("tunnels.$index.detail", tunnel.detail)
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

        private fun runtimePrefsServerDirectoryUri(context: Context): String? =
            context.getSharedPreferences(RuntimePrefsName, Context.MODE_PRIVATE)
                .getString(ServerDirectoryUriKey, null)

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
