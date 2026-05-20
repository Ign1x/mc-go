package com.mcgo.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.MinecraftServerType
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.TunnelLaunchSelection
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.allocateManualTunnelRemotePort
import com.mcgo.app.ui.model.activeTunnelLabels
import com.mcgo.app.ui.model.assignTunnelRemotePort
import com.mcgo.app.ui.model.effectiveTunnelBindings
import com.mcgo.app.ui.model.canStartServerFromUi
import com.mcgo.app.ui.model.connectionAddresses
import com.mcgo.app.ui.model.formatPlayerCapacity
import com.mcgo.app.ui.PendingServerIconChange
import com.mcgo.app.ui.ServerAvatar
import com.mcgo.app.ui.model.isRuntimeBusy
import com.mcgo.app.ui.model.resolveServerConsoleText

@Composable
fun ServersScreen(
    servers: List<ServerCardState>,
    availableTunnels: List<TunnelProfile>,
    vanillaVersions: List<String>,
    paperVersions: List<String>,
    purpurVersions: List<String>,
    fabricVersions: List<String>,
    forgeVersions: List<String>,
    neoForgeVersions: List<String>,
    quiltVersions: List<String>,
    serverDirectoryUri: String? = null,
    currentModpackImportServerIds: Set<String> = emptySet(),
    dynamicBackground: Boolean = true,
    supportedProvisionableJavaVersions: Set<Int> = setOf(8, 11, 17, 21, 25),
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
    showCreateServer: Boolean = false,
    onRequestCreateServer: () -> Unit = {},
    onDismissCreateServer: () -> Unit = {},
    onCreateServer: (ServerCardState) -> Unit = {},
    onCreateServerFromModpack: (ServerCardState, android.net.Uri) -> Unit = { _, _ -> },
    onImportWorldArchive: (String, android.net.Uri) -> Unit = { _, _ -> },
    onExportWorldArchive: (String, android.net.Uri) -> Unit = { _, _ -> },
    onImportModFile: (String, android.net.Uri) -> Unit = { _, _ -> },
    onStartServer: (serverId: String, startupPort: Int, tunnelSelections: List<TunnelLaunchSelection>) -> Unit,
    onStopServer: (serverId: String) -> Unit,
    onDeleteServer: (serverId: String) -> Unit,
    onOpenConsole: (serverId: String) -> Unit,
    onEditServer: (serverId: String) -> Unit,
) {
    var pendingStartServer by remember { mutableStateOf<ServerCardState?>(null) }
    var pendingDeleteServer by remember { mutableStateOf<ServerCardState?>(null) }

    if (showCreateServer) {
        CreateServerDialog(
            servers = servers,
            vanillaVersions = vanillaVersions,
            paperVersions = paperVersions,
            purpurVersions = purpurVersions,
            fabricVersions = fabricVersions,
            forgeVersions = forgeVersions,
            neoForgeVersions = neoForgeVersions,
            quiltVersions = quiltVersions,
            supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
            onDismiss = onDismissCreateServer,
            onCreate = onCreateServer,
            onCreateFromModpack = onCreateServerFromModpack,
        )
    }

    pendingStartServer?.let { server ->
        StartServerDialog(
            server = server,
            allServers = servers,
            availableTunnels = availableTunnels,
            frpRuntimeSupported = supportedProvisionableJavaVersions.contains(25),
            onDismiss = { pendingStartServer = null },
            onConfirm = { startupPort, tunnelSelections ->
                onStartServer(server.id, startupPort, tunnelSelections)
                pendingStartServer = null
            },
        )
    }

    pendingDeleteServer?.let { server ->
        DeleteServerDialog(
            server = server,
            onDismiss = { pendingDeleteServer = null },
            onConfirm = {
                onDeleteServer(server.id)
                pendingDeleteServer = null
            },
        )
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }
        if (servers.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "还没有服务器", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "默认先留空。你可以先创建原版 / Paper / Purpur / Fabric / Forge / NeoForge / Quilt 服务器。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "添加后的服务器都可以继续启动、编辑或删除。创建弹窗中也可以直接导入整合包。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onRequestCreateServer) {
                        Text("创建第一个服务器")
                    }
                }
            }
        } else {
            items(items = servers, key = { it.id }) { server ->
                ServerCard(
                    server = server,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    currentModpackImportServerIds = currentModpackImportServerIds,
                    onImportWorldArchive = { uri -> onImportWorldArchive(server.id, uri) },
                    onExportWorldArchive = { uri -> onExportWorldArchive(server.id, uri) },
                    onImportModFile = { uri -> onImportModFile(server.id, uri) },
                    onOpenConsole = { onOpenConsole(server.id) },
                    onEditServer = { onEditServer(server.id) },
                    onStartClick = { pendingStartServer = server },
                    onStopClick = { onStopServer(server.id) },
                    onDeleteClick = { pendingDeleteServer = server },
                )
            }
        }
        item { Spacer(modifier = Modifier.height(96.dp + bottomContentPadding)) }
    }
}

@Composable
private fun ServerCard(
    server: ServerCardState,
    currentModpackImportServerIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    onImportWorldArchive: (android.net.Uri) -> Unit,
    onExportWorldArchive: (android.net.Uri) -> Unit,
    onImportModFile: (android.net.Uri) -> Unit,
    onOpenConsole: () -> Unit,
    onEditServer: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    var fileMenuExpanded by remember(server.id) { mutableStateOf(false) }
    val worldImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportWorldArchive(uri)
    }
    val worldExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) onExportWorldArchive(uri)
    }
    val modImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportModFile(uri)
    }
    val statusColor = when (server.launchStatus) {
        ServerLaunchStatus.Running -> MaterialTheme.colorScheme.secondary
        ServerLaunchStatus.Launching -> MaterialTheme.colorScheme.primary
        ServerLaunchStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val animatedStatusColor = animateColorAsState(statusColor, label = "serverStatusColor")
    val modpackImportInProgress = currentModpackImportServerIds.contains(server.id)
    val connectionAddresses = server.connectionAddresses()
    val connectionAddress = connectionAddresses.firstOrNull() ?: server.port.let { "127.0.0.1:$it" }
    val tunnelLabels = server.activeTunnelLabels()
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerAvatar(
                    server = server,
                    pendingServerIconChange = PendingServerIconChange.Unchanged,
                    modifier = Modifier.size(48.dp),
                )
                Column {
                    Text(text = server.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${server.edition} · ${server.worldName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        connectionAddresses.forEach { address: String ->
                            Surface(
                                modifier = Modifier.clickable {
                                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                        ClipData.newPlainText("${server.name} address", address),
                                    )
                                    Toast.makeText(context, "连接地址已复制", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = address,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                        tunnelLabels.forEach { tunnelLabel: String ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                                contentColor = MaterialTheme.colorScheme.secondary,
                            ) {
                                Text(
                                    text = tunnelLabel,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
            StatusDotBadge(
                text = server.launchStatus.label,
                color = animatedStatusColor.value,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ServerMeta(
                title = stringResource(R.string.server_meta_players),
                value = formatPlayerCapacity(server.onlinePlayers, server.maxPlayers),
                modifier = Modifier.weight(1f),
            )
            ServerMeta(
                title = stringResource(R.string.server_meta_port),
                value = server.port.toString(),
                modifier = Modifier.weight(1f),
            )
            ServerMeta(
                title = stringResource(R.string.server_meta_memory),
                value = server.memoryLabel,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val startEnabled = canStartServerFromUi(server) && !modpackImportInProgress
            val stopEnabled = server.isRuntimeBusy() && !modpackImportInProgress
            Box {
                IconButton(onClick = { fileMenuExpanded = true }, enabled = !modpackImportInProgress) {
                    Icon(Icons.Outlined.Folder, contentDescription = "文件管理")
                }
                DropdownMenu(
                    expanded = fileMenuExpanded,
                    onDismissRequest = { fileMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("导入存档") },
                        enabled = !server.isRuntimeBusy(),
                        onClick = {
                            fileMenuExpanded = false
                            worldImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导出存档") },
                        onClick = {
                            fileMenuExpanded = false
                            worldExportLauncher.launch("${server.worldName}.zip")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("安装模组") },
                        enabled = (server.serverType == MinecraftServerType.Fabric ||
                            server.serverType == MinecraftServerType.Forge ||
                            server.serverType == MinecraftServerType.NeoForge ||
                            server.serverType == MinecraftServerType.Quilt) && !server.isRuntimeBusy(),
                        onClick = {
                            fileMenuExpanded = false
                            modImportLauncher.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*"))
                        },
                    )
                }
            }
            IconButton(onClick = onOpenConsole, enabled = !modpackImportInProgress) {
                Icon(Icons.Outlined.Terminal, contentDescription = stringResource(R.string.server_action_console))
            }
            IconButton(
                onClick = {
                    when {
                        stopEnabled -> onStopClick()
                        startEnabled -> onStartClick()
                    }
                },
                enabled = stopEnabled || startEnabled,
            ) {
                AnimatedContent(
                    targetState = stopEnabled,
                    label = "serverActionIcon",
                ) { stopping ->
                    Icon(
                        imageVector = if (stopping) Icons.Outlined.StopCircle else Icons.Outlined.PlayCircle,
                        contentDescription = if (stopping) stringResource(R.string.server_action_stop) else stringResource(R.string.server_action_start),
                    )
                }
            }
            IconButton(onClick = onEditServer, enabled = !modpackImportInProgress) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.server_action_edit))
            }
            IconButton(onClick = onDeleteClick, enabled = !modpackImportInProgress) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.server_action_delete))
            }
        }
        AnimatedVisibility(
            visible = server.launchStatus == ServerLaunchStatus.Launching ||
                server.launchStatus == ServerLaunchStatus.Stopping ||
                server.launchStatus == ServerLaunchStatus.Failed,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(140)),
            label = "runtimeProgressPanel",
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                RuntimeProgressPanel(server)
            }
        }
    }
}

@Composable
private fun RuntimeProgressPanel(server: ServerCardState) {
    val context = LocalContext.current
    val consoleText = remember(server.runtimeLogPath, server.runtimeLogs) { resolveServerConsoleText(server) }
    val latestRuntimeLog = server.runtimeLogs.lastOrNull().orEmpty()
    val progressTitle = if (server.runtimeLogs.lastOrNull()?.contains("导入整合包") == true) "导入进度" else "启动进度"
    val progressColor = if (latestRuntimeLog.contains("导入整合包")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = progressTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${server.launchProgress.coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = progressColor,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(
                    enabled = consoleText.isNotBlank(),
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                            ClipData.newPlainText("${server.name} MC-GO logs", consoleText),
                        )
                        Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = if (server.launchStatus == ServerLaunchStatus.Failed) "复制失败日志" else "复制运行日志",
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { server.launchProgress.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = progressColor,
        )
        server.runtimeLogs.takeLast(3).forEach { log ->
            Text(
                text = "• $log",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeleteServerDialog(
    server: ServerCardState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确认删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("删除 ${server.name}？") },
        text = { Text("会从列表移除此服务器配置；运行中的服务会先停止。") },
    )
}

@Composable
private fun StartServerDialog(
    server: ServerCardState,
    allServers: List<ServerCardState>,
    availableTunnels: List<TunnelProfile>,
    frpRuntimeSupported: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (startupPort: Int, tunnelSelections: List<TunnelLaunchSelection>) -> Unit,
) {
    val selectedTunnelIds = remember(server.name) {
        mutableStateListOf<String>().apply {
            addAll(server.effectiveTunnelBindings().map { it.tunnelId })
        }
    }
    val remotePortInputs = remember(server.name) {
        mutableStateMapOf<String, String>().apply {
            server.effectiveTunnelBindings().forEach { binding ->
                if (binding.remotePort != null) {
                    put(binding.tunnelId, binding.remotePort.toString())
                }
            }
        }
    }
    var portInput by remember(server.name) { mutableStateOf(server.port.toString()) }
    val selectedTunnels = availableTunnels.filter { selectedTunnelIds.contains(it.id) }
    val primaryTunnel = selectedTunnels.firstOrNull()
    val canEditPort = selectedTunnels.any { it.supportsCustomPortOnStart() }
    val runtimeTunnelSupported = selectedTunnels.all {
        it.kind == com.mcgo.app.ui.model.TunnelKind.Frp && frpRuntimeSupported
    }
    val resolvedPort = if (canEditPort) {
        portInput.toIntOrNull() ?: server.defaultPort
    } else {
        primaryTunnel?.resolveStartupPort(server.defaultPort, portInput.toIntOrNull())
            ?: server.defaultPort
    }

    LaunchedEffect(selectedTunnelIds.toList(), availableTunnels) {
        selectedTunnels.forEach { tunnel ->
            if (remotePortInputs[tunnel.id].isNullOrBlank()) {
                val reserved = runCatching {
                    assignTunnelRemotePort(
                        server = server,
                        tunnel = tunnel,
                        requestedRemotePort = null,
                        servers = allServers,
                    )
                }.getOrNull()
                if (reserved != null) {
                    remotePortInputs[tunnel.id] = reserved.toString()
                }
            }
        }
    }

    val tunnelSelections = selectedTunnels.map { tunnel ->
        val requestedRemotePort = remotePortInputs[tunnel.id]?.toIntOrNull()
        val resolvedRemotePort = if (tunnel.supportsCustomPortOnStart()) {
            requestedRemotePort
                ?: runCatching {
                    assignTunnelRemotePort(
                        server = server,
                        tunnel = tunnel,
                        requestedRemotePort = null,
                        servers = allServers,
                    )
                }.getOrNull()
        } else {
            tunnel.remotePort
        }
        TunnelLaunchSelection(
            tunnelId = tunnel.id,
            remotePort = resolvedRemotePort,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(resolvedPort, tunnelSelections) }, enabled = runtimeTunnelSupported) {
                Text("启动实例")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = { Text("启动 ${server.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "选择要绑定的隧道，可多选；手动服务器参数可分别指定远端端口，单隧道配置会沿用固定端口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TunnelStartupChoice(
                        selected = selectedTunnelIds.isEmpty(),
                        title = "不启用隧道",
                        subtitle = "仅使用实例默认端口 ${server.defaultPort}",
                        onClick = { selectedTunnelIds.clear() },
                    )
                    availableTunnels.forEach { tunnel ->
                        val reserved = runCatching {
                            assignTunnelRemotePort(
                                server = server,
                                tunnel = tunnel,
                                requestedRemotePort = null,
                                servers = allServers,
                            )
                        }.getOrNull()
                        TunnelStartupChoice(
                            selected = selectedTunnelIds.contains(tunnel.id),
                            title = tunnel.name,
                            subtitle = if (tunnel.supportsCustomPortOnStart()) {
                                "${tunnel.kind.label} · 远端 ${reserved ?: "自动分配"}"
                            } else {
                                "${tunnel.kind.label} · 固定远端 ${tunnel.remotePort ?: "未解析"}"
                            },
                            trailing = tunnel.latencyLabel(),
                            onClick = {
                                if (selectedTunnelIds.contains(tunnel.id)) {
                                    selectedTunnelIds.remove(tunnel.id)
                                    remotePortInputs.remove(tunnel.id)
                                } else {
                                    selectedTunnelIds.add(tunnel.id)
                                }
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canEditPort,
                    label = { Text("本地开服端口") },
                    supportingText = {
                        Text(
                            if (canEditPort) "本地端口用于 Paper 监听，可与多条隧道的远端端口不同"
                            else "当前模式使用固定本地端口：$resolvedPort",
                        )
                    },
                    singleLine = true,
                )
                selectedTunnels.forEach { tunnel ->
                    if (tunnel.supportsCustomPortOnStart()) {
                        OutlinedTextField(
                            value = remotePortInputs[tunnel.id].orEmpty(),
                            onValueChange = { remotePortInputs[tunnel.id] = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("${tunnel.name} 远端端口") },
                            supportingText = {
                                Text("首次默认自动分配未占用端口；之后默认沿用这个端口，也可分别手动修改")
                            },
                            singleLine = true,
                        )
                    }
                    Text(
                        text = tunnel.connectionSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!runtimeTunnelSupported && selectedTunnels.isNotEmpty()) {
                    Text(
                        text = if (!frpRuntimeSupported) {
                            "当前设备暂不支持内置 FRP 客户端，仅 arm64-v8a 设备可真启动隧道。"
                        } else {
                            "当前仅支持 FRP 隧道真启动；请取消非 FRP 隧道后再启动。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
private fun TunnelStartupChoice(
    selected: Boolean,
    title: String,
    subtitle: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    FilterChip(
        modifier = Modifier
            .width(188.dp)
            .height(88.dp),
        selected = selected,
        onClick = onClick,
        label = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, fontWeight = FontWeight.Medium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
                trailing?.let {
                    Text(text = it, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun StatusDotBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
            )
            Text(text = text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ServerMeta(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
