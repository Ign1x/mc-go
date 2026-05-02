package com.mcgo.app.server

import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.finalizePendingServerDeletion
import com.mcgo.app.ui.model.markLaunchFailed
import com.mcgo.app.ui.model.markLaunchRunning
import com.mcgo.app.ui.model.withLaunchProgress
import com.mcgo.app.ui.storage.ServerProfileStore
import java.nio.file.Path

fun reducePaperRuntimeEvent(server: ServerCardState, event: PaperServerEvent): ServerCardState = when (event.status) {
    PaperServerEventStatus.Running -> server.markLaunchRunning(event.message)
    PaperServerEventStatus.Failed -> server.markLaunchFailed(event.message)
    PaperServerEventStatus.Stopping -> server.markLaunchStopping(event.message)
    PaperServerEventStatus.Stopped -> server.clearRuntimeState(ServerLaunchStatus.Stopped, event.message)
    PaperServerEventStatus.Launching -> server.withLaunchProgress(
        progress = event.progress ?: server.launchProgress,
        logLine = event.message,
        status = ServerLaunchStatus.Launching,
        online = false,
    )
    null -> server.copy(runtimeLogs = (server.runtimeLogs + event.message).takeLast(12))
}

fun syncPaperRuntimeEvent(filesDir: Path, event: PaperServerEvent) {
    val store = ServerProfileStore(filesDir.resolve("server_profiles.properties"))
    val servers = store.load()
    if (servers.isEmpty()) return
    val updated = finalizePendingServerDeletion(
        servers.map { server ->
            if (server.id == event.serverId) reducePaperRuntimeEvent(server, event) else server
        },
    )
    if (updated != servers) {
        store.save(updated)
    }
}

fun reconcilePersistedRuntimeState(
    servers: List<ServerCardState>,
    runtimeAlive: Boolean,
): List<ServerCardState> = if (runtimeAlive) {
    servers
} else {
    servers.map { server ->
        if (
            server.launchStatus == ServerLaunchStatus.Launching ||
            server.launchStatus == ServerLaunchStatus.Stopping ||
            server.launchStatus == ServerLaunchStatus.Running ||
            server.isOnline
        ) {
            server.clearRuntimeState(ServerLaunchStatus.Stopped, "运行时进程已结束，已恢复为空闲状态")
        } else {
            server
        }
    }
}

private fun ServerCardState.markLaunchStopping(message: String): ServerCardState = copy(
    launchStatus = ServerLaunchStatus.Stopping,
    launchProgress = 0,
    runtimeLogs = (runtimeLogs + message).takeLast(12),
)

private fun ServerCardState.clearRuntimeState(status: ServerLaunchStatus, message: String): ServerCardState = copy(
    isOnline = false,
    port = defaultPort,
    activeTunnelLabel = null,
    launchStatus = status,
    launchProgress = 0,
    runtimeLogs = (runtimeLogs + message).takeLast(12),
)
