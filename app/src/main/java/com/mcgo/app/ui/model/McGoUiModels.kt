package com.mcgo.app.ui.model

enum class MetricAccent {
    Blue,
    Green,
    Gold,
    Violet,
}

enum class SettingsCategoryIcon {
    Appearance,
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
    val onlinePlayers: Int,
    val maxPlayers: Int,
    val memoryLabel: String,
    val isOnline: Boolean,
)

data class SettingsSectionState(
    val title: String,
    val subtitle: String,
    val highlight: String,
    val icon: SettingsCategoryIcon,
)
