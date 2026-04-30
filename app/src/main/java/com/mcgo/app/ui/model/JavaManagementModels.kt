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
    summaryLabel = "JRE 8 / 11 / 17 / 21 / 25",
    runtimeOptions = listOf(
        jreOption(8, "旧版 Minecraft 1.16 及以下服务端使用；支持在线下载托管 JRE。", installedVersions, downloadProgressByMajor),
        jreOption(11, "部分过渡版本与插件工具链使用；支持在线下载或导入 Android JRE 包。", installedVersions, downloadProgressByMajor),
        jreOption(17, "Minecraft 1.18 - 1.20.4 Paper 服务端使用；支持在线下载托管 JRE。", installedVersions, downloadProgressByMajor),
        jreOption(21, "Minecraft 1.20.5+ / 1.21.x Paper 服务端使用；支持在线下载托管 JRE。", installedVersions, downloadProgressByMajor),
        jreOption(25, "Minecraft 与 Paper 新版本使用；支持在线下载或导入 Android JRE 包。", installedVersions, downloadProgressByMajor),
    ),
)

private fun jreOption(
    majorVersion: Int,
    description: String,
    installedVersions: Set<Int>,
    downloadProgressByMajor: Map<Int, Int>,
    onlineInstallAvailable: Boolean = true,
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
            else -> "导入文件"
        },
        deleteActionLabel = if (installed && progress == null) "删除" else null,
        downloadProgressPercent = progress,
        downloadSourceLabel = "自动选择官方源 / 国内镜像",
    )
}
