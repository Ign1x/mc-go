package com.mcgo.app.ui.model

data class JavaRuntimeOption(
    val title: String,
    val description: String,
    val statusLabel: String,
)

data class JavaPermissionItem(
    val title: String,
    val description: String,
    val androidPermission: String? = null,
    val required: Boolean = true,
)

data class JavaManagementState(
    val summaryLabel: String,
    val runtimeOptions: List<JavaRuntimeOption>,
    val permissionItems: List<JavaPermissionItem>,
)

fun defaultJavaManagementState(): JavaManagementState = JavaManagementState(
    summaryLabel = "应用内 Java Runtime · 私有目录运行 · 前台服务保活",
    runtimeOptions = listOf(
        JavaRuntimeOption(
            title = "应用内 Java Runtime",
            description = "优先使用应用内托管的 Java Runtime，不依赖 Android 系统 PATH 中存在通用 java 命令。",
            statusLabel = "推荐",
        ),
        JavaRuntimeOption(
            title = "导入 Runtime",
            description = "允许用户通过 SAF 选择并导入兼容的 Java Runtime，存放到 App 私有目录后统一管理。",
            statusLabel = "可选",
        ),
        JavaRuntimeOption(
            title = "服务器工作目录",
            description = "服务器文件使用 App 私有目录或用户选择目录，避免申请 MANAGE_EXTERNAL_STORAGE。",
            statusLabel = "安全",
        ),
    ),
    permissionItems = listOf(
        JavaPermissionItem(
            title = "前台服务通知",
            description = "长时间开服时显示常驻通知，便于停止服务器并降低后台被系统清理的概率。",
            androidPermission = "android.permission.POST_NOTIFICATIONS",
        ),
        JavaPermissionItem(
            title = "保持 CPU 唤醒",
            description = "服务器运行期间按需申请 WakeLock，避免锁屏后 Tick 与网络连接被中断。",
            androidPermission = "android.permission.WAKE_LOCK",
        ),
        JavaPermissionItem(
            title = "前台服务运行",
            description = "用 Foreground Service 承载 Java 进程、控制台与隧道守护逻辑。",
            androidPermission = "android.permission.FOREGROUND_SERVICE",
        ),
        JavaPermissionItem(
            title = "用户选择目录",
            description = "通过系统文件选择器授权服务器目录，不申请所有文件访问权限。",
            androidPermission = null,
            required = false,
        ),
        JavaPermissionItem(
            title = "电池优化白名单",
            description = "引导用户手动允许后台运行，减少系统省电策略终止服务。",
            androidPermission = null,
            required = false,
        ),
    ),
)
