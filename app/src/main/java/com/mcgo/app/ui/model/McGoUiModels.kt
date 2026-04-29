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
    Notifications,
    Storage,
    Diagnostics,
    Labs,
}

data class HeroStatus(
    val activeServerName: String,
    val uptimeMinutes: Int,
    val onlinePlayers: Int,
    val maxPlayers: Int,
    val statusLabel: String,
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
    val edition: String,
    val worldName: String,
    val port: Int,
    val defaultPort: Int = port,
    val onlinePlayers: Int,
    val maxPlayers: Int,
    val memoryLabel: String,
    val isOnline: Boolean,
    val selectedTunnelId: String? = null,
    val activeTunnelLabel: String? = null,
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
