package com.mcgo.app.server

import android.content.Context
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.ServerLaunchStatus
import com.mcgo.app.ui.model.clearTunnelRuntimeBindings
import com.mcgo.app.ui.model.effectiveTunnelBindings
import com.mcgo.app.ui.model.finalizePendingServerDeletion
import com.mcgo.app.ui.model.isRuntimeBusy
import com.mcgo.app.ui.model.markLaunchFailed
import com.mcgo.app.ui.model.markLaunchRunning
import com.mcgo.app.ui.model.withLaunchProgress
import com.mcgo.app.ui.model.withTunnelBindings
import com.mcgo.app.ui.storage.ServerProfileStore
import com.mcgo.app.ui.storage.ServerProfileStoreGlobalLock
import java.nio.file.Path

fun reducePaperRuntimeEvent(server: ServerCardState, event: PaperServerEvent): ServerCardState {
    val mergedTunnelBindings = when {
        event.tunnelBindings.isNotEmpty() -> event.tunnelBindings
        else -> server.effectiveTunnelBindings()
    }
    val mergedServer = server.withTunnelBindings(mergedTunnelBindings.map { binding ->
        val primaryFallbackAddress = event.runtimeAddress ?: binding.runtimeAddress
        val primaryFallbackLabel = event.activeTunnelLabel ?: binding.activeLabel
        binding.copy(
            activeLabel = if (event.tunnelBindings.isNotEmpty()) binding.activeLabel else primaryFallbackLabel,
            runtimeAddress = if (event.tunnelBindings.isNotEmpty()) binding.runtimeAddress else primaryFallbackAddress,
        )
    })
    val resolvedOnlinePlayers = event.onlinePlayers ?: mergedServer.onlinePlayers
    val resolvedOnlinePlayerNames = event.onlinePlayerNames ?: mergedServer.onlinePlayerNames
    return when (event.status) {
        PaperServerEventStatus.Running -> mergedServer.markLaunchRunning(event.message).copy(
            onlinePlayers = resolvedOnlinePlayers,
            onlinePlayerNames = resolvedOnlinePlayerNames,
        )
        PaperServerEventStatus.Failed -> mergedServer.markLaunchFailed(event.message)
        PaperServerEventStatus.Stopping -> mergedServer.markLaunchStopping(event.message).copy(
            onlinePlayers = resolvedOnlinePlayers,
            onlinePlayerNames = resolvedOnlinePlayerNames,
        )
        PaperServerEventStatus.Stopped -> mergedServer.clearRuntimeState(ServerLaunchStatus.Stopped, event.message)
        PaperServerEventStatus.Launching -> mergedServer.withLaunchProgress(
            progress = event.progress ?: mergedServer.launchProgress,
            logLine = event.message,
            status = ServerLaunchStatus.Launching,
            online = false,
        ).copy(
            onlinePlayers = resolvedOnlinePlayers,
            onlinePlayerNames = resolvedOnlinePlayerNames,
        )
        null -> mergedServer.copy(
            onlinePlayers = resolvedOnlinePlayers,
            onlinePlayerNames = resolvedOnlinePlayerNames,
            runtimeLogs = (mergedServer.runtimeLogs + event.message).takeLast(12),
        )
    }
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

fun syncPaperRuntimeEvent(context: Context, event: PaperServerEvent) = synchronized(ServerProfileStoreGlobalLock) {
    val filesDir = context.filesDir.toPath()
    val store = ServerProfileStore(filesDir.resolve("server_profiles.properties"))
    val servers = store.load()
    if (servers.isEmpty()) return@synchronized
    val updated = finalizePendingServerDeletion(
        servers.map { server ->
            if (server.id == event.serverId) reducePaperRuntimeEvent(server, event) else server
        },
    )
    if (updated != servers) {
        val removedPendingDeletionServerIds = servers
            .filter { it.pendingDeletion }
            .map { it.id }
            .filter { removedId -> updated.none { it.id == removedId } }
        store.save(updated)
        removedPendingDeletionServerIds.forEach { serverId ->
            deleteManagedServerWorkspaceFromAuthorizedDirectory(
                context = context,
                authorizedDirectoryUri = context.getSharedPreferences("mcgo_runtime_permissions", Context.MODE_PRIVATE)
                    .getString("server_directory_uri", null),
                serverId = serverId,
            )
            deleteManagedServerWorkspaceFromPrivateDirectory(filesDir, serverId)
        }
        syncServerProfilesToAuthorizedDirectory(
            context = context,
            authorizedDirectoryUri = context.getSharedPreferences("mcgo_runtime_permissions", Context.MODE_PRIVATE)
                .getString("server_directory_uri", null),
            sourceProfilesPath = filesDir.resolve("server_profiles.properties"),
        )
    }
}

fun reconcilePersistedRuntimeState(
    servers: List<ServerCardState>,
    runtimeAlive: Boolean,
): List<ServerCardState> = if (runtimeAlive) {
    servers
} else {
    reconcilePersistedRuntimeState(
        servers = servers,
        activeRuntimeSlots = emptySet(),
    )
}

fun reconcilePersistedRuntimeState(
    servers: List<ServerCardState>,
    activeRuntimeSlots: Set<Int>,
): List<ServerCardState> = servers.map { server ->
    val effectiveRuntimeSlot = server.runtimeSlot ?: if (activeRuntimeSlots.contains(1) && server.isRuntimeBusy()) 1 else null
    val runtimeSlotAlive = effectiveRuntimeSlot?.let(activeRuntimeSlots::contains) ?: false
    if (
        (server.launchStatus == ServerLaunchStatus.Launching ||
            server.launchStatus == ServerLaunchStatus.Stopping ||
            server.launchStatus == ServerLaunchStatus.Running ||
            server.isOnline) &&
            !runtimeSlotAlive
    ) {
        server.clearRuntimeState(ServerLaunchStatus.Stopped, "运行时进程已结束，已恢复为空闲状态")
    } else {
        if (server.runtimeSlot == null && effectiveRuntimeSlot != null) server.copy(runtimeSlot = effectiveRuntimeSlot) else server
    }
}

private fun ServerCardState.markLaunchStopping(message: String): ServerCardState = copy(
    launchStatus = ServerLaunchStatus.Stopping,
    launchProgress = 0,
    runtimeLogs = (runtimeLogs + message).takeLast(12),
)

private fun ServerCardState.clearRuntimeState(status: ServerLaunchStatus, message: String): ServerCardState = clearTunnelRuntimeBindings().copy(
    isOnline = false,
    onlinePlayers = 0,
    onlinePlayerNames = emptyList(),
    port = defaultPort,
    activeTunnelLabel = null,
    runtimeAddress = null,
    launchStatus = status,
    launchProgress = 0,
    runtimeLogs = (runtimeLogs + message).takeLast(12),
    runtimeSlot = null,
)
