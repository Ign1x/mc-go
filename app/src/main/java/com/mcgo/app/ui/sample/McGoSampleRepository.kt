package com.mcgo.app.ui.sample

import com.mcgo.app.ui.model.AppearanceSettingsState
import com.mcgo.app.ui.model.AppearanceToggleState
import com.mcgo.app.ui.model.DashboardMetric
import com.mcgo.app.ui.model.HeroStatus
import com.mcgo.app.ui.model.MetricAccent
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.SettingsCategoryIcon
import com.mcgo.app.ui.model.SettingsSectionState
import com.mcgo.app.ui.model.formatBatteryCurrent
import com.mcgo.app.ui.model.formatPlayerCapacity

object McGoSampleRepository {

    fun heroStatus(): HeroStatus = HeroStatus(
        activeServerName = "Creative Plot",
        uptimeMinutes = 127,
        onlinePlayers = 2,
        maxPlayers = 10,
        statusLabel = "运行中",
    )

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

    fun settingsSections(): List<SettingsSectionState> = listOf(
        SettingsSectionState(
            title = "界面与外观",
            subtitle = "主题、卡片透明度、动效与字体大小",
            highlight = "浅色 · 透明卡片",
            icon = SettingsCategoryIcon.Appearance,
        ),
        SettingsSectionState(
            title = "通知与提醒",
            subtitle = "启动提醒、异常通知、后台保活提示",
            highlight = "仅重要提醒",
            icon = SettingsCategoryIcon.Notifications,
        ),
        SettingsSectionState(
            title = "下载与存储",
            subtitle = "服务端包、地图缓存、备份目录管理",
            highlight = "自动清理旧缓存",
            icon = SettingsCategoryIcon.Storage,
        ),
        SettingsSectionState(
            title = "日志与诊断",
            subtitle = "日志级别、导出、问题反馈与诊断",
            highlight = "支持导出调试日志",
            icon = SettingsCategoryIcon.Diagnostics,
        ),
        SettingsSectionState(
            title = "实验性功能",
            subtitle = "预览特性、兼容选项与实验开关",
            highlight = "Labs 已启用",
            icon = SettingsCategoryIcon.Labs,
        ),
    )

    fun appearanceSettings(): AppearanceSettingsState = AppearanceSettingsState(
        themeModes = listOf("浅色", "跟随系统", "深色"),
        selectedThemeMode = "浅色",
        accentOptions = listOf("科技蓝", "森林绿", "紫晶", "暖阳橙"),
        selectedAccent = "森林绿",
        fontScaleOptions = listOf("紧凑", "标准", "舒适"),
        selectedFontScale = "紧凑",
        motionOptions = listOf("省电", "标准", "灵动"),
        selectedMotionMode = "标准",
        cardTransparencyPercent = 82,
        toggles = listOf(
            AppearanceToggleState(
                title = "透明卡片",
                subtitle = "保留轻透玻璃质感，弱化厚重底色",
                enabled = true,
            ),
            AppearanceToggleState(
                title = "动态背景",
                subtitle = "保留轻微彩色氛围光，不喧宾夺主",
                enabled = true,
            ),
            AppearanceToggleState(
                title = "紧凑字体",
                subtitle = "降低层级字号，让信息更密一点",
                enabled = true,
            ),
        ),
    )

    fun recentEvents(): List<String> = listOf(
        "世界自动保存已完成 · 2 分钟前",
        "公网映射正常 · 当前延迟 38 ms",
        "Creative Plot 当前负载 ${formatPlayerCapacity(2, 10)}",
    )
}
