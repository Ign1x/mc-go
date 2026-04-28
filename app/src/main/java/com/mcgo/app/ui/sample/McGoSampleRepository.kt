package com.mcgo.app.ui.sample

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
        activeServerName = "Vanilla Survival",
        uptimeMinutes = 127,
        onlinePlayers = 5,
        maxPlayers = 20,
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
            name = "Vanilla Survival",
            edition = "Java 1.21.5",
            worldName = "Seed Harbor",
            port = 25565,
            onlinePlayers = 5,
            maxPlayers = 20,
            memoryLabel = "2.5 GB RAM",
            isOnline = true,
        ),
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
            title = "Server Properties",
            subtitle = "端口、在线人数、MOTD、白名单",
            highlight = "25565 · 20 人",
            icon = SettingsCategoryIcon.Server,
        ),
        SettingsSectionState(
            title = "Game Rules",
            subtitle = "PVP、难度、死亡掉落、白天锁定",
            highlight = "keepInventory · normal",
            icon = SettingsCategoryIcon.GameRule,
        ),
        SettingsSectionState(
            title = "Java / Bedrock",
            subtitle = "Java 运行参数、版本管理、兼容桥接",
            highlight = "Java 21 build runtime",
            icon = SettingsCategoryIcon.Edition,
        ),
        SettingsSectionState(
            title = "App Preferences",
            subtitle = "主题、通知、日志保留、自动备份",
            highlight = "Light glass dashboard",
            icon = SettingsCategoryIcon.App,
        ),
        SettingsSectionState(
            title = "Safety & Recovery",
            subtitle = "快照、崩溃恢复、启动前检查",
            highlight = "Auto snapshot on start",
            icon = SettingsCategoryIcon.Safety,
        ),
    )

    fun recentEvents(): List<String> = listOf(
        "世界自动保存已完成 · 2 分钟前",
        "公网映射正常 · 当前延迟 38 ms",
        "Vanilla Survival 当前负载 ${formatPlayerCapacity(5, 20)}",
    )
}
