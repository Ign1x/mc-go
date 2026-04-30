package com.mcgo.app.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TermuxCommandResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val serverId = intent?.getStringExtra("serverId") ?: return
        val result = intent.getBundleExtra("result")
        val stdout = result?.getString("stdout").orEmpty()
        val stderr = result?.getString("stderr").orEmpty()
        val exitCode = result?.takeIf { it.containsKey("exitCode") }?.getInt("exitCode")
        val errCode = result?.takeIf { it.containsKey("err") }?.getInt("err")
        val errmsg = result?.getString("errmsg").orEmpty()
        val output = listOf(stdout, stderr, errmsg)
            .flatMap { it.lineSequence().toList() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .takeLast(10)
            .joinToString(separator = "\n")
            .take(1800)
        val message = buildString {
            append("Termux 返回")
            if (exitCode != null) append("：$exitCode")
            if (errCode != null && errCode != 0) append("；错误码：$errCode")
            if (output.isNotBlank()) append("\n").append(output)
        }
        val status = classifyTermuxResultStatus(
            resultMissing = result == null,
            errCode = errCode,
            exitCode = exitCode,
            errmsg = errmsg,
        )
        PaperServerEvents.publish(
            PaperServerEvent(
                serverId = serverId,
                status = status,
                progress = if (status == PaperServerEventStatus.Failed) 0 else null,
                message = message,
            ),
        )
    }
}

fun classifyTermuxResultStatus(
    resultMissing: Boolean,
    errCode: Int?,
    exitCode: Int?,
    errmsg: String,
): PaperServerEventStatus {
    val normalStopCodes = setOf(0, 130, 137, 143)
    val failed = resultMissing ||
        (errCode != null && errCode != 0) ||
        (exitCode != null && exitCode !in normalStopCodes) ||
        (exitCode == null && errmsg.isNotBlank())
    return if (failed) PaperServerEventStatus.Failed else PaperServerEventStatus.Stopped
}
