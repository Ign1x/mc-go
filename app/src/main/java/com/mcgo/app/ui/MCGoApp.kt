package com.mcgo.app.ui

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mcgo.app.McGoUserAgent
import com.mcgo.app.R
import com.mcgo.app.network.measureTcpLatency
import com.mcgo.app.network.parseTcpEndpoint
import com.mcgo.app.server.JavaRuntimeArchiveKind
import com.mcgo.app.server.JavaRuntimeArchiveSource
import com.mcgo.app.server.JavaRuntimeInstallException
import com.mcgo.app.server.OfficialPojavLauncherApkSha256
import com.mcgo.app.server.OfficialPojavLauncherCertSha256
import com.mcgo.app.server.PaperServerEvents
import com.mcgo.app.server.PaperServerService
import com.mcgo.app.server.abiArchiveName
import com.mcgo.app.server.classifyJavaRuntimeArchiveName
import com.mcgo.app.server.deleteJavaRuntime
import com.mcgo.app.server.extractTarXzSafely
import com.mcgo.app.server.fallbackPaperVersions
import com.mcgo.app.server.fetchPaperVersions
import com.mcgo.app.server.filterProvisionablePaperVersions
import com.mcgo.app.server.installPojavRuntimeFromApk
import com.mcgo.app.server.installRuntimeFromTarXz
import com.mcgo.app.server.installRuntimeWithStaging
import com.mcgo.app.server.javaRuntimeArchiveTempSuffix
import com.mcgo.app.server.managedPaperServerLogFile
import com.mcgo.app.server.reconcilePersistedRuntimeState
import com.mcgo.app.server.reducePaperRuntimeEvent
import com.mcgo.app.server.resolvePojavRuntimeComponent
import com.mcgo.app.server.scanInstalledJavaVersions
import com.mcgo.app.server.sha256Hex
import com.mcgo.app.server.stopRequestMessage
import com.mcgo.app.server.trustedRuntimeArchivesForVersion
import com.mcgo.app.server.validateRuntimeArchiveTrust
import com.mcgo.app.ui.components.FluidGradientBackground
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.AppearancePreferencesSaver
import com.mcgo.app.ui.model.ConsoleErrorColor
import com.mcgo.app.ui.model.ConsoleInfoColor
import com.mcgo.app.ui.model.ConsoleTimestampColor
import com.mcgo.app.ui.model.ConsoleWarnColor
import com.mcgo.app.ui.model.JavaSelectionMode
import com.mcgo.app.ui.model.McGoPage
import com.mcgo.app.ui.model.McGoPageChrome
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.ThemeModePreference
import com.mcgo.app.ui.model.TunnelLatencyResult
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.applyPaperServerEdits
import com.mcgo.app.ui.model.applyTunnelLatencyResults
import com.mcgo.app.ui.model.buildConsoleAnnotatedLog
import com.mcgo.app.ui.model.canStartServerFromUi
import com.mcgo.app.ui.model.defaultJavaManagementState
import com.mcgo.app.ui.model.detachDeletedTunnel
import com.mcgo.app.ui.model.finalizePendingServerDeletion
import com.mcgo.app.ui.model.isManagedRuntimeProvisioningAvailable
import com.mcgo.app.ui.model.isRuntimeBusy
import com.mcgo.app.ui.model.markLaunchFailed
import com.mcgo.app.ui.model.markUnsupportedManagedRuntime
import com.mcgo.app.ui.model.normalizeConsoleCommand
import com.mcgo.app.ui.model.removeTunnelProfile
import com.mcgo.app.ui.model.recommendedJavaMajorVersion
import com.mcgo.app.ui.model.requestServerDeletion
import com.mcgo.app.ui.model.resolveServerConsoleText
import com.mcgo.app.ui.model.startWithTunnel
import com.mcgo.app.ui.model.stopServer
import com.mcgo.app.ui.model.upsertTunnelProfile
import com.mcgo.app.ui.model.withLaunchProgress
import com.mcgo.app.ui.sample.McGoSampleRepository
import com.mcgo.app.ui.screens.ServersScreen
import com.mcgo.app.ui.screens.SettingsScreen
import com.mcgo.app.ui.screens.StatusScreen
import com.mcgo.app.ui.screens.TunnelsScreen
import com.mcgo.app.ui.storage.AppearancePreferencesStore
import com.mcgo.app.ui.storage.ServerProfileStore
import com.mcgo.app.ui.storage.TunnelProfileStore
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens
import com.mcgo.app.ui.theme.McGoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.util.jar.JarFile

private const val RuntimePrefsName = "mcgo_runtime_permissions"
private const val ServerDirectoryUriKey = "server_directory_uri"
private const val ServerDirectoryGrantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
private const val PaperRuntimeProcessSuffix = ":paper_runtime"

private data class PendingStartRequest(
    val serverId: String,
    val tunnelId: String?,
    val startupPort: Int,
)

private enum class PendingServerDirectoryAction {
    StartServer,
    OpenConsole,
    EditServer,
    SettingsRequest,
}

private enum class McGoDestination(
    val page: McGoPage,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Status(McGoPage.Status, R.string.nav_status, Icons.Outlined.Speed),
    Servers(McGoPage.Servers, R.string.nav_servers, Icons.Outlined.Dns),
    Tunnels(McGoPage.Tunnels, R.string.nav_tunnels, Icons.Outlined.SwapHoriz),
    Settings(McGoPage.Settings, R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun MCGoApp() {
    val context = LocalContext.current
    val tunnelStore = remember(context) {
        TunnelProfileStore(context.filesDir.toPath().resolve("tunnel_profiles.properties"))
    }
    val serverStore = remember(context) {
        ServerProfileStore(context.filesDir.toPath().resolve("server_profiles.properties"))
    }
    val appearanceStore = remember(context) {
        AppearancePreferencesStore(context.filesDir.toPath().resolve("appearance_preferences.properties"))
    }
    var appearancePreferences by rememberSaveable(stateSaver = AppearancePreferencesSaver) {
        mutableStateOf(appearanceStore.load())
    }
    val supportedProvisionableJavaVersions = remember {
        if (Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") setOf(8, 11, 17, 21, 25) else setOf(8, 11, 17, 21)
    }
    val runtimeAliveOnLaunch = remember(context) { isPaperRuntimeProcessAlive(context) }
    val persistedServers = remember(serverStore) { serverStore.load() }
    val reconciledPersistedServers = remember(persistedServers, runtimeAliveOnLaunch) {
        finalizePendingServerDeletion(
            reconcilePersistedRuntimeState(
                servers = persistedServers,
                runtimeAlive = runtimeAliveOnLaunch,
            ).map { it.markUnsupportedManagedRuntime(supportedProvisionableJavaVersions) },
        )
    }
    var servers by remember(serverStore) {
        mutableStateOf(reconciledPersistedServers)
    }
    var tunnels by remember(tunnelStore) { mutableStateOf(tunnelStore.load()) }
    LaunchedEffect(reconciledPersistedServers) {
        if (reconciledPersistedServers != persistedServers) {
            serverStore.save(reconciledPersistedServers)
        }
    }
    val paperVersions by produceState(initialValue = filterProvisionablePaperVersions(fallbackPaperVersions())) {
        value = withContext(Dispatchers.IO) { fetchPaperVersions() }
    }

    McGoTheme(appearancePreferences = appearancePreferences) {
        MCGoAppScaffold(
            appearancePreferences = appearancePreferences,
            servers = servers,
            tunnels = tunnels,
            paperVersions = paperVersions,
            supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
            onAppearancePreferencesChange = {
                appearancePreferences = it
                appearanceStore.save(it)
            },
            onServersChange = { servers = it },
            onTunnelsChange = { tunnels = it },
            onTunnelsChangeAndPersist = {
                tunnels = it
                tunnelStore.save(it)
            },
            onPersistServers = { serverStore.save(it) },
        )
    }
}

@Composable
private fun MCGoAppScaffold(
    appearancePreferences: AppearancePreferences,
    servers: List<ServerCardState>,
    tunnels: List<TunnelProfile>,
    paperVersions: List<String>,
    supportedProvisionableJavaVersions: Set<Int>,
    onAppearancePreferencesChange: (AppearancePreferences) -> Unit,
    onServersChange: (List<ServerCardState>) -> Unit,
    onTunnelsChange: (List<TunnelProfile>) -> Unit,
    onTunnelsChangeAndPersist: (List<TunnelProfile>) -> Unit,
    onPersistServers: (List<ServerCardState>) -> Unit,
) {
    val appContext = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(McGoDestination.Status) }
    var showTunnelComposer by remember { mutableStateOf(false) }
    var showServerComposer by remember { mutableStateOf(false) }
    var editingTunnelId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingServerId by rememberSaveable { mutableStateOf<String?>(null) }
    var consoleServerId by rememberSaveable { mutableStateOf<String?>(null) }
    val chrome = McGoPageChrome.forPage(destination.page)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailableMessage = stringResource(R.string.snackbar_unavailable_action)
    val notifyUnavailableFeature: () -> Unit = remember(scope, snackbarHostState, unavailableMessage) {
        {
            scope.launch {
                snackbarHostState.showSnackbar(unavailableMessage)
            }
            Unit
        }
    }
    val visuals = LocalMcGoVisualTokens.current
    val fluidBackgroundSpec = visuals.fluidBackgroundSpec
    val bottomBarAlpha = if (appearancePreferences.transparentCards) {
        appearancePreferences.cardContainerAlpha().coerceIn(0.78f, 0.96f)
    } else {
        1f
    }
    val latestTunnels by rememberUpdatedState(tunnels)
    var installedJavaVersions by remember(appContext) {
        mutableStateOf(scanInstalledJavaVersions(appContext.filesDir.toPath()))
    }
    var javaDownloadProgress by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    val javaManagementState = remember(installedJavaVersions, javaDownloadProgress, supportedProvisionableJavaVersions) {
        defaultJavaManagementState(
            installedVersions = installedJavaVersions,
            downloadProgressByMajor = javaDownloadProgress,
            supportedProvisionableVersions = supportedProvisionableJavaVersions,
        )
    }
    val runtimePrefs = remember(appContext) { appContext.getSharedPreferences(RuntimePrefsName, Context.MODE_PRIVATE) }
    var serverDirectoryUriText by remember(appContext) {
        mutableStateOf(runtimePrefs.getString(ServerDirectoryUriKey, null))
    }
    var pendingServerDirectoryAction by remember { mutableStateOf<PendingServerDirectoryAction?>(null) }
    val latestServers by rememberUpdatedState(servers)
    fun persistServerDirectoryUri(uri: Uri?) {
        serverDirectoryUriText = uri?.toString()
        runtimePrefs.edit().apply {
            if (uri == null) remove(ServerDirectoryUriKey) else putString(ServerDirectoryUriKey, uri.toString())
        }.apply()
    }
    fun hasServerDirectoryGrant(): Boolean = serverDirectoryUriText
        ?.let { Uri.parse(it) }
        ?.let { uri ->
            appContext.contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == uri && permission.isReadPermission && permission.isWritePermission
            }
        } == true
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(uri, ServerDirectoryGrantFlags)
            }
            persistServerDirectoryUri(uri)
            scope.launch { snackbarHostState.showSnackbar("服务器目录已授权") }
        } else {
            pendingServerDirectoryAction = null
            scope.launch { snackbarHostState.showSnackbar("目录功能需要先授权服务器目录") }
        }
    }
    fun requestServerDirectory(action: PendingServerDirectoryAction) {
        pendingServerDirectoryAction = action
        directoryPickerLauncher.launch(serverDirectoryUriText?.let(Uri::parse))
    }
    fun startServerNow(request: PendingStartRequest) {
        val tunnel = tunnels.firstOrNull { it.id == request.tunnelId }
        val targetServer = servers.firstOrNull { it.id == request.serverId }
        if (targetServer == null) {
            scope.launch { snackbarHostState.showSnackbar("未找到服务器") }
            return
        }
        if (!canStartServerFromUi(targetServer)) {
            scope.launch { snackbarHostState.showSnackbar("${targetServer.name} 已在启动或运行中") }
            return
        }
        if (servers.any { it.id != request.serverId && it.isRuntimeBusy() }) {
            scope.launch { snackbarHostState.showSnackbar("当前版本先支持单服运行，请先停止其他服务器") }
            return
        }
        if (targetServer.javaMajorVersion !in installedJavaVersions) {
            val guidance = if (isManagedRuntimeProvisioningAvailable(targetServer.javaMajorVersion, supportedProvisionableJavaVersions)) {
                "缺少 Java ${targetServer.javaMajorVersion} 托管运行时，请先到设置 > Java 管理安装"
            } else {
                "当前版本暂不提供 Java ${targetServer.javaMajorVersion} 托管运行时；该 Minecraft 版本暂不支持一键开服"
            }
            val failedServers = servers.map { server ->
                if (server.id == request.serverId) {
                    server.markLaunchFailed(guidance)
                } else {
                    server
                }
            }
            onServersChange(failedServers)
            onPersistServers(failedServers)
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (isManagedRuntimeProvisioningAvailable(targetServer.javaMajorVersion, supportedProvisionableJavaVersions)) {
                        "请先安装 Java ${targetServer.javaMajorVersion} 托管 JRE"
                    } else {
                        "当前暂不支持该 Minecraft 版本所需的 Java ${targetServer.javaMajorVersion} 运行时"
                    },
                )
            }
            return
        }
        val runtimeLogPath = managedPaperServerLogFile(appContext.filesDir.toPath(), request.serverId).toString()
        val updatedServers = servers.map { server ->
            if (server.id != request.serverId) {
                server
            } else {
                server.startWithTunnel(tunnel = tunnel, startupPort = request.startupPort)
                    .copy(runtimeLogPath = runtimeLogPath)
                    .withLaunchProgress(8, "已提交启动任务，准备使用内置 HotSpot 运行")
            }
        }
        onServersChange(updatedServers)
        onPersistServers(updatedServers)
        updatedServers.firstOrNull { it.id == request.serverId }?.let { PaperServerService.start(appContext, it) }
        scope.launch {
            snackbarHostState.showSnackbar(
                tunnel?.let { "${targetServer.name} 已通过 ${it.name} 开始启动" } ?: "${targetServer.name} 开始启动",
            )
        }
    }

    LaunchedEffect(Unit) {
        PaperServerEvents.events.collect { event ->
            val updatedServers = finalizePendingServerDeletion(
                latestServers.map { server ->
                    if (server.id == event.serverId) reducePaperRuntimeEvent(server, event) else server
                },
            )
            onServersChange(updatedServers)
            onPersistServers(updatedServers)
        }
    }

    LaunchedEffect(appContext) {
        while (true) {
            val serverSnapshot = latestServers
            if (serverSnapshot.any { it.isRuntimeBusy() } && !isPaperRuntimeProcessAlive(appContext)) {
                val reconciledServers = finalizePendingServerDeletion(
                    reconcilePersistedRuntimeState(
                        servers = serverSnapshot,
                        runtimeAlive = false,
                    ).map { it.markUnsupportedManagedRuntime(supportedProvisionableJavaVersions) },
                )
                if (reconciledServers != serverSnapshot) {
                    onServersChange(reconciledServers)
                    onPersistServers(reconciledServers)
                }
            }
            delay(1500)
        }
    }

    fun refreshTunnelLatency(targetTunnelId: String?) {
        scope.launch {
            val tunnelSnapshot = latestTunnels
            val selectedTunnels = targetTunnelId?.let { targetId -> tunnelSnapshot.filter { it.id == targetId } } ?: tunnelSnapshot
            if (selectedTunnels.isEmpty()) return@launch
            val measuredResults = withContext(Dispatchers.IO) {
                selectedTunnels.map { profile ->
                    val latencyMs = parseTcpEndpoint(profile.serverAddress)?.let { endpoint ->
                        measureTcpLatency(endpoint)
                    }
                    TunnelLatencyResult(
                        tunnelId = profile.id,
                        serverAddress = profile.serverAddress,
                        latencyMs = latencyMs,
                    )
                }
            }
            onTunnelsChange(applyTunnelLatencyResults(latestTunnels, measuredResults))
        }
    }

    LaunchedEffect(tunnels) {
        onServersChange(
            servers.map { server ->
                when {
                    !server.isOnline -> server.copy(activeTunnelLabel = null)
                    else -> {
                        val matchedTunnel = tunnels.firstOrNull { it.id == server.selectedTunnelId }
                        server.copy(activeTunnelLabel = matchedTunnel?.let { "${it.name} · ${it.latencyLabel()}" })
                    }
                }
            },
        )
    }

    LaunchedEffect(destination) {
        if (destination != McGoDestination.Tunnels) {
            showTunnelComposer = false
            editingTunnelId = null
        }
        if (destination != McGoDestination.Servers) {
            showServerComposer = false
            editingServerId = null
            consoleServerId = null
        }
    }

    val onDownloadJava: (Int) -> Unit = remember(appContext, scope, snackbarHostState) {
        { majorVersion ->
            if (javaDownloadProgress.containsKey(majorVersion)) return@remember
            javaDownloadProgress = javaDownloadProgress + (majorVersion to 1)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        downloadAndInstallPojavRuntime(appContext, majorVersion) { progress ->
                            javaDownloadProgress = javaDownloadProgress + (majorVersion to progress.coerceIn(1, 99))
                        }
                    }
                }
                javaDownloadProgress = javaDownloadProgress - majorVersion
                result.onSuccess {
                    installedJavaVersions = scanInstalledJavaVersions(appContext.filesDir.toPath())
                    snackbarHostState.showSnackbar("Java $majorVersion 托管 JRE 已下载安装")
                }.onFailure { error ->
                    snackbarHostState.showSnackbar(error.userFacingInstallMessage(majorVersion))
                }
            }
        }
    }
    val onInstallJavaArchive: (Int, Uri) -> Unit = remember(appContext, scope, snackbarHostState) {
        { majorVersion, uri ->
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        installJavaRuntimeFromUri(
                            context = appContext,
                            uri = uri,
                            majorVersion = majorVersion,
                        )
                    }
                }
                result.onSuccess {
                    installedJavaVersions = scanInstalledJavaVersions(appContext.filesDir.toPath())
                    snackbarHostState.showSnackbar("Java $majorVersion 托管 JRE 已安装")
                }.onFailure { error ->
                    snackbarHostState.showSnackbar(error.userFacingInstallMessage(majorVersion))
                }
            }
        }
    }
    val onDeleteJava: (Int) -> Unit = remember(appContext, scope, snackbarHostState) {
        { majorVersion ->
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { deleteJavaRuntime(appContext.filesDir.toPath(), majorVersion) }
                }
                result.onSuccess {
                    installedJavaVersions = scanInstalledJavaVersions(appContext.filesDir.toPath())
                    snackbarHostState.showSnackbar("Java $majorVersion 托管 JRE 已删除")
                }.onFailure { error ->
                    snackbarHostState.showSnackbar(error.message ?: "删除 Java $majorVersion 失败")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FluidGradientBackground(
            spec = fluidBackgroundSpec,
            animate = appearancePreferences.dynamicBackground,
        )
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = bottomBarAlpha),
                ) {
                    McGoDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            },
            floatingActionButton = {
                when (destination) {
                    McGoDestination.Servers -> ExtendedFloatingActionButton(
                        onClick = { showServerComposer = true },
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text("创建 Paper") },
                    )
                    McGoDestination.Tunnels -> ExtendedFloatingActionButton(
                        onClick = { showTunnelComposer = true },
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text("新增隧道") },
                    )
                    else -> Unit
                }
            },
            floatingActionButtonPosition = FabPosition.End,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                consoleServerId?.let { serverId ->
                    servers.firstOrNull { it.id == serverId }?.let { server ->
                        ServerConsoleDialog(
                            server = server,
                            onDismiss = { consoleServerId = null },
                            onSubmitCommand = { command ->
                                val normalized = normalizeConsoleCommand(command)
                                PaperServerService.sendCommand(appContext, server.id, normalized.trim())
                                scope.launch {
                                    snackbarHostState.showSnackbar("已发送指令：${normalized.trim()}")
                                }
                                true
                            },
                        )
                    }
                }
                editingServerId?.let { serverId ->
                    servers.firstOrNull { it.id == serverId }?.let { server ->
                        EditPaperServerDialog(
                            server = server,
                            paperVersions = paperVersions,
                            supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
                            onDismiss = { editingServerId = null },
                            onSave = { edited ->
                                val updatedServers = servers.map { existing -> if (existing.id == edited.id) edited else existing }
                                onServersChange(updatedServers)
                                onPersistServers(updatedServers)
                                editingServerId = null
                                scope.launch { snackbarHostState.showSnackbar("已更新 ${edited.name}") }
                            },
                        )
                    }
                }
                when (destination) {
                    McGoDestination.Status -> StatusScreen(modifier = Modifier.fillMaxSize())
                    McGoDestination.Servers -> ServersScreen(
                        servers = servers,
                        availableTunnels = tunnels,
                        paperVersions = paperVersions,
                        supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
                        modifier = Modifier.fillMaxSize(),
                        showCreateServer = showServerComposer,
                        onDismissCreateServer = { showServerComposer = false },
                        onCreateServer = { server ->
                            val updatedServers = servers + server.markUnsupportedManagedRuntime(supportedProvisionableJavaVersions)
                            onServersChange(updatedServers)
                            onPersistServers(updatedServers)
                            showServerComposer = false
                            scope.launch { snackbarHostState.showSnackbar("已创建 ${server.name}") }
                        },
                        onStartServer = { serverId, tunnelId, startupPort ->
                            if (!hasServerDirectoryGrant()) {
                                requestServerDirectory(PendingServerDirectoryAction.StartServer)
                            } else {
                                startServerNow(PendingStartRequest(serverId, tunnelId, startupPort))
                            }
                        },
                        onStopServer = { serverId ->
                            PaperServerService.stop(appContext, serverId)
                            val updatedServers = servers.map { server ->
                                if (server.id == serverId) {
                                    server.copy(runtimeLogs = (server.runtimeLogs + stopRequestMessage()).takeLast(12))
                                        .stopServer()
                                } else {
                                    server
                                }
                            }
                            onServersChange(updatedServers)
                            onPersistServers(updatedServers)
                        },
                        onDeleteServer = { serverId ->
                            val targetServer = servers.firstOrNull { it.id == serverId }
                            if (targetServer?.isRuntimeBusy() == true) {
                                PaperServerService.stop(appContext, serverId)
                                val updatedServers = finalizePendingServerDeletion(
                                    servers.map { server ->
                                        if (server.id == serverId) requestServerDeletion(server).copy(
                                            runtimeLogs = (server.runtimeLogs + stopRequestMessage()).takeLast(12),
                                        ) else server
                                    },
                                )
                                onServersChange(updatedServers)
                                onPersistServers(updatedServers)
                                scope.launch { snackbarHostState.showSnackbar("已停止并删除 ${targetServer.name}") }
                            } else {
                                val updatedServers = finalizePendingServerDeletion(servers.filterNot { it.id == serverId })
                                onServersChange(updatedServers)
                                onPersistServers(updatedServers)
                                scope.launch {
                                    snackbarHostState.showSnackbar("已删除 ${targetServer?.name ?: "服务器"}")
                                }
                            }
                        },
                        onOpenConsole = { serverId ->
                            consoleServerId = serverId
                        },
                        onEditServer = { serverId ->
                            editingServerId = serverId
                        },
                    )
                    McGoDestination.Tunnels -> TunnelsScreen(
                        tunnels = tunnels,
                        showComposer = showTunnelComposer,
                        editingTunnelId = editingTunnelId,
                        onDismissComposer = {
                            showTunnelComposer = false
                            editingTunnelId = null
                        },
                        onSaveTunnel = { profile ->
                            val updated = upsertTunnelProfile(tunnels, profile)
                            onTunnelsChangeAndPersist(updated)
                            editingTunnelId = null
                        },
                        onEditTunnel = { tunnelId ->
                            editingTunnelId = tunnelId
                            showTunnelComposer = true
                        },
                        onDeleteTunnel = { tunnelId ->
                            val updatedTunnels = removeTunnelProfile(tunnels, tunnelId)
                            onTunnelsChangeAndPersist(updatedTunnels)
                            val updatedServers = detachDeletedTunnel(servers, tunnelId)
                            onServersChange(updatedServers)
                            onPersistServers(updatedServers)
                        },
                        onRefreshTunnelLatency = ::refreshTunnelLatency,
                        modifier = Modifier.fillMaxSize(),
                    )
                    McGoDestination.Settings -> SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        appearancePreferences = appearancePreferences,
                        onAppearancePreferencesChange = onAppearancePreferencesChange,
                        javaManagementState = javaManagementState,
                        onDownloadJava = onDownloadJava,
                        onInstallJavaArchive = onInstallJavaArchive,
                        onDeleteJava = onDeleteJava,
                        serverDirectoryUri = serverDirectoryUriText,
                        onRequestServerDirectory = {
                            requestServerDirectory(PendingServerDirectoryAction.SettingsRequest)
                        },
                    )
                }
            }
        }
    }
}

private fun downloadAndInstallPojavRuntime(
    context: Context,
    majorVersion: Int,
    onProgress: (Int) -> Unit = {},
): Path {
    val filesDir = context.filesDir.toPath()
    val archives = trustedRuntimeArchivesForVersion(
        majorVersion = majorVersion,
        abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
    )
    val tempFiles = mutableListOf<Path>()
    try {
        fun downloadArchive(archive: com.mcgo.app.server.TrustedJavaRuntimeTarball, start: Int, end: Int): Path {
            val suffix = archive.url.substringAfterLast('/').let { if (it.endsWith(".tar.xz")) ".tar.xz" else ".archive" }
            val tempFile = Files.createTempFile(context.cacheDir.toPath(), "mcgo-runtime-", suffix)
            tempFiles.add(tempFile)
            downloadVerifiedFileToPath(
                urls = runtimeDownloadUrlsForRegion(context, archive.url),
                target = tempFile,
                expectedArchive = archive,
            ) { progress ->
                val mapped = start + ((end - start) * progress.coerceIn(0, 100) / 100)
                onProgress(mapped.coerceIn(start, end))
            }
            return tempFile
        }

        if (majorVersion == 25) {
            val arm64Archive = archives.single()
            val tempArchive = downloadArchive(arm64Archive, start = 1, end = 90)
            onProgress(94)
            return installRuntimeWithStaging(filesDir = filesDir, majorVersion = majorVersion) { tempDir ->
                Files.newInputStream(tempArchive).use { input -> extractTarXzSafely(input, tempDir) }
            }
        }

        val universalArchive = archives.first { it.displayName.endsWith("universal.tar.xz") }
        val abiArchive = archives.first { it != universalArchive }
        val universalTemp = downloadArchive(universalArchive, start = 1, end = 48)
        val abiTemp = downloadArchive(abiArchive, start = 49, end = 86)
        onProgress(90)
        return installRuntimeWithStaging(filesDir = filesDir, majorVersion = majorVersion) { tempDir ->
            Files.newInputStream(universalTemp).use { input -> extractTarXzSafely(input, tempDir) }
            Files.newInputStream(abiTemp).use { input -> extractTarXzSafely(input, tempDir) }
        }
    } finally {
        onProgress(100)
        tempFiles.forEach { Files.deleteIfExists(it) }
    }
}

private fun downloadFileToPath(urls: List<String>, target: Path, onProgress: (Int) -> Unit = {}) {
    var lastError: Exception? = null
    urls.distinct().forEach { url ->
        try {
            downloadSingleFileToPath(url, target, onProgress)
            return
        } catch (error: Exception) {
            lastError = error
        }
    }
    throw JavaRuntimeInstallException("下载 JRE 失败", lastError)
}

private fun downloadVerifiedFileToPath(
    urls: List<String>,
    target: Path,
    expectedArchive: com.mcgo.app.server.TrustedJavaRuntimeTarball,
    onProgress: (Int) -> Unit = {},
) {
    downloadVerifiedFileFromAnyUrl(
        urls = urls,
        target = target,
        expectedSha256 = expectedArchive.sha256,
        expectedDisplayName = expectedArchive.displayName,
        downloader = ::downloadSingleFileToPath,
        onProgress = onProgress,
    )
}

internal fun downloadVerifiedFileFromAnyUrl(
    urls: List<String>,
    target: Path,
    expectedSha256: String,
    expectedDisplayName: String,
    downloader: (String, Path, (Int) -> Unit) -> Unit,
    onProgress: (Int) -> Unit = {},
) {
    var lastError: Exception? = null
    urls.distinct().forEach { url ->
        try {
            Files.deleteIfExists(target)
            downloader(url, target, onProgress)
            val actualSha256 = sha256Hex(target)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                throw JavaRuntimeInstallException(
                    "JRE 安装包可信校验失败：$expectedDisplayName 的 SHA-256 与预期不匹配",
                )
            }
            return
        } catch (error: Exception) {
            lastError = error
        }
    }
    throw JavaRuntimeInstallException("下载 JRE 失败", lastError)
}

private fun downloadSingleFileToPath(url: String, target: Path, onProgress: (Int) -> Unit) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 60_000
        requestMethod = "GET"
        setRequestProperty("User-Agent", McGoUserAgent)
    }
    try {
        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            throw JavaRuntimeInstallException("下载 JRE 失败：HTTP $statusCode")
        }
        val contentLength = connection.contentLengthLong.takeIf { it > 0L }
        connection.inputStream.use { input ->
            Files.newOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    contentLength?.let { onProgress(((copied * 100) / it).toInt().coerceIn(1, 100)) }
                }
                if (contentLength == null) onProgress(100)
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun runtimeDownloadUrlsForRegion(context: Context, canonicalUrl: String): List<String> {
    val mirror = "https://gh-proxy.com/$canonicalUrl"
    val language = context.resources.configuration.locales.get(0).language.lowercase()
    return if (language == "zh") listOf(mirror, canonicalUrl) else listOf(canonicalUrl, mirror)
}

private fun isPaperRuntimeProcessAlive(context: Context): Boolean {
    val activityManager = context.getSystemService(ActivityManager::class.java) ?: return false
    val targetProcessName = context.packageName + PaperRuntimeProcessSuffix
    @Suppress("DEPRECATION")
    return activityManager.runningAppProcesses?.any { process ->
        process.processName == targetProcessName
    } == true
}

private fun installJavaRuntimeFromUri(
    context: Context,
    uri: Uri,
    majorVersion: Int,
): Path {
    val displayName = uri.displayName(context).ifBlank { "java-runtime.archive" }
    val archiveKind = classifyJavaRuntimeArchiveName(displayName)
    val tempFile = copyUriToTempFile(
        context = context,
        uri = uri,
        suffix = javaRuntimeArchiveTempSuffix(displayName),
    )
    return try {
        validateRuntimeArchiveTrust(
            archiveKind = archiveKind,
            source = JavaRuntimeArchiveSource.UserImport,
            sha256 = sha256Hex(tempFile),
            displayName = displayName,
            signerCertSha256 = when (archiveKind) {
                JavaRuntimeArchiveKind.PojavApk -> pojavRuntimeComponentSignerCertSha256(tempFile, majorVersion)
                JavaRuntimeArchiveKind.TarXz -> null
            },
        )
        when (archiveKind) {
            JavaRuntimeArchiveKind.PojavApk -> installPojavRuntimeFromApk(
                apkPath = tempFile,
                filesDir = context.filesDir.toPath(),
                majorVersion = majorVersion,
            )
            JavaRuntimeArchiveKind.TarXz -> installRuntimeFromTarXz(
                archivePath = tempFile,
                filesDir = context.filesDir.toPath(),
                majorVersion = majorVersion,
            )
        }
    } finally {
        Files.deleteIfExists(tempFile)
    }
}

private fun pojavRuntimeComponentSignerCertSha256(apkPath: Path, majorVersion: Int): String? = runCatching {
    JarFile(apkPath.toFile(), true).use { jar ->
        val component = resolvePojavRuntimeComponent(jar.asZipFile(), majorVersion)
        val targetEntries = listOf(
            "assets/components/$component/universal.tar.xz",
            "assets/components/$component/${abiArchiveName(Build.SUPPORTED_ABIS.firstOrNull().orEmpty())}",
        )
        for (entryName in targetEntries) {
            val entry = jar.getJarEntry(entryName) ?: return@runCatching null
            jar.getInputStream(entry).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (input.read(buffer) >= 0) {
                    // consume to trigger certificate verification
                }
            }
            val certificate = entry.certificates
                ?.firstOrNull()
                ?.let { it as? X509Certificate }
                ?: return@runCatching null
            val digest = sha256Hex(certificate.encoded.inputStream())
            if (digest != OfficialPojavLauncherCertSha256) return@runCatching digest
        }
        OfficialPojavLauncherCertSha256
    }
}.getOrNull()

private fun JarFile.asZipFile(): java.util.zip.ZipFile = this

private fun copyUriToTempFile(
    context: Context,
    uri: Uri,
    suffix: String,
): Path {
    val tempFile = Files.createTempFile(context.cacheDir.toPath(), "mcgo-java-runtime-", suffix)
    try {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw JavaRuntimeInstallException("无法读取选择的 JRE 文件")
            Files.copy(input, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return tempFile
    } catch (error: Exception) {
        Files.deleteIfExists(tempFile)
        if (error is JavaRuntimeInstallException) throw error
        throw JavaRuntimeInstallException("复制 JRE 文件失败", error)
    }
}

private fun Uri.displayName(context: Context): String {
    context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex).orEmpty()
        }
    }
    return lastPathSegment.orEmpty()
}

private fun Throwable.userFacingInstallMessage(majorVersion: Int): String {
    val baseMessage = message ?: "安装失败"
    return if (this is JavaRuntimeInstallException) {
        "Java $majorVersion 安装失败：$baseMessage"
    } else {
        "Java $majorVersion 安装失败：${baseMessage.take(80)}"
    }
}

@Composable
private fun ServerConsoleDialog(
    server: ServerCardState,
    onDismiss: () -> Unit,
    onSubmitCommand: (String) -> Boolean,
) {
    val consoleText = remember(server.runtimeLogPath, server.runtimeLogs) { resolveServerConsoleText(server) }
    val context = LocalContext.current
    val annotatedLog = remember(consoleText) { buildConsoleAnnotatedLog(consoleText) }
    var command by remember(server.id) { mutableStateOf("") }
    var inlineError by remember(server.id) { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    LaunchedEffect(annotatedLog.text) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        containerColor = Color(0xFF1F1F1F),
        tonalElevation = 0.dp,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .background(
                                    when (server.launchStatus) {
                                        ServerLaunchStatus.Running -> ConsoleInfoColor
                                        ServerLaunchStatus.Failed -> ConsoleErrorColor
                                        ServerLaunchStatus.Stopping -> ConsoleWarnColor
                                        else -> ConsoleTimestampColor
                                    },
                                    CircleShape,
                                ),
                        )
                        Text(
                            text = server.launchStatus.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFD0D7DE),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = server.runtimeLogPath
                                ?.let { java.io.File(it) }
                                ?.takeIf { it.isFile }
                                ?.readText()
                                ?.takeIf { it.isNotBlank() }
                                ?: consoleText
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                ClipData.newPlainText("${server.name} logs", clipboard),
                            )
                        },
                    ) {
                        Text("复制日志")
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = Color(0xFF050505),
                    shape = CardDefaults.shape,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(scrollState),
                    ) {
                        BasicText(
                            text = annotatedLog,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFE6EDF3),
                                fontFamily = FontFamily.Monospace,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.35,
                            ),
                        )
                    }
                }
                inlineError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = {
                            command = it
                            if (inlineError != null) inlineError = null
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("发送指令") },
                        placeholder = { Text("例如：list / say hello / stop") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                    IconButton(
                        onClick = {
                            val result = runCatching {
                                val normalized = normalizeConsoleCommand(command)
                                if (!onSubmitCommand(normalized)) {
                                    error("当前 Paper 进程尚未接收标准输入，请稍后再试")
                                }
                                command = ""
                            }
                            inlineError = result.exceptionOrNull()?.message
                        },
                    ) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = "发送指令")
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPaperServerDialog(
    server: ServerCardState,
    paperVersions: List<String>,
    supportedProvisionableJavaVersions: Set<Int>,
    onDismiss: () -> Unit,
    onSave: (ServerCardState) -> Unit,
) {
    val versionOptions = remember(paperVersions) { com.mcgo.app.server.resolveProvisionablePaperVersionOptions(paperVersions) }
    var name by remember(server.id) { mutableStateOf(server.name) }
    var minecraftVersion by remember(server.id) { mutableStateOf(server.minecraftVersion) }
    var versionMenuExpanded by remember(server.id) { mutableStateOf(false) }
    var javaSelectionMode by remember(server.id) { mutableStateOf(server.javaSelectionMode) }
    var selectedJavaMajorVersion by remember(server.id) { mutableStateOf(server.javaMajorVersion) }
    var maxPlayers by remember(server.id) { mutableStateOf(server.maxPlayers.toString()) }
    var memoryMb by remember(server.id) { mutableStateOf(server.memoryMb.toString()) }
    var port by remember(server.id) { mutableStateOf(server.defaultPort.toString()) }
    var worldName by remember(server.id) { mutableStateOf(server.worldName) }
    val resolvedMaxPlayers = maxPlayers.toIntOrNull()?.coerceIn(1, 200) ?: server.maxPlayers
    val resolvedMemoryMb = memoryMb.toIntOrNull()?.coerceAtLeast(512) ?: server.memoryMb
    val resolvedPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: server.defaultPort
    val recommendedJava = remember(minecraftVersion) { recommendedJavaMajorVersion(minecraftVersion) }
    LaunchedEffect(minecraftVersion, javaSelectionMode) {
        if (javaSelectionMode == JavaSelectionMode.Recommended) {
            selectedJavaMajorVersion = recommendedJava
        }
    }
    val canSave = name.isNotBlank() && minecraftVersion.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        applyPaperServerEdits(
                            server = server,
                            name = name,
                            minecraftVersion = minecraftVersion,
                            maxPlayers = resolvedMaxPlayers,
                            memoryMb = resolvedMemoryMb,
                            port = resolvedPort,
                            worldName = worldName,
                            javaMajorVersion = selectedJavaMajorVersion,
                            javaSelectionMode = javaSelectionMode,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("编辑 ${server.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务器名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = minecraftVersion,
                    onValueChange = { minecraftVersion = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Minecraft 版本") },
                    supportingText = { Text("可直接填写，也可参考官方版本列表") },
                    singleLine = true,
                )
                Text(
                    text = "可选版本：${versionOptions.takeLast(8).joinToString(" / ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                JavaSelectionChipRow(
                    title = "Java 版本策略",
                    options = listOf("跟随推荐", "手动指定"),
                    selectedOption = if (javaSelectionMode == JavaSelectionMode.Recommended) "跟随推荐" else "手动指定",
                    onSelected = { selected ->
                        javaSelectionMode = if (selected == "跟随推荐") JavaSelectionMode.Recommended else JavaSelectionMode.Manual
                    },
                )
                if (javaSelectionMode == JavaSelectionMode.Manual) {
                    JavaVersionChipRow(
                        options = supportedProvisionableJavaVersions.toList().sorted(),
                        selectedJavaMajorVersion = selectedJavaMajorVersion,
                        onSelected = { selectedJavaMajorVersion = it },
                    )
                }
                Text(
                    text = if (javaSelectionMode == JavaSelectionMode.Recommended) {
                        "当前推荐：Java $recommendedJava"
                    } else {
                        "当前手动指定：Java $selectedJavaMajorVersion；Minecraft 推荐 Java $recommendedJava"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = worldName,
                    onValueChange = { worldName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("世界名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maxPlayers,
                    onValueChange = { maxPlayers = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("最大玩家数") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = memoryMb,
                    onValueChange = { memoryMb = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("内存 MB") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("默认端口") },
                    singleLine = true,
                )
                if (server.isRuntimeBusy()) {
                    Text(
                        text = "服务器当前正在启动/运行；本次仅更新配置资料，不会强制改动当前运行中的端口与日志状态。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun JavaSelectionChipRow(
    title: String,
    options: List<String>,
    selectedOption: String,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selectedOption,
                    onClick = { onSelected(option) },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun JavaVersionChipRow(
    options: List<Int>,
    selectedJavaMajorVersion: Int,
    onSelected: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selectedJavaMajorVersion,
                onClick = { onSelected(option) },
                label = { Text("Java $option") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}
