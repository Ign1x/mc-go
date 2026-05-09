package com.mcgo.app.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mcgo.app.ui.model.ServerTunnelBinding
import java.util.concurrent.Executors

const val PaperRuntimeEventAction = "com.mcgo.app.server.RUNTIME_EVENT"
private const val ExtraServerId = "serverId"
private const val ExtraStatus = "status"
private const val ExtraProgress = "progress"
private const val ExtraMessage = "message"
private const val ExtraActiveTunnelLabel = "activeTunnelLabel"
private const val ExtraRuntimeAddress = "runtimeAddress"
private const val ExtraTunnelBindingCount = "tunnelBindingCount"

fun Context.sendPaperRuntimeEvent(event: PaperServerEvent) {
    sendBroadcast(
        Intent(this, PaperRuntimeEventReceiver::class.java).apply {
            action = PaperRuntimeEventAction
            putExtra(ExtraServerId, event.serverId)
            putExtra(ExtraStatus, event.status?.name)
            putExtra(ExtraProgress, event.progress)
            putExtra(ExtraMessage, event.message)
            putExtra(ExtraActiveTunnelLabel, event.activeTunnelLabel)
            putExtra(ExtraRuntimeAddress, event.runtimeAddress)
            putExtra(ExtraTunnelBindingCount, event.tunnelBindings.size)
            event.tunnelBindings.forEachIndexed { index, binding ->
                putExtra("tunnelBinding.$index.tunnelId", binding.tunnelId)
                putExtra("tunnelBinding.$index.remotePort", binding.remotePort ?: -1)
                putExtra("tunnelBinding.$index.activeLabel", binding.activeLabel)
                putExtra("tunnelBinding.$index.runtimeAddress", binding.runtimeAddress)
            }
        },
    )
}

class PaperRuntimeEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PaperRuntimeEventAction) return
        val pendingResult = goAsync()
        RuntimeEventSyncExecutor.execute {
            try {
                val tunnelBindingCount = intent.getIntExtra(ExtraTunnelBindingCount, 0)
                val tunnelBindings = (0 until tunnelBindingCount).mapNotNull { index ->
                    val tunnelId = intent.getStringExtra("tunnelBinding.$index.tunnelId") ?: return@mapNotNull null
                    ServerTunnelBinding(
                        tunnelId = tunnelId,
                        remotePort = intent.getIntExtra("tunnelBinding.$index.remotePort", -1).takeIf { it > 0 },
                        activeLabel = intent.getStringExtra("tunnelBinding.$index.activeLabel"),
                        runtimeAddress = intent.getStringExtra("tunnelBinding.$index.runtimeAddress"),
                    )
                }
                val event = PaperServerEvent(
                    serverId = intent.getStringExtra(ExtraServerId) ?: return@execute,
                    status = intent.getStringExtra(ExtraStatus)?.let(PaperServerEventStatus::valueOf),
                    progress = intent.getIntExtra(ExtraProgress, -1).takeIf { it >= 0 },
                    message = intent.getStringExtra(ExtraMessage).orEmpty(),
                    activeTunnelLabel = intent.getStringExtra(ExtraActiveTunnelLabel),
                    runtimeAddress = intent.getStringExtra(ExtraRuntimeAddress),
                    tunnelBindings = tunnelBindings,
                )
                syncPaperRuntimeEvent(context, event)
                PaperServerEvents.publish(event)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private val RuntimeEventSyncExecutor = Executors.newSingleThreadExecutor()
