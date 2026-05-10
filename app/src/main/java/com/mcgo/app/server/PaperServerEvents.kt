package com.mcgo.app.server

import com.mcgo.app.ui.model.ServerTunnelBinding
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class PaperServerEventStatus {
    Launching,
    Stopping,
    Running,
    Failed,
    Stopped,
}

data class PaperServerEvent(
    val serverId: String,
    val status: PaperServerEventStatus? = null,
    val progress: Int? = null,
    val message: String,
    val activeTunnelLabel: String? = null,
    val runtimeAddress: String? = null,
    val tunnelBindings: List<ServerTunnelBinding> = emptyList(),
    val onlinePlayers: Int? = null,
    val onlinePlayerNames: List<String>? = null,
)

object PaperServerEvents {
    private val mutableEvents = MutableSharedFlow<PaperServerEvent>(extraBufferCapacity = 96)
    val events = mutableEvents.asSharedFlow()

    fun publish(event: PaperServerEvent) {
        mutableEvents.tryEmit(event)
    }
}
