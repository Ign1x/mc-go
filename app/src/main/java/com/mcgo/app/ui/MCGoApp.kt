package com.mcgo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.network.measureTcpLatency
import com.mcgo.app.network.parseTcpEndpoint
import com.mcgo.app.ui.components.FluidGradientBackground
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.AppearancePreferencesSaver
import com.mcgo.app.ui.model.McGoPage
import com.mcgo.app.ui.model.McGoPageChrome
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.TunnelLatencyResult
import com.mcgo.app.ui.model.applyTunnelLatencyResults
import com.mcgo.app.ui.model.detachDeletedTunnel
import com.mcgo.app.ui.model.removeTunnelProfile
import com.mcgo.app.ui.model.startWithTunnel
import com.mcgo.app.ui.model.stopServer
import com.mcgo.app.ui.model.upsertTunnelProfile
import com.mcgo.app.ui.sample.McGoSampleRepository
import com.mcgo.app.ui.screens.ServersScreen
import com.mcgo.app.ui.screens.SettingsScreen
import com.mcgo.app.ui.screens.StatusScreen
import com.mcgo.app.ui.screens.TunnelsScreen
import com.mcgo.app.ui.storage.TunnelProfileStore
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens
import com.mcgo.app.ui.theme.McGoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var appearancePreferences by rememberSaveable(stateSaver = AppearancePreferencesSaver) {
        mutableStateOf(AppearancePreferences())
    }
    var servers by remember { mutableStateOf(McGoSampleRepository.serverCards()) }
    var tunnels by remember(tunnelStore) { mutableStateOf(tunnelStore.load()) }

    McGoTheme(appearancePreferences = appearancePreferences) {
        MCGoAppScaffold(
            appearancePreferences = appearancePreferences,
            servers = servers,
            tunnels = tunnels,
            onAppearancePreferencesChange = { appearancePreferences = it },
            onServersChange = { servers = it },
            onTunnelsChange = { tunnels = it },
            onPersistTunnels = { tunnelStore.save(it) },
        )
    }
}

@Composable
private fun MCGoAppScaffold(
    appearancePreferences: AppearancePreferences,
    servers: List<ServerCardState>,
    tunnels: List<TunnelProfile>,
    onAppearancePreferencesChange: (AppearancePreferences) -> Unit,
    onServersChange: (List<ServerCardState>) -> Unit,
    onTunnelsChange: (List<TunnelProfile>) -> Unit,
    onPersistTunnels: (List<TunnelProfile>) -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(McGoDestination.Status) }
    var showTunnelComposer by remember { mutableStateOf(false) }
    var editingTunnelId by rememberSaveable { mutableStateOf<String?>(null) }
    val chrome = McGoPageChrome.forPage(destination.page)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val featureMessage = stringResource(R.string.snackbar_demo_action)
    val notifyUnavailableFeature: () -> Unit = remember(scope, snackbarHostState, featureMessage) {
        {
            scope.launch {
                snackbarHostState.showSnackbar(featureMessage)
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
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
                        onClick = notifyUnavailableFeature,
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
                        showLeadCard = chrome.showLeadCard,
                        onStartServer = { serverName, tunnelId, startupPort ->
                            val tunnel = tunnels.firstOrNull { it.id == tunnelId }
                            onServersChange(
                                servers.map { server ->
                                    if (server.name != serverName) {
                                        server
                                    } else {
                                        server.startWithTunnel(tunnel = tunnel, startupPort = startupPort)
                                    }
                                },
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    tunnel?.let { "$serverName 已通过 ${it.name} 启动" } ?: "$serverName 已启动",
                                )
                            }
                        },
                        onStopServer = { serverName ->
                            onServersChange(
                                servers.map { server ->
                                    if (server.name == serverName) server.stopServer() else server
                                },
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar("$serverName 已停止")
                            }
                        },
                        onActionClick = notifyUnavailableFeature,
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
                            onTunnelsChange(updatedTunnels)
                            onPersistTunnels(updatedTunnels)
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
                            onTunnelsChange(updatedTunnels)
                            onPersistTunnels(updatedTunnels)
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
                    )
                }
            }
        }
    }
}
