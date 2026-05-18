package com.mcgo.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.network.measureTcpLatency
import com.mcgo.app.network.parseTcpEndpoint
import com.mcgo.app.server.MaxPaperRuntimeSlots
import com.mcgo.app.server.PaperServerEvents
import com.mcgo.app.server.PaperServerService
import com.mcgo.app.server.activePaperRuntimeSlots
import com.mcgo.app.server.allocateRuntimeSlot
import com.mcgo.app.server.authorizedServerProfilesAvailable
import com.mcgo.app.server.managedPaperServerIconFile
import com.mcgo.app.server.deleteJavaRuntime
import com.mcgo.app.server.deleteManagedServerWorkspaceFromAuthorizedDirectory
import com.mcgo.app.server.hasAuthorizedManagedServerWorkspaceReady
import com.mcgo.app.server.deleteManagedServerWorkspaceFromPrivateDirectory
import com.mcgo.app.server.exportManagedServerWorldArchive
import com.mcgo.app.server.fallbackPaperVersions
import com.mcgo.app.server.fallbackPurpurVersions
import com.mcgo.app.server.fallbackVanillaVersions
import com.mcgo.app.server.fallbackFabricVersions
import com.mcgo.app.server.fetchFabricVersions
import com.mcgo.app.server.fetchForgeVersions
import com.mcgo.app.server.fetchNeoForgeVersions
import com.mcgo.app.server.fetchPaperVersions
import com.mcgo.app.server.fetchQuiltVersions
import com.mcgo.app.server.fetchProvisionableMinecraftVersions
import com.mcgo.app.server.fetchPurpurVersions
import com.mcgo.app.server.fetchVanillaVersions
import com.mcgo.app.server.filterProvisionablePaperVersions
import com.mcgo.app.server.importManagedServerModpackArchive
import com.mcgo.app.server.importManagedServerWorldArchive
import com.mcgo.app.server.isInstallerBootstrapScript
import com.mcgo.app.server.installManagedServerModFile
import com.mcgo.app.server.approveManagedServerSetupScript
import com.mcgo.app.server.findManagedServerSetupScript
import com.mcgo.app.server.detectImportedModpackServerMetadata
import com.mcgo.app.server.managedServerTargetJarPath
import com.mcgo.app.server.writeManagedServerPayloadSha
import com.mcgo.app.server.managedPaperServerLogFile
import com.mcgo.app.server.ManagedServerWorkspaceMode
import com.mcgo.app.server.discardManagedServerWorkspaceAfterForegroundAccess
import com.mcgo.app.server.prepareManagedServerWorkspaceAccess
import com.mcgo.app.server.prepareManagedServerWorkspaceForForegroundAccess
import com.mcgo.app.server.releaseManagedServerWorkspaceAfterForegroundAccess
import com.mcgo.app.server.resolveAuthorizedServersRootPath
import com.mcgo.app.server.resolveManagedServerWorkspaceDirectory
import com.mcgo.app.server.resolveNewModpackServerImportFailureRecovery
import com.mcgo.app.server.shouldSyncImportedModpackWorkspaceImmediately
import com.mcgo.app.server.migratePrivateServerDataToAuthorizedDirectory
import com.mcgo.app.server.reconcilePersistedRuntimeState
import com.mcgo.app.server.reducePaperRuntimeEvent
import com.mcgo.app.server.requiresManagedServerSetupApproval
import com.mcgo.app.server.restoreManagedServerWorkspaceFromAuthorizedDirectory
import com.mcgo.app.server.restoreManagedServerIconFromAuthorizedDirectory
import com.mcgo.app.server.restoreServerProfilesFromAuthorizedDirectory
import com.mcgo.app.server.scanInstalledJavaVersions
import com.mcgo.app.server.stopRequestMessage
import com.mcgo.app.server.syncManagedServerWorkspaceToAuthorizedDirectory
import com.mcgo.app.server.syncServerProfilesToAuthorizedDirectory
import com.mcgo.app.server.deleteManagedServerWorkspaceFromAuthorizedDirectory
import com.mcgo.app.status.DevicePerformanceMonitor
import com.mcgo.app.status.rememberStatusDashboardState

import com.mcgo.app.ui.components.FluidGradientBackground
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.AppearancePreferencesSaver
import com.mcgo.app.ui.model.JavaSelectionMode
import com.mcgo.app.ui.model.McGoPageChrome
import com.mcgo.app.ui.model.SettingsDestination
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.ThemeModePreference
import com.mcgo.app.ui.model.TunnelLatencyResult
import com.mcgo.app.ui.model.TunnelLaunchSelection
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.assignTunnelRemotePort
import com.mcgo.app.ui.model.applyTunnelLatencyResults
import com.mcgo.app.ui.model.MinecraftServerType
import com.mcgo.app.ui.model.MinecraftServerType.Paper

import com.mcgo.app.ui.model.canStartServerFromUi
import com.mcgo.app.ui.model.defaultJavaManagementState
import com.mcgo.app.ui.model.detachDeletedTunnel
import com.mcgo.app.ui.model.finalizePendingServerDeletion
import com.mcgo.app.ui.model.isManagedRuntimeProvisioningAvailable
import com.mcgo.app.ui.model.isRuntimeBusy
import com.mcgo.app.ui.model.markAwaitingManagedRuntimeInstall
import com.mcgo.app.ui.model.markLaunchFailed
import com.mcgo.app.ui.model.markModpackImportInProgress
import com.mcgo.app.ui.model.markModpackImportRecoveredAfterSyncFailure
import com.mcgo.app.ui.model.markUnsupportedManagedRuntime
import com.mcgo.app.ui.model.normalizeConsoleCommand
import com.mcgo.app.ui.model.removeTunnelProfile
import com.mcgo.app.ui.model.requestServerDeletion
import com.mcgo.app.ui.model.startWithTunnels
import com.mcgo.app.ui.model.stopServer
import com.mcgo.app.ui.model.upsertTunnelProfile
import com.mcgo.app.ui.model.usesTunnel
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

private const val ServerDirectoryGrantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

private data class PendingStartRequest(
    val serverId: String,
    val startupPort: Int,
    val tunnelSelections: List<TunnelLaunchSelection>,
)

private data class PendingManagedRuntimeStart(
    val request: PendingStartRequest,
    val javaMajorVersion: Int,
)

private data class PendingModpackSetupApproval(
    val request: PendingStartRequest,
    val serverName: String,
    val scriptName: String,
    val workspaceMode: ManagedServerWorkspaceMode,
    val containsInstallerBootstrap: Boolean = false,
)

private data class PendingCreateServerFromModpack(
    val server: ServerCardState,
    val archiveUri: Uri,
)

private sealed interface StartupUiState {
    data object Loading : StartupUiState

    data class Ready(
        val appearancePreferences: AppearancePreferences,
        val persistedServers: List<ServerCardState>,
        val reconciledPersistedServers: List<ServerCardState>,
        val persistedTunnels: List<TunnelProfile>,
        val activeRuntimeSlotsOnLaunch: Set<Int>,
        val persistedServerDirectoryUri: String?,
    ) : StartupUiState
}

@Composable
private fun MCGoStartupLoadingScreen(appearancePreferences: AppearancePreferences) {
    McGoTheme(appearancePreferences = appearancePreferences) {
        val visuals = LocalMcGoVisualTokens.current
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = visuals.primaryTextColor,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                FluidGradientBackground(
                    spec = visuals.fluidBackgroundSpec,
                    animate = appearancePreferences.dynamicBackground,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.padding(horizontal = 28.dp),
                        color = visuals.cardContainerColor,
                        contentColor = visuals.primaryTextColor,
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, visuals.cardStrokeColor),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.startup_loading_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.startup_loading_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = visuals.secondaryTextColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class PendingServerDirectoryAction {
    StartServer,
    OpenConsole,
    EditServer,
    SettingsRequest,
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
    val appearanceStore = remember(context) {
        AppearancePreferencesStore(context.filesDir.toPath().resolve("appearance_preferences.properties"))
    }
    val supportedProvisionableJavaVersions = remember {
        if (Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") setOf(8, 11, 17, 21, 25) else setOf(8, 11, 17, 21)
    }
    val startupUiState by produceState<StartupUiState>(initialValue = StartupUiState.Loading, appContext, serverStorePath) {
        value = withContext(Dispatchers.IO) {
            val appearancePreferences = appearanceStore.load()
            val persistedServerDirectoryUri = runtimePrefs.getString(ServerDirectoryUriKey, null)
            val activeRuntimeSlotsOnLaunch = activePaperRuntimeSlots(context)
            val authorizedProfilesAvailable = authorizedServerProfilesAvailable(context, persistedServerDirectoryUri)
            if (authorizedProfilesAvailable) {
                restoreServerProfilesFromAuthorizedDirectory(
                    context = context,
                    authorizedDirectoryUri = persistedServerDirectoryUri,
                    targetProfilesPath = serverStorePath,
                )
            }
            val persistedServers = serverStore.load().also { loadedServers ->
                if (authorizedProfilesAvailable) {
                    loadedServers.forEach { server ->
                        if (hasAuthorizedManagedServerWorkspaceReady(context, persistedServerDirectoryUri, server.id)) {
                            restoreManagedServerIconFromAuthorizedDirectory(
                                context = context,
                                authorizedDirectoryUri = persistedServerDirectoryUri,
                                serverId = server.id,
                                targetIconPath = managedPaperServerIconFile(context.filesDir.toPath(), server.id),
                            )
                        }
                    }
                }
                if (!authorizedProfilesAvailable && persistedServerDirectoryUri != null && loadedServers.isNotEmpty()) {
                    val migratedServerIds = migratePrivateServerDataToAuthorizedDirectory(
                        context = context,
                        authorizedDirectoryUri = persistedServerDirectoryUri,
                        filesDir = context.filesDir.toPath(),
                        serverIds = loadedServers.map { it.id },
                    )
                    loadedServers.filter { it.id in migratedServerIds }.forEach { server ->
                        deleteManagedServerWorkspaceFromPrivateDirectory(context.filesDir.toPath(), server.id)
                    }
                    syncServerProfilesToAuthorizedDirectory(
                        context = context,
                        authorizedDirectoryUri = persistedServerDirectoryUri,
                        sourceProfilesPath = serverStorePath,
                    )
                }
            }
            val reconciledPersistedServers = finalizePendingServerDeletion(
                reconcilePersistedRuntimeState(
                    servers = persistedServers,
                    activeRuntimeSlots = activeRuntimeSlotsOnLaunch,
                ).map { it.markUnsupportedManagedRuntime(supportedProvisionableJavaVersions) },
            )
            StartupUiState.Ready(
                appearancePreferences = appearancePreferences,
                persistedServers = persistedServers,
                reconciledPersistedServers = reconciledPersistedServers,
                persistedTunnels = tunnelStore.load(),
                activeRuntimeSlotsOnLaunch = activeRuntimeSlotsOnLaunch,
                persistedServerDirectoryUri = persistedServerDirectoryUri,
            )
        }
    }

    when (val state = startupUiState) {
        StartupUiState.Loading -> MCGoStartupLoadingScreen(appearancePreferences = AppearancePreferences())
        is StartupUiState.Ready -> {
            var appearancePreferences by rememberSaveable(stateSaver = AppearancePreferencesSaver) {
                mutableStateOf(state.appearancePreferences)
            }
            val persistedServerDirectoryUri = state.persistedServerDirectoryUri
            val persistedServers = state.persistedServers
            val reconciledPersistedServers = state.reconciledPersistedServers
            val activeRuntimeSlotsOnLaunch = state.activeRuntimeSlotsOnLaunch
            var servers by remember(serverStore, state.reconciledPersistedServers) {
                mutableStateOf(state.reconciledPersistedServers)
            }
            var tunnels by remember(tunnelStore, state.persistedTunnels) { mutableStateOf(state.persistedTunnels) }
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
            val fabricVersions by produceState(initialValue = fallbackFabricVersions()) {
                value = withContext(Dispatchers.IO) { fetchFabricVersions() }
            }
            val forgeVersions by produceState(initialValue = fallbackVanillaVersions()) {
                value = withContext(Dispatchers.IO) { fetchForgeVersions() }
            }
            val neoForgeVersions by produceState(initialValue = fallbackVanillaVersions()) {
                value = withContext(Dispatchers.IO) { fetchNeoForgeVersions() }
            }
            val quiltVersions by produceState(initialValue = fallbackVanillaVersions()) {
                value = withContext(Dispatchers.IO) { fetchQuiltVersions() }
            }

            McGoTheme(appearancePreferences = appearancePreferences) {
                MCGoAppScaffold(
                    appearancePreferences = appearancePreferences,
                    servers = servers,
                    tunnels = tunnels,
                    vanillaVersions = vanillaVersions,
                    paperVersions = paperVersions,
                    purpurVersions = purpurVersions,
                    fabricVersions = fabricVersions,
                    forgeVersions = forgeVersions,
                    neoForgeVersions = neoForgeVersions,
                    quiltVersions = quiltVersions,
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
    }
}

private fun restoreManagedManagedServerWorkspaceOnStartup(
    context: Context,
    authorizedDirectoryUri: String?,
    serverId: String,
) {
    val authorizedServersRoot = resolveAuthorizedServersRootPath(context, authorizedDirectoryUri)
    resolveManagedServerWorkspaceDirectory(
        filesDir = context.filesDir.toPath(),
        authorizedServersRoot = authorizedServersRoot,
        serverId = serverId,
    )
    prepareManagedServerWorkspaceForForegroundAccess(
        context = context,
        authorizedDirectoryUri = authorizedDirectoryUri,
        filesDir = context.filesDir.toPath(),
        serverId = serverId,
    )
}

@Composable
private fun MCGoAppScaffold(
    appearancePreferences: AppearancePreferences,
    servers: List<ServerCardState>,
    tunnels: List<TunnelProfile>,
    vanillaVersions: List<String>,
    paperVersions: List<String>,
    purpurVersions: List<String>,
    fabricVersions: List<String>,
    forgeVersions: List<String>,
    neoForgeVersions: List<String>,
    quiltVersions: List<String>,
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
    val statusDashboardState = rememberStatusDashboardState(
        appEntryElapsedRealtimeMillis = appEntryElapsedRealtimeMillis,
        statusMonitor = statusMonitor,
    )
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
    var pendingCreateServerFromModpack by remember { mutableStateOf<PendingCreateServerFromModpack?>(null) }
    var currentModpackImportServerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingStartRequest by remember { mutableStateOf<PendingStartRequest?>(null) }
    var pendingStartServerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingManagedRuntimeStarts by remember { mutableStateOf<List<PendingManagedRuntimeStart>>(emptyList()) }
    var pendingModpackSetupApproval by remember { mutableStateOf<PendingModpackSetupApproval?>(null) }
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
                    val failedPendings = pendingManagedRuntimeStarts.filter { it.javaMajorVersion == majorVersion }
                    if (failedPendings.isNotEmpty()) {
                        pendingManagedRuntimeStarts = pendingManagedRuntimeStarts.filterNot { it.javaMajorVersion == majorVersion }
                        val failedServerIds = failedPendings.map { it.request.serverId }.toSet()
                        val failedServers = latestServers.map { server ->
                            if (server.id in failedServerIds) {
                                server.markLaunchFailed(error.userFacingInstallMessage(majorVersion))
                            } else {
                                server
                            }
                        }
                        onServersChange(failedServers)
                        syncServerProfilesToAuthorizedDirectoryNow(failedServers)
                    }
                    snackbarHostState.showSnackbar(error.userFacingInstallMessage(majorVersion))
                }
            }
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
                        restoredServers.forEach { server ->
                            if (hasAuthorizedManagedServerWorkspaceReady(appContext, serverDirectoryUriText, server.id)) {
                                restoreManagedServerIconFromAuthorizedDirectory(
                                    context = appContext,
                                    authorizedDirectoryUri = serverDirectoryUriText,
                                    serverId = server.id,
                                    targetIconPath = managedPaperServerIconFile(appContext.filesDir.toPath(), server.id),
                                )
                            }
                        }
                    } else {
                        val migratedServerIds = migratePrivateServerDataToAuthorizedDirectory(
                            context = appContext,
                            authorizedDirectoryUri = serverDirectoryUriText,
                            filesDir = appContext.filesDir.toPath(),
                            serverIds = restoredServers.map { it.id },
                        )
                        restoredServers.filter { it.id in migratedServerIds }.forEach { server ->
                            deleteManagedServerWorkspaceFromPrivateDirectory(appContext.filesDir.toPath(), server.id)
                        }
                        if (migratedServerIds.size == restoredServers.size) {
                            snackbarHostState.showSnackbar("服务器目录已授权，现有服务器数据已同步到该目录")
                        } else {
                            snackbarHostState.showSnackbar("服务器目录已授权；部分服务器数据同步失败，已保留原本地副本，请稍后重试")
                        }
                    }
                    syncServerProfilesToAuthorizedDirectory(
                        context = appContext,
                        authorizedDirectoryUri = serverDirectoryUriText,
                        sourceProfilesPath = serverStorePath,
                    )
                    restoredServers
                }
                onServersChange(restoredServers)
                if (authorizedServerProfilesAvailable(appContext, serverDirectoryUriText)) {
                    snackbarHostState.showSnackbar("服务器目录已授权，已连接现有外部服务器数据")
                }
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
    fun <T> withPreparedManagedServerWorkspace(serverId: String, block: (Path) -> T): T {
        // syncManagedServerWorkspaceToAuthorizedDirectory( ... ) now goes through
        // releaseManagedServerWorkspaceAfterForegroundAccess(...) so source-contract
        // tests still see the explicit sync call path.
        val filesDir = appContext.filesDir.toPath()
        val workspaceAccess = prepareManagedServerWorkspaceAccess(
            context = appContext,
            authorizedDirectoryUri = serverDirectoryUriText,
            filesDir = filesDir,
            serverId = serverId,
        )
        val workspaceMode = workspaceAccess.mode
        val workDir = workspaceAccess.path
        var operationSucceeded = false
        return try {
            block(workDir).also {
                operationSucceeded = true
            }
        } finally {
            if (workspaceMode.shouldSyncBack && operationSucceeded) {
                check(
                    releaseManagedServerWorkspaceAfterForegroundAccess(
                        context = appContext,
                        authorizedDirectoryUri = serverDirectoryUriText,
                        filesDir = filesDir,
                        serverId = serverId,
                        workspaceMode = workspaceMode,
                    ),
                ) { "同步服务器目录到已授权位置失败" }
            }
        }
    }
    fun cleanupPreparedManagedServerWorkspace(serverId: String, workspaceMode: ManagedServerWorkspaceMode = ManagedServerWorkspaceMode.PrivateEphemeralMirror) {
        runCatching {
            check(
                releaseManagedServerWorkspaceAfterForegroundAccess(
                    context = appContext,
                    authorizedDirectoryUri = serverDirectoryUriText,
                    filesDir = appContext.filesDir.toPath(),
                    serverId = serverId,
                    workspaceMode = workspaceMode,
                ),
            ) { "清理临时服务器目录失败" }
        }.onFailure { cleanupError ->
            scope.launch { snackbarHostState.showSnackbar(cleanupError.message ?: "清理临时服务器目录失败") }
        }
    }
    fun startServerNow(request: PendingStartRequest) {
        scope.launch {
            val initialServers = latestServers
            val initialTargetServer = initialServers.firstOrNull { it.id == request.serverId }
            if (initialTargetServer == null) {
                snackbarHostState.showSnackbar("未找到服务器")
                return@launch
            }
            if (request.serverId in pendingStartServerIds) {
                snackbarHostState.showSnackbar("${initialTargetServer.name} 正在准备启动，请稍候")
                return@launch
            }
            if (!canStartServerFromUi(initialTargetServer)) {
                snackbarHostState.showSnackbar("${initialTargetServer.name} 已在启动或运行中")
                return@launch
            }
            pendingStartServerIds = pendingStartServerIds + request.serverId
            try {
                val filesDir = appContext.filesDir.toPath()
                val workspaceAccess = runCatching {
                    withContext(Dispatchers.IO) {
                        prepareManagedServerWorkspaceAccess(
                            context = appContext,
                            authorizedDirectoryUri = serverDirectoryUriText,
                            filesDir = filesDir,
                            serverId = request.serverId,
                        )
                    }
                }.getOrElse { error ->
                    snackbarHostState.showSnackbar(error.message ?: "准备服务器目录失败")
                    return@launch
                }
                val workspaceMode = workspaceAccess.mode
                val workDir = workspaceAccess.path
                suspend fun releasePreparedWorkspaceIfNeeded() {
                    try {
                        withContext(Dispatchers.IO) {
                            check(
                                discardManagedServerWorkspaceAfterForegroundAccess(
                                    context = appContext,
                                    authorizedDirectoryUri = serverDirectoryUriText,
                                    filesDir = filesDir,
                                    serverId = request.serverId,
                                    workspaceMode = workspaceMode,
                                ),
                            ) { "清理临时服务器目录失败" }
                        }
                    } catch (cleanupError: Throwable) {
                        snackbarHostState.showSnackbar(cleanupError.message ?: "清理临时服务器目录失败")
                    }
                }
                val currentServers = latestServers
                val targetServer = currentServers.firstOrNull { it.id == request.serverId }
                if (targetServer == null) {
                    try {
                        withContext(Dispatchers.IO) {
                            check(
                                discardManagedServerWorkspaceAfterForegroundAccess(
                                    context = appContext,
                                    authorizedDirectoryUri = serverDirectoryUriText,
                                    filesDir = filesDir,
                                    serverId = request.serverId,
                                    workspaceMode = workspaceMode,
                                ),
                            ) { "清理临时服务器目录失败" }
                        }
                    } catch (cleanupError: Throwable) {
                        snackbarHostState.showSnackbar(cleanupError.message ?: "清理临时服务器目录失败")
                    }
                    snackbarHostState.showSnackbar("未找到服务器")
                    return@launch
                }
                if (!canStartServerFromUi(targetServer)) {
                    try {
                        withContext(Dispatchers.IO) {
                            check(
                                discardManagedServerWorkspaceAfterForegroundAccess(
                                    context = appContext,
                                    authorizedDirectoryUri = serverDirectoryUriText,
                                    filesDir = filesDir,
                                    serverId = request.serverId,
                                    workspaceMode = workspaceMode,
                                ),
                            ) { "清理临时服务器目录失败" }
                        }
                    } catch (cleanupError: Throwable) {
                        snackbarHostState.showSnackbar(cleanupError.message ?: "清理临时服务器目录失败")
                    }
                    snackbarHostState.showSnackbar("${targetServer.name} 已在启动或运行中")
                    return@launch
                }
                val currentTunnels = latestTunnels
                val pendingSetupScript = requiresManagedServerSetupApproval(workDir)
                if (pendingSetupScript != null) {
                    pendingModpackSetupApproval = PendingModpackSetupApproval(
                        request = request,
                        serverName = targetServer.name,
                        scriptName = pendingSetupScript.fileName.toString(),
                        workspaceMode = workspaceMode,
                        containsInstallerBootstrap = isInstallerBootstrapScript(pendingSetupScript, workDir),
                    )
                    return@launch
                }
                val selectedTunnels = request.tunnelSelections.mapNotNull { selection ->
                    currentTunnels.firstOrNull { it.id == selection.tunnelId }?.let { tunnel -> selection to tunnel }
                }
                if (selectedTunnels.size != request.tunnelSelections.size) {
                    releasePreparedWorkspaceIfNeeded()
                    snackbarHostState.showSnackbar("部分隧道已不存在，请重新选择")
                    return@launch
                }
                val resolvedStartupPorts = selectedTunnels.map { (_, tunnel) ->
                    tunnel.resolveStartupPort(targetServer.defaultPort, request.startupPort)
                }.distinct()
                if (resolvedStartupPorts.size > 1) {
                    releasePreparedWorkspaceIfNeeded()
                    snackbarHostState.showSnackbar("所选隧道要求的本地端口不一致，请改为兼容的隧道组合")
                    return@launch
                }
                val resolvedPort = resolvedStartupPorts.singleOrNull() ?: request.startupPort
                val runtimeAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
                if (selectedTunnels.any { (_, tunnel) -> tunnel.kind != com.mcgo.app.ui.model.TunnelKind.Frp }) {
                    releasePreparedWorkspaceIfNeeded()
                    snackbarHostState.showSnackbar("当前仅支持 FRP 隧道真启动；请先取消非 FRP 隧道")
                    return@launch
                }
                if (selectedTunnels.isNotEmpty() && runtimeAbi != "arm64-v8a") {
                    releasePreparedWorkspaceIfNeeded()
                    snackbarHostState.showSnackbar("当前设备 ABI 为 $runtimeAbi，暂不支持内置 FRP 客户端")
                    return@launch
                }
                if (currentServers.any { it.id != request.serverId && it.isRuntimeBusy() && it.port == resolvedPort }) {
                    releasePreparedWorkspaceIfNeeded()
                    snackbarHostState.showSnackbar("端口 $resolvedPort 已被其他运行中的服务器占用")
                    return@launch
                }
                val allocatedSlot = allocateRuntimeSlot(
                    servers = currentServers,
                    targetServerId = request.serverId,
                    maxSlots = MaxPaperRuntimeSlots,
                ) ?: run {
                    releasePreparedWorkspaceIfNeeded()
                    snackbarHostState.showSnackbar("同时运行的服务器已达到上限（$MaxPaperRuntimeSlots）")
                    return@launch
                }
                if (targetServer.javaMajorVersion !in installedJavaVersions) {
                    if (isManagedRuntimeProvisioningAvailable(targetServer.javaMajorVersion, supportedProvisionableJavaVersions)) {
                        releasePreparedWorkspaceIfNeeded()
                        pendingManagedRuntimeStarts = pendingManagedRuntimeStarts + PendingManagedRuntimeStart(request, targetServer.javaMajorVersion)
                        val awaitingInstallServers = currentServers.map { server ->
                            if (server.id == request.serverId) {
                                server.markAwaitingManagedRuntimeInstall(targetServer.javaMajorVersion)
                            } else {
                                server
                            }
                        }
                        onServersChange(awaitingInstallServers)
                        syncServerProfilesToAuthorizedDirectoryNow(awaitingInstallServers)
                        onDownloadJava(targetServer.javaMajorVersion)
                        snackbarHostState.showSnackbar("未检测到 Java ${targetServer.javaMajorVersion}，已开始自动安装")
                        return@launch
                    }
                    releasePreparedWorkspaceIfNeeded()
                    val guidance = "当前版本暂不提供 Java ${targetServer.javaMajorVersion} 托管运行时；该 Minecraft 版本暂不支持一键开服"
                    val failedServers = currentServers.map { server ->
                        if (server.id == request.serverId) {
                            server.markLaunchFailed(guidance)
                        } else {
                            server
                        }
                    }
                    onServersChange(failedServers)
                    syncServerProfilesToAuthorizedDirectoryNow(failedServers)
                    snackbarHostState.showSnackbar("当前暂不支持该 Minecraft 版本所需的 Java ${targetServer.javaMajorVersion} 运行时")
                    return@launch
                }
                val selectedTunnelsWithPorts = runCatching {
                    selectedTunnels.map { (selection, tunnel) ->
                        tunnel.copy(
                            remotePort = assignTunnelRemotePort(
                                server = targetServer,
                                tunnel = tunnel,
                                requestedRemotePort = selection.remotePort,
                                servers = currentServers,
                            ),
                        )
                    }
                }.getOrElse { error ->
                    releasePreparedWorkspaceIfNeeded()
                    snackbarHostState.showSnackbar(error.message ?: "隧道远端端口分配失败")
                    return@launch
                }
                val runtimeLogPath = managedPaperServerLogFile(appContext.filesDir.toPath(), request.serverId).toString()
                val updatedServers = currentServers.map { server ->
                    if (server.id != request.serverId) {
                        server
                    } else {
                        server
                            .startWithTunnels(tunnels = selectedTunnelsWithPorts, startupPort = resolvedPort)
                            .copy(
                                runtimeLogPath = runtimeLogPath,
                                runtimeSlot = allocatedSlot,
                            )
                            .withLaunchProgress(8, "已提交启动任务，准备使用内置 HotSpot 运行")
                    }
                }
                onServersChange(updatedServers)
                syncServerProfilesToAuthorizedDirectoryNow(updatedServers)
                updatedServers.firstOrNull { it.id == request.serverId }?.let {
                    PaperServerService.start(
                        appContext,
                        it,
                        selectedTunnelsWithPorts,
                        workspacePath = workDir.toString(),
                        workspaceMode = workspaceMode,
                    )
                }
                snackbarHostState.showSnackbar(
                    if (selectedTunnelsWithPorts.isNotEmpty()) {
                        "${targetServer.name} 已通过 ${selectedTunnelsWithPorts.joinToString("、") { it.name }} 开始启动"
                    } else {
                        "${targetServer.name} 开始启动"
                    },
                )
            } finally {
                pendingStartServerIds = pendingStartServerIds - request.serverId
            }
        }
    }
    val queuedStartRequest = pendingStartRequest
    if (queuedStartRequest != null && hasServerDirectoryGrant()) {
        pendingStartRequest = null
        startServerNow(queuedStartRequest)
    }
    LaunchedEffect(installedJavaVersions, pendingManagedRuntimeStarts) {
        val completedPendings = pendingManagedRuntimeStarts.filter { it.javaMajorVersion in installedJavaVersions }
        if (completedPendings.isNotEmpty()) {
            pendingManagedRuntimeStarts = pendingManagedRuntimeStarts.filterNot { it.javaMajorVersion in installedJavaVersions }
            completedPendings.forEach { completedPending ->
                pendingStartRequest = completedPending.request
            }
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
                pendingModpackSetupApproval?.let { pendingApproval ->
                    AlertDialog(
                        onDismissRequest = {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        check(
                                            discardManagedServerWorkspaceAfterForegroundAccess(
                                                context = appContext,
                                                authorizedDirectoryUri = serverDirectoryUriText,
                                                filesDir = appContext.filesDir.toPath(),
                                                serverId = pendingApproval.request.serverId,
                                                workspaceMode = pendingApproval.workspaceMode,
                                            ),
                                        ) { "清理临时服务器目录失败" }
                                    }
                                } catch (cleanupError: Throwable) {
                                    snackbarHostState.showSnackbar(cleanupError.message ?: "清理临时服务器目录失败")
                                } finally {
                                    pendingModpackSetupApproval = null
                                }
                            }
                        },
                        title = { Text("执行整合包安装脚本？") },
                        text = {
                            Text("${pendingApproval.serverName} 检测到整合包安装脚本 ${pendingApproval.scriptName}。确认后立即执行安装脚本，并继续启动服务器。")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                val filesDir = appContext.filesDir.toPath()
                                                val workspaceAccess = prepareManagedServerWorkspaceAccess(
                                                    context = appContext,
                                                    authorizedDirectoryUri = serverDirectoryUriText,
                                                    filesDir = filesDir,
                                                    serverId = pendingApproval.request.serverId,
                                                )
                                                approveManagedServerSetupScript(workspaceAccess.path)
                                                if (
                                                    workspaceAccess.mode.shouldSyncBack &&
                                                    shouldSyncImportedModpackWorkspaceImmediately(
                                                        workspaceMode = workspaceAccess.mode,
                                                        containsInstallerBootstrap = pendingApproval.containsInstallerBootstrap,
                                                    )
                                                ) {
                                                    check(
                                                        releaseManagedServerWorkspaceAfterForegroundAccess(
                                                            context = appContext,
                                                            authorizedDirectoryUri = serverDirectoryUriText,
                                                            filesDir = filesDir,
                                                            serverId = pendingApproval.request.serverId,
                                                            workspaceMode = workspaceAccess.mode,
                                                        ),
                                                    ) { "确认整合包安装脚本后同步服务器目录失败" }
                                                }
                                            }
                                        }.onSuccess {
                                            pendingModpackSetupApproval = null
                                            snackbarHostState.showSnackbar("已确认安装脚本并继续启动")
                                            startServerNow(pendingApproval.request)
                                        }.onFailure {
                                            snackbarHostState.showSnackbar(it.message ?: "确认整合包安装脚本失败")
                                        }
                                    }
                                },
                            ) {
                                Text("确认安装并启动")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            check(
                                                discardManagedServerWorkspaceAfterForegroundAccess(
                                                    context = appContext,
                                                    authorizedDirectoryUri = serverDirectoryUriText,
                                                    filesDir = appContext.filesDir.toPath(),
                                                    serverId = pendingApproval.request.serverId,
                                                    workspaceMode = pendingApproval.workspaceMode,
                                                ),
                                            ) { "清理临时服务器目录失败" }
                                        }
                                    } catch (cleanupError: Throwable) {
                                        snackbarHostState.showSnackbar(cleanupError.message ?: "清理临时服务器目录失败")
                                    } finally {
                                        pendingModpackSetupApproval = null
                                    }
                                }
                            }) {
                                Text("取消")
                            }
                        },
                    )
                }
                AnimatedContent(targetState = destination, label = "appDestination") { animatedDestination ->
                    when (animatedDestination) {
                    McGoDestination.Status -> StatusScreen(
                        dashboardState = statusDashboardState,
                        modifier = Modifier.fillMaxSize(),
                        bottomContentPadding = bottomContentPadding,
                    )
                    McGoDestination.Servers -> ServersScreen(
                        servers = servers,
                        availableTunnels = tunnels,
                        vanillaVersions = vanillaVersions,
                        paperVersions = paperVersions,
                        purpurVersions = purpurVersions,
                        fabricVersions = fabricVersions,
                        forgeVersions = forgeVersions,
                        neoForgeVersions = neoForgeVersions,
                        quiltVersions = quiltVersions,
                        serverDirectoryUri = serverDirectoryUriText,
                        currentModpackImportServerIds = currentModpackImportServerIds,
                        dynamicBackground = appearancePreferences.dynamicBackground,
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
                        onCreateServerFromModpack = { server, archiveUri ->
                            pendingCreateServerFromModpack = PendingCreateServerFromModpack(server, archiveUri)
                            currentModpackImportServerIds = currentModpackImportServerIds + server.id
                            scope.launch {
                                val currentServers = latestServers
                                val provisionalServers = currentServers + server
                                    .markUnsupportedManagedRuntime(supportedProvisionableJavaVersions)
                                    .markModpackImportInProgress(3, "正在准备导入整合包")
                                onServersChange(provisionalServers)
                                syncServerProfilesToAuthorizedDirectoryNow(provisionalServers)
                                var importCompleted = false
                                var importedWorkspaceMode = ManagedServerWorkspaceMode.PrivateEphemeralMirror
                                var recoveredImportedServer: ServerCardState? = null
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val tempPack = Files.createTempFile("mcgo-modpack-", ".zip")
                                        appContext.contentResolver.openInputStream(archiveUri)?.use { input ->
                                            Files.newOutputStream(tempPack).use { output -> input.copyTo(output) }
                                        } ?: error("无法读取整合包文件")
                                        try {
                                            val filesDir = appContext.filesDir.toPath()
                                            val updateImportProgress = { progress: Int, message: String ->
                                                val updatedServers = latestServers.map { existing ->
                                                    if (existing.id == server.id) {
                                                        existing.markModpackImportInProgress(progress, message)
                                                    } else {
                                                        existing
                                                    }
                                                }
                                                onServersChange(updatedServers)
                                                syncServerProfilesToAuthorizedDirectoryNow(updatedServers)
                                            }
                                            updateImportProgress(8, "正在读取整合包文件")
                                            val workspaceAccess = prepareManagedServerWorkspaceAccess(
                                                context = appContext,
                                                authorizedDirectoryUri = serverDirectoryUriText,
                                                filesDir = filesDir,
                                                serverId = server.id,
                                            )
                                            updateImportProgress(16, "正在准备整合包目标目录")
                                            importedWorkspaceMode = workspaceAccess.mode
                                            val workDir = workspaceAccess.path
                                            var operationSucceeded = false
                                            try {
                                                importManagedServerModpackArchive(
                                                    archiveFile = tempPack,
                                                    serverWorkDir = workDir,
                                                    onProgress = { progress, message ->
                                                        val mapped = 20 + ((progress.coerceIn(0, 100) * 55) / 100)
                                                        updateImportProgress(mapped, message)
                                                    },
                                                )
                                                importCompleted = true
                                                updateImportProgress(82, "正在识别整合包元数据")
                                                val metadata = detectImportedModpackServerMetadata(workDir)
                                                val detectedTargetJar = managedServerTargetJarPath(
                                                    serverWorkDir = workDir,
                                                    serverTypeName = metadata.serverType.name,
                                                    minecraftVersion = metadata.minecraftVersion,
                                                )
                                                writeManagedServerPayloadSha(workDir, detectedTargetJar)
                                                val setupScriptName = findManagedServerSetupScript(workDir)?.fileName?.toString()
                                                val updatedServer = server.copy(
                                                    edition = "${metadata.serverType.label} ${metadata.minecraftVersion}",
                                                    serverType = metadata.serverType,
                                                    minecraftVersion = metadata.minecraftVersion,
                                                    javaMajorVersion = metadata.javaMajorVersion,
                                                    javaSelectionMode = JavaSelectionMode.Recommended,
                                                ).markUnsupportedManagedRuntime(supportedProvisionableJavaVersions)
                                                updateImportProgress(90, "正在写入整合包识别结果")
                                                recoveredImportedServer = updatedServer
                                                operationSucceeded = true
                                                val shouldSyncImportedWorkspaceImmediately = shouldSyncImportedModpackWorkspaceImmediately(
                                                    workspaceMode = workspaceAccess.mode,
                                                    containsInstallerBootstrap = setupScriptName != null,
                                                )
                                                if (workspaceAccess.mode.shouldSyncBack && shouldSyncImportedWorkspaceImmediately) {
                                                    updateImportProgress(96, "正在同步整合包到已授权目录")
                                                    check(
                                                        releaseManagedServerWorkspaceAfterForegroundAccess(
                                                            context = appContext,
                                                            authorizedDirectoryUri = serverDirectoryUriText,
                                                            filesDir = filesDir,
                                                            serverId = server.id,
                                                            workspaceMode = workspaceAccess.mode,
                                                        ),
                                                    ) { "同步服务器目录到已授权位置失败" }
                                                }
                                                updateImportProgress(100, "整合包导入完成")
                                                Pair(updatedServer, setupScriptName)
                                            } finally {
                                                if (!operationSucceeded && workspaceAccess.mode.shouldSyncBack && !importCompleted) {
                                                    deleteManagedServerWorkspaceFromPrivateDirectory(filesDir, server.id)
                                                }
                                            }
                                        } finally {
                                            Files.deleteIfExists(tempPack)
                                        }
                                    }
                                }.onSuccess { (updatedServer, setupScriptName) ->
                                    val updatedServers = latestServers.filterNot { it.id == server.id } + updatedServer
                                    onServersChange(updatedServers)
                                    syncServerProfilesToAuthorizedDirectoryNow(updatedServers)
                                    showServerComposer = false
                                    pendingCreateServerFromModpack = null
                                    currentModpackImportServerIds = currentModpackImportServerIds - server.id
                                    val suffix = if (setupScriptName != null) "；整合包包含安装脚本 ${setupScriptName}，请先确认执行整合包安装脚本后再启动" else ""
                                    snackbarHostState.showSnackbar("已导入整合包并创建 ${updatedServer.name}${suffix}")
                                }.onFailure {
                                    val errorMessage = it.message ?: "未知错误"
                                    val recovery = resolveNewModpackServerImportFailureRecovery(
                                        workspaceMode = importedWorkspaceMode,
                                        importCompleted = importCompleted,
                                    )
                                    val recoveredServers = if (recovery.keepServerEntry) {
                                        val recoveredServer = (recoveredImportedServer ?: latestServers.firstOrNull { existing -> existing.id == server.id } ?: server)
                                            .markModpackImportRecoveredAfterSyncFailure(errorMessage)
                                        latestServers.filterNot { existing -> existing.id == server.id } + recoveredServer
                                    } else {
                                        latestServers.filterNot { existing -> existing.id == server.id }
                                    }
                                    onServersChange(recoveredServers)
                                    syncServerProfilesToAuthorizedDirectoryNow(recoveredServers)
                                    if (recovery.deletePrivateWorkspace) {
                                        deleteManagedServerWorkspaceFromPrivateDirectory(appContext.filesDir.toPath(), server.id)
                                    }
                                    if (recovery.deleteAuthorizedWorkspace) {
                                        deleteManagedServerWorkspaceFromAuthorizedDirectory(appContext, serverDirectoryUriText, server.id)
                                    }
                                    pendingCreateServerFromModpack = null
                                    currentModpackImportServerIds = currentModpackImportServerIds - server.id
                                    snackbarHostState.showSnackbar("导入整合包失败：$errorMessage")
                                }
                            }
                        },
                        onImportWorldArchive = { serverId, archiveUri ->
                            val targetServer = servers.firstOrNull { it.id == serverId } ?: return@ServersScreen
                            if (targetServer.isRuntimeBusy()) {
                                scope.launch { snackbarHostState.showSnackbar("请先停止 ${targetServer.name}，再导入存档") }
                                return@ServersScreen
                            }
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        withPreparedManagedServerWorkspace(targetServer.id) { workDir ->
                                            importManagedServerWorldArchive(
                                                context = appContext,
                                                archiveUri = archiveUri,
                                                targetWorldDir = workDir.resolve(targetServer.worldName),
                                            )
                                        }
                                    }
                                }.onSuccess {
                                    snackbarHostState.showSnackbar("已导入 ${targetServer.name} 的存档")
                                }.onFailure {
                                    snackbarHostState.showSnackbar("导入存档失败：${it.message ?: "未知错误"}")
                                }
                            }
                        },
                        onExportWorldArchive = { serverId, archiveUri ->
                            val targetServer = servers.firstOrNull { it.id == serverId } ?: return@ServersScreen
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        withPreparedManagedServerWorkspace(targetServer.id) { workDir ->
                                            exportManagedServerWorldArchive(
                                                context = appContext,
                                                sourceWorldDir = workDir.resolve(targetServer.worldName),
                                                targetUri = archiveUri,
                                            )
                                        }
                                    }
                                }.onSuccess {
                                    snackbarHostState.showSnackbar("已导出 ${targetServer.name} 的存档")
                                }.onFailure {
                                    snackbarHostState.showSnackbar("导出存档失败：${it.message ?: "未知错误"}")
                                }
                            }
                        },
                        onImportModFile = { serverId, modUri ->
                            val targetServer = servers.firstOrNull { it.id == serverId } ?: return@ServersScreen
                            if (targetServer.serverType != MinecraftServerType.Fabric &&
                                targetServer.serverType != MinecraftServerType.Forge &&
                                targetServer.serverType != MinecraftServerType.NeoForge &&
                                targetServer.serverType != MinecraftServerType.Quilt) {
                                scope.launch { snackbarHostState.showSnackbar("当前只有 Fabric / Forge / NeoForge / Quilt 服务器支持安装模组") }
                                return@ServersScreen
                            }
                            if (targetServer.isRuntimeBusy()) {
                                scope.launch { snackbarHostState.showSnackbar("请先停止 ${targetServer.name}，再安装模组") }
                                return@ServersScreen
                            }
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val displayName = modUri.displayName(appContext).ifBlank { "mod.jar" }
                                        require(displayName.endsWith(".jar", ignoreCase = true)) { "请选择 .jar 模组文件" }
                                        val tempMod = Files.createTempFile("mcgo-mod-", ".jar")
                                        appContext.contentResolver.openInputStream(modUri)?.use { input ->
                                            Files.newOutputStream(tempMod).use { output -> input.copyTo(output) }
                                        } ?: error("无法读取模组文件")
                                        try {
                                            withPreparedManagedServerWorkspace(targetServer.id) { workDir ->
                                                installManagedServerModFile(
                                                    sourceFile = tempMod,
                                                    serverWorkDir = workDir,
                                                    targetFileName = displayName,
                                                )
                                            }
                                        } finally {
                                            Files.deleteIfExists(tempMod)
                                        }
                                    }
                                }.onSuccess {
                                    snackbarHostState.showSnackbar("已为 ${targetServer.name} 安装模组")
                                }.onFailure {
                                    snackbarHostState.showSnackbar("安装模组失败：${it.message ?: "未知错误"}")
                                }
                            }
                        },
                        onStartServer = { serverId, startupPort, tunnelSelections ->
                            if (!hasServerDirectoryGrant()) {
                                pendingStartRequest = PendingStartRequest(serverId, startupPort, tunnelSelections)
                                requestServerDirectory(PendingServerDirectoryAction.StartServer)
                            } else {
                                startServerNow(PendingStartRequest(serverId, startupPort, tunnelSelections))
                            }
                        },
                        onStopServer = { serverId ->
                            pendingManagedRuntimeStarts = pendingManagedRuntimeStarts.filterNot { it.request.serverId == serverId }
                            pendingStartRequest = pendingStartRequest?.takeUnless { it.serverId == serverId }
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
                            pendingManagedRuntimeStarts = pendingManagedRuntimeStarts.filterNot { it.request.serverId == serverId }
                            pendingStartRequest = pendingStartRequest?.takeUnless { it.serverId == serverId }
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
                            val inUseServers = servers.filter { it.usesTunnel(tunnelId) && it.isRuntimeBusy() }
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
                        onExportLogs = {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { exportDebugLogs(appContext) } }
                                    .onSuccess { shareIntent -> appContext.startActivity(shareIntent) }
                                    .onFailure { snackbarHostState.showSnackbar("提取日志失败：${it.message ?: "未知错误"}") }
                            }
                        },
                    )
                }
                }
            }
        }
        AnimatedVisibility(
            visible = activeEditingServer != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            label = "editServerOverlay",
        ) {
            activeEditingServer?.let { server ->
                EditPaperServerDialog(
                    server = server,
                    vanillaVersions = vanillaVersions,
                    paperVersions = paperVersions,
                    purpurVersions = purpurVersions,
                    fabricVersions = fabricVersions,
                    forgeVersions = forgeVersions,
                    neoForgeVersions = neoForgeVersions,
                    quiltVersions = quiltVersions,
                    supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
                    dynamicBackground = appearancePreferences.dynamicBackground,
                    serverDirectoryUri = serverDirectoryUriText,
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
}

@Composable
private fun RequestRuntimePermissions() = Unit

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
