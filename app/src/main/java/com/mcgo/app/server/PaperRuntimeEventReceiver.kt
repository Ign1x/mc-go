package com.mcgo.app.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

const val PaperRuntimeEventAction = "com.mcgo.app.server.RUNTIME_EVENT"
private const val ExtraServerId = "serverId"
private const val ExtraStatus = "status"
private const val ExtraProgress = "progress"
private const val ExtraMessage = "message"

fun Context.sendPaperRuntimeEvent(event: PaperServerEvent) {
    sendBroadcast(
        Intent(this, PaperRuntimeEventReceiver::class.java).apply {
            action = PaperRuntimeEventAction
            putExtra(ExtraServerId, event.serverId)
            putExtra(ExtraStatus, event.status?.name)
            putExtra(ExtraProgress, event.progress)
            putExtra(ExtraMessage, event.message)
        },
    )
}

class PaperRuntimeEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PaperRuntimeEventAction) return
        val event = PaperServerEvent(
            serverId = intent.getStringExtra(ExtraServerId) ?: return,
            status = intent.getStringExtra(ExtraStatus)?.let(PaperServerEventStatus::valueOf),
            progress = intent.getIntExtra(ExtraProgress, -1).takeIf { it >= 0 },
            message = intent.getStringExtra(ExtraMessage).orEmpty(),
        )
        syncPaperRuntimeEvent(context.filesDir.toPath(), event)
        PaperServerEvents.publish(event)
    }
}
