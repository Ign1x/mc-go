package com.mcgo.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.app.ActivityManager
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.network.measureTcpLatency
import com.mcgo.app.network.parseTcpEndpoint
import com.mcgo.app.server.PaperServerEventStatus
import com.mcgo.app.server.PaperServerEvents
import com.mcgo.app.server.PaperServerService
import com.mcgo.app.server.reconcilePersistedRuntimeState
import com.mcgo.app.server.reducePaperRuntimeEvent
import com.mcgo.app.server.stopRequestMessage
import com.mcgo.app.server.JavaRuntimeArchiveKind
import com.mcgo.app.server.JavaRuntimeArchiveSource
import com.mcgo.app.server.JavaRuntimeInstallException
import com.mcgo.app.server.OfficialPojavLauncherApkSha256
import com.mcgo.app.server.OfficialPojavLauncherCertSha256
import com.mcgo.app.server.abiArchiveName
import com.mcgo.app.server.classifyJavaRuntimeArchiveName
import com.mcgo.app.server.deleteJavaRuntime
import com.mcgo.app.server.fallbackPaperVersions
import com.mcgo.app.server.fetchPaperVersions
import com.mcgo.app.server.filterProvisionablePaperVersions
import com.mcgo.app.server.installPojavRuntimeFromApk
import com.mcgo.app.server.javaRuntimeArchiveTempSuffix
import com.mcgo.app.server.managedPaperServerLogFile
import com.mcgo.app.server.resolvePojavRuntimeComponent
import com.mcgo.app.server.scanInstalledJavaVersions
import com.mcgo.app.server.sha256Hex
import com.mcgo.app.server.validateRuntimeArchiveTrust
import com.mcgo.app.ui.components.FluidGradientBackground
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.AppearancePreferencesSaver
import com.mcgo.app.ui.model.McGoPage
import com.mcgo.app.ui.model.McGoPageChrome
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.ThemeModePreference
import com.mcgo.app.ui.model.TunnelLatencyResult
import com.mcgo.app.ui.model.applyTunnelLatencyResults
import com.mcgo.app.ui.model.defaultJavaManagementState
import com.mcgo.app.ui.model.detachDeletedTunnel
import com.mcgo.app.ui.model.isManagedRuntimeProvisioningAvailable
import com.mcgo.app.ui.model.removeTunnelProfile
import com.mcgo.app.ui.model.finalizePendingServerDeletion
import com.mcgo.app.ui.model.canStartServerFromUi
import com.mcgo.app.ui.model.isRuntimeBusy
import com.mcgo.app.ui.model.markLaunchFailed
import com.mcgo.app.ui.model.markUnsupportedManagedRuntime
import com.mcgo.app.ui.model.requestServerDeletion
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.startWithTunnel
import com.mcgo.app.ui.model.withLaunchProgress
import com.mcgo.app.ui.model.stopServer
import com.mcgo.app.ui.model.upsertTunnelProfile
import com.mcgo.app.ui.sample.McGoSampleRepository
import com.mcgo.app.ui.screens.ServersScreen
import com.mcgo.app.ui.screens.SettingsScreen
import com.mcgo.app.ui.screens.StatusScreen
import com.mcgo.app.ui.screens.TunnelsScreen
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
    var appearancePreferences by rememberSaveable(stateSaver = AppearancePreferencesSaver) {
        mutableStateOf(AppearancePreferences())
    }
    val runtimeAliveOnLaunch = remember(context) { isPaperRuntimeProcessAlive(context) }
    val persistedServers = remember(serverStore) { serverStore.load() }
    val reconciledPersistedServers = remember(persistedServers, runtimeAliveOnLaunch) {
        finalizePendingServerDeletion(
            reconcilePersistedRuntimeState(
                servers = persistedServers,
                runtimeAlive = runtimeAliveOnLaunch,
            ).map { it.markUnsupportedManagedRuntime() },
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
            onAppearancePreferencesChange = { appearancePreferences = it },
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
    val javaManagementState = remember(installedJavaVersions, javaDownloadProgress) {
        defaultJavaManagementState(
            installedVersions = installedJavaVersions,
            downloadProgressByMajor = javaDownloadProgress,
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
            val guidance = if (isManagedRuntimeProvisioningAvailable(targetServer.javaMajorVersion)) {
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
                    if (isManagedRuntimeProvisioningAvailable(targetServer.javaMajorVersion)) {
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
                    ).map { it.markUnsupportedManagedRuntime() },
                )
                if (reconciledServers != serverSnapshot) {
                    onServersChange(reconciledServers)
                    onPersistServers(reconciledServers)
                }
            }
            delay(1500)
        }
    }

    LaunchedEffect(tunnels.map { it.id to it.serverAddress }) {
        while (true) {
            val tunnelSnapshot = latestTunnels
            if (tunnelSnapshot.isNotEmpty()) {
                val measuredResults = withContext(Dispatchers.IO) {
                    tunnelSnapshot.map { profile ->
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
            delay(5000)
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
            modifier = Modifier.fillMaxSize(),
        )
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = if (destination == McGoDestination.Settings) 96.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(chrome.titleRes),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(chrome.subtitleRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (destination == McGoDestination.Settings) {
                        IconButton(
                            modifier = Modifier.align(Alignment.TopEnd),
                            onClick = { onAppearancePreferencesChange(appearancePreferences.copy(themeMode = appearancePreferences.themeMode.next())) },
                        ) {
                            Icon(
                                imageVector = when (appearancePreferences.themeMode) {
                                    ThemeModePreference.FollowSystem -> Icons.Outlined.Brightness4
                                    ThemeModePreference.Light -> Icons.Outlined.WbSunny
                                    ThemeModePreference.Dark -> Icons.Outlined.DarkMode
                                },
                                contentDescription = "切换主题：${appearancePreferences.themeMode.label}",
                            )
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = bottomBarAlpha),
                    tonalElevation = 0.dp,
                ) {
                    McGoDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = stringResource(item.labelRes),
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            },
            floatingActionButton = {
                when (destination) {
                    McGoDestination.Servers -> ExtendedFloatingActionButton(
                        onClick = { showServerComposer = true },
                        text = { Text(stringResource(R.string.action_create_server)) },
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                    McGoDestination.Tunnels -> ExtendedFloatingActionButton(
                        onClick = {
                            editingTunnelId = null
                            showTunnelComposer = true
                        },
                        text = { Text(stringResource(R.string.action_add_tunnel)) },
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
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
                when (destination) {
                    McGoDestination.Status -> StatusScreen(modifier = Modifier.fillMaxSize())
                    McGoDestination.Servers -> ServersScreen(
                        modifier = Modifier.fillMaxSize(),
                        servers = servers,
                        availableTunnels = tunnels,
                        paperVersions = paperVersions,
                        showCreateServer = showServerComposer,
                        onDismissCreateServer = { showServerComposer = false },
                        onCreateServer = { server ->
                            val updatedServers = servers + server
                            onServersChange(updatedServers)
                            onPersistServers(updatedServers)
                            scope.launch {
                                snackbarHostState.showSnackbar("已创建 ${server.name}")
                            }
                        },
                        onStartServer = { serverId, tunnelId, startupPort ->
                            startServerNow(PendingStartRequest(serverId, tunnelId, startupPort))
                        },
                        onStopServer = { serverId ->
                            val targetServer = servers.firstOrNull { it.id == serverId }
                            val updatedServers = servers.map { server ->
                                if (server.id == serverId) {
                                    server.copy(
                                        launchStatus = ServerLaunchStatus.Stopping,
                                        launchProgress = 0,
                                        runtimeLogs = (server.runtimeLogs + stopRequestMessage()).takeLast(12),
                                    )
                                } else {
                                    server
                                }
                            }
                            onServersChange(updatedServers)
                            onPersistServers(updatedServers)
                            PaperServerService.stop(appContext, serverId)
                            scope.launch {
                                snackbarHostState.showSnackbar("已请求停止 ${targetServer?.name ?: "服务器"}")
                            }
                        },
                        onDeleteServer = { serverId ->
                            val targetServer = servers.firstOrNull { it.id == serverId }
                            if (targetServer?.isRuntimeBusy() == true) {
                                val updatedServers = finalizePendingServerDeletion(
                                    servers.map { server ->
                                        if (server.id == serverId) requestServerDeletion(server) else server
                                    },
                                )
                                onServersChange(updatedServers)
                                onPersistServers(updatedServers)
                                PaperServerService.stop(appContext, serverId)
                                scope.launch {
                                    snackbarHostState.showSnackbar("已请求删除 ${targetServer.name}，将在服务停止后自动移除")
                                }
                                return@ServersScreen
                            }
                            val updatedServers = servers.filterNot { it.id == serverId }
                            onServersChange(updatedServers)
                            onPersistServers(updatedServers)
                            scope.launch {
                                snackbarHostState.showSnackbar("已删除 ${targetServer?.name ?: "服务器"}")
                            }
                        },
                        onActionClick = {
                            if (!hasServerDirectoryGrant()) {
                                requestServerDirectory(PendingServerDirectoryAction.EditServer)
                            } else {
                                notifyUnavailableFeature()
                            }
                        },
                    )
                    McGoDestination.Tunnels -> TunnelsScreen(
                        modifier = Modifier.fillMaxSize(),
                        tunnels = tunnels,
                        showComposer = showTunnelComposer,
                        editingTunnelId = editingTunnelId,
                        onDismissComposer = {
                            showTunnelComposer = false
                            editingTunnelId = null
                        },
                        onSaveTunnel = { profile ->
                            val existed = tunnels.any { it.id == profile.id }
                            val updatedTunnels = upsertTunnelProfile(tunnels, profile)
                            onTunnelsChangeAndPersist(updatedTunnels)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (existed) "已更新 ${profile.name}" else "已添加 ${profile.name}",
                                )
                            }
                        },
                        onEditTunnel = { tunnelId ->
                            editingTunnelId = tunnelId
                            showTunnelComposer = true
                        },
                        onDeleteTunnel = { tunnelId ->
                            val tunnelName = tunnels.firstOrNull { it.id == tunnelId }?.name ?: "该隧道"
                            val updatedTunnels = removeTunnelProfile(tunnels, tunnelId)
                            onTunnelsChangeAndPersist(updatedTunnels)
                            onServersChange(detachDeletedTunnel(servers, tunnelId))
                            if (editingTunnelId == tunnelId) {
                                editingTunnelId = null
                                showTunnelComposer = false
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar("已删除 $tunnelName")
                            }
                        },
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
                        onServerDirectorySelected = { uri -> persistServerDirectoryUri(uri) },
                        onRequestServerDirectory = { requestServerDirectory(PendingServerDirectoryAction.SettingsRequest) },
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
    val tempFile = Files.createTempFile(context.cacheDir.toPath(), "mcgo-pojav-runtime-", ".apk")
    try {
        val urls = runtimeDownloadUrlsForRegion(context)
        downloadFileToPath(urls, tempFile) { progress -> onProgress(progress.coerceIn(1, 82)) }
        validateRuntimeArchiveTrust(
            archiveKind = JavaRuntimeArchiveKind.PojavApk,
            source = JavaRuntimeArchiveSource.OfficialDownload,
            sha256 = sha256Hex(tempFile),
            displayName = "PojavLauncher.apk",
            signerCertSha256 = OfficialPojavLauncherCertSha256,
        )
        onProgress(86)
        return installPojavRuntimeFromApk(
            apkPath = tempFile,
            filesDir = context.filesDir.toPath(),
            majorVersion = majorVersion,
        )
    } finally {
        onProgress(100)
        Files.deleteIfExists(tempFile)
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

private fun downloadSingleFileToPath(url: String, target: Path, onProgress: (Int) -> Unit) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 60_000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "MC-GO/0.2.15")
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
                    contentLength?.let { onProgress(((copied * 80) / it).toInt().coerceIn(1, 82)) }
                }
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun runtimeDownloadUrlsForRegion(context: Context): List<String> {
    val github = PojavLauncherApkUrl
    val mirror = "https://gh-proxy.com/$github"
    val language = context.resources.configuration.locales.get(0).language.lowercase()
    return if (language == "zh") listOf(mirror, github) else listOf(github, mirror)
}

private fun isPaperRuntimeProcessAlive(context: Context): Boolean {
    val activityManager = context.getSystemService(ActivityManager::class.java) ?: return false
    val targetProcessName = context.packageName + PaperRuntimeProcessSuffix
    @Suppress("DEPRECATION")
    return activityManager.runningAppProcesses?.any { process ->
        process.processName == targetProcessName
    } == true
}

private const val PojavLauncherApkUrl = "https://github.com/PojavLauncherTeam/PojavLauncher/releases/download/gladiolus/PojavLauncher.apk"

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
            JavaRuntimeArchiveKind.TarXz -> throw JavaRuntimeInstallException(
                "当前仅允许通过可信校验的官方 Pojav APK 导入运行时；tar.xz/txz 直导入已暂时禁用",
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
