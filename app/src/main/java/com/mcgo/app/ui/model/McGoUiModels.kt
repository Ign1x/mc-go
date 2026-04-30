package com.mcgo.app.ui.model

enum class MetricAccent {
    Blue,
    Green,
    Gold,
    Violet,
}

enum class SettingsCategoryIcon {
    Appearance,
    JavaRuntime,
    RuntimePermissions,
    Notifications,
    Storage,
    Diagnostics,
    Labs,
}

enum class MinecraftServerType(val label: String) {
    Paper("Paper"),
}

enum class ServerLaunchStatus(val label: String) {
    Ready("就绪"),
    Running("运行中"),
    Stopped("已停止"),
}

data class PaperLaunchPlan(
    val serverJarName: String,
    val javaMajorVersion: Int,
    val arguments: List<String>,
)

data class DashboardMetric(
    val title: String,
    val valueLabel: String,
    val detailLabel: String,
    val trendValues: List<Float>,
    val accent: MetricAccent,
)

data class ServerCardState(
    val name: String,
    val id: String = createServerId(name),
    val edition: String,
    val worldName: String,
    val port: Int,
    val defaultPort: Int = port,
    val onlinePlayers: Int,
    val maxPlayers: Int,
    val memoryLabel: String,
    val memoryMb: Int = parseMemoryMb(memoryLabel),
    val isOnline: Boolean,
    val selectedTunnelId: String? = null,
    val activeTunnelLabel: String? = null,
    val serverType: MinecraftServerType = MinecraftServerType.Paper,
    val minecraftVersion: String = edition.substringAfter(' ', "1.21.4"),
    val javaMajorVersion: Int = recommendedJavaMajorVersion(edition.substringAfter(' ', "1.21.4")),
    val launchStatus: ServerLaunchStatus = if (isOnline) ServerLaunchStatus.Running else ServerLaunchStatus.Ready,
    val launchPlan: PaperLaunchPlan? = null,
)

data class SettingsSectionState(
    val title: String,
    val subtitle: String,
    val highlight: String,
    val icon: SettingsCategoryIcon,
)

data class AppearanceToggleState(
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
)

data class AppearanceSettingsState(
    val themeModes: List<String>,
    val selectedThemeMode: String,
    val accentOptions: List<String>,
    val selectedAccent: String,
    val fontScaleOptions: List<String>,
    val selectedFontScale: String,
    val cardTransparencyPercent: Int,
    val toggles: List<AppearanceToggleState>,
)

fun recommendedJavaMajorVersion(minecraftVersion: String): Int {
    val parts = minecraftVersion.split('.').mapNotNull { it.toIntOrNull() }
    val minor = parts.getOrNull(1) ?: return 21
    val patch = parts.getOrNull(2) ?: 0
    return when {
        minor <= 16 -> 8
        minor == 17 -> 11
        minor in 18..20 && patch <= 4 -> 17
        else -> 21
    }
}

fun formatMemoryMb(memoryMb: Int): String = if (memoryMb % 1024 == 0) {
    "${memoryMb / 1024}.0 GB RAM"
} else {
    "${memoryMb} MB RAM"
}

fun createPaperServer(
    name: String,
    minecraftVersion: String,
    maxPlayers: Int,
    memoryMb: Int,
    port: Int = 25565,
    worldName: String = "world",
): ServerCardState = ServerCardState(
    id = createServerId(name.ifBlank { "Paper 服务器" }),
    name = name.ifBlank { "Paper 服务器" },
    edition = "Paper $minecraftVersion",
    worldName = worldName.ifBlank { "world" },
    port = port,
    defaultPort = port,
    onlinePlayers = 0,
    maxPlayers = maxPlayers,
    memoryLabel = formatMemoryMb(memoryMb),
    memoryMb = memoryMb,
    isOnline = false,
    serverType = MinecraftServerType.Paper,
    minecraftVersion = minecraftVersion,
    javaMajorVersion = recommendedJavaMajorVersion(minecraftVersion),
    launchStatus = ServerLaunchStatus.Ready,
)

private fun parseMemoryMb(memoryLabel: String): Int {
    val value = memoryLabel.substringBefore(' ').toFloatOrNull() ?: return 1024
    return if (memoryLabel.contains("GB")) (value * 1024).toInt() else value.toInt()
}

private fun createServerId(name: String): String {
    val slug = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "paper-server" }
    return "server-$slug-${System.currentTimeMillis()}"
}
