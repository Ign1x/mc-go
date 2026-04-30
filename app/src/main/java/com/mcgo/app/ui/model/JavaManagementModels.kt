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
)

data class JavaManagementState(
    val sectionTitle: String,
    val summaryLabel: String,
    val runtimeOptions: List<JavaRuntimeOption>,
)

fun defaultJavaManagementState(
    installedVersions: Set<Int> = emptySet(),
): JavaManagementState = JavaManagementState(
    sectionTitle = "托管 JRE",
    summaryLabel = "JRE 8 / 11 / 17 / 21 / 25",
    runtimeOptions = listOf(
        jreOption(8, "旧版 Minecraft 1.16 及以下服务端使用。", installedVersions, onlineInstallAvailable = true),
        jreOption(11, "部分过渡版本与插件工具链使用；请导入 Android JRE 包。", installedVersions),
        jreOption(17, "Minecraft 1.18 - 1.20.4 Paper 服务端使用。", installedVersions, onlineInstallAvailable = true),
        jreOption(21, "Minecraft 1.20.5+ / 1.21.x Paper 服务端使用。", installedVersions, onlineInstallAvailable = true),
        jreOption(25, "Minecraft 与 Paper 新版本使用；请导入 Android JRE 包。", installedVersions),
    ),
)

private fun jreOption(
    majorVersion: Int,
    description: String,
    installedVersions: Set<Int>,
    onlineInstallAvailable: Boolean = false,
): JavaRuntimeOption {
    val installed = majorVersion in installedVersions
    return JavaRuntimeOption(
        majorVersion = majorVersion,
        title = "Java $majorVersion",
        description = description,
        statusLabel = if (installed) "已安装" else "未安装",
        onlineInstallAvailable = onlineInstallAvailable,
        primaryActionLabel = when {
            installed -> null
            onlineInstallAvailable -> "下载安装"
            else -> "导入文件"
        },
        deleteActionLabel = if (installed) "删除" else null,
    )
}
