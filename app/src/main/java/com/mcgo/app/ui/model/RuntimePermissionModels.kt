package com.mcgo.app.ui.model

enum class RuntimePermissionStatus {
    Granted,
    NeedsRequest,
}

enum class PermissionActionPlacement {
    StatusRowEnd,
}

data class RuntimePermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val androidPermission: String? = null,
    val required: Boolean = true,
    val status: RuntimePermissionStatus,
    val statusLabel: String,
    val actionLabel: String?,
    val detail: String? = null,
    val actionPlacement: PermissionActionPlacement = PermissionActionPlacement.StatusRowEnd,
)

data class RuntimePermissionState(
    val summaryLabel: String,
    val permissionItems: List<RuntimePermissionItem>,
)

fun defaultRuntimePermissionState(
    postNotificationsGranted: Boolean,
    wakeLockGranted: Boolean,
    foregroundServiceGranted: Boolean,
    serverDirectorySelected: Boolean,
    batteryOptimizationIgnored: Boolean,
    serverDirectoryUri: String? = null,
    termuxRunCommandGranted: Boolean = false,
): RuntimePermissionState {
    fun item(
        id: String,
        title: String,
        description: String,
        granted: Boolean,
        androidPermission: String? = null,
        required: Boolean = true,
        actionWhenMissing: String,
        detail: String? = null,
    ): RuntimePermissionItem = RuntimePermissionItem(
        id = id,
        title = title,
        description = description,
        androidPermission = androidPermission,
        required = required,
        status = if (granted) RuntimePermissionStatus.Granted else RuntimePermissionStatus.NeedsRequest,
        statusLabel = if (granted) "已授权" else "未授权",
        actionLabel = if (granted) null else actionWhenMissing,
        detail = detail,
    )

    return RuntimePermissionState(
        summaryLabel = "通知、目录、唤醒与后台运行权限",
        permissionItems = listOf(
            item(
                id = "post-notifications",
                title = "前台服务通知",
                description = "开服时显示常驻通知，方便停止服务器并降低后台被清理概率。",
                granted = postNotificationsGranted,
                androidPermission = "android.permission.POST_NOTIFICATIONS",
                actionWhenMissing = "申请",
            ),
            item(
                id = "wake-lock",
                title = "保持 CPU 唤醒",
                description = "服务器运行期间保持 Tick 与网络连接稳定。",
                granted = wakeLockGranted,
                androidPermission = "android.permission.WAKE_LOCK",
                actionWhenMissing = "检查",
            ),
            item(
                id = "foreground-service",
                title = "前台服务运行",
                description = "承载 Paper 进程、控制台和隧道守护逻辑。",
                granted = foregroundServiceGranted,
                androidPermission = "android.permission.FOREGROUND_SERVICE",
                actionWhenMissing = "检查",
            ),
            item(
                id = "termux-run-command",
                title = "Termux 启动桥接",
                description = "通过 Termux RUN_COMMAND 运行 OpenJDK，避免 Android 禁止执行应用私有目录里的下载二进制。",
                granted = termuxRunCommandGranted,
                androidPermission = "com.termux.permission.RUN_COMMAND",
                required = true,
                actionWhenMissing = "授权",
                detail = if (termuxRunCommandGranted) {
                    "已授权 MC-GO 调用 Termux；请确认 Termux 已安装 OpenJDK。"
                } else {
                    "需要安装 Termux，并在 Termux 设置 allow-external-apps=true。"
                },
            ),
            item(
                id = "server-directory",
                title = "服务器目录",
                description = "授权后用于导入、备份和编辑服务器文件；Termux 开服使用 Termux 主目录。",
                granted = serverDirectorySelected,
                required = false,
                actionWhenMissing = "授权",
                detail = if (serverDirectorySelected) {
                    serverDirectoryUri?.let { "已持久授权：$it" } ?: "已持久授权服务器工作目录"
                } else {
                    "未授权时仍可通过 Termux 启动；文件管理功能需要目录授权。"
                },
            ),
            item(
                id = "battery-optimization",
                title = "电池优化白名单",
                description = "引导系统允许长时间后台开服，减少省电策略终止服务。",
                granted = batteryOptimizationIgnored,
                required = false,
                actionWhenMissing = "设置",
            ),
        ),
    )
}
