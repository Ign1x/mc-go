package com.mcgo.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.TunnelKind
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.TunnelProtocol
import com.mcgo.app.ui.model.TunnelSource
import com.mcgo.app.ui.model.importTunnelProfile

@Composable
fun TunnelsScreen(
    tunnels: List<TunnelProfile>,
    showComposer: Boolean,
    onDismissComposer: () -> Unit,
    onAddTunnel: (TunnelProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (showComposer) {
        TunnelComposerDialog(
            onDismiss = onDismissComposer,
            onAddTunnel = {
                onAddTunnel(it)
                onDismissComposer()
            },
        )
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            TunnelOverviewCard(
                tunnels = tunnels,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        if (tunnels.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(text = "还没有隧道", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "先添加 FRP 或其他隧道，开服时就能直接选择使用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(items = tunnels, key = { it.id }) { tunnel ->
                TunnelCard(
                    tunnel = tunnel,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        item { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
private fun TunnelOverviewCard(
    tunnels: List<TunnelProfile>,
    modifier: Modifier = Modifier,
) {
    val manualCount = tunnels.count { it.source == TunnelSource.ManualServer }
    val configCount = tunnels.count { it.source == TunnelSource.PastedConfig }
    GlassCard(modifier = modifier) {
        Text(text = "隧道列表", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "参数模板 $manualCount 个 · 单隧道配置 $configCount 个 · 支持开服时选择使用",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TunnelCard(
    tunnel: TunnelProfile,
    modifier: Modifier = Modifier,
) {
    val accent = latencyColor(tunnel.currentLatencyMs)
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
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(
                        imageVector = tunnelIcon(tunnel.kind),
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = tunnel.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${tunnel.kind.label} · ${tunnel.startupModeLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LatencyBadge(latencyMs = tunnel.currentLatencyMs, healthLabel = tunnel.healthLabel, accent = accent)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = tunnel.connectionSummary(),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = tunnel.detailSummary(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tunnel.formatLabel()?.let { formatLabel ->
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = formatLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                tunnel.localPort?.let {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accent.copy(alpha = 0.14f),
                        contentColor = accent,
                    ) {
                        Text(
                            text = "本地 $it",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LatencyBadge(
    latencyMs: Int,
    healthLabel: String,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.14f),
        contentColor = accent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "$latencyMs ms", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(text = healthLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TunnelComposerDialog(
    onDismiss: () -> Unit,
    onAddTunnel: (TunnelProfile) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(TunnelSource.ManualServer.name) }
    var manualName by rememberSaveable { mutableStateOf("") }
    var manualKind by rememberSaveable { mutableStateOf(TunnelKind.Frp.name) }
    var manualAddress by rememberSaveable { mutableStateOf("") }
    var manualRemotePort by rememberSaveable { mutableStateOf("38001") }
    var manualProtocol by rememberSaveable { mutableStateOf(TunnelProtocol.Tcp.label) }
    var importAlias by rememberSaveable { mutableStateOf("") }
    var importText by rememberSaveable { mutableStateOf("") }

    val importPreview = remember(importAlias, importText) {
        importText.takeIf { it.isNotBlank() }?.let {
            importTunnelProfile(
                rawConfig = it,
                fallbackName = importAlias.ifBlank { "导入隧道" },
            )
        }
    }
    val isManualMode = mode == TunnelSource.ManualServer.name
    val canSaveManual = manualAddress.isNotBlank()
    val canSaveImport = importPreview != null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (isManualMode) {
                        onAddTunnel(
                            TunnelProfile.manualServer(
                                name = manualName.ifBlank { "${TunnelKind.valueOf(manualKind).label} 节点" },
                                kind = TunnelKind.valueOf(manualKind),
                                serverAddress = manualAddress,
                                remotePort = manualRemotePort.toIntOrNull(),
                                protocol = TunnelProtocol.fromLabel(manualProtocol),
                            ),
                        )
                    } else {
                        importPreview?.let(onAddTunnel)
                    }
                },
                enabled = if (isManualMode) canSaveManual else canSaveImport,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = { Text("添加隧道") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TunnelSource.entries.forEach { source ->
                        FilterChip(
                            selected = mode == source.name,
                            onClick = { mode = source.name },
                            label = { Text(if (source == TunnelSource.ManualServer) "填写参数" else "粘贴配置") },
                            colors = themedFilterChipColors(),
                        )
                    }
                }
                if (isManualMode) {
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("隧道名称") },
                        singleLine = true,
                    )
                    ChoiceChipRow(
                        title = "隧道类型",
                        options = TunnelKind.entries.map { it.label },
                        selectedOption = TunnelKind.valueOf(manualKind).label,
                        onSelected = { selected -> manualKind = TunnelKind.entries.first { it.label == selected }.name },
                    )
                    OutlinedTextField(
                        value = manualAddress,
                        onValueChange = { manualAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("服务器地址") },
                        supportingText = { Text("例如 frp.example.com / playit.gg") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = manualRemotePort,
                        onValueChange = { manualRemotePort = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("远端端口") },
                        supportingText = { Text("本地开服端口在启动实例时再自定义") },
                        singleLine = true,
                    )
                    ChoiceChipRow(
                        title = "协议",
                        options = TunnelProtocol.entries.map { it.label },
                        selectedOption = manualProtocol,
                        onSelected = { manualProtocol = it },
                    )
                } else {
                    OutlinedTextField(
                        value = importAlias,
                        onValueChange = { importAlias = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("显示名称（可选）") },
                        supportingText = { Text("如果配置里带 name，会优先使用配置里的隧道名") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        label = { Text("粘贴配置内容") },
                    )
                    importPreview?.let { preview ->
                        GlassCard {
                            Text(text = "自动识别", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${preview.kind.label} · ${preview.formatLabel() ?: "纯文本"} · ${preview.startupModeLabel()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = preview.connectionSummary(), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preview.detailSummary(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ChoiceChipRow(
    title: String,
    options: List<String>,
    selectedOption: String,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selectedOption,
                    onClick = { onSelected(option) },
                    label = { Text(option) },
                    colors = themedFilterChipColors(),
                )
            }
        }
    }
}

@Composable
private fun themedFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
)

@Composable
private fun tunnelIcon(kind: TunnelKind) = when (kind) {
    TunnelKind.Frp -> Icons.Outlined.Router
    TunnelKind.Nps -> Icons.Outlined.Lan
    TunnelKind.Playit -> Icons.Outlined.AddLink
    TunnelKind.Tailscale -> Icons.Outlined.Dns
    TunnelKind.Custom -> Icons.Outlined.ContentPaste
}

@Composable
private fun latencyColor(latencyMs: Int): Color = when {
    latencyMs <= 35 -> MaterialTheme.colorScheme.secondary
    latencyMs <= 70 -> MaterialTheme.colorScheme.primary
    latencyMs <= 110 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}
