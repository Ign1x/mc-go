package com.mcgo.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material.icons.outlined.Remove
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
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
import com.mcgo.app.server.deleteManagedServerIconFromAuthorizedDirectory
import com.mcgo.app.server.syncManagedServerIconToAuthorizedDirectory
import com.mcgo.app.server.managedPaperServerIconFile
import com.mcgo.app.server.deleteJavaRuntime
import com.mcgo.app.server.deleteManagedServerWorkspaceFromAuthorizedDirectory
import com.mcgo.app.server.deleteManagedServerWorkspaceFromPrivateDirectory
import com.mcgo.app.server.extractTarXzSafely
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
import com.mcgo.app.server.deleteManagedServerIcon
import com.mcgo.app.server.writeManagedServerIcon
import com.mcgo.app.server.fetchProvisionableMinecraftVersions
import com.mcgo.app.server.fetchPurpurVersions
import com.mcgo.app.server.fetchVanillaVersions
import com.mcgo.app.server.filterProvisionablePaperVersions
import com.mcgo.app.server.installManagedServerModFile
import com.mcgo.app.server.installPojavRuntimeFromApk
import com.mcgo.app.server.importManagedServerWorldArchive
import com.mcgo.app.server.approveManagedServerSetupScript
import com.mcgo.app.server.findManagedServerSetupScript
import com.mcgo.app.server.importManagedServerModpackArchive
import com.mcgo.app.server.installRuntimeFromTarXz
import com.mcgo.app.server.installRuntimeWithStaging
import com.mcgo.app.server.javaRuntimeArchiveTempSuffix
import com.mcgo.app.server.managedPaperServerLogFile
import com.mcgo.app.server.migratePrivateServerDataToAuthorizedDirectory
import com.mcgo.app.server.reconcilePersistedRuntimeState
import com.mcgo.app.server.reducePaperRuntimeEvent
import com.mcgo.app.server.requiresManagedServerSetupApproval
import com.mcgo.app.server.resolvePojavRuntimeComponent
import com.mcgo.app.server.restoreManagedServerWorkspaceFromAuthorizedDirectory
import com.mcgo.app.server.restoreManagedServerIconFromAuthorizedDirectory
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
import com.mcgo.app.status.rememberStatusDashboardState

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
import com.mcgo.app.ui.model.TunnelLaunchSelection
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
import com.mcgo.app.ui.model.markAwaitingManagedRuntimeInstall
import com.mcgo.app.ui.model.markLaunchFailed
import com.mcgo.app.ui.model.markUnsupportedManagedRuntime
import com.mcgo.app.ui.model.normalizeConsoleCommand
import com.mcgo.app.ui.model.parsePaperServerPropertiesEditorText
import com.mcgo.app.ui.model.sanitizeAdvancedServerPropertiesOverride
import com.mcgo.app.ui.model.removeTunnelProfile
import com.mcgo.app.ui.model.recommendedJavaMajorVersion
import com.mcgo.app.ui.model.requestServerDeletion
import com.mcgo.app.ui.model.resolveServerConsoleText
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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.cert.X509Certificate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.jar.JarFile
import kotlin.math.roundToInt

private const val RuntimePrefsName = "mcgo_runtime_permissions"
private const val ServerDirectoryUriKey = "server_directory_uri"
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
)

sealed interface PendingServerIconChange {
    data object Unchanged : PendingServerIconChange
    data object Remove : PendingServerIconChange
    data class Replace(val pngBytes: ByteArray) : PendingServerIconChange
}

private data class PendingServerIconCrop(
    val sourceUri: Uri,
    val previewBitmap: Bitmap,
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
                    loadedServers.filterNot { it.runtimeSlot in activeRuntimeSlotsOnLaunch }.forEach { server ->
                        restoreManagedManagedServerWorkspaceOnStartup(
                            context = context,
                            authorizedDirectoryUri = persistedServerDirectoryUri,
                            serverId = server.id,
                        )
                        restoreManagedServerIconFromAuthorizedDirectory(
                            context = context,
                            authorizedDirectoryUri = persistedServerDirectoryUri,
                            serverId = server.id,
                            targetIconPath = managedPaperServerIconFile(context.filesDir.toPath(), server.id),
                        )
                    }
                }
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
    restoreManagedServerWorkspaceFromAuthorizedDirectory(
        context = context,
        authorizedDirectoryUri = authorizedDirectoryUri,
        serverId = serverId,
        targetWorkspaceDir = com.mcgo.app.server.managedPaperServerDirectory(context.filesDir.toPath(), serverId),
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
    var pendingStartRequest by remember { mutableStateOf<PendingStartRequest?>(null) }
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
                        restoredServers.filterNot { it.isRuntimeBusy() }.forEach { server ->
                            restoreManagedServerWorkspaceFromAuthorizedDirectory(
                                context = appContext,
                                authorizedDirectoryUri = serverDirectoryUriText,
                                serverId = server.id,
                                targetWorkspaceDir = com.mcgo.app.server.managedPaperServerDirectory(appContext.filesDir.toPath(), server.id),
                            )
                            restoreManagedServerIconFromAuthorizedDirectory(
                                context = appContext,
                                authorizedDirectoryUri = serverDirectoryUriText,
                                serverId = server.id,
                                targetIconPath = managedPaperServerIconFile(appContext.filesDir.toPath(), server.id),
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
        val currentServers = latestServers
        val targetServer = currentServers.firstOrNull { it.id == request.serverId }
        if (targetServer == null) {
            scope.launch { snackbarHostState.showSnackbar("未找到服务器") }
            return
        }
        if (!canStartServerFromUi(targetServer)) {
            scope.launch { snackbarHostState.showSnackbar("${targetServer.name} 已在启动或运行中") }
            return
        }
        val workDir = com.mcgo.app.server.managedPaperServerDirectory(appContext.filesDir.toPath(), request.serverId)
        val pendingSetupScript = requiresManagedServerSetupApproval(workDir)
        if (pendingSetupScript != null) {
            pendingModpackSetupApproval = PendingModpackSetupApproval(
                request = request,
                serverName = targetServer.name,
                scriptName = pendingSetupScript.fileName.toString(),
            )
            return
        }
        val selectedTunnels = request.tunnelSelections.mapNotNull { selection ->
            tunnels.firstOrNull { it.id == selection.tunnelId }?.let { tunnel -> selection to tunnel }
        }
        if (selectedTunnels.size != request.tunnelSelections.size) {
            scope.launch { snackbarHostState.showSnackbar("部分隧道已不存在，请重新选择") }
            return
        }
        val resolvedStartupPorts = selectedTunnels.map { (_, tunnel) ->
            tunnel.resolveStartupPort(targetServer.defaultPort, request.startupPort)
        }.distinct()
        if (resolvedStartupPorts.size > 1) {
            scope.launch { snackbarHostState.showSnackbar("所选隧道要求的本地端口不一致，请改为兼容的隧道组合") }
            return
        }
        val resolvedPort = resolvedStartupPorts.singleOrNull() ?: request.startupPort
        val runtimeAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        if (selectedTunnels.any { (_, tunnel) -> tunnel.kind != com.mcgo.app.ui.model.TunnelKind.Frp }) {
            scope.launch { snackbarHostState.showSnackbar("当前仅支持 FRP 隧道真启动；请先取消非 FRP 隧道") }
            return
        }
        if (selectedTunnels.isNotEmpty() && runtimeAbi != "arm64-v8a") {
            scope.launch { snackbarHostState.showSnackbar("当前设备 ABI 为 $runtimeAbi，暂不支持内置 FRP 客户端") }
            return
        }
        if (currentServers.any { it.id != request.serverId && it.isRuntimeBusy() && it.port == resolvedPort }) {
            scope.launch { snackbarHostState.showSnackbar("端口 $resolvedPort 已被其他运行中的服务器占用") }
            return
        }
        val allocatedSlot = allocateRuntimeSlot(
            servers = currentServers,
            targetServerId = request.serverId,
            maxSlots = MaxPaperRuntimeSlots,
        ) ?: run {
            scope.launch { snackbarHostState.showSnackbar("同时运行的服务器已达到上限（$MaxPaperRuntimeSlots）") }
            return
        }
        if (targetServer.javaMajorVersion !in installedJavaVersions) {
            if (isManagedRuntimeProvisioningAvailable(targetServer.javaMajorVersion, supportedProvisionableJavaVersions)) {
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
                scope.launch {
                    snackbarHostState.showSnackbar("未检测到 Java ${targetServer.javaMajorVersion}，已开始自动安装")
                }
                return
            }
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
            scope.launch {
                snackbarHostState.showSnackbar("当前暂不支持该 Minecraft 版本所需的 Java ${targetServer.javaMajorVersion} 运行时")
            }
            return
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
            scope.launch { snackbarHostState.showSnackbar(error.message ?: "隧道远端端口分配失败") }
            return
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
        updatedServers.firstOrNull { it.id == request.serverId }?.let { PaperServerService.start(appContext, it, selectedTunnelsWithPorts) }
        scope.launch {
            snackbarHostState.showSnackbar(
                if (selectedTunnelsWithPorts.isNotEmpty()) {
                    "${targetServer.name} 已通过 ${selectedTunnelsWithPorts.joinToString("、") { it.name }} 开始启动"
                } else {
                    "${targetServer.name} 开始启动"
                },
            )
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
                        onDismissRequest = { pendingModpackSetupApproval = null },
                        title = { Text("执行整合包安装脚本？") },
                        text = {
                            Text("${pendingApproval.serverName} 检测到整合包安装脚本 ${pendingApproval.scriptName}。该脚本将在后续启动前执行一次，请先确认执行整合包安装脚本。")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                val workDir = com.mcgo.app.server.managedPaperServerDirectory(appContext.filesDir.toPath(), pendingApproval.request.serverId)
                                                approveManagedServerSetupScript(workDir)
                                                syncManagedServerWorkspaceToAuthorizedDirectory(
                                                    context = appContext,
                                                    authorizedDirectoryUri = serverDirectoryUriText,
                                                    serverId = pendingApproval.request.serverId,
                                                    sourceWorkspaceDir = workDir,
                                                )
                                            }
                                        }.onSuccess {
                                            val approvedRequest = pendingApproval.request
                                            pendingModpackSetupApproval = null
                                            startServerNow(approvedRequest)
                                        }.onFailure {
                                            snackbarHostState.showSnackbar(it.message ?: "确认整合包安装脚本失败")
                                        }
                                    }
                                },
                            ) {
                                Text("确认并启动")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingModpackSetupApproval = null }) {
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
                        onImportWorldArchive = { serverId, archiveUri ->
                            val targetServer = servers.firstOrNull { it.id == serverId } ?: return@ServersScreen
                            if (targetServer.isRuntimeBusy()) {
                                scope.launch { snackbarHostState.showSnackbar("请先停止 ${targetServer.name}，再导入存档") }
                                return@ServersScreen
                            }
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        importManagedServerWorldArchive(
                                            context = appContext,
                                            archiveUri = archiveUri,
                                            targetWorldDir = com.mcgo.app.server.managedPaperServerDirectory(appContext.filesDir.toPath(), targetServer.id).resolve(targetServer.worldName),
                                        )
                                        syncManagedServerWorkspaceToAuthorizedDirectory(
                                            context = appContext,
                                            authorizedDirectoryUri = serverDirectoryUriText,
                                            serverId = targetServer.id,
                                            sourceWorkspaceDir = com.mcgo.app.server.managedPaperServerDirectory(appContext.filesDir.toPath(), targetServer.id),
                                        )
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
                                        exportManagedServerWorldArchive(
                                            context = appContext,
                                            sourceWorldDir = com.mcgo.app.server.managedPaperServerDirectory(appContext.filesDir.toPath(), targetServer.id).resolve(targetServer.worldName),
                                            targetUri = archiveUri,
                                        )
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
                                            installManagedServerModFile(
                                                sourceFile = tempMod,
                                                serverWorkDir = com.mcgo.app.server.managedPaperServerDirectory(appContext.filesDir.toPath(), targetServer.id),
                                                targetFileName = displayName,
                                            )
                                            syncManagedServerWorkspaceToAuthorizedDirectory(
                                                context = appContext,
                                                authorizedDirectoryUri = serverDirectoryUriText,
                                                serverId = targetServer.id,
                                                sourceWorkspaceDir = com.mcgo.app.server.managedPaperServerDirectory(appContext.filesDir.toPath(), targetServer.id),
                                            )
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
                        onImportModpackArchive = { serverId, archiveUri ->
                            val targetServer = servers.firstOrNull { it.id == serverId } ?: return@ServersScreen
                            if (targetServer.serverType != MinecraftServerType.Fabric &&
                                targetServer.serverType != MinecraftServerType.Forge &&
                                targetServer.serverType != MinecraftServerType.NeoForge &&
                                targetServer.serverType != MinecraftServerType.Quilt) {
                                scope.launch { snackbarHostState.showSnackbar("当前仅 Fabric / Forge / NeoForge / Quilt 服务器支持导入整合包") }
                                return@ServersScreen
                            }
                            if (targetServer.isRuntimeBusy()) {
                                scope.launch { snackbarHostState.showSnackbar("请先停止 ${targetServer.name}，再导入整合包") }
                                return@ServersScreen
                            }
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val tempPack = Files.createTempFile("mcgo-modpack-", ".zip")
                                        appContext.contentResolver.openInputStream(archiveUri)?.use { input ->
                                            Files.newOutputStream(tempPack).use { output -> input.copyTo(output) }
                                        } ?: error("无法读取整合包文件")
                                        try {
                                            val workDir = com.mcgo.app.server.managedPaperServerDirectory(appContext.filesDir.toPath(), targetServer.id)
                                            importManagedServerModpackArchive(
                                                archiveFile = tempPack,
                                                serverWorkDir = workDir,
                                                targetJar = com.mcgo.app.server.managedServerTargetJarPath(
                                                    serverWorkDir = workDir,
                                                    serverTypeName = targetServer.serverType.name,
                                                    minecraftVersion = targetServer.minecraftVersion,
                                                ),
                                            )
                                            val setupScript = findManagedServerSetupScript(workDir)
                                            syncManagedServerWorkspaceToAuthorizedDirectory(
                                                context = appContext,
                                                authorizedDirectoryUri = serverDirectoryUriText,
                                                serverId = targetServer.id,
                                                sourceWorkspaceDir = workDir,
                                            )
                                            setupScript?.fileName?.toString()
                                        } finally {
                                            Files.deleteIfExists(tempPack)
                                        }
                                    }
                                }.onSuccess { setupScriptName ->
                                    val suffix = if (setupScriptName != null) "；整合包包含安装脚本 ${setupScriptName}，请先确认执行整合包安装脚本后再启动" else ""
                                    snackbarHostState.showSnackbar("已导入 ${targetServer.name} 的整合包${suffix}")
                                }.onFailure {
                                    snackbarHostState.showSnackbar("导入整合包失败：${it.message ?: "未知错误"}")
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

private fun exportDebugLogs(context: Context): Intent {
    val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
    val exportDir = context.cacheDir.toPath().resolve("mcgo_debug_logs")
    Files.createDirectories(exportDir)
    val exportFile = exportDir.resolve("mcgo_debug_logs-$timestamp.txt")
    val filesDir = context.filesDir.toPath()
    val sections = buildList {
        add("== mcgo debug export ==")
        add("generatedAt=$timestamp")
        add("")
        add(readLogExportSection("server_profiles.properties", filesDir.resolve("server_profiles.properties")))
        add(readLogExportSection("tunnel_profiles.properties", filesDir.resolve("tunnel_profiles.properties")))
        add(readLogExportSection("appearance_preferences.properties", filesDir.resolve("appearance_preferences.properties")))
        add(readRuntimePrefsExportSection(context))
        val serversRoot = filesDir.resolve("servers")
        if (Files.isDirectory(serversRoot)) {
            Files.walk(serversRoot).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".log") }
                    .sorted()
                    .forEach { logPath -> add(readLogExportSection(filesDir.relativize(logPath).toString(), logPath)) }
            }
        }
    }
    Files.write(exportFile, sections.joinToString(separator = "\n").toByteArray(Charsets.UTF_8))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile.toFile())
    return Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MC-GO 调试日志 $timestamp")
            putExtra(Intent.EXTRA_TEXT, "MC-GO 调试日志，问题反馈时建议附上此文件。")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, exportFile.fileName.toString(), uri)
        },
        "分享 MC-GO 调试日志",
    ).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun readLogExportSection(title: String, path: Path): String {
    val body = if (Files.isRegularFile(path)) {
        runCatching { redactSensitiveLogExportText(String(Files.readAllBytes(path), Charsets.UTF_8)) }
            .getOrElse { "<read failed: ${it.message}>" }
    } else {
        "<missing>"
    }
    return """
        |===== $title =====
        |$body
        |
    """.trimMargin()
}

private fun readRuntimePrefsExportSection(context: Context): String {
    val prefs = context.getSharedPreferences(RuntimePrefsName, Context.MODE_PRIVATE)
    val entries = prefs.all.entries.sortedBy { it.key }
    val body = if (entries.isEmpty()) {
        "<empty>"
    } else {
        entries.joinToString(separator = "\n") { (key, value) ->
            val renderedValue = if (key == "server_directory_uri") "<redacted>" else value.toString()
            "$key=$renderedValue"
        }
    }
    return """
        |===== runtime_prefs =====
        |$body
        |
    """.trimMargin()
}

private fun redactSensitiveLogExportText(rawText: String): String = rawText
    .lineSequence()
    .map { line ->
        val key = line.substringBefore('=')
        when {
            key.endsWith("credentialValue") -> "$key=<redacted>"
            key.endsWith("rawConfigText") -> "$key=<redacted>"
            key.endsWith("rawConfigPreview") -> "$key=<redacted>"
            else -> line
        }
    }
    .joinToString(separator = "\n")

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
    var selectedOnlinePlayer by remember(server.id) { mutableStateOf<String?>(null) }
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
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "在线玩家",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFD0D7DE),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (server.onlinePlayerNames.isEmpty()) {
                                Text(
                                    text = "当前无人在线",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8B949E),
                                )
                            } else {
                                server.onlinePlayerNames.forEach { playerName ->
                                    Surface(
                                        modifier = Modifier.combinedClickable(
                                            onClick = {},
                                            onLongClick = { selectedOnlinePlayer = playerName },
                                        ),
                                        shape = RoundedCornerShape(999.dp),
                                        color = ConsoleInfoColor.copy(alpha = 0.14f),
                                        contentColor = ConsoleInfoColor,
                                    ) {
                                        Text(
                                            text = playerName,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                        selectedOnlinePlayer?.let { playerName ->
                            DropdownMenu(
                                expanded = true,
                                onDismissRequest = { selectedOnlinePlayer = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("复制昵称") },
                                    onClick = {
                                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                            ClipData.newPlainText("player-name", playerName),
                                        )
                                        selectedOnlinePlayer = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("踢出玩家") },
                                    onClick = {
                                        if (onSubmitCommand("kick $playerName")) inlineError = null else inlineError = "当前 Paper 进程尚未接收标准输入，请稍后再试"
                                        selectedOnlinePlayer = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("授予 OP") },
                                    onClick = {
                                        if (onSubmitCommand("op $playerName")) inlineError = null else inlineError = "当前 Paper 进程尚未接收标准输入，请稍后再试"
                                        selectedOnlinePlayer = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("移除 OP") },
                                    onClick = {
                                        if (onSubmitCommand("deop $playerName")) inlineError = null else inlineError = "当前 Paper 进程尚未接收标准输入，请稍后再试"
                                        selectedOnlinePlayer = null
                                    },
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
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
    fabricVersions: List<String>,
    forgeVersions: List<String>,
    neoForgeVersions: List<String>,
    quiltVersions: List<String>,
    supportedProvisionableJavaVersions: Set<Int>,
    dynamicBackground: Boolean,
    serverDirectoryUri: String?,
    onDismiss: () -> Unit,
    onSave: (ServerCardState) -> Unit,
) {
    val baseVersionOptions: List<String> = remember(server.serverType, vanillaVersions, paperVersions, purpurVersions, fabricVersions, forgeVersions, neoForgeVersions, quiltVersions, supportedProvisionableJavaVersions) {
        when (server.serverType) {
            MinecraftServerType.Vanilla -> vanillaVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
            MinecraftServerType.Paper -> com.mcgo.app.server.resolveProvisionablePaperVersionOptions(
                versions = paperVersions,
                supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
            )
            MinecraftServerType.Purpur -> purpurVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
            MinecraftServerType.Fabric -> fabricVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
            MinecraftServerType.Forge -> forgeVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
            MinecraftServerType.NeoForge -> neoForgeVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
            MinecraftServerType.Quilt -> quiltVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingServerIconChange by remember(server.id) { mutableStateOf<PendingServerIconChange>(PendingServerIconChange.Unchanged) }
    var pendingServerIconCrop by remember(server.id) { mutableStateOf<PendingServerIconCrop?>(null) }
    val iconPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val preparedCrop = withContext(Dispatchers.IO) {
                runCatching {
                    val previewBitmap = decodeServerIconPreviewBitmap(context, uri)
                    PendingServerIconCrop(
                        sourceUri = uri,
                        previewBitmap = previewBitmap,
                    )
                }
            }
            preparedCrop.onSuccess { cropState ->
                pendingServerIconCrop = cropState
                overlayDestination = EditServerOverlayDestination.IconCrop
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "服务器图标读取失败：${error.message ?: "请换一张图片再试"}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

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
    ).copy(
        serverIconVersion = when (pendingServerIconChange) {
            PendingServerIconChange.Unchanged -> server.serverIconVersion
            PendingServerIconChange.Remove -> 0L
            is PendingServerIconChange.Replace -> System.currentTimeMillis()
        },
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

        EditServerOverlayDestination.IconCrop -> {
            pendingServerIconCrop?.let { cropState ->
                ServerIconCropDialog(
                    previewBitmap = cropState.previewBitmap,
                    dynamicBackground = dynamicBackground,
                    onDismiss = {
                        pendingServerIconCrop = null
                        overlayDestination = EditServerOverlayDestination.Form
                    },
                    onApply = { pngBytes ->
                        pendingServerIconChange = PendingServerIconChange.Replace(pngBytes)
                        pendingServerIconCrop = null
                        overlayDestination = EditServerOverlayDestination.Form
                    },
                )
            }
        }

        EditServerOverlayDestination.Form -> {
            EditFullScreenScaffold(
                title = "编辑 ${server.name}",
                subtitle = "",
                leadingIcon = Icons.Outlined.Tune,
                dynamicBackground = dynamicBackground,
                layoutMode = EditFullScreenScaffoldLayoutMode.ScrollableChrome,
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
                            onClick = {
                                val editedServer = buildDraftServer()
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        when (val change = pendingServerIconChange) {
                                            PendingServerIconChange.Unchanged -> Unit
                                            PendingServerIconChange.Remove -> {
                                                deleteManagedServerIcon(context.filesDir.toPath(), editedServer.id)
                                                if (serverDirectoryUri != null) {
                                                    deleteManagedServerIconFromAuthorizedDirectory(
                                                        context = context,
                                                        authorizedDirectoryUri = serverDirectoryUri,
                                                        serverId = editedServer.id,
                                                        fileName = "server-icon.png",
                                                    )
                                                }
                                            }
                                            is PendingServerIconChange.Replace -> {
                                                writeManagedServerIcon(context.filesDir.toPath(), editedServer.id, change.pngBytes)
                                                if (serverDirectoryUri != null) {
                                                    syncManagedServerIconToAuthorizedDirectory(
                                                        context = context,
                                                        authorizedDirectoryUri = serverDirectoryUri,
                                                        serverId = editedServer.id,
                                                        iconPath = managedPaperServerIconFile(context.filesDir.toPath(), editedServer.id),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    onSave(editedServer)
                                }
                            },
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

                    EditSettingsSectionCard(title = "图标与基础设置") {
                        ServerIconEditorCard(
                            server = server,
                            pendingServerIconChange = pendingServerIconChange,
                            onPickIcon = {
                                iconPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onRemoveIcon = { pendingServerIconChange = PendingServerIconChange.Remove },
                            showRemoveAction = true,
                            pickButtonLabel = "更换图标",
                            preferSingleRowActions = true,
                        )
                        EditSettingsDivider()
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
    ScrollableChrome,
}

private enum class EditServerOverlayDestination {
    Form,
    Properties,
    IconCrop,
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

@Composable
internal fun ServerIconEditorCard(
    server: ServerCardState,
    pendingServerIconChange: PendingServerIconChange,
    onPickIcon: () -> Unit,
    onRemoveIcon: () -> Unit,
    showRemoveAction: Boolean = true,
    pickButtonLabel: String = "选择图标",
    preferSingleRowActions: Boolean = false,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val containerModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
        val pickAction: @Composable () -> Unit = {
            OutlinedButton(
                onClick = onPickIcon,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(pickButtonLabel)
            }
        }
        val removeAction: @Composable () -> Unit = {
            OutlinedButton(
                onClick = onRemoveIcon,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("移除图标")
            }
        }
        val actionColumn: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pickAction()
                if (showRemoveAction) {
                    removeAction()
                }
            }
        }
        val actionRow: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onPickIcon,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(pickButtonLabel)
                }
                if (showRemoveAction) {
                    OutlinedButton(
                        onClick = onRemoveIcon,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("移除图标")
                    }
                }
            }
        }
        if (preferSingleRowActions) {
            Row(
                modifier = containerModifier,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerAvatar(
                    server = server,
                    pendingServerIconChange = pendingServerIconChange,
                    modifier = Modifier.size(72.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "服务器图标",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "使用手机图片，裁剪成 1:1 后自动转为 64×64 PNG",
                        style = MaterialTheme.typography.bodySmall,
                        color = editPageColors().secondaryText,
                    )
                    actionRow()
                }
            }
        } else if (maxWidth < 360.dp) {
            Column(
                modifier = containerModifier,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ServerAvatar(
                        server = server,
                        pendingServerIconChange = pendingServerIconChange,
                        modifier = Modifier.size(64.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "服务器图标",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "使用手机图片，裁剪成 1:1 后自动转为 64×64 PNG",
                            style = MaterialTheme.typography.bodySmall,
                            color = editPageColors().secondaryText,
                        )
                    }
                }
                actionColumn()
            }
        } else {
            Row(
                modifier = containerModifier,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerAvatar(
                    server = server,
                    pendingServerIconChange = pendingServerIconChange,
                    modifier = Modifier.size(72.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "服务器图标",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "使用手机图片，裁剪成 1:1 后自动转为 64×64 PNG",
                        style = MaterialTheme.typography.bodySmall,
                        color = editPageColors().secondaryText,
                    )
                    actionColumn()
                }
            }
        }
    }
}

@Composable
fun ServerAvatar(
    server: ServerCardState,
    pendingServerIconChange: PendingServerIconChange = PendingServerIconChange.Unchanged,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pendingBitmap = remember(pendingServerIconChange) {
        when (pendingServerIconChange) {
            is PendingServerIconChange.Replace -> BitmapFactory.decodeByteArray(
                pendingServerIconChange.pngBytes,
                0,
                pendingServerIconChange.pngBytes.size,
            )
            else -> null
        }
    }
    val persistedBitmap by produceState<Bitmap?>(initialValue = null, server.id, server.serverIconVersion) {
        value = withContext(Dispatchers.IO) {
            loadManagedServerIcon(context.filesDir.toPath(), server.id)
        }
    }
    val resolvedBitmap = when (pendingServerIconChange) {
        PendingServerIconChange.Remove -> null
        is PendingServerIconChange.Replace -> pendingBitmap
        PendingServerIconChange.Unchanged -> persistedBitmap
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (resolvedBitmap != null) {
            Image(
                bitmap = resolvedBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = server.name.firstOrNull()?.uppercase() ?: "M",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun ServerIconCropDialog(
    previewBitmap: Bitmap,
    dynamicBackground: Boolean,
    onDismiss: () -> Unit,
    onApply: (ByteArray) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val cropViewportDp = configuration.screenWidthDp.dp - 40.dp
    val density = LocalDensity.current
    val cropViewportPx = remember(cropViewportDp, density) { with(density) { cropViewportDp.roundToPx() } }
    var cropScale by remember(previewBitmap) { mutableStateOf(1f) }
    var cropOffset by remember(previewBitmap, cropViewportPx) {
        mutableStateOf(
            clampServerIconCropOffset(
                sourceWidth = previewBitmap.width,
                sourceHeight = previewBitmap.height,
                viewportSizePx = cropViewportPx,
                scale = cropScale,
                offset = Offset.Zero,
            ),
        )
    }
    fun updateCropScale(nextScale: Float) {
        val resolvedScale = nextScale.coerceIn(1f, 6f)
        cropScale = resolvedScale
        cropOffset = clampServerIconCropOffset(
            sourceWidth = previewBitmap.width,
            sourceHeight = previewBitmap.height,
            viewportSizePx = cropViewportPx,
            scale = resolvedScale,
            offset = cropOffset,
        )
    }
    val imageBitmap = remember(previewBitmap) { previewBitmap.asImageBitmap() }
    val baseDisplayScale = remember(previewBitmap, cropViewportPx) {
        cropViewportPx / minOf(previewBitmap.width, previewBitmap.height).toFloat().coerceAtLeast(1f)
    }
    val imageDisplayWidthDp = with(density) { (previewBitmap.width * baseDisplayScale).toDp() }
    val imageDisplayHeightDp = with(density) { (previewBitmap.height * baseDisplayScale).toDp() }
    BackHandler(enabled = true, onBack = onDismiss)
    EditFullScreenScaffold(
        title = "裁剪服务器图标",
        subtitle = "拖动和缩放，导出为 1:1 64×64 PNG",
        leadingIcon = Icons.Outlined.Edit,
        dynamicBackground = dynamicBackground,
        layoutMode = EditFullScreenScaffoldLayoutMode.ScrollableChrome,
        onDismiss = onDismiss,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }
                Button(
                    onClick = {
                        onApply(
                            cropServerIconToSquarePng(
                                source = previewBitmap,
                                viewportSizePx = cropViewportPx,
                                scale = cropScale,
                                offset = cropOffset,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = cropViewportPx > 0,
                ) { Text("应用图标") }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(cropViewportDp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black.copy(alpha = 0.16f))
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            RoundedCornerShape(28.dp),
                        )
                        .pointerInput(previewBitmap, cropViewportPx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (cropScale * zoom).coerceIn(1f, 6f)
                                val unclampedOffset = cropOffset + pan
                                updateCropScale(nextScale)
                                cropOffset = clampServerIconCropOffset(
                                    sourceWidth = previewBitmap.width,
                                    sourceHeight = previewBitmap.height,
                                    viewportSizePx = cropViewportPx,
                                    scale = cropScale,
                                    offset = unclampedOffset,
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(
                                width = imageDisplayWidthDp,
                                height = imageDisplayHeightDp,
                            )
                            .graphicsLayer(
                                scaleX = cropScale,
                                scaleY = cropScale,
                                translationX = cropOffset.x,
                                translationY = cropOffset.y,
                            ),
                        contentScale = ContentScale.Fit,
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = editPageColors().cardContainerColor,
                    contentColor = editPageColors().primaryText,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, editPageColors().cardStrokeColor),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("缩放", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = formatServerIconCropZoomLabel(cropScale),
                                style = MaterialTheme.typography.labelLarge,
                                color = editPageColors().secondaryText,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(onClick = { updateCropScale(cropScale - 0.25f) }) {
                                Icon(Icons.Outlined.Remove, contentDescription = "缩小")
                            }
                            Slider(
                                value = cropScale,
                                onValueChange = { updateCropScale(it) },
                                valueRange = 1f..6f,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { updateCropScale(cropScale + 0.25f) }) {
                                Icon(Icons.Outlined.Add, contentDescription = "放大")
                            }
                        }
                        TextButton(onClick = {
                                updateCropScale(1f)
                                cropOffset = Offset.Zero
                            }) {
                            Text("重置位置与缩放")
                        }
                    }
                }
            }
        }
    }
}

internal fun decodeServerIconPreviewBitmap(context: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                calculateImageDecoderTargetSize(
                    sourceWidth = info.size.width,
                    sourceHeight = info.size.height,
                    maxSize = 2048,
                )?.let { target ->
                    decoder.setTargetSize(target.width, target.height)
                }
                decoder.isMutableRequired = false
            }
        }.getOrNull()?.let { return it }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: error("无法读取所选图片")
    val sampled = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 2048)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, sampled)
    }
        ?: error("无法读取所选图片")
}

data class ImageDecoderTargetSize(
    val width: Int,
    val height: Int,
)

fun calculateImageDecoderTargetSize(sourceWidth: Int, sourceHeight: Int, maxSize: Int): ImageDecoderTargetSize? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || maxSize <= 0) return null
    val longestSide = maxOf(sourceWidth, sourceHeight)
    if (longestSide <= maxSize) return null
    val scale = maxSize.toDouble() / longestSide.toDouble()
    val targetWidth = (sourceWidth * scale).toInt().coerceIn(1, maxSize)
    val targetHeight = (sourceHeight * scale).toInt().coerceIn(1, maxSize)
    return ImageDecoderTargetSize(width = targetWidth, height = targetHeight)
}

private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sample = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxSize || currentHeight > maxSize) {
        sample *= 2
        currentWidth /= 2
        currentHeight /= 2
    }
    return sample.coerceAtLeast(1)
}

data class ServerIconCropWindow(
    val startX: Int,
    val startY: Int,
    val size: Int,
)

internal fun resolveServerIconCropWindow(
    sourceWidth: Int,
    sourceHeight: Int,
    viewportSizePx: Int,
    scale: Float,
    offset: Offset,
): ServerIconCropWindow {
    val minSide = minOf(sourceWidth, sourceHeight).toFloat().coerceAtLeast(1f)
    val baseDisplayScale = viewportSizePx / minSide
    val sourcePixelsPerViewportPx = 1f / (baseDisplayScale * scale)
    val cropSize = (viewportSizePx * sourcePixelsPerViewportPx)
        .roundToInt()
        .coerceIn(1, minOf(sourceWidth, sourceHeight))
    val centeredLeft = (sourceWidth - cropSize) / 2f
    val centeredTop = (sourceHeight - cropSize) / 2f
    val translatedX = centeredLeft - (offset.x * sourcePixelsPerViewportPx)
    val translatedY = centeredTop - (offset.y * sourcePixelsPerViewportPx)
    return ServerIconCropWindow(
        startX = translatedX.roundToInt().coerceIn(0, sourceWidth - cropSize),
        startY = translatedY.roundToInt().coerceIn(0, sourceHeight - cropSize),
        size = cropSize,
    )
}

internal fun clampServerIconCropOffset(
    sourceWidth: Int,
    sourceHeight: Int,
    viewportSizePx: Int,
    scale: Float,
    offset: Offset,
): Offset {
    val minSide = minOf(sourceWidth, sourceHeight).toFloat().coerceAtLeast(1f)
    val baseDisplayScale = viewportSizePx / minSide
    val displayWidth = sourceWidth * baseDisplayScale * scale
    val displayHeight = sourceHeight * baseDisplayScale * scale
    val maxOffsetX = ((displayWidth - viewportSizePx) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((displayHeight - viewportSizePx) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY),
    )
}

internal fun formatServerIconCropZoomLabel(scale: Float): String =
    String.format(Locale.US, "%.0f%%", scale.coerceIn(1f, 6f) * 100f)

private fun cropServerIconToSquarePng(
    source: Bitmap,
    viewportSizePx: Int,
    scale: Float,
    offset: Offset,
): ByteArray {
    val cropWindow = resolveServerIconCropWindow(
        sourceWidth = source.width,
        sourceHeight = source.height,
        viewportSizePx = viewportSizePx,
        scale = scale,
        offset = offset,
    )
    val cropped = Bitmap.createBitmap(source, cropWindow.startX, cropWindow.startY, cropWindow.size, cropWindow.size)
    val resized = Bitmap.createScaledBitmap(cropped, 64, 64, true)
    return java.io.ByteArrayOutputStream().use { output ->
        resized.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
}

private fun loadManagedServerIcon(filesDir: Path, serverId: String): Bitmap? = runCatching {
    val iconFile = com.mcgo.app.server.managedPaperServerIconFile(filesDir, serverId)
    if (!Files.isRegularFile(iconFile)) return@runCatching null
    BitmapFactory.decodeFile(iconFile.toString())
}.getOrNull()

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
            val headerCard: @Composable (Modifier) -> Unit = { modifier ->
                Surface(
                    modifier = modifier,
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
            val headerOverlay: @Composable () -> Unit = {
                headerCard(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .onSizeChanged { headerOverlayHeightPx = it.height },
                )
            }
            val footerCard: @Composable (Modifier) -> Unit = { modifier ->
                Surface(
                    modifier = modifier,
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
            val footerOverlay: @Composable () -> Unit = {
                footerCard(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .onSizeChanged { footerOverlayHeightPx = it.height },
                )
            }

            val headerInline: @Composable () -> Unit = {
                headerCard(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            val footerInline: @Composable () -> Unit = {
                footerCard(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            when (layoutMode) {
                EditFullScreenScaffoldLayoutMode.ScrollableChrome -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        headerInline()
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            content()
                        }
                        footerInline()
                    }
                }

                EditFullScreenScaffoldLayoutMode.PinnedChrome -> {
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

    EditSettingRowShell(
        icon = icon,
        label = label,
        onClick = { expanded = true },
    ) {
        Box(
            modifier = Modifier.wrapContentWidth(align = Alignment.End),
            contentAlignment = Alignment.CenterEnd,
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
