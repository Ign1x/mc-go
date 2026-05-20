package com.mcgo.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mcgo.app.ui.model.ConsoleErrorColor
import com.mcgo.app.ui.model.ConsoleInfoColor
import com.mcgo.app.ui.model.ConsoleTimestampColor
import com.mcgo.app.ui.model.ConsoleWarnColor
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.buildConsoleAnnotatedLog
import com.mcgo.app.ui.model.normalizeConsoleCommand
import com.mcgo.app.ui.model.resolveServerConsoleText

@Composable
internal fun ServerConsoleDialog(
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
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                ClipData.newPlainText("${server.name} logs", consoleText),
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
