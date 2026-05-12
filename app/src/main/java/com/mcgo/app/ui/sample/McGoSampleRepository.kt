package com.mcgo.app.ui.sample

import com.mcgo.app.ui.model.AccentPreset
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.AppearanceSettingsState
import com.mcgo.app.ui.model.AppearanceToggleState
import com.mcgo.app.ui.model.DashboardMetric
import com.mcgo.app.ui.model.MetricAccent
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.SettingsCategoryIcon
import com.mcgo.app.ui.model.SettingsSectionState
import com.mcgo.app.ui.model.ThemeModePreference
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.defaultJavaManagementState
import com.mcgo.app.ui.model.formatBatteryCurrent

object McGoSampleRepository {

    fun dashboardMetrics(): List<DashboardMetric> = listOf(
        DashboardMetric(
            title = "RAM",
            valueLabel = "38%",
            detailLabel = "3.0 / 8.0 GB",
            trendValues = listOf(1.6f, 1.8f, 2.1f, 2.4f, 2.5f, 2.8f, 3.0f, 3.1f),
            accent = MetricAccent.Green,
        ),
        DashboardMetric(
            title = "Network I/O",
            valueLabel = "12.8 Mbps",
            detailLabel = "上传 4.2 · 下载 8.6",
            trendValues = listOf(2f, 4f, 7f, 9f, 11f, 13f, 12f, 12.8f),
            accent = MetricAccent.Blue,
        ),
        DashboardMetric(
            title = "CPU 温度",
            valueLabel = "46.3°C",
            detailLabel = "CPU 负载 42% · 最近 2 秒",
            trendValues = listOf(41.2f, 42.0f, 42.8f, 43.5f, 44.4f, 45.1f, 45.7f, 46.3f),
            accent = MetricAccent.Coral,
        ),
        DashboardMetric(
            title = "GPU 温度",
            valueLabel = "43.8°C",
            detailLabel = "图形核心热区",
            trendValues = listOf(39.6f, 40.2f, 40.9f, 41.4f, 41.9f, 42.7f, 43.1f, 43.8f),
            accent = MetricAccent.Violet,
        ),
        DashboardMetric(
            title = "电池温度",
            valueLabel = "39.4°C",
            detailLabel = "当前电量 78%",
            trendValues = listOf(35.8f, 36.4f, 37.2f, 37.9f, 38.4f, 38.8f, 39.1f, 39.4f),
            accent = MetricAccent.Gold,
        ),
        DashboardMetric(
            title = "电池电流",
            valueLabel = formatBatteryCurrent(1240),
            detailLabel = "USB-C 快充中",
            trendValues = listOf(0.3f, 0.5f, 0.4f, 0.8f, 0.9f, 1.1f, 1.0f, 1.24f),
            accent = MetricAccent.Teal,
        ),
    )

    fun serverCards(): List<ServerCardState> = emptyList()

    fun tunnelProfiles(): List<TunnelProfile> = emptyList()

    fun settingsSections(): List<SettingsSectionState> = listOf(
        SettingsSectionState(
            title = "界面与外观",
            subtitle = "主题、色彩与背景",
            highlight = AppearancePreferences().summaryLabel(),
            icon = SettingsCategoryIcon.Appearance,
        ),
        SettingsSectionState(
            title = "Java 管理",
            subtitle = "托管 JRE 槽位",
            highlight = defaultJavaManagementState().summaryLabel,
            icon = SettingsCategoryIcon.JavaRuntime,
        ),
        SettingsSectionState(
            title = "服务器目录",
            subtitle = "选择、重连与找回外部服务器数据目录",
            highlight = "可随时重新选择",
            icon = SettingsCategoryIcon.Storage,
        ),
        SettingsSectionState(
            title = "运行权限",
            subtitle = "通知、目录、唤醒与后台权限",
            highlight = "查看状态并申请",
            icon = SettingsCategoryIcon.RuntimePermissions,
        ),
        SettingsSectionState(
            title = "帮助与调试",
            subtitle = "问题定位、日志提取、关于与反馈建议",
            highlight = "查看版本并导出日志",
            icon = SettingsCategoryIcon.Diagnostics,
        ),
    )

    fun appearanceSettings(): AppearanceSettingsState {
        val defaults = AppearancePreferences()
        return AppearanceSettingsState(
            themeModes = ThemeModePreference.entries.map { it.label },
            selectedThemeMode = defaults.themeMode.label,
            accentOptions = AccentPreset.entries.map { it.label },
            selectedAccent = defaults.accentPreset.label,
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
