package com.mcgo.app.ui.model

data class JavaRuntimeOption(
    val majorVersion: Int,
    val title: String,
    val description: String,
    val statusLabel: String,
    val managedByApp: Boolean = true,
    val onlineInstallAvailable: Boolean = false,
    val primaryActionLabel: String? = null,
    val deleteActionLabel: String? = null,
    val downloadProgressPercent: Int? = null,
    val downloadSourceLabel: String? = null,
)

data class JavaManagementState(
    val sectionTitle: String,
    val summaryLabel: String,
    val runtimeOptions: List<JavaRuntimeOption>,
)

fun defaultJavaManagementState(
    installedVersions: Set<Int> = emptySet(),
    downloadProgressByMajor: Map<Int, Int> = emptyMap(),
): JavaManagementState = JavaManagementState(
    sectionTitle = "托管 JRE",
    summaryLabel = "JRE 8 / 17 / 21",
    runtimeOptions = listOf(
        jreOption(8, "Minecraft 1.7.10 - 1.11 Paper 服务端使用；支持在线下载托管 JRE。", installedVersions, downloadProgressByMajor, onlineInstallAvailable = true, importLabel = null),
        jreOption(17, "Minecraft 1.12 - 1.19 Paper 服务端使用；支持在线下载托管 JRE；对 1.13 - 1.16.5 会自动兼容旧版 Paper Java 限制。", installedVersions, downloadProgressByMajor, onlineInstallAvailable = true, importLabel = null),
        jreOption(21, "Minecraft 1.20 - 1.21.x Paper 服务端使用；支持在线下载托管 JRE。", installedVersions, downloadProgressByMajor, onlineInstallAvailable = true, importLabel = null),
    ),
)

private fun jreOption(
    majorVersion: Int,
    description: String,
    installedVersions: Set<Int>,
    downloadProgressByMajor: Map<Int, Int>,
    onlineInstallAvailable: Boolean,
    importLabel: String?,
): JavaRuntimeOption {
    val installed = majorVersion in installedVersions
    val progress = downloadProgressByMajor[majorVersion]?.coerceIn(0, 100)
    return JavaRuntimeOption(
        majorVersion = majorVersion,
        title = "Java $majorVersion",
        description = description,
        statusLabel = when {
            installed -> "已安装"
            progress != null -> "下载中 ${progress}%"
            else -> "未安装"
        },
        onlineInstallAvailable = onlineInstallAvailable,
        primaryActionLabel = when {
            installed -> null
            progress != null -> null
            onlineInstallAvailable -> "下载安装"
            importLabel != null -> importLabel
            else -> null
        },
        deleteActionLabel = if (installed && progress == null) "删除" else null,
        downloadProgressPercent = progress,
        downloadSourceLabel = "自动选择官方源 / 国内镜像",
    )
}
