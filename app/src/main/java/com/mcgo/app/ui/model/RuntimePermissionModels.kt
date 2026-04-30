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
): RuntimePermissionState {
    fun item(
        id: String,
        title: String,
        description: String,
        granted: Boolean,
        androidPermission: String? = null,
        required: Boolean = true,
        actionWhenMissing: String,
    ): RuntimePermissionItem = RuntimePermissionItem(
        id = id,
        title = title,
        description = description,
        androidPermission = androidPermission,
        required = required,
        status = if (granted) RuntimePermissionStatus.Granted else RuntimePermissionStatus.NeedsRequest,
        statusLabel = if (granted) "已授权" else "未授权",
        actionLabel = if (granted) null else actionWhenMissing,
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
                id = "server-directory",
                title = "服务器目录",
                description = "通过系统目录选择器授权服务器工作目录。",
                granted = serverDirectorySelected,
                required = false,
                actionWhenMissing = "选择",
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
