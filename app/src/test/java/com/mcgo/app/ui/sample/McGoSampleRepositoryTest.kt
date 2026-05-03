package com.mcgo.app.ui.sample

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class McGoSampleRepositoryTest {

    @Test
    fun dashboardMetrics_providesSixPhoneServerSignalsInThermalFriendlyOrder() {
        val titles = McGoSampleRepository.dashboardMetrics().map { it.title }

        assertThat(titles).containsExactly(
            "RAM",
            "Network I/O",
            "CPU 温度",
            "GPU 温度",
            "电池温度",
            "电池电流",
        ).inOrder()
    }

    @Test
    fun serverCards_startEmptyUntilUserCreatesOwnServers() {
        assertThat(McGoSampleRepository.serverCards()).isEmpty()
    }

    @Test
    fun settingsSections_topLevelShowsAppearanceJavaManagementAndRuntimePermissions() {
        val sections = McGoSampleRepository.settingsSections()

        assertThat(sections.map { it.title }).containsExactly("界面与外观", "Java 管理", "运行权限").inOrder()
        assertThat(sections.first().subtitle).contains("主题")
        assertThat(sections[1].subtitle).contains("JRE")
        assertThat(sections.last().subtitle).contains("权限")
    }

    @Test
    fun tunnelProfiles_startEmptyUntilUserAddsOwnEntries() {
        val tunnels = McGoSampleRepository.tunnelProfiles()

        assertThat(tunnels).isEmpty()
    }

    @Test
    fun appearanceSettings_exposeSystemColorWideDensityAndLeanToggles() {
        val appearance = McGoSampleRepository.appearanceSettings()

        assertThat(appearance.themeModes).containsExactly("跟随系统", "浅色", "深色").inOrder()
        assertThat(appearance.selectedThemeMode).isEqualTo("跟随系统")
        assertThat(appearance.accentOptions).containsExactly("科技蓝", "森林绿", "紫晶", "暖阳橙", "系统颜色").inOrder()
        assertThat(appearance.selectedAccent).isEqualTo("森林绿")
        assertThat(appearance.cardTransparencyPercent).isEqualTo(82)
        assertThat(appearance.toggles.map { it.title }).containsExactly(
            "透明卡片",
            "动态背景",
        ).inOrder()
    }
}
