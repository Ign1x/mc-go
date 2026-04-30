package com.mcgo.app.ui.sample

import com.mcgo.app.ui.model.AccentPreset
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.AppearanceSettingsState
import com.mcgo.app.ui.model.AppearanceToggleState
import com.mcgo.app.ui.model.DashboardMetric
import com.mcgo.app.ui.model.FontScalePreference
import com.mcgo.app.ui.model.MetricAccent
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.SettingsCategoryIcon
import com.mcgo.app.ui.model.SettingsSectionState
import com.mcgo.app.ui.model.ThemeModePreference
import com.mcgo.app.ui.model.defaultJavaManagementState
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.formatBatteryCurrent

object McGoSampleRepository {

    fun dashboardMetrics(): List<DashboardMetric> = listOf(
        DashboardMetric(
            title = "CPU",
            valueLabel = "42%",
            detailLabel = "8-core spike · 峰值 68%",
            trendValues = listOf(24f, 30f, 28f, 41f, 35f, 52f, 48f, 42f),
            accent = MetricAccent.Blue,
        ),
        DashboardMetric(
            title = "RAM",
            valueLabel = "3.1 / 8 GB",
            detailLabel = "JVM heap 2.0 GB",
            trendValues = listOf(1.6f, 1.8f, 2.1f, 2.4f, 2.5f, 2.8f, 3.0f, 3.1f),
            accent = MetricAccent.Green,
        ),
        DashboardMetric(
            title = "Network I/O",
            valueLabel = "12.8 Mbps",
            detailLabel = "上传 4.2 · 下载 8.6",
            trendValues = listOf(2f, 4f, 7f, 9f, 11f, 13f, 12f, 12.8f),
            accent = MetricAccent.Violet,
        ),
        DashboardMetric(
            title = "Battery Current",
            valueLabel = formatBatteryCurrent(1240),
            detailLabel = "USB-C 快充中",
            trendValues = listOf(0.3f, 0.5f, 0.4f, 0.8f, 0.9f, 1.1f, 1.0f, 1.24f),
            accent = MetricAccent.Gold,
        ),
    )

    fun serverCards(): List<ServerCardState> = listOf(
        ServerCardState(
            name = "Creative Plot",
            edition = "Java 1.20.6",
            worldName = "Sky Blocks",
            port = 25566,
            onlinePlayers = 2,
            maxPlayers = 10,
            memoryLabel = "1.5 GB RAM",
            isOnline = true,
        ),
        ServerCardState(
            name = "Modpack Test",
            edition = "Forge 1.20.1",
            worldName = "Redstone Lab",
            port = 25567,
            onlinePlayers = 0,
            maxPlayers = 8,
            memoryLabel = "3.0 GB RAM",
            isOnline = false,
        ),
    )

    fun tunnelProfiles(): List<TunnelProfile> = emptyList()

    fun settingsSections(): List<SettingsSectionState> = listOf(
        SettingsSectionState(
            title = "界面与外观",
            subtitle = "主题、色彩、字体与背景",
            highlight = AppearancePreferences().summaryLabel(),
            icon = SettingsCategoryIcon.Appearance,
        ),
        SettingsSectionState(
            title = "Java 管理",
            subtitle = "JRE 8/11/17/21/25 托管",
            highlight = defaultJavaManagementState().summaryLabel,
            icon = SettingsCategoryIcon.JavaRuntime,
        ),
        SettingsSectionState(
            title = "运行权限",
            subtitle = "通知、目录、唤醒与后台权限",
            highlight = "查看状态并申请",
            icon = SettingsCategoryIcon.RuntimePermissions,
        ),
    )

    fun appearanceSettings(): AppearanceSettingsState {
        val defaults = AppearancePreferences()
        return AppearanceSettingsState(
            themeModes = ThemeModePreference.entries.map { it.label },
            selectedThemeMode = defaults.themeMode.label,
            accentOptions = AccentPreset.entries.map { it.label },
            selectedAccent = defaults.accentPreset.label,
            fontScaleOptions = FontScalePreference.entries.map { it.label },
            selectedFontScale = defaults.fontScale.label,
            cardTransparencyPercent = defaults.cardTransparencyPercent,
            toggles = listOf(
                AppearanceToggleState(
                    title = "透明卡片",
                    subtitle = "控制卡片通透感",
                    enabled = defaults.transparentCards,
                ),
                AppearanceToggleState(
                    title = "动态背景",
                    subtitle = "控制背景动效",
                    enabled = defaults.dynamicBackground,
                ),
            ),
        )
    }

}
