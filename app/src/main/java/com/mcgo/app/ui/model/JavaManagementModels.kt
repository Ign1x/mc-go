package com.mcgo.app.ui.model

data class JavaRuntimeOption(
    val majorVersion: Int,
    val title: String,
    val description: String,
    val statusLabel: String,
    val managedByApp: Boolean = true,
)

data class JavaManagementState(
    val sectionTitle: String,
    val summaryLabel: String,
    val runtimeOptions: List<JavaRuntimeOption>,
)

fun defaultJavaManagementState(): JavaManagementState = JavaManagementState(
    sectionTitle = "托管 JRE",
    summaryLabel = "JRE 8 / 11 / 17 / 21 / 25",
    runtimeOptions = listOf(
        JavaRuntimeOption(
            majorVersion = 8,
            title = "Java 8",
            description = "托管给旧版 Minecraft 1.16 及以下服务端使用。",
            statusLabel = "内置槽位",
        ),
        JavaRuntimeOption(
            majorVersion = 11,
            title = "Java 11",
            description = "托管给部分过渡版本与插件工具链使用。",
            statusLabel = "内置槽位",
        ),
        JavaRuntimeOption(
            majorVersion = 17,
            title = "Java 17",
            description = "托管给 Minecraft 1.18 - 1.20.4 Paper 服务端使用。",
            statusLabel = "推荐",
        ),
        JavaRuntimeOption(
            majorVersion = 21,
            title = "Java 21",
            description = "托管给 Minecraft 1.20.5+ / 1.21.x Paper 服务端使用。",
            statusLabel = "当前主线",
        ),
        JavaRuntimeOption(
            majorVersion = 25,
            title = "Java 25",
            description = "托管给 Minecraft 与 Paper 新版本使用。",
            statusLabel = "预置",
        ),
    ),
)
