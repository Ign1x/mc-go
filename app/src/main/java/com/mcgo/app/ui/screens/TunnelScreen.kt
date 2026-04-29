package com.mcgo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
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
import com.mcgo.app.ui.model.TunnelSource
import com.mcgo.app.ui.model.importTunnelProfile
import com.mcgo.app.ui.model.manualTunnelFieldSpec

@Composable
fun TunnelsScreen(
    tunnels: List<TunnelProfile>,
    showComposer: Boolean,
    editingTunnelId: String?,
    onDismissComposer: () -> Unit,
    onSaveTunnel: (TunnelProfile) -> Unit,
    onEditTunnel: (String) -> Unit,
    onDeleteTunnel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editingTunnel = remember(editingTunnelId, tunnels) {
        tunnels.firstOrNull { it.id == editingTunnelId }
    }

    if (showComposer) {
        TunnelComposerDialog(
            initialTunnel = editingTunnel,
            onDismiss = onDismissComposer,
            onSaveTunnel = {
                onSaveTunnel(it)
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
                        text = "默认先留空。你可以按自己的 FRP、NPS、Playit 或 Tailscale 方案逐个添加。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "添加后的隧道都可以继续编辑或删除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(items = tunnels, key = { it.id }) { tunnel ->
                TunnelCard(
                    tunnel = tunnel,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    onEdit = { onEditTunnel(tunnel.id) },
                    onDelete = { onDeleteTunnel(tunnel.id) },
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
            text = if (tunnels.isEmpty()) {
                "当前还没有隧道。添加后可以在这里看延迟、编辑参数或删除。"
            } else {
                "参数模板 $manualCount 个 · 单隧道配置 $configCount 个 · 支持后续编辑与删除"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TunnelCard(
    tunnel: TunnelProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tunnel.formatLabel()?.let { formatLabel ->
                TunnelMetaChip(
                    text = formatLabel,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }
            tunnel.portRange?.takeIf { it.isNotBlank() }?.let { portRange ->
                TunnelMetaChip(
                    text = "范围 $portRange",
                    containerColor = accent.copy(alpha = 0.14f),
                    contentColor = accent,
                )
            }
            tunnel.localPort?.let {
                TunnelMetaChip(
                    text = "本地 $it",
                    containerColor = accent.copy(alpha = 0.14f),
                    contentColor = accent,
                )
            }
            tunnel.remotePort?.let {
                TunnelMetaChip(
                    text = "远端 $it",
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        tunnel.rawConfigPreview?.let { preview ->
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
            ) {
                Text(
                    text = preview,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("编辑")
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(6.dp))
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TunnelMetaChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
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
    initialTunnel: TunnelProfile?,
    onDismiss: () -> Unit,
    onSaveTunnel: (TunnelProfile) -> Unit,
) {
    val editorKey = initialTunnel?.id ?: "new"
    var mode by rememberSaveable(editorKey) { mutableStateOf(initialTunnel?.source?.name ?: TunnelSource.ManualServer.name) }
    var manualName by rememberSaveable(editorKey) {
        mutableStateOf(if (initialTunnel?.source == TunnelSource.ManualServer) initialTunnel.name else "")
    }
    var manualKind by rememberSaveable(editorKey) { mutableStateOf((initialTunnel?.kind ?: TunnelKind.Frp).name) }
    var manualAddress by rememberSaveable(editorKey) {
        mutableStateOf(if (initialTunnel?.source == TunnelSource.ManualServer) initialTunnel.serverAddress else "")
    }
    var manualCredential by rememberSaveable(editorKey) {
        mutableStateOf(if (initialTunnel?.source == TunnelSource.ManualServer) initialTunnel.credentialValue.orEmpty() else "")
    }
    var manualPortRange by rememberSaveable(editorKey) {
        mutableStateOf(if (initialTunnel?.source == TunnelSource.ManualServer) initialTunnel.portRange.orEmpty() else "")
    }
    var importAlias by rememberSaveable(editorKey) {
        mutableStateOf(if (initialTunnel?.source == TunnelSource.PastedConfig) initialTunnel.name else "")
    }
    var importText by rememberSaveable(editorKey) {
        mutableStateOf(if (initialTunnel?.source == TunnelSource.PastedConfig) initialTunnel.rawConfigText.orEmpty() else "")
    }

    val selectedKind = TunnelKind.valueOf(manualKind)
    val manualSpec = remember(selectedKind) { manualTunnelFieldSpec(selectedKind) }
    val importPreview = remember(importAlias, importText) {
        importText.takeIf { it.isNotBlank() }?.let {
            importTunnelProfile(
                rawConfig = it,
                fallbackName = importAlias.ifBlank { initialTunnel?.name ?: "导入隧道" },
            )
        }
    }
    val isManualMode = mode == TunnelSource.ManualServer.name
    val canSaveManual = manualAddress.isNotBlank() && manualCredential.isNotBlank() && manualPortRange.isNotBlank()
    val canSaveImport = importPreview != null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (isManualMode) {
                        val newProfile = TunnelProfile.manualServer(
                            name = manualName.ifBlank { "${selectedKind.label} 节点" },
                            kind = selectedKind,
                            serverAddress = manualAddress,
                            credentialValue = manualCredential,
                            portRange = manualPortRange,
                        )
                        onSaveTunnel(
                            if (initialTunnel == null) newProfile else newProfile.copy(id = initialTunnel.id),
                        )
                    } else {
                        importPreview?.let { preview ->
                            onSaveTunnel(
                                if (initialTunnel == null) preview else preview.copy(id = initialTunnel.id),
                            )
                        }
                    }
                },
                enabled = if (isManualMode) canSaveManual else canSaveImport,
            ) {
                Text(if (initialTunnel == null) "保存" else "保存修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = { Text(if (initialTunnel == null) "添加隧道" else "编辑隧道") },
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
                        selectedOption = selectedKind.label,
                        onSelected = { selected -> manualKind = TunnelKind.entries.first { it.label == selected }.name },
                    )
                    OutlinedTextField(
                        value = manualAddress,
                        onValueChange = { manualAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(manualSpec.addressLabel) },
                        supportingText = { Text(manualSpec.addressHint) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = manualCredential,
                        onValueChange = { manualCredential = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(manualSpec.credentialLabel) },
                        supportingText = { Text(manualSpec.credentialHint) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = manualPortRange,
                        onValueChange = { manualPortRange = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(manualSpec.portRangeLabel) },
                        supportingText = { Text(manualSpec.portRangeHint) },
                        singleLine = true,
                    )
                    GlassCard {
                        Text(text = "保存后效果", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = listOfNotNull(
                                manualAddress.takeIf { it.isNotBlank() },
                                manualPortRange.takeIf { it.isNotBlank() }?.let { "端口范围 $it" },
                            ).joinToString(" · ").ifBlank { "待填写连接信息" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${selectedKind.label} 参数模板 · ${manualSpec.credentialLabel} 会被保存，开服时可继续改端口",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = importAlias,
                        onValueChange = { importAlias = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("显示名称（可选）") },
                        supportingText = { Text("如果配置里自带 name，会优先使用配置里的隧道名") },
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
