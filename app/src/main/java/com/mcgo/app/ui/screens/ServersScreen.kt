package com.mcgo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.formatPlayerCapacity
import com.mcgo.app.ui.sample.McGoSampleRepository
import com.mcgo.app.ui.theme.Green500
import com.mcgo.app.ui.theme.Ink600
import com.mcgo.app.ui.theme.Red500
import com.mcgo.app.ui.theme.SurfaceSoft

@Composable
fun ServersScreen(
    modifier: Modifier = Modifier,
    showLeadCard: Boolean = false,
    onActionClick: () -> Unit,
) {
    val servers = McGoSampleRepository.serverCards()

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }
        if (showLeadCard) {
            item {
                GlassCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(text = stringResource(R.string.servers_overview_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.servers_overview_body, servers.size, servers.count { it.isOnline }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink600,
                    )
                }
            }
        }
        items(items = servers, key = { it.name }) { server ->
            ServerCard(
                server = server,
                modifier = Modifier.padding(horizontal = 20.dp),
                onActionClick = onActionClick,
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
) {
    val statusColor = if (server.isOnline) Green500 else Red500
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
                    color = SurfaceSoft,
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
                        color = Ink600,
                    )
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
                onClick = onActionClick,
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
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = Ink600)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
