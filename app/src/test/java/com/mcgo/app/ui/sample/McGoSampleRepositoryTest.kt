package com.mcgo.app.ui.sample

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class McGoSampleRepositoryTest {

    @Test
    fun dashboardMetrics_providesFourPhoneServerSignals() {
        val titles = McGoSampleRepository.dashboardMetrics().map { it.title }

        assertThat(titles).containsExactly("CPU", "RAM", "Network I/O", "Battery Current").inOrder()
    }

    @Test
    fun serverCards_removeVanillaSurvivalPresetButKeepMixedStatuses() {
        val servers = McGoSampleRepository.serverCards()

        assertThat(servers).hasSize(2)
        assertThat(servers.map { it.name }).doesNotContain("Vanilla Survival")
        assertThat(servers.any { it.isOnline }).isTrue()
        assertThat(servers.any { !it.isOnline }).isTrue()
        assertThat(servers.first().name).isEqualTo("Creative Plot")
    }

    @Test
    fun settingsSections_topLevelOnlyShowsAppearanceEntry() {
        val sections = McGoSampleRepository.settingsSections()

        assertThat(sections).hasSize(1)
        assertThat(sections.single().title).isEqualTo("界面与外观")
        assertThat(sections.single().subtitle).contains("主题")
    }

    @Test
    fun appearanceSettings_exposeSystemColorWideDensityAndLeanToggles() {
        val appearance = McGoSampleRepository.appearanceSettings()

        assertThat(appearance.themeModes).containsExactly("浅色", "跟随系统", "深色").inOrder()
        assertThat(appearance.selectedThemeMode).isEqualTo("浅色")
        assertThat(appearance.accentOptions).containsExactly("科技蓝", "森林绿", "紫晶", "暖阳橙", "系统颜色").inOrder()
        assertThat(appearance.selectedAccent).isEqualTo("森林绿")
        assertThat(appearance.fontScaleOptions).containsExactly("紧凑", "标准", "宽松").inOrder()
        assertThat(appearance.selectedFontScale).isEqualTo("紧凑")
        assertThat(appearance.cardTransparencyPercent).isEqualTo(82)
        assertThat(appearance.toggles.map { it.title }).containsExactly(
            "透明卡片",
            "动态背景",
        ).inOrder()
    }
}
