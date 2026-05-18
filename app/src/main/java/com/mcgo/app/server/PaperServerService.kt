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
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.ServerTunnelBinding
import com.mcgo.app.ui.model.TunnelConfigFormat
import com.mcgo.app.ui.model.TunnelKind
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.TunnelSource
import com.mcgo.app.ui.model.createFabricServer
import com.mcgo.app.ui.model.createForgeServer
import com.mcgo.app.ui.model.createNeoForgeServer
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.createPurpurServer
import com.mcgo.app.ui.model.createQuiltServer
import com.mcgo.app.ui.model.createVanillaServer
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
                        if (findManagedServerSetupScript(runtimeContext.workingDirectory)?.let { script ->
                                isInstallerBootstrapScript(script, runtimeContext.workingDirectory)
                            } == true
                        ) {
                            completedInstallerBootstrapOnly = true
                            publishEvent(installerBootstrapSetupCompletedEvent(server.id))
                            return@runCatching
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
                        currentTunnelBindings = tunnelPlans.map { plan ->
                            ServerTunnelBinding(
                                tunnelId = plan.tunnelId,
                                remotePort = plan.remotePort,
                                activeLabel = plan.displayLabel,
                                runtimeAddress = plan.runtimeAddress,
                            )
                        }
                        publish(
                            server.id,
                            PaperServerEventStatus.Launching,
                            68,
                            "正在启动 ${tunnelPlans.joinToString("、") { it.displayLabel }} 隧道",
                        )
                        startFrpcForPlans(server, tunnelPlans)
                        val primaryPlan = tunnelPlans.first()
                        publishEvent(
                            PaperServerEvent(
                                serverId = server.id,
                                status = PaperServerEventStatus.Launching,
                                progress = 72,
                                message = "FRP 隧道已启动，等待服务器绑定端口",
                                activeTunnelLabel = primaryPlan.displayLabel,
                                runtimeAddress = primaryPlan.runtimeAddress,
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
                        currentWorkspacePath?.let { workspacePath ->
                            check(
                                syncManagedServerWorkspaceToAuthorizedDirectory(
                                    context = this@PaperServerService,
                                    authorizedDirectoryUri = runtimePrefsServerDirectoryUri(this@PaperServerService),
                                    serverId = server.id,
                                    sourceWorkspaceDir = workspacePath,
                                ),
                            ) { "停止时同步服务器目录失败" }
                        }
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
            publishEvent(PaperServerEvent(serverId, null, null, "控制台指令不能为空"))
            return
        }
        if (PaperJvmLauncher.submitCommand(rawCommand + "\n")) {
            publish(serverId, runtimeMonitorEventStatus(runtimeRunning = runtimeRunning, stopRequested = stopRequested), if (runtimeRunning && !stopRequested) 100 else null, runtimeCommandMessage(rawCommand))
        } else {
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
                val exitCode = process.waitFor()
                if (!isActive) return@launch
                val frpcLines = readAppendedNonBlankLines(
                    frpcLogFile,
                    frpcLogStartOffset,
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
                putExtra("workspacePrepared", server.runtimeLogPath != null)
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

fun installerBootstrapSetupCompletedEvent(serverId: String): PaperServerEvent = PaperServerEvent(
    serverId = serverId,
    status = PaperServerEventStatus.Stopped,
    progress = 0,
    message = "整合包安装脚本已执行完成，请再次点击启动继续拉起服务器",
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

fun selectFrpcExitLogLine(lines: List<String>): String? {
    val normalizedLines = lines.map(String::trim).filter(String::isNotBlank)
    val matchers = listOf<(String) -> Boolean>(
        { line -> line.contains("token in login doesn't match token from configuration", ignoreCase = true) },
        { line -> line.contains("login to the server failed", ignoreCase = true) },
        { line -> line.contains("connect to server error", ignoreCase = true) },
        { line -> line.contains("frpc service", ignoreCase = true) },
    )
    return matchers.firstNotNullOfOrNull { matcher -> normalizedLines.lastOrNull(matcher) }
}

fun frpcExitMessage(exitCode: Int, lastLogLine: String?): String {
    val normalizedLine = lastLogLine?.trim().orEmpty()
    return when {
        normalizedLine.contains("token in login doesn't match token from configuration", ignoreCase = true) -> {
            "FRP token 不匹配，请检查隧道配置中的 token 是否与服务端一致"
        }
        normalizedLine.isNotBlank() -> "FRP 退出码 $exitCode；$normalizedLine"
        else -> "FRP 退出码 $exitCode；公网入口已断开"
    }
}

internal fun decodeServerCardStateExtrasForTest(extras: Map<String, Any?>): ServerCardState = decodeServerCardStateExtras(extras)
internal fun decodeTunnelProfileExtrasForTest(extras: Map<String, Any?>): TunnelProfile? = decodeTunnelProfileExtras(extras)
internal fun decodeTunnelProfilesExtrasForTest(extras: Map<String, Any?>): List<TunnelProfile> = decodeTunnelProfilesExtras(extras)
internal fun hydrateLaunchTunnelProfilesForTest(
    storedProfiles: List<TunnelProfile>,
    launchProfiles: List<TunnelProfile>,
): List<TunnelProfile> = hydrateLaunchTunnelProfiles(storedProfiles, launchProfiles)

private fun Intent.toServerCardState(): ServerCardState = decodeServerCardStateExtras(
    mapOf(
        "id" to getStringExtra("id"),
        "name" to getStringExtra("name"),
        "serverType" to getStringExtra("serverType"),
        "minecraftVersion" to getStringExtra("minecraftVersion"),
        "maxPlayers" to getIntExtra("maxPlayers", 20),
        "memoryMb" to getIntExtra("memoryMb", 2048),
        "port" to getIntExtra("port", 25565),
        "worldName" to getStringExtra("worldName"),
        "javaMajorVersion" to getIntExtra("javaMajorVersion", 0),
        "runtimeSlot" to getIntExtra("runtimeSlot", -1),
        "selectedTunnelId" to getStringExtra("selectedTunnelId"),
        "activeTunnelLabel" to getStringExtra("activeTunnelLabel"),
        "runtimeAddress" to getStringExtra("runtimeAddress"),
        "tunnelRemotePort" to getIntExtra("tunnelRemotePort", -1),
        "gameMode" to getStringExtra("gameMode"),
        "difficulty" to getStringExtra("difficulty"),
        "onlineMode" to getBooleanExtra("onlineMode", true),
        "pvpEnabled" to getBooleanExtra("pvpEnabled", true),
        "serverPropertiesOverride" to getStringExtra("serverPropertiesOverride"),
    ),
)

private fun decodeServerCardStateExtras(extras: Map<String, Any?>): ServerCardState {
    val serverType = (extras["serverType"] as? String)
        ?.let { runCatching { enumValueOf<com.mcgo.app.ui.model.MinecraftServerType>(it) }.getOrNull() }
        ?: com.mcgo.app.ui.model.MinecraftServerType.Paper
    val baseServer = when (serverType) {
        com.mcgo.app.ui.model.MinecraftServerType.Vanilla -> createVanillaServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.Paper -> createPaperServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.Purpur -> createPurpurServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.Fabric -> createFabricServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.Forge -> createForgeServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.NeoForge -> createNeoForgeServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
        com.mcgo.app.ui.model.MinecraftServerType.Quilt -> createQuiltServer(
            name = extras["name"] as? String ?: "",
            minecraftVersion = extras["minecraftVersion"] as? String ?: "1.21.4",
            maxPlayers = extras["maxPlayers"] as? Int ?: 20,
            memoryMb = extras["memoryMb"] as? Int ?: 2048,
            port = extras["port"] as? Int ?: 25565,
            worldName = extras["worldName"] as? String ?: "world",
            tunnelRemotePort = (extras["tunnelRemotePort"] as? Int)?.takeIf { it > 0 },
            gameMode = (extras["gameMode"] as? String)?.let(PaperGameMode::valueOf) ?: PaperGameMode.Survival,
            difficulty = (extras["difficulty"] as? String)?.let(PaperDifficulty::valueOf) ?: PaperDifficulty.Normal,
            onlineMode = extras["onlineMode"] as? Boolean ?: true,
            pvpEnabled = extras["pvpEnabled"] as? Boolean ?: true,
            serverPropertiesOverride = extras["serverPropertiesOverride"] as? String,
        )
    }
    return baseServer.let { server ->
        server.copy(
            id = extras["id"] as? String ?: "paper-server",
            javaMajorVersion = (extras["javaMajorVersion"] as? Int)?.takeIf { it > 0 } ?: server.javaMajorVersion,
            selectedTunnelId = extras["selectedTunnelId"] as? String,
            activeTunnelLabel = extras["activeTunnelLabel"] as? String,
            runtimeAddress = extras["runtimeAddress"] as? String,
            runtimeSlot = (extras["runtimeSlot"] as? Int)?.takeIf { it > 0 },
        )
    }
}

private fun Intent.toTunnelProfiles(): List<TunnelProfile> = decodeTunnelProfilesExtras(
    buildMap {
        put("tunnelCount", getIntExtra("tunnelCount", 0))
        val tunnelCount = getIntExtra("tunnelCount", 0)
        repeat(tunnelCount) { index ->
            put("tunnels.$index.id", getStringExtra("tunnels.$index.id"))
            put("tunnels.$index.name", getStringExtra("tunnels.$index.name"))
            put("tunnels.$index.kind", getStringExtra("tunnels.$index.kind"))
            put("tunnels.$index.source", getStringExtra("tunnels.$index.source"))
            put("tunnels.$index.format", getStringExtra("tunnels.$index.format"))
            put("tunnels.$index.serverAddress", getStringExtra("tunnels.$index.serverAddress"))
            put("tunnels.$index.remotePort", getIntExtra("tunnels.$index.remotePort", -1))
            put("tunnels.$index.localPort", getIntExtra("tunnels.$index.localPort", -1))
            put("tunnels.$index.portRange", getStringExtra("tunnels.$index.portRange"))
            put("tunnels.$index.detail", getStringExtra("tunnels.$index.detail"))
        }
    },
)

private fun decodeTunnelProfilesExtras(extras: Map<String, Any?>): List<TunnelProfile> {
    val tunnelCount = extras["tunnelCount"] as? Int ?: 0
    return (0 until tunnelCount).mapNotNull { index ->
        decodeTunnelProfileExtras(
            mapOf(
                "tunnel.id" to extras["tunnels.$index.id"],
                "tunnel.name" to extras["tunnels.$index.name"],
                "tunnel.kind" to extras["tunnels.$index.kind"],
                "tunnel.source" to extras["tunnels.$index.source"],
                "tunnel.format" to extras["tunnels.$index.format"],
                "tunnel.serverAddress" to extras["tunnels.$index.serverAddress"],
                "tunnel.remotePort" to extras["tunnels.$index.remotePort"],
                "tunnel.localPort" to extras["tunnels.$index.localPort"],
                "tunnel.portRange" to extras["tunnels.$index.portRange"],
                "tunnel.detail" to extras["tunnels.$index.detail"],
            ),
        )
    }
}

private fun hydrateLaunchTunnelProfiles(
    storedProfiles: List<TunnelProfile>,
    launchProfiles: List<TunnelProfile>,
): List<TunnelProfile> {
    val storedById = storedProfiles.associateBy { it.id }
    return launchProfiles.map { launch ->
        val stored = storedById[launch.id] ?: return@map launch
        stored.copy(
            name = launch.name.ifBlank { stored.name },
            kind = launch.kind,
            source = launch.source,
            format = launch.format ?: stored.format,
            serverAddress = launch.serverAddress.ifBlank { stored.serverAddress },
            remotePort = launch.remotePort ?: stored.remotePort,
            localPort = launch.localPort ?: stored.localPort,
            credentialValue = launch.credentialValue ?: stored.credentialValue,
            portRange = launch.portRange ?: stored.portRange,
            detail = launch.detail ?: stored.detail,
        )
    }
}

private fun Intent.toTunnelProfile(): TunnelProfile? = decodeTunnelProfileExtras(
    mapOf(
        "tunnel.id" to getStringExtra("tunnel.id"),
        "tunnel.name" to getStringExtra("tunnel.name"),
        "tunnel.kind" to getStringExtra("tunnel.kind"),
        "tunnel.source" to getStringExtra("tunnel.source"),
        "tunnel.format" to getStringExtra("tunnel.format"),
        "tunnel.serverAddress" to getStringExtra("tunnel.serverAddress"),
        "tunnel.remotePort" to getIntExtra("tunnel.remotePort", -1),
        "tunnel.localPort" to getIntExtra("tunnel.localPort", -1),
        "tunnel.credentialValue" to getStringExtra("tunnel.credentialValue"),
        "tunnel.portRange" to getStringExtra("tunnel.portRange"),
        "tunnel.detail" to getStringExtra("tunnel.detail"),
    ),
)

private fun decodeTunnelProfileExtras(extras: Map<String, Any?>): TunnelProfile? {
    val tunnelId = extras["tunnel.id"] as? String ?: return null
    val kind = (extras["tunnel.kind"] as? String)?.let(TunnelKind::valueOf) ?: TunnelKind.Frp
    val source = (extras["tunnel.source"] as? String)?.let(TunnelSource::valueOf) ?: TunnelSource.ManualServer
    val format = (extras["tunnel.format"] as? String)?.let(TunnelConfigFormat::valueOf)
    return TunnelProfile(
        id = tunnelId,
        name = extras["tunnel.name"] as? String ?: "FRP",
        kind = kind,
        source = source,
        format = format,
        serverAddress = extras["tunnel.serverAddress"] as? String ?: "",
        remotePort = (extras["tunnel.remotePort"] as? Int)?.takeIf { it > 0 },
        localPort = (extras["tunnel.localPort"] as? Int)?.takeIf { it > 0 },
        credentialValue = extras["tunnel.credentialValue"] as? String,
        portRange = extras["tunnel.portRange"] as? String,
        rawConfigPreview = null,
        rawConfigText = null,
        detail = extras["tunnel.detail"] as? String,
    )
}
