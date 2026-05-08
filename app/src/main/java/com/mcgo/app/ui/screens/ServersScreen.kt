package com.mcgo.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

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
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.mcgo.app.server.fetchProvisionableMinecraftVersions
import com.mcgo.app.server.fetchPurpurVersions
import com.mcgo.app.server.fetchVanillaVersions
import com.mcgo.app.server.initialProvisionablePaperVersion
import com.mcgo.app.server.resolveProvisionablePaperVersionOptions
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.JavaSelectionMode
import com.mcgo.app.ui.model.MinecraftServerType
import com.mcgo.app.ui.model.recommendedJavaMajorVersion
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.allocateManualTunnelRemotePort
import com.mcgo.app.ui.model.assignTunnelRemotePort
import com.mcgo.app.ui.model.canStartServerFromUi
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.createPurpurServer
import com.mcgo.app.ui.model.createVanillaServer
import com.mcgo.app.ui.model.formatPlayerCapacity
import com.mcgo.app.ui.model.isRuntimeBusy
import java.io.File

@Composable
fun ServersScreen(
    servers: List<ServerCardState>,
    availableTunnels: List<TunnelProfile>,
    vanillaVersions: List<String>,
    paperVersions: List<String>,
    purpurVersions: List<String>,
    supportedProvisionableJavaVersions: Set<Int> = setOf(8, 11, 17, 21, 25),
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
    showCreateServer: Boolean = false,
    onDismissCreateServer: () -> Unit = {},
    onCreateServer: (ServerCardState) -> Unit = {},
    onStartServer: (serverId: String, tunnelId: String?, startupPort: Int, remotePort: Int?) -> Unit,
    onStopServer: (serverId: String) -> Unit,
    onDeleteServer: (serverId: String) -> Unit,
    onOpenConsole: (serverId: String) -> Unit,
    onEditServer: (serverId: String) -> Unit,
) {
    var pendingStartServer by remember { mutableStateOf<ServerCardState?>(null) }
    var pendingDeleteServer by remember { mutableStateOf<ServerCardState?>(null) }

    if (showCreateServer) {
        CreateServerDialog(
            vanillaVersions = vanillaVersions,
            paperVersions = paperVersions,
            purpurVersions = purpurVersions,
            supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
            onDismiss = onDismissCreateServer,
            onCreate = onCreateServer,
        )
    }

    pendingStartServer?.let { server ->
        StartServerDialog(
            server = server,
            allServers = servers,
            availableTunnels = availableTunnels,
            frpRuntimeSupported = supportedProvisionableJavaVersions.contains(25),
            onDismiss = { pendingStartServer = null },
            onConfirm = { tunnelId, startupPort, remotePort ->
                onStartServer(server.id, tunnelId, startupPort, remotePort)
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
        items(items = servers, key = { it.id }) { server ->
            ServerCard(
                server = server,
                modifier = Modifier.padding(horizontal = 20.dp),
                onOpenConsole = { onOpenConsole(server.id) },
                onEditServer = { onEditServer(server.id) },
                onStartClick = { pendingStartServer = server },
                onStopClick = { onStopServer(server.id) },
                onDeleteClick = { pendingDeleteServer = server },
            )
        }
        item { Spacer(modifier = Modifier.height(96.dp + bottomContentPadding)) }
    }
}

@Composable
private fun ServerCard(
    server: ServerCardState,
    modifier: Modifier = Modifier,
    onOpenConsole: () -> Unit,
    onEditServer: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    val statusColor = when (server.launchStatus) {
        ServerLaunchStatus.Running -> MaterialTheme.colorScheme.secondary
        ServerLaunchStatus.Launching -> MaterialTheme.colorScheme.primary
        ServerLaunchStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val connectionAddress = server.runtimeAddress ?: server.port.let { "127.0.0.1:$it" }
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
                        imageVector = Icons.Outlined.Terminal,
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.clickable {
                                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                    ClipData.newPlainText("${server.name} address", connectionAddress),
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
                                    text = connectionAddress,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        server.activeTunnelLabel?.let { tunnelLabel ->
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val startEnabled = canStartServerFromUi(server)
            val stopEnabled = server.isRuntimeBusy()
            IconButton(onClick = onOpenConsole) {
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
                Icon(
                    imageVector = if (stopEnabled) Icons.Outlined.StopCircle else Icons.Outlined.PlayCircle,
                    contentDescription = if (stopEnabled) stringResource(R.string.server_action_stop) else stringResource(R.string.server_action_start),
                )
            }
            IconButton(onClick = onEditServer) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.server_action_edit))
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.server_action_delete))
            }
        }
    }
}

@Composable
private fun RuntimeProgressPanel(server: ServerCardState) {
    val context = LocalContext.current
    val fallbackLogsText = remember(server.runtimeLogs) { server.runtimeLogs.joinToString(separator = "\n") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "启动进度",
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
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(
                    enabled = fallbackLogsText.isNotBlank() || server.runtimeLogPath != null,
                    onClick = {
                        val copiedText = server.runtimeLogPath
                            ?.let(::File)
                            ?.takeIf { it.isFile }
                            ?.readText()
                            ?.takeIf { it.isNotBlank() }
                            ?: fallbackLogsText
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                            ClipData.newPlainText("${server.name} MC-GO logs", copiedText),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateServerDialog(
    vanillaVersions: List<String>,
    paperVersions: List<String>,
    purpurVersions: List<String>,
    supportedProvisionableJavaVersions: Set<Int>,
    onDismiss: () -> Unit,
    onCreate: (ServerCardState) -> Unit,
) {
    val vanillaVersionOptions = remember(vanillaVersions, supportedProvisionableJavaVersions) {
        vanillaVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
    }
    val paperVersionOptions = remember(paperVersions, supportedProvisionableJavaVersions) {
        resolveProvisionablePaperVersionOptions(paperVersions, supportedProvisionableJavaVersions)
    }
    val purpurVersionOptions = remember(purpurVersions, supportedProvisionableJavaVersions) {
        purpurVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
    }
    var selectedServerType by remember { mutableStateOf(MinecraftServerType.Paper) }
    var name by remember { mutableStateOf("Paper 服务器") }
    var lastAutoGeneratedName by remember { mutableStateOf("Paper 服务器") }
    var minecraftVersion by remember { mutableStateOf("") }
    var versionWasAutoSelected by remember { mutableStateOf(true) }
    var versionMenuExpanded by remember { mutableStateOf(false) }
    var javaSelectionMode by remember { mutableStateOf(JavaSelectionMode.Recommended) }
    var javaVersionMenuExpanded by remember { mutableStateOf(false) }
    var selectedJavaMajorVersion by remember { mutableStateOf(recommendedJavaMajorVersion(minecraftVersion)) }
    var maxPlayers by remember { mutableStateOf("20") }
    var memoryMb by remember { mutableStateOf("2048") }
    var port by remember { mutableStateOf("25565") }
    val resolvedMaxPlayers = maxPlayers.toIntOrNull()?.coerceIn(1, 200) ?: 20
    val resolvedMemoryMb = memoryMb.toIntOrNull()?.coerceAtLeast(512) ?: 2048
    val resolvedPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: 25565
    val recommendedJava = remember(minecraftVersion) { recommendedJavaMajorVersion(minecraftVersion) }
    val javaVersionOptions = remember(
        supportedProvisionableJavaVersions,
        selectedJavaMajorVersion,
        recommendedJava,
    ) {
        buildList {
            add(recommendedJava)
            add(selectedJavaMajorVersion)
            addAll(supportedProvisionableJavaVersions)
        }.distinct().sorted()
    }
    LaunchedEffect(minecraftVersion, javaSelectionMode) {
        if (javaSelectionMode == JavaSelectionMode.Recommended) {
            selectedJavaMajorVersion = recommendedJava
        }
    }
    LaunchedEffect(selectedServerType) {
        val defaultName = when (selectedServerType) {
            MinecraftServerType.Vanilla -> "Vanilla 服务器"
            MinecraftServerType.Paper -> "Paper 服务器"
            MinecraftServerType.Purpur -> "Purpur 服务器"
        }
        if (name.isBlank() || name == lastAutoGeneratedName) {
            name = defaultName
        }
        lastAutoGeneratedName = defaultName
    }
    LaunchedEffect(selectedServerType, vanillaVersionOptions, paperVersionOptions, purpurVersionOptions) {
        val versionOptions = when (selectedServerType) {
            MinecraftServerType.Vanilla -> vanillaVersionOptions
            MinecraftServerType.Paper -> paperVersionOptions
            MinecraftServerType.Purpur -> purpurVersionOptions
        }
        if (minecraftVersion !in versionOptions || versionWasAutoSelected) {
            minecraftVersion = versionOptions.lastOrNull().orEmpty()
            versionWasAutoSelected = true
        }
    }
    val canCreate = name.isNotBlank() && minecraftVersion.isNotBlank()

    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(
                enabled = canCreate,
                onClick = {
                    val server = when (selectedServerType) {
                        MinecraftServerType.Vanilla -> createVanillaServer(
                            name = name,
                            minecraftVersion = minecraftVersion,
                            maxPlayers = resolvedMaxPlayers,
                            memoryMb = resolvedMemoryMb,
                            port = resolvedPort,
                            javaMajorVersion = selectedJavaMajorVersion,
                            javaSelectionMode = javaSelectionMode,
                        )
                        MinecraftServerType.Paper -> createPaperServer(
                            name = name,
                            minecraftVersion = minecraftVersion,
                            maxPlayers = resolvedMaxPlayers,
                            memoryMb = resolvedMemoryMb,
                            port = resolvedPort,
                            javaMajorVersion = selectedJavaMajorVersion,
                            javaSelectionMode = javaSelectionMode,
                        )
                        MinecraftServerType.Purpur -> createPurpurServer(
                            name = name,
                            minecraftVersion = minecraftVersion,
                            maxPlayers = resolvedMaxPlayers,
                            memoryMb = resolvedMemoryMb,
                            port = resolvedPort,
                            javaMajorVersion = selectedJavaMajorVersion,
                            javaSelectionMode = javaSelectionMode,
                        )
                    }
                    onCreate(server)
                    onDismiss()
                },
            ) {
                Text("创建服务器")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("创建服务器") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MinecraftServerType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedServerType == type,
                            onClick = { selectedServerType = type },
                            label = { Text(type.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务器名称") },
                    singleLine = true,
                )
                ExposedDropdownMenuBox(
                    expanded = versionMenuExpanded,
                    onExpandedChange = { versionMenuExpanded = !versionMenuExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val versionOptions = when (selectedServerType) {
                        MinecraftServerType.Vanilla -> vanillaVersionOptions
                        MinecraftServerType.Paper -> paperVersionOptions
                        MinecraftServerType.Purpur -> purpurVersionOptions
                    }
                    OutlinedTextField(
                        value = minecraftVersion,
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        readOnly = true,
                        label = { Text("Minecraft 版本") },
                        supportingText = {
                            Text(
                                when (selectedServerType) {
                                    MinecraftServerType.Vanilla -> "从 Vanilla 官方版本列表选择"
                                    MinecraftServerType.Paper -> "从 Paper 官方版本列表选择"
                                    MinecraftServerType.Purpur -> "从 Purpur 官方版本列表选择"
                                },
                            )
                        },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = versionMenuExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = versionMenuExpanded,
                        onDismissRequest = { versionMenuExpanded = false },
                    ) {
                        versionOptions.asReversed().forEach { version ->
                            DropdownMenuItem(
                                text = { Text(version) },
                                onClick = {
                                    minecraftVersion = version
                                    versionWasAutoSelected = false
                                    versionMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = javaVersionMenuExpanded,
                    onExpandedChange = { javaVersionMenuExpanded = !javaVersionMenuExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = if (javaSelectionMode == JavaSelectionMode.Recommended) {
                            "自动"
                        } else {
                            "Java $selectedJavaMajorVersion"
                        },
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        readOnly = true,
                        label = { Text("运行时 Java") },
                        supportingText = {
                            Text("当前推荐：Java $recommendedJava")
                        },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = javaVersionMenuExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = javaVersionMenuExpanded,
                        onDismissRequest = { javaVersionMenuExpanded = false },
                    ) {
                        val javaMenuOptions = listOf<String>("自动") + javaVersionOptions.map { "Java $it" }
                        javaMenuOptions.forEach { selected ->
                            DropdownMenuItem(
                                text = { Text(selected) },
                                onClick = {
                                    if (selected == "自动") {
                                        javaSelectionMode = JavaSelectionMode.Recommended
                                        selectedJavaMajorVersion = recommendedJava
                                    } else {
                                        javaSelectionMode = JavaSelectionMode.Manual
                                        selectedJavaMajorVersion = selected.removePrefix("Java ").toIntOrNull() ?: selectedJavaMajorVersion
                                    }
                                    javaVersionMenuExpanded = false
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
    allServers: List<ServerCardState>,
    availableTunnels: List<TunnelProfile>,
    frpRuntimeSupported: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (tunnelId: String?, startupPort: Int, remotePort: Int?) -> Unit,
) {
    var selectedTunnelId by remember(server.name) { mutableStateOf(server.selectedTunnelId) }
    val selectedTunnel = remember(selectedTunnelId, availableTunnels) {
        availableTunnels.firstOrNull { it.id == selectedTunnelId }
    }
    var portInput by remember(server.name) { mutableStateOf(server.defaultPort.toString()) }
    var remotePortInput by remember(server.name) { mutableStateOf(server.tunnelRemotePort?.toString().orEmpty()) }

    LaunchedEffect(selectedTunnelId) {
        portInput = (selectedTunnel?.resolveStartupPort(server.defaultPort, server.defaultPort) ?: server.defaultPort).toString()
        remotePortInput = selectedTunnel?.let { tunnel ->
            runCatching {
                assignTunnelRemotePort(
                    server = if (server.selectedTunnelId == tunnel.id) server else server.copy(tunnelRemotePort = null),
                    tunnel = tunnel,
                    requestedRemotePort = null,
                    servers = allServers,
                ).toString()
            }.getOrDefault("")
        }.orEmpty()
    }

    val canEditPort = selectedTunnel?.supportsCustomPortOnStart() == true
    val runtimeTunnelSupported = selectedTunnel == null || (selectedTunnel.kind == com.mcgo.app.ui.model.TunnelKind.Frp && frpRuntimeSupported)
    val resolvedPort = selectedTunnel?.resolveStartupPort(server.defaultPort, portInput.toIntOrNull()) ?: server.defaultPort
    val resolvedRemotePort = selectedTunnel?.let { tunnel ->
        remotePortInput.toIntOrNull()
            ?: runCatching {
                assignTunnelRemotePort(
                    server = if (server.selectedTunnelId == tunnel.id) server else server.copy(tunnelRemotePort = null),
                    tunnel = tunnel,
                    requestedRemotePort = null,
                    servers = allServers,
                )
            }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedTunnelId, resolvedPort, resolvedRemotePort) }, enabled = runtimeTunnelSupported) {
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
                                val reserved = runCatching {
                                    assignTunnelRemotePort(
                                        server = if (server.selectedTunnelId == tunnel.id) server else server.copy(tunnelRemotePort = null),
                                        tunnel = tunnel,
                                        requestedRemotePort = null,
                                        servers = allServers,
                                    )
                                }.getOrNull()
                                "${tunnel.kind.label} · 远端 ${reserved ?: "自动分配"}"
                            } else {
                                "${tunnel.kind.label} · 固定远端 ${tunnel.remotePort ?: "未解析"}"
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
                    label = { Text("本地开服端口") },
                    supportingText = {
                        Text(
                            if (canEditPort) "本地端口用于 Paper 监听，可与隧道远端端口不同"
                            else "当前模式使用固定本地端口：$resolvedPort",
                        )
                    },
                    singleLine = true,
                )
                if (selectedTunnel != null) {
                    OutlinedTextField(
                        value = remotePortInput,
                        onValueChange = { remotePortInput = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedTunnel.supportsCustomPortOnStart(),
                        label = { Text("隧道远端端口") },
                        supportingText = {
                            Text("首次默认自动分配未占用端口；之后默认沿用这个端口，也可手动修改")
                        },
                        singleLine = true,
                    )
                }
                selectedTunnel?.let { tunnel ->
                    Text(
                        text = tunnel.connectionSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!runtimeTunnelSupported) {
                        Text(
                            text = if (!frpRuntimeSupported) {
                                "当前设备暂不支持内置 FRP 客户端，仅 arm64-v8a 设备可真启动隧道。"
                            } else {
                                "当前仅支持 FRP 隧道真启动；该隧道类型会导致启动失败。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
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
