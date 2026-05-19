package com.mcgo.app.ui

import android.net.Uri
import com.mcgo.app.server.ManagedServerWorkspaceMode
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.TunnelLaunchSelection
import java.nio.file.Path

internal data class PendingStartRequest(
    val serverId: String,
    val startupPort: Int,
    val tunnelSelections: List<TunnelLaunchSelection>,
)

internal data class PendingManagedRuntimeStart(
    val request: PendingStartRequest,
    val javaMajorVersion: Int,
)

internal data class PendingModpackSetupApproval(
    val request: PendingStartRequest,
    val serverName: String,
    val defaultScriptRelativePath: String,
    val scriptCandidates: List<String>,
    val workspaceMode: ManagedServerWorkspaceMode,
)

internal data class PendingCreateServerFromModpack(
    val server: ServerCardState,
    val archiveUri: Uri,
)

internal fun managedSetupScriptRelativePath(serverWorkDir: Path, script: Path): String =
    serverWorkDir.toAbsolutePath().normalize()
        .relativize(script.toAbsolutePath().normalize())
        .toString()
        .replace('\\', '/')

internal enum class PendingServerDirectoryAction {
    StartServer,
    OpenConsole,
    EditServer,
    SettingsRequest,
}
