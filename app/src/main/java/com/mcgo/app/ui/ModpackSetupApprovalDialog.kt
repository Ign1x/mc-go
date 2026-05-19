package com.mcgo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ModpackSetupApprovalDialog(
    serverName: String,
    defaultScriptRelativePath: String,
    scriptCandidates: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var setupScriptInput by rememberSaveable(
        serverName,
        defaultScriptRelativePath,
    ) { mutableStateOf("") }
    val candidateScriptSummary = scriptCandidates
        .take(6)
        .joinToString("、")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入整合包启动脚本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$serverName 包含可执行脚本。MC-GO 不再猜测脚本名称，请输入要执行的服务器目录相对路径。")
                if (candidateScriptSummary.isNotBlank()) {
                    Text("可选脚本：$candidateScriptSummary")
                }
                OutlinedTextField(
                    value = setupScriptInput,
                    onValueChange = { setupScriptInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("脚本相对路径") },
                    placeholder = { Text("例如：run.sh 或 pack scripts/start.sh") },
                )
                Text("确认后会执行该脚本；脚本 stdout/stderr 会实时写入服务器运行日志，并显示在启动进度中。")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(setupScriptInput.trim()) },
            ) {
                Text("确认安装并启动")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
