package com.mcgo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.formatPlayerCapacity

@Composable
fun ServersScreen(
    servers: List<ServerCardState>,
    availableTunnels: List<TunnelProfile>,
    paperVersions: List<String>,
    modifier: Modifier = Modifier,
    showCreateServer: Boolean = false,
    onDismissCreateServer: () -> Unit = {},
    onCreateServer: (ServerCardState) -> Unit = {},
    onStartServer: (serverId: String, tunnelId: String?, startupPort: Int) -> Unit,
    onStopServer: (serverId: String) -> Unit,
    onActionClick: () -> Unit,
) {
    var pendingStartServer by remember { mutableStateOf<ServerCardState?>(null) }

    if (showCreateServer) {
        CreatePaperServerDialog(
            paperVersions = paperVersions,
            onDismiss = onDismissCreateServer,
            onCreate = onCreateServer,
        )
    }

    pendingStartServer?.let { server ->
        StartServerDialog(
            server = server,
            availableTunnels = availableTunnels,
            onDismiss = { pendingStartServer = null },
            onConfirm = { tunnelId, startupPort ->
                onStartServer(server.id, tunnelId, startupPort)
                pendingStartServer = null
            },
        )
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }
        items(items = servers, key = { it.id }) { server ->
            ServerCard(
                server = server,
                modifier = Modifier.padding(horizontal = 20.dp),
                onActionClick = onActionClick,
                onStartClick = { pendingStartServer = server },
                onStopClick = { onStopServer(server.id) },
            )
        }
        item { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
private fun ServerCard(
    server: ServerCardState,
    modifier: Modifier = Modifier,
    onActionClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val statusColor = if (server.isOnline) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Dns,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column {
                    Text(text = server.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${server.edition} · ${server.worldName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    server.activeTunnelLabel?.let { tunnelLabel ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.primary,
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
            StatusDotBadge(
                text = if (server.isOnline) stringResource(R.string.server_status_online) else stringResource(R.string.server_status_offline),
                color = statusColor,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onActionClick,
                label = { Text(stringResource(R.string.server_action_console)) },
                leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
            )
            AssistChip(
                onClick = { if (server.isOnline) onStopClick() else onStartClick() },
                label = {
                    Text(
                        if (server.isOnline) stringResource(R.string.server_action_stop)
                        else stringResource(R.string.server_action_start),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (server.isOnline) Icons.Outlined.StopCircle else Icons.Outlined.PlayCircle,
                        contentDescription = null,
                    )
                },
            )
            AssistChip(
                onClick = onActionClick,
                label = { Text(stringResource(R.string.server_action_edit)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun CreatePaperServerDialog(
    paperVersions: List<String>,
    onDismiss: () -> Unit,
    onCreate: (ServerCardState) -> Unit,
) {
    val versionOptions = paperVersions.ifEmpty { listOf("1.21.4") }
    var name by remember { mutableStateOf("Paper 生存服") }
    var minecraftVersion by remember(versionOptions) { mutableStateOf(versionOptions.last()) }
    var versionMenuExpanded by remember { mutableStateOf(false) }
    var maxPlayers by remember { mutableStateOf("20") }
    var memoryMb by remember { mutableStateOf("2048") }
    var port by remember { mutableStateOf("25565") }
    val resolvedMaxPlayers = maxPlayers.toIntOrNull()?.coerceIn(1, 200) ?: 20
    val resolvedMemoryMb = memoryMb.toIntOrNull()?.coerceAtLeast(512) ?: 2048
    val resolvedPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: 25565
    val canCreate = name.isNotBlank() && minecraftVersion.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = canCreate,
                onClick = {
                    onCreate(
                        createPaperServer(
                            name = name,
                            minecraftVersion = minecraftVersion,
                            maxPlayers = resolvedMaxPlayers,
                            memoryMb = resolvedMemoryMb,
                            port = resolvedPort,
                        ),
                    )
                    onDismiss()
                },
            ) {
                Text("创建 Paper")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("创建原版 Paper 服务器") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "从 Paper 官方版本列表选择历史版本，MC-GO 会准备 EULA、server.properties 与启动文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务器名称") },
                    singleLine = true,
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = minecraftVersion,
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { versionMenuExpanded = true },
                        readOnly = true,
                        label = { Text("Minecraft 版本") },
                        supportingText = { Text("从 Paper 官方版本列表选择，包含历史版本") },
                        singleLine = true,
                    )
                    DropdownMenu(
                        expanded = versionMenuExpanded,
                        onDismissRequest = { versionMenuExpanded = false },
                    ) {
                        versionOptions.asReversed().forEach { version ->
                            DropdownMenuItem(
                                text = { Text(version) },
                                onClick = {
                                    minecraftVersion = version
                                    versionMenuExpanded = false
                                },
                            )
                        }
                    }
                }
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
            }
        },
    )
}

@Composable
private fun StartServerDialog(
    server: ServerCardState,
    availableTunnels: List<TunnelProfile>,
    onDismiss: () -> Unit,
    onConfirm: (tunnelId: String?, startupPort: Int) -> Unit,
) {
    var selectedTunnelId by remember(server.name) { mutableStateOf(server.selectedTunnelId) }
    val selectedTunnel = remember(selectedTunnelId, availableTunnels) {
        availableTunnels.firstOrNull { it.id == selectedTunnelId }
    }
    var portInput by remember(server.name) { mutableStateOf(server.defaultPort.toString()) }

    LaunchedEffect(selectedTunnelId) {
        portInput = (selectedTunnel?.resolveStartupPort(server.defaultPort, server.defaultPort) ?: server.defaultPort).toString()
    }

    val canEditPort = selectedTunnel?.supportsCustomPortOnStart() == true
    val resolvedPort = selectedTunnel?.resolveStartupPort(server.defaultPort, portInput.toIntOrNull()) ?: server.defaultPort

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedTunnelId, resolvedPort) }) {
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
                    text = "选择要绑定的隧道。手动服务器参数可自定义端口；单隧道配置会沿用固定端口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TunnelStartupChoice(
                        selected = selectedTunnelId == null,
                        title = "不启用隧道",
                        subtitle = "仅使用实例默认端口 ${server.defaultPort}",
                        onClick = { selectedTunnelId = null },
                    )
                    availableTunnels.forEach { tunnel ->
                        TunnelStartupChoice(
                            selected = selectedTunnelId == tunnel.id,
                            title = tunnel.name,
                            subtitle = if (tunnel.supportsCustomPortOnStart()) {
                                "${tunnel.kind.label} · 启动时可改端口"
                            } else {
                                "${tunnel.kind.label} · 固定 ${tunnel.resolveStartupPort(server.port, null)} 端口"
                            },
                            trailing = tunnel.latencyLabel(),
                            onClick = { selectedTunnelId = tunnel.id },
                        )
                    }
                }
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canEditPort,
                    label = { Text("开服端口") },
                    supportingText = {
                        Text(
                            if (canEditPort) "当前选中的是服务器参数，开服时可自定义端口"
                            else "当前模式使用固定端口：$resolvedPort",
                        )
                    },
                    singleLine = true,
                )
                selectedTunnel?.let { tunnel ->
                    Text(
                        text = tunnel.connectionSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun StatusDotBadge(text: String, color: Color) {
    Surface(
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
