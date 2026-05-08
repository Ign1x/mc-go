package com.mcgo.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.mcgo.app.server.MaxPaperRuntimeSlots
import com.mcgo.app.server.PaperServerEvents
import com.mcgo.app.server.PaperServerService
import com.mcgo.app.server.abiArchiveName
import com.mcgo.app.server.activePaperRuntimeSlots
import com.mcgo.app.server.allocateRuntimeSlot
import com.mcgo.app.server.classifyJavaRuntimeArchiveName
import com.mcgo.app.server.authorizedServerProfilesAvailable
import com.mcgo.app.server.deleteJavaRuntime
import com.mcgo.app.server.deleteManagedServerWorkspaceFromAuthorizedDirectory
import com.mcgo.app.server.deleteManagedServerWorkspaceFromPrivateDirectory
import com.mcgo.app.server.extractTarXzSafely
import com.mcgo.app.server.fallbackPaperVersions
import com.mcgo.app.server.fallbackPurpurVersions
import com.mcgo.app.server.fallbackVanillaVersions
import com.mcgo.app.server.fetchPaperVersions
import com.mcgo.app.server.fetchProvisionableMinecraftVersions
import com.mcgo.app.server.fetchPurpurVersions
import com.mcgo.app.server.fetchVanillaVersions
import com.mcgo.app.server.filterProvisionablePaperVersions
import com.mcgo.app.server.installPojavRuntimeFromApk
import com.mcgo.app.server.installRuntimeFromTarXz
import com.mcgo.app.server.installRuntimeWithStaging
import com.mcgo.app.server.javaRuntimeArchiveTempSuffix
import com.mcgo.app.server.managedPaperServerLogFile
import com.mcgo.app.server.migratePrivateServerDataToAuthorizedDirectory
import com.mcgo.app.server.reconcilePersistedRuntimeState
import com.mcgo.app.server.reducePaperRuntimeEvent
import com.mcgo.app.server.resolvePojavRuntimeComponent
import com.mcgo.app.server.restoreManagedServerWorkspaceFromAuthorizedDirectory
import com.mcgo.app.server.restoreServerProfilesFromAuthorizedDirectory
import com.mcgo.app.server.scanInstalledJavaVersions
import com.mcgo.app.server.sha256Hex
import com.mcgo.app.server.stopRequestMessage
import com.mcgo.app.server.syncManagedServerWorkspaceToAuthorizedDirectory
import com.mcgo.app.server.syncServerProfilesToAuthorizedDirectory
import com.mcgo.app.server.deleteManagedServerWorkspaceFromAuthorizedDirectory
import com.mcgo.app.server.trustedRuntimeArchivesForVersion
import com.mcgo.app.server.validateRuntimeArchiveTrust
import com.mcgo.app.status.DevicePerformanceMonitor

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
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.model.SettingsDestination
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.ThemeModePreference
import com.mcgo.app.ui.model.TunnelLatencyResult
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.assignTunnelRemotePort
import com.mcgo.app.ui.model.applyPaperServerEdits
import com.mcgo.app.ui.model.applyTunnelLatencyResults
import com.mcgo.app.ui.model.MinecraftServerType
import com.mcgo.app.ui.model.MinecraftServerType.Paper

import com.mcgo.app.ui.model.buildConsoleAnnotatedLog
import com.mcgo.app.ui.model.buildPaperServerPropertiesEditorText
import com.mcgo.app.ui.model.canStartServerFromUi
import com.mcgo.app.ui.model.defaultJavaManagementState
import com.mcgo.app.ui.model.detachDeletedTunnel
import com.mcgo.app.ui.model.finalizePendingServerDeletion
import com.mcgo.app.ui.model.isManagedRuntimeProvisioningAvailable
import com.mcgo.app.ui.model.isRuntimeBusy
import com.mcgo.app.ui.model.markLaunchFailed
import com.mcgo.app.ui.model.markUnsupportedManagedRuntime
import com.mcgo.app.ui.model.normalizeConsoleCommand
import com.mcgo.app.ui.model.parsePaperServerPropertiesEditorText
import com.mcgo.app.ui.model.sanitizeAdvancedServerPropertiesOverride
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
import com.mcgo.app.ui.storage.ServerProfileStoreGlobalLock
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

private data class PendingStartRequest(
    val serverId: String,
    val tunnelId: String?,
    val startupPort: Int,
    val remotePort: Int?,
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
    val appContext = context.applicationContext
    val appEntryElapsedRealtimeMillis = remember { SystemClock.elapsedRealtime() }
    val statusMonitor = remember(appContext, appEntryElapsedRealtimeMillis) {
        DevicePerformanceMonitor(appContext, appEntryElapsedRealtimeMillis)
    }
    val tunnelStore = remember(context) {
        TunnelProfileStore(context.filesDir.toPath().resolve("tunnel_profiles.properties"))
    }
    val serverStorePath = remember(context) { context.filesDir.toPath().resolve("server_profiles.properties") }
    val serverStore = remember(serverStorePath) {
        ServerProfileStore(serverStorePath)
    }
    val runtimePrefs = remember(context) { context.getSharedPreferences(RuntimePrefsName, Context.MODE_PRIVATE) }
    val persistedServerDirectoryUri = remember(runtimePrefs) {
        runtimePrefs.getString(ServerDirectoryUriKey, null)
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
    val activeRuntimeSlotsOnLaunch = remember(context) { activePaperRuntimeSlots(context) }
    val persistedServers = remember(serverStore, persistedServerDirectoryUri) {
        val authorizedProfilesAvailable = authorizedServerProfilesAvailable(context, persistedServerDirectoryUri)
        if (authorizedProfilesAvailable) {
            restoreServerProfilesFromAuthorizedDirectory(
                context = context,
                authorizedDirectoryUri = persistedServerDirectoryUri,
                targetProfilesPath = serverStorePath,
            )
        }
        serverStore.load().also { loadedServers ->
            if (!authorizedProfilesAvailable && persistedServerDirectoryUri != null && loadedServers.isNotEmpty()) {
                migratePrivateServerDataToAuthorizedDirectory(
                    context = context,
                    authorizedDirectoryUri = persistedServerDirectoryUri,
                    filesDir = context.filesDir.toPath(),
                    serverIds = loadedServers.map { it.id },
                )
                syncServerProfilesToAuthorizedDirectory(
                    context = context,
                    authorizedDirectoryUri = persistedServerDirectoryUri,
                    sourceProfilesPath = serverStorePath,
                )
            }
        }
    }
    val reconciledPersistedServers = remember(persistedServers, activeRuntimeSlotsOnLaunch) {
        finalizePendingServerDeletion(
            reconcilePersistedRuntimeState(
                servers = persistedServers,
                activeRuntimeSlots = activeRuntimeSlotsOnLaunch,
            ).map { it.markUnsupportedManagedRuntime(supportedProvisionableJavaVersions) },
        )
    }
    var servers by remember(serverStore) {
        mutableStateOf(reconciledPersistedServers)
    }
    var tunnels by remember(tunnelStore) { mutableStateOf(tunnelStore.load()) }
    LaunchedEffect(reconciledPersistedServers, persistedServerDirectoryUri) {
        if (reconciledPersistedServers != persistedServers) {
            serverStore.save(reconciledPersistedServers)
        }
        if (authorizedServerProfilesAvailable(context, persistedServerDirectoryUri)) {
            syncServerProfilesToAuthorizedDirectory(
                context = context,
                authorizedDirectoryUri = persistedServerDirectoryUri,
                sourceProfilesPath = serverStorePath,
            )
        }
    }
    val vanillaVersions by produceState(initialValue = fallbackVanillaVersions()) {
        value = withContext(Dispatchers.IO) { fetchVanillaVersions() }
    }
    val paperVersions by produceState(initialValue = filterProvisionablePaperVersions(fallbackPaperVersions())) {
        value = withContext(Dispatchers.IO) { fetchPaperVersions() }
    }
    val purpurVersions by produceState(initialValue = fallbackPurpurVersions()) {
        value = withContext(Dispatchers.IO) { fetchPurpurVersions() }
    }

    McGoTheme(appearancePreferences = appearancePreferences) {
        MCGoAppScaffold(
            appearancePreferences = appearancePreferences,
            servers = servers,
            tunnels = tunnels,
            vanillaVersions = vanillaVersions,
            paperVersions = paperVersions,
            purpurVersions = purpurVersions,
            supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
            appEntryElapsedRealtimeMillis = appEntryElapsedRealtimeMillis,
            statusMonitor = statusMonitor,
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
            serverStorePath = serverStorePath,
            serverStore = serverStore,
            onPersistServers = { serverStore.save(it) },
        )
    }
}

@Composable
private fun MCGoAppScaffold(
    appearancePreferences: AppearancePreferences,
    servers: List<ServerCardState>,
    tunnels: List<TunnelProfile>,
    vanillaVersions: List<String>,
    paperVersions: List<String>,
    purpurVersions: List<String>,
    supportedProvisionableJavaVersions: Set<Int>,
    appEntryElapsedRealtimeMillis: Long,
    statusMonitor: DevicePerformanceMonitor,
    onAppearancePreferencesChange: (AppearancePreferences) -> Unit,
    onServersChange: (List<ServerCardState>) -> Unit,
    onTunnelsChange: (List<TunnelProfile>) -> Unit,
    onTunnelsChangeAndPersist: (List<TunnelProfile>) -> Unit,
    serverStorePath: Path,
    serverStore: ServerProfileStore,
    onPersistServers: (List<ServerCardState>) -> Unit,
) {
    RequestRuntimePermissions()
    val appContext = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(McGoDestination.Status) }
    var settingsDestination by rememberSaveable { mutableStateOf(SettingsDestination.Overview) }
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
    val layoutDirection = LocalLayoutDirection.current
    val activeEditingServer = editingServerId?.let { serverId ->
        servers.firstOrNull { it.id == serverId }
    }
    LaunchedEffect(editingServerId, servers) {
        if (editingServerId != null && activeEditingServer == null) {
            editingServerId = null
        }
    }
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
    val restoreProfilesFromAuthorizedDirectory = remember(appContext, serverStorePath) {
        {
            restoreServerProfilesFromAuthorizedDirectory(
                context = appContext,
                authorizedDirectoryUri = serverDirectoryUriText,
                targetProfilesPath = serverStorePath,
            )
        }
    }
    var pendingServerDirectoryAction by remember { mutableStateOf<PendingServerDirectoryAction?>(null) }
    var pendingStartRequest by remember { mutableStateOf<PendingStartRequest?>(null) }
    val latestServers by rememberUpdatedState(servers)
    fun persistServerDirectoryUri(uri: Uri?) {
        serverDirectoryUriText = uri?.toString()
        runtimePrefs.edit().apply {
            if (uri == null) remove(ServerDirectoryUriKey) else putString(ServerDirectoryUriKey, uri.toString())
        }.apply()
    }
    fun syncServerProfilesToAuthorizedDirectoryNow(serverSnapshot: List<ServerCardState>) {
        synchronized(ServerProfileStoreGlobalLock) {
            onPersistServers(serverSnapshot)
            syncServerProfilesToAuthorizedDirectory(
                context = appContext,
                authorizedDirectoryUri = serverDirectoryUriText,
                sourceProfilesPath = serverStorePath,
            )
        }
    }
    fun hasServerDirectoryGrant(): Boolean = ServerDirectoryPermissionEffect(serverDirectoryUriText, appContext)
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val permissionGranted = runCatching {
                appContext.contentResolver.takePersistableUriPermission(uri, ServerDirectoryGrantFlags)
                true
            }.getOrDefault(false)
            if (!permissionGranted) {
                pendingStartRequest = null
                pendingServerDirectoryAction = null
                scope.launch { snackbarHostState.showSnackbar("服务器目录授权失败，请重新选择可持久授权的目录") }
                return@rememberLauncherForActivityResult
            }
            persistServerDirectoryUri(uri)
            scope.launch {
                val restoredServers = withContext(Dispatchers.IO) {
                    val authorizedProfilesAvailable = authorizedServerProfilesAvailable(appContext, serverDirectoryUriText)
                    if (authorizedProfilesAvailable) {
                        restoreProfilesFromAuthorizedDirectory()
                    }
                    val restoredServers = finalizePendingServerDeletion(
                        reconcilePersistedRuntimeState(
                            servers = serverStore.load(),
                            activeRuntimeSlots = activePaperRuntimeSlots(appContext),
                        ).map { it.markUnsupportedManagedRuntime(supportedProvisionableJavaVersions) },
                    )
                    syncServerProfilesToAuthorizedDirectoryNow(restoredServers)
                    if (authorizedProfilesAvailable) {
                        restoredServers.filterNot { it.isRuntimeBusy() }.forEach { server ->
                            restoreManagedServerWorkspaceFromAuthorizedDirectory(
                                context = appContext,
                                authorizedDirectoryUri = serverDirectoryUriText,
                                serverId = server.id,
                                targetWorkspaceDir = com.mcgo.app.server.managedPaperServerDirectory(appContext.filesDir.toPath(), server.id),
                            )
                        }
                    } else {
                        migratePrivateServerDataToAuthorizedDirectory(
                            context = appContext,
                            authorizedDirectoryUri = serverDirectoryUriText,
                            filesDir = appContext.filesDir.toPath(),
                            serverIds = restoredServers.map { it.id },
                        )
                    }
                    syncServerProfilesToAuthorizedDirectory(
                        context = appContext,
                        authorizedDirectoryUri = serverDirectoryUriText,
                        sourceProfilesPath = serverStorePath,
                    )
                    restoredServers
                }
                onServersChange(restoredServers)
                snackbarHostState.showSnackbar("服务器目录已授权，现有服务器数据已同步到该目录")
            }
        } else {
            pendingStartRequest = null
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
        val resolvedPort = tunnel?.resolveStartupPort(targetServer.defaultPort, request.startupPort) ?: request.startupPort
        val reservedTunnelRemotePort = tunnel?.let {
            runCatching {
                assignTunnelRemotePort(
                    server = if (targetServer.selectedTunnelId == it.id) targetServer else targetServer.copy(tunnelRemotePort = null),
                    tunnel = it,
                    requestedRemotePort = request.remotePort,
                    servers = servers,
                )
            }.getOrElse { error ->
                scope.launch { snackbarHostState.showSnackbar(error.message ?: "隧道远端端口分配失败") }
                return
            }
        }
        val runtimeAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        if (tunnel != null && tunnel.kind != com.mcgo.app.ui.model.TunnelKind.Frp) {
            scope.launch { snackbarHostState.showSnackbar("当前仅支持 FRP 隧道真启动；请先取消该隧道或改用 FRP") }
            return
        }
        if (tunnel != null && runtimeAbi != "arm64-v8a") {
            scope.launch { snackbarHostState.showSnackbar("当前设备 ABI 为 $runtimeAbi，暂不支持内置 FRP 客户端") }
            return
        }
        if (servers.any { it.id != request.serverId && it.isRuntimeBusy() && it.port == resolvedPort }) {
            scope.launch { snackbarHostState.showSnackbar("端口 $resolvedPort 已被其他运行中的服务器占用") }
            return
        }
        val allocatedSlot = allocateRuntimeSlot(
            servers = servers,
            targetServerId = request.serverId,
            maxSlots = MaxPaperRuntimeSlots,
        ) ?: run {
            scope.launch { snackbarHostState.showSnackbar("同时运行的服务器已达到上限（$MaxPaperRuntimeSlots）") }
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
            syncServerProfilesToAuthorizedDirectoryNow(failedServers)
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
                server.copy(tunnelRemotePort = reservedTunnelRemotePort ?: server.tunnelRemotePort)
                    .startWithTunnel(tunnel = tunnel, startupPort = request.startupPort)
                    .copy(
                        runtimeLogPath = runtimeLogPath,
                        runtimeSlot = allocatedSlot,
                    )
                    .withLaunchProgress(8, "已提交启动任务，准备使用内置 HotSpot 运行")
            }
        }
        onServersChange(updatedServers)
        syncServerProfilesToAuthorizedDirectoryNow(updatedServers)
        updatedServers.firstOrNull { it.id == request.serverId }?.let { PaperServerService.start(appContext, it, tunnel) }
        scope.launch {
            snackbarHostState.showSnackbar(
                tunnel?.let { "${targetServer.name} 已通过 ${it.name} 开始启动" } ?: "${targetServer.name} 开始启动",
            )
        }
    }
    val queuedStartRequest = pendingStartRequest
    if (queuedStartRequest != null && hasServerDirectoryGrant()) {
        pendingStartRequest = null
        startServerNow(queuedStartRequest)
    }

    LaunchedEffect(Unit) {
        PaperServerEvents.events.collect { event ->
            val updatedServers = finalizePendingServerDeletion(
                latestServers.map { server ->
                    if (server.id == event.serverId) reducePaperRuntimeEvent(server, event) else server
                },
            )
            onServersChange(updatedServers)
        }
    }

    LaunchedEffect(appContext) {
        while (true) {
            val serverSnapshot = latestServers
            val activeSlots = activePaperRuntimeSlots(appContext)
            val expectedBusySlots = serverSnapshot.filter { it.isRuntimeBusy() }.mapNotNull { it.runtimeSlot }.toSet()
            if (serverSnapshot.any { it.isRuntimeBusy() } && expectedBusySlots != activeSlots) {
                val reconciledServers = finalizePendingServerDeletion(
                    reconcilePersistedRuntimeState(
                        servers = serverSnapshot,
                        activeRuntimeSlots = activeSlots,
                    ).map { it.markUnsupportedManagedRuntime(supportedProvisionableJavaVersions) },
                )
                if (reconciledServers != serverSnapshot) {
                    onServersChange(reconciledServers)
                    syncServerProfilesToAuthorizedDirectoryNow(reconciledServers)
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

    LaunchedEffect(destination) {
        if (destination != McGoDestination.Settings) {
            settingsDestination = SettingsDestination.Overview
        }
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
                if (activeEditingServer == null && !(destination == McGoDestination.Settings && settingsDestination != SettingsDestination.Overview)) {
                    FloatingGlassBottomMenu(
                        destination = destination,
                        bottomBarAlpha = bottomBarAlpha,
                        transparentCards = appearancePreferences.transparentCards,
                        onDestinationSelected = { destination = it },
                    )
                }
            },
            floatingActionButton = {
                when (destination) {
                    McGoDestination.Servers -> if (activeEditingServer == null) {
                        ExtendedFloatingActionButton(
                            onClick = { showServerComposer = true },
                            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            text = { Text("创建服务器") },
                        )
                    }
                    McGoDestination.Tunnels -> Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FloatingActionButton(
                            onClick = { refreshTunnelLatency(null) },
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh tunnels")
                        }
                        ExtendedFloatingActionButton(
                            onClick = { showTunnelComposer = true },
                            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            text = { Text("新增隧道") },
                        )
                    }
                    else -> Unit
                }
            },
            floatingActionButtonPosition = FabPosition.End,
        ) { innerPadding ->
            val bottomContentPadding = innerPadding.calculateBottomPadding()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        PaddingValues(
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            top = innerPadding.calculateTopPadding(),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = 0.dp,
                        ),
                    ),
            ) {
                consoleServerId?.let { serverId ->
                    servers.firstOrNull { it.id == serverId }?.let { server ->
                        ServerConsoleDialog(
                            server = server,
                            onDismiss = { consoleServerId = null },
                            onSubmitCommand = { command ->
                                val normalized = normalizeConsoleCommand(command)
                                PaperServerService.sendCommand(appContext, server.id, normalized.trim(), server.runtimeSlot)
                                scope.launch {
                                    snackbarHostState.showSnackbar("已发送指令：${normalized.trim()}")
                                }
                                true
                            },
                        )
                    }
                }
                when (destination) {
                    McGoDestination.Status -> StatusScreen(
                        modifier = Modifier.fillMaxSize(),
                        appEntryElapsedRealtimeMillis = appEntryElapsedRealtimeMillis,
                        statusMonitor = statusMonitor,
                        bottomContentPadding = bottomContentPadding,
                    )
                    McGoDestination.Servers -> ServersScreen(
                        servers = servers,
                        availableTunnels = tunnels,
                        vanillaVersions = vanillaVersions,
                        paperVersions = paperVersions,
                        purpurVersions = purpurVersions,
                        supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
                        modifier = Modifier.fillMaxSize(),
                        bottomContentPadding = bottomContentPadding,
                        showCreateServer = showServerComposer,
                        onDismissCreateServer = { showServerComposer = false },
                        onCreateServer = { server ->
                            val updatedServers = servers + server.markUnsupportedManagedRuntime(supportedProvisionableJavaVersions)
                            onServersChange(updatedServers)
                            syncServerProfilesToAuthorizedDirectoryNow(updatedServers)
                            showServerComposer = false
                            scope.launch { snackbarHostState.showSnackbar("已创建 ${server.name}") }
                        },
                        onStartServer = { serverId, tunnelId, startupPort, remotePort ->
                            if (!hasServerDirectoryGrant()) {
                                pendingStartRequest = PendingStartRequest(serverId, tunnelId, startupPort, remotePort)
                                requestServerDirectory(PendingServerDirectoryAction.StartServer)
                            } else {
                                startServerNow(PendingStartRequest(serverId, tunnelId, startupPort, remotePort))
                            }
                        },
                        onStopServer = { serverId ->
                            val targetServer = servers.firstOrNull { it.id == serverId } ?: return@ServersScreen
                            PaperServerService.stop(appContext, serverId, targetServer.runtimeSlot)
                            val updatedServers = servers.map { server ->
                                if (server.id == serverId) {
                                    server.copy(
                                        launchStatus = ServerLaunchStatus.Stopping,
                                        runtimeLogs = (server.runtimeLogs + stopRequestMessage()).takeLast(12),
                                    )
                                } else {
                                    server
                                }
                            }
                            onServersChange(updatedServers)
                            syncServerProfilesToAuthorizedDirectoryNow(updatedServers)
                        },
                        onDeleteServer = { serverId ->
                            val targetServer = servers.firstOrNull { it.id == serverId }
                            if (targetServer?.isRuntimeBusy() == true) {
                                PaperServerService.stop(appContext, serverId, targetServer.runtimeSlot)
                                val updatedServers = finalizePendingServerDeletion(
                                    servers.map { server ->
                                        if (server.id == serverId) requestServerDeletion(server).copy(
                                            runtimeLogs = (server.runtimeLogs + stopRequestMessage()).takeLast(12),
                                        ) else server
                                    },
                                )
                                onServersChange(updatedServers)
                                syncServerProfilesToAuthorizedDirectoryNow(updatedServers)
                                scope.launch { snackbarHostState.showSnackbar("已停止并删除 ${targetServer.name}") }
                            } else {
                                val updatedServers = finalizePendingServerDeletion(servers.filterNot { it.id == serverId })
                                deleteManagedServerWorkspaceFromPrivateDirectory(appContext.filesDir.toPath(), serverId)
                                deleteManagedServerWorkspaceFromAuthorizedDirectory(appContext, serverDirectoryUriText, serverId)
                                onServersChange(updatedServers)
                                syncServerProfilesToAuthorizedDirectoryNow(updatedServers)
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
                            val inUseServers = servers.filter { it.selectedTunnelId == tunnelId && it.isRuntimeBusy() }
                            if (inUseServers.isNotEmpty()) {
                                inUseServers.forEach { runningServer ->
                                    PaperServerService.stop(appContext, runningServer.id, runningServer.runtimeSlot)
                                }
                                scope.launch {
                                    snackbarHostState.showSnackbar("该隧道仍被运行中的服务器使用，已先停止相关实例；待停止完成后再删除")
                                }
                                return@TunnelsScreen
                            }
                            val updatedTunnels = removeTunnelProfile(tunnels, tunnelId)
                            onTunnelsChangeAndPersist(updatedTunnels)
                            val updatedServers = detachDeletedTunnel(servers, tunnelId)
                            onServersChange(updatedServers)
                            syncServerProfilesToAuthorizedDirectoryNow(updatedServers)
                        },
                        modifier = Modifier.fillMaxSize(),
                        bottomContentPadding = bottomContentPadding,
                    )
                    McGoDestination.Settings -> SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        bottomContentPadding = bottomContentPadding,
                        appearancePreferences = appearancePreferences,
                        onAppearancePreferencesChange = onAppearancePreferencesChange,
                        javaManagementState = javaManagementState,
                        onDownloadJava = onDownloadJava,
                        onInstallJavaArchive = onInstallJavaArchive,
                        onDeleteJava = onDeleteJava,
                        serverDirectoryUri = serverDirectoryUriText,
                        settingsDestination = settingsDestination,
                        onSettingsDestinationChange = { settingsDestination = it },
                        onRequestServerDirectory = {
                            requestServerDirectory(PendingServerDirectoryAction.SettingsRequest)
                        },
                    )
                }
            }
        }
        activeEditingServer?.let { server ->
            EditPaperServerDialog(
                server = server,
                vanillaVersions = vanillaVersions,
                paperVersions = paperVersions,
                purpurVersions = purpurVersions,
                supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
                dynamicBackground = appearancePreferences.dynamicBackground,
                onDismiss = { editingServerId = null },
                onSave = { edited ->
                    val updatedServers = servers.map { existing -> if (existing.id == edited.id) edited else existing }
                    onServersChange(updatedServers)
                    syncServerProfilesToAuthorizedDirectoryNow(updatedServers)
                    editingServerId = null
                    scope.launch { snackbarHostState.showSnackbar("已更新 ${edited.name}") }
                },
            )
        }
    }
}

@Composable
private fun RequestRuntimePermissions() = Unit

@Composable
private fun FloatingGlassBottomMenu(
    destination: McGoDestination,
    bottomBarAlpha: Float,
    transparentCards: Boolean,
    onDestinationSelected: (McGoDestination) -> Unit,
) {
    val visuals = LocalMcGoVisualTokens.current
    val selectedContentColor = MaterialTheme.colorScheme.primary
    val unselectedContentColor = visuals.primaryTextColor.copy(alpha = 0.6f)
    val containerColor = if (transparentCards) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f * bottomBarAlpha)
    } else {
        visuals.cardContainerColor
    }
    val backdropBaseColor = if (transparentCards) {
        visuals.cardContainerColor
    } else {
        MaterialTheme.colorScheme.surface
    }
    val menuBackdropGradient = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            backdropBaseColor.copy(alpha = 0.04f * bottomBarAlpha),
            backdropBaseColor.copy(alpha = 0.18f * bottomBarAlpha),
            backdropBaseColor.copy(alpha = 0.32f * bottomBarAlpha),
        ),
        startY = 0f,
        endY = Float.POSITIVE_INFINITY,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(menuBackdropGradient),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            color = containerColor,
            contentColor = unselectedContentColor,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, visuals.cardStrokeColor.copy(alpha = 0.58f)),
            tonalElevation = 0.dp,
            shadowElevation = 24.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                McGoDestination.entries.forEach { item ->
                    val selected = destination == item
                    val contentColor = if (selected) selectedContentColor else unselectedContentColor
                    val interactionSource = remember(item) { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = selected,
                                onClick = { onDestinationSelected(item) },
                                role = Role.Tab,
                                interactionSource = interactionSource,
                                indication = null,
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .indication(
                                    interactionSource = interactionSource,
                                    indication = ripple(
                                        bounded = true,
                                        radius = 28.dp,
                                    ),
                                )
                                .background(
                                    color = if (selected) selectedContentColor.copy(alpha = 0.14f) else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = contentColor,
                            )
                        }
                        Text(
                            text = stringResource(item.labelRes),
                            color = contentColor,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun ServerDirectoryPermissionEffect(
    serverDirectoryUriText: String?,
    context: Context,
): Boolean = serverDirectoryUriText
    ?.let(Uri::parse)
    ?.let { uri ->
        context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission && permission.isWritePermission
        }
    } == true

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

@Composable
private fun EditPaperServerDialog(
    server: ServerCardState,
    vanillaVersions: List<String>,
    paperVersions: List<String>,
    purpurVersions: List<String>,
    supportedProvisionableJavaVersions: Set<Int>,
    dynamicBackground: Boolean,
    onDismiss: () -> Unit,
    onSave: (ServerCardState) -> Unit,
) {
    val baseVersionOptions: List<String> = remember(server.serverType, vanillaVersions, paperVersions, purpurVersions, supportedProvisionableJavaVersions) {
        when (server.serverType) {
            MinecraftServerType.Vanilla -> vanillaVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
            MinecraftServerType.Paper -> com.mcgo.app.server.resolveProvisionablePaperVersionOptions(
                versions = paperVersions,
                supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
            )
            MinecraftServerType.Purpur -> purpurVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
        }
    }
    val versionOptions: List<String> = remember(baseVersionOptions, server.minecraftVersion) {
        if (baseVersionOptions.contains(server.minecraftVersion)) baseVersionOptions else baseVersionOptions + server.minecraftVersion
    }
    var name by remember(server.id) { mutableStateOf(server.name) }
    var minecraftVersion by remember(server.id) { mutableStateOf(server.minecraftVersion) }
    var javaSelectionMode by remember(server.id) { mutableStateOf(server.javaSelectionMode) }
    var manualJavaMajorVersion by remember(server.id) { mutableStateOf(server.javaMajorVersion) }
    var maxPlayers by remember(server.id) { mutableStateOf(server.maxPlayers.toString()) }
    var memoryMb by remember(server.id) { mutableStateOf(server.memoryMb.toString()) }
    var port by remember(server.id) { mutableStateOf(server.defaultPort.toString()) }
    var worldName by remember(server.id) { mutableStateOf(server.worldName) }
    var gameMode by remember(server.id) { mutableStateOf(server.gameMode) }
    var difficulty by remember(server.id) { mutableStateOf(server.difficulty) }
    var onlineMode by remember(server.id) { mutableStateOf(server.onlineMode) }
    var pvpEnabled by remember(server.id) { mutableStateOf(server.pvpEnabled) }
    var serverPropertiesOverride by remember(server.id) { mutableStateOf(server.serverPropertiesOverride) }
    var overlayDestination by remember(server.id) { mutableStateOf(EditServerOverlayDestination.Form) }

    val recommendedJava = remember(minecraftVersion) { recommendedJavaMajorVersion(minecraftVersion) }
    LaunchedEffect(minecraftVersion, javaSelectionMode) {
        if (javaSelectionMode == JavaSelectionMode.Recommended) {
            manualJavaMajorVersion = recommendedJava
        }
    }

    val javaVersionOptions = remember(
        supportedProvisionableJavaVersions,
        server.javaMajorVersion,
        manualJavaMajorVersion,
        recommendedJava,
    ) {
        buildList {
            add(recommendedJava)
            add(server.javaMajorVersion)
            add(manualJavaMajorVersion)
            addAll(supportedProvisionableJavaVersions)
        }.distinct().sorted()
    }
    val resolvedMaxPlayers = maxPlayers.toIntOrNull()?.coerceIn(1, 200) ?: server.maxPlayers
    val resolvedMemoryMb = memoryMb.toIntOrNull()?.coerceAtLeast(512) ?: server.memoryMb
    val resolvedPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: server.defaultPort
    val resolvedJavaMajorVersion = if (javaSelectionMode == JavaSelectionMode.Recommended) recommendedJava else manualJavaMajorVersion
    val canSave = name.isNotBlank() && minecraftVersion.isNotBlank()

    fun buildDraftServer(): ServerCardState = applyPaperServerEdits(
        server = server,
        name = name,
        minecraftVersion = minecraftVersion,
        maxPlayers = resolvedMaxPlayers,
        memoryMb = resolvedMemoryMb,
        port = resolvedPort,
        worldName = worldName,
        javaMajorVersion = resolvedJavaMajorVersion,
        javaSelectionMode = javaSelectionMode,
        gameMode = gameMode,
        difficulty = difficulty,
        onlineMode = onlineMode,
        pvpEnabled = pvpEnabled,
        serverPropertiesOverride = sanitizeAdvancedServerPropertiesOverride(serverPropertiesOverride),
    )

    fun applyDraftToForm(editedServer: ServerCardState) {
        name = editedServer.name
        minecraftVersion = editedServer.minecraftVersion
        javaSelectionMode = editedServer.javaSelectionMode
        manualJavaMajorVersion = editedServer.javaMajorVersion
        maxPlayers = editedServer.maxPlayers.toString()
        memoryMb = editedServer.memoryMb.toString()
        port = editedServer.defaultPort.toString()
        worldName = editedServer.worldName
        gameMode = editedServer.gameMode
        difficulty = editedServer.difficulty
        onlineMode = editedServer.onlineMode
        pvpEnabled = editedServer.pvpEnabled
        serverPropertiesOverride = editedServer.serverPropertiesOverride
    }

    BackHandler(enabled = true, onBack = onDismiss)

    when (overlayDestination) {
        EditServerOverlayDestination.Properties -> {
            val draftServer = buildDraftServer()
            PaperServerPropertiesEditorDialog(
                server = draftServer,
                initialText = buildPaperServerPropertiesEditorText(draftServer),
                dynamicBackground = dynamicBackground,
                onDismiss = { overlayDestination = EditServerOverlayDestination.Form },
                onApply = { editedText ->
                    applyDraftToForm(parsePaperServerPropertiesEditorText(draftServer, editedText))
                    overlayDestination = EditServerOverlayDestination.Form
                },
            )
        }

        EditServerOverlayDestination.Form -> {
            EditFullScreenScaffold(
                title = "编辑 ${server.name}",
                subtitle = "",
                leadingIcon = Icons.Outlined.Tune,
                dynamicBackground = dynamicBackground,
                layoutMode = EditFullScreenScaffoldLayoutMode.InlineChrome,
                onDismiss = onDismiss,
                footer = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { overlayDestination = EditServerOverlayDestination.Properties },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("编辑 server.properties")
                        }
                        Button(
                            onClick = { onSave(buildDraftServer()) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canSave,
                        ) {
                            Text("保存配置")
                        }
                    }
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (server.isRuntimeBusy()) {
                        EditSettingsInfoCard(
                            icon = Icons.Outlined.Warning,
                            title = "当前运行中，只更新配置资料",
                            body = "服务器当前正在启动或运行；本次保存仅更新配置资料，不会强制改动当前运行中的端口与日志状态。",
                        )
                    }

                    EditSettingsSectionCard(title = "基础设置") {
                        EditTextSettingRow(
                            icon = Icons.Outlined.Edit,
                            label = "服务器名称",
                            value = name,
                            placeholder = "请输入名称",
                            onValueChange = { name = it },
                        )
                        EditSettingsDivider()
                        EditMenuSettingRow(
                            icon = Icons.Outlined.Dns,
                            label = "Minecraft 版本",
                            valueLabel = minecraftVersion,
                            options = versionOptions.asReversed(),
                            optionLabel = { it },
                            onSelect = { minecraftVersion = it },
                        )
                    }

                    EditSettingsSectionCard(title = "核心与性能") {
                        EditMenuSettingRow(
                            icon = Icons.Outlined.Settings,
                            label = "Java",
                            valueLabel = if (javaSelectionMode == JavaSelectionMode.Recommended) {
                                "自动"
                            } else {
                                "Java $manualJavaMajorVersion"
                            },
                            options = listOf<String>("自动") + javaVersionOptions.map { "Java $it" },
                            optionLabel = { it },
                            onSelect = { selected ->
                                if (selected == "自动") {
                                    javaSelectionMode = JavaSelectionMode.Recommended
                                    manualJavaMajorVersion = recommendedJava
                                } else {
                                    javaSelectionMode = JavaSelectionMode.Manual
                                    manualJavaMajorVersion = selected.removePrefix("Java ").toIntOrNull() ?: manualJavaMajorVersion
                                }
                            },
                        )
                        EditSettingsDivider()
                        EditTextSettingRow(
                            icon = Icons.Outlined.Speed,
                            label = "分配内存 MB",
                            value = memoryMb,
                            placeholder = server.memoryMb.toString(),
                            keyboardType = KeyboardType.Number,
                            onValueChange = { memoryMb = it.filter(Char::isDigit) },
                        )
                    }

                    EditSettingsSectionCard(title = "常用游戏规则") {
                        EditTextSettingRow(
                            icon = Icons.Outlined.Public,
                            label = "世界名称",
                            value = worldName,
                            placeholder = "world",
                            onValueChange = { worldName = it },
                        )
                        EditSettingsDivider()
                        EditMenuSettingRow(
                            icon = Icons.Outlined.Tune,
                            label = "游戏模式",
                            valueLabel = gameMode.displayLabel(),
                            options = PaperGameMode.entries,
                            optionLabel = { it.displayLabel() },
                            onSelect = { gameMode = it },
                        )
                        EditSettingsDivider()
                        EditMenuSettingRow(
                            icon = Icons.Outlined.Tune,
                            label = "难度",
                            valueLabel = difficulty.displayLabel(),
                            options = PaperDifficulty.entries,
                            optionLabel = { it.displayLabel() },
                            onSelect = { difficulty = it },
                        )
                        EditSettingsDivider()
                        EditSwitchSettingRow(
                            icon = Icons.Outlined.Public,
                            label = "正版验证",
                            supportingText = "关闭后可允许离线/外网玩家，安全风险更高，请谨慎使用",
                            supportingTextColor = MaterialTheme.colorScheme.error,
                            checked = onlineMode,
                            onCheckedChange = { onlineMode = it },
                        )
                        EditSettingsDivider()
                        EditSwitchSettingRow(
                            icon = Icons.Outlined.Tune,
                            label = "PvP",
                            checked = pvpEnabled,
                            onCheckedChange = { pvpEnabled = it },
                        )
                    }

                    EditSettingsSectionCard(title = "网络与高级") {
                        EditTextSettingRow(
                            icon = Icons.Outlined.Dns,
                            label = "最大玩家数",
                            value = maxPlayers,
                            placeholder = server.maxPlayers.toString(),
                            keyboardType = KeyboardType.Number,
                            onValueChange = { maxPlayers = it.filter(Char::isDigit) },
                        )
                        EditSettingsDivider()
                        EditTextSettingRow(
                            icon = Icons.Outlined.SwapHoriz,
                            label = "默认端口",
                            value = port,
                            placeholder = server.defaultPort.toString(),
                            keyboardType = KeyboardType.Number,
                            onValueChange = { port = it.filter(Char::isDigit) },
                        )
                    }
                }
            }
        }
    }
}

private enum class EditFullScreenScaffoldLayoutMode {
    PinnedChrome,
    InlineChrome,
}

private enum class EditServerOverlayDestination {
    Form,
    Properties,
}

@Composable
private fun PaperServerPropertiesEditorDialog(
    server: ServerCardState,
    initialText: String,
    dynamicBackground: Boolean,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var editorText by remember(server.id, initialText) { mutableStateOf(initialText) }
    val (propertiesBringIntoViewRequester, onPropertiesFocusChanged) = rememberImeBringIntoViewRequester()

    BackHandler(enabled = true, onBack = onDismiss)
    val colors = editPageColors()
    EditFullScreenScaffold(
        title = "编辑 server.properties",
        subtitle = "",
        leadingIcon = Icons.Outlined.Edit,
        dynamicBackground = dynamicBackground,
        layoutMode = EditFullScreenScaffoldLayoutMode.PinnedChrome,
        onDismiss = onDismiss,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = { onApply(editorText) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("应用并返回")
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                color = colors.editorContainerColor,
                contentColor = colors.primaryText,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, colors.cardStrokeColor),
            ) {
                BasicTextField(
                    value = editorText,
                    onValueChange = { editorText = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .bringIntoViewRequester(propertiesBringIntoViewRequester)
                        .onFocusEvent { onPropertiesFocusChanged(it.isFocused) }
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState()),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.Transparent,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primaryText),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (editorText.isBlank()) {
                                Text(
                                    text = "server.properties",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.secondaryText,
                                )
                            } else {
                                BasicText(
                                    text = buildServerPropertiesAnnotatedText(
                                        editorText,
                                        colors.primaryText,
                                        colors.secondaryText,
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }
    }
}

private data class EditPageColors(
    val backgroundOverlayColor: Color,
    val cardContainerColor: Color,
    val editorContainerColor: Color,
    val iconContainerColor: Color,
    val cardStrokeColor: Color,
    val dividerColor: Color,
    val primaryText: Color,
    val secondaryText: Color,
)

@Composable
private fun editPageColors(): EditPageColors {
    val visuals = LocalMcGoVisualTokens.current
    val scheme = MaterialTheme.colorScheme
    return EditPageColors(
        backgroundOverlayColor = scheme.background.copy(alpha = 0.52f),
        cardContainerColor = scheme.surface.copy(alpha = 0.92f),
        editorContainerColor = scheme.surfaceVariant.copy(alpha = 0.88f),
        iconContainerColor = scheme.primary.copy(alpha = 0.14f),
        cardStrokeColor = visuals.cardStrokeColor,
        dividerColor = scheme.outline.copy(alpha = 0.24f),
        primaryText = visuals.primaryTextColor,
        secondaryText = visuals.secondaryTextColor,
    )
}

@Composable
private fun EditFullScreenScaffold(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector,
    dynamicBackground: Boolean,
    layoutMode: EditFullScreenScaffoldLayoutMode = EditFullScreenScaffoldLayoutMode.PinnedChrome,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = editPageColors()
    val backgroundSpec = LocalMcGoVisualTokens.current.fluidBackgroundSpec
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = colors.primaryText,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FluidGradientBackground(
                spec = backgroundSpec,
                animate = dynamicBackground,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.backgroundOverlayColor),
            )
            EditOverlayInteractionBlocker()
            val density = LocalDensity.current
            var headerOverlayHeightPx by remember { mutableIntStateOf(0) }
            var footerOverlayHeightPx by remember { mutableIntStateOf(0) }
            val contentTopPadding = with(density) { headerOverlayHeightPx.toDp() }
            val footerBottomPadding = with(density) { footerOverlayHeightPx.toDp() }
            val headerOverlay: @Composable () -> Unit = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .onSizeChanged { headerOverlayHeightPx = it.height },
                    color = colors.cardContainerColor,
                    contentColor = colors.primaryText,
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, colors.cardStrokeColor),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditSettingsLeadingIcon(icon = leadingIcon)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.primaryText,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText,
                            )
                        }
                        Surface(
                            color = colors.iconContainerColor,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            val footerOverlay: @Composable () -> Unit = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .onSizeChanged { footerOverlayHeightPx = it.height },
                    color = colors.cardContainerColor,
                    contentColor = colors.primaryText,
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, colors.cardStrokeColor),
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        footer()
                    }
                }
            }

            if (layoutMode == EditFullScreenScaffoldLayoutMode.InlineChrome) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(top = contentTopPadding, start = 16.dp, end = 16.dp, bottom = footerBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    content()
                }
                headerOverlay()
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding(),
                ) {
                    footerOverlay()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(top = contentTopPadding, start = 16.dp, end = 16.dp, bottom = footerBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        content()
                    }
                }
                headerOverlay()
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding(),
                ) {
                    footerOverlay()
                }
            }
        }
    }
}

private fun buildServerPropertiesAnnotatedText(
    rawText: String,
    baseColor: Color,
    secondaryColor: Color,
    keyColor: Color,
    separatorColor: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder(rawText)
    builder.addStyle(
        SpanStyle(
            color = baseColor,
            fontFamily = FontFamily.Monospace,
        ),
        start = 0,
        end = rawText.length,
    )
    rawText.lineSequence().fold(0) { offset, line ->
        val lineEnd = offset + line.length
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> {
                builder.addStyle(SpanStyle(color = secondaryColor), offset, lineEnd)
            }
            '=' in line -> {
                val separatorIndex = line.indexOf('=')
                if (separatorIndex > 0) {
                    builder.addStyle(SpanStyle(color = keyColor), offset, offset + separatorIndex)
                    builder.addStyle(SpanStyle(color = separatorColor), offset + separatorIndex, offset + separatorIndex + 1)
                    if (separatorIndex + 1 < line.length) {
                        builder.addStyle(SpanStyle(color = baseColor.copy(alpha = 0.92f)), offset + separatorIndex + 1, lineEnd)
                    }
                }
            }
        }
        lineEnd + 1
    }
    return builder.toAnnotatedString()
}

@Composable
private fun EditOverlayInteractionBlocker() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    )
}

@Composable
private fun EditSettingsInfoCard(
    icon: ImageVector,
    title: String,
    body: String,
) {
    val colors = editPageColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.cardContainerColor,
        contentColor = colors.primaryText,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.cardStrokeColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditSettingsLeadingIcon(icon = icon)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primaryText,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                )
            }
        }
    }
}

@Composable
private fun EditSettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = editPageColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = colors.secondaryText,
        )
        Surface(
            color = colors.cardContainerColor,
            contentColor = colors.primaryText,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, colors.cardStrokeColor),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun EditSettingsLeadingIcon(icon: ImageVector) {
    val colors = editPageColors()
    Surface(
        color = colors.iconContainerColor,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(10.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EditSettingRowShell(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit,
) {
    val colors = editPageColors()
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditSettingsLeadingIcon(icon = icon)
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.primaryText,
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            trailingContent()
        }
    }
}

@Composable
private fun EditTextSettingRow(
    icon: ImageVector,
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    val colors = editPageColors()
    val (bringIntoViewRequester, onFocusChanged) = rememberImeBringIntoViewRequester()
    EditSettingRowShell(icon = icon, label = label) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusEvent { onFocusChanged(it.isFocused) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = colors.primaryText,
                textAlign = TextAlign.End,
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText,
                            textAlign = TextAlign.End,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun rememberImeBringIntoViewRequester(): Pair<BringIntoViewRequester, (Boolean) -> Unit> {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val onFocusChanged = remember(bringIntoViewRequester, scope) {
        { isFocused: Boolean ->
            if (isFocused) {
                scope.launch {
                    delay(120)
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }
    }
    return bringIntoViewRequester to onFocusChanged
}

@Composable
private fun <T> EditMenuSettingRow(
    icon: ImageVector,
    label: String,
    valueLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = editPageColors()
    var expanded by remember(label, valueLabel, options) { mutableStateOf(false) }

    Box {
        EditSettingRowShell(
            icon = icon,
            label = label,
            onClick = { expanded = true },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                    textAlign = TextAlign.End,
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = colors.secondaryText,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EditSwitchSettingRow(
    icon: ImageVector,
    label: String,
    supportingText: String? = null,
    supportingTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    EditSettingRowShell(
        icon = icon,
        label = label,
        onClick = { onCheckedChange(!checked) },
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
            supportingText?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = supportingTextColor,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun EditSettingsDivider() {
    val colors = editPageColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(colors.dividerColor),
    )
}

private fun PaperGameMode.displayLabel(): String = when (this) {
    PaperGameMode.Survival -> "生存"
    PaperGameMode.Creative -> "创造"
    PaperGameMode.Adventure -> "冒险"
    PaperGameMode.Spectator -> "旁观"
}

private fun PaperDifficulty.displayLabel(): String = when (this) {
    PaperDifficulty.Peaceful -> "和平"
    PaperDifficulty.Easy -> "简单"
    PaperDifficulty.Normal -> "普通"
    PaperDifficulty.Hard -> "困难"
}
