package com.mcgo.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.resolveServerConsoleText

@Composable
internal fun RuntimeProgressPanel(server: ServerCardState, modpackImportInProgress: Boolean = false) {
    val context = LocalContext.current
    val consoleText = remember(server.runtimeLogPath, server.runtimeLogs) { resolveServerConsoleText(server) }
    val latestRuntimeLog = server.runtimeLogs.lastOrNull().orEmpty()
    val importProgressActive = isModpackImportProgressActive(
        modpackImportInProgress = modpackImportInProgress,
        latestRuntimeLog = latestRuntimeLog,
    )
    val progressTitle = runtimeProgressTitle(
        launchStatus = server.launchStatus,
        importProgressActive = importProgressActive,
    )
    val progressColor = if (importProgressActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
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
        server.runtimeLogs.takeLast(6).forEach { log ->
            Text(
                text = "• $log",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun isModpackImportProgressActive(
    modpackImportInProgress: Boolean,
    latestRuntimeLog: String,
): Boolean = modpackImportInProgress ||
    latestRuntimeLog.contains("导入整合包") ||
    latestRuntimeLog.contains("解压整合包") ||
    latestRuntimeLog.contains("整合包导入")

internal fun runtimeProgressTitle(
    launchStatus: ServerLaunchStatus,
    importProgressActive: Boolean,
): String = when {
    launchStatus == ServerLaunchStatus.Stopping -> "停止进度"
    importProgressActive -> "导入进度"
    else -> "启动进度"
}
