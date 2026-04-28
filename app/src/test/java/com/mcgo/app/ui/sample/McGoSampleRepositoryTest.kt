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
    fun serverCards_mixOnlineAndOfflineInstances() {
        val servers = McGoSampleRepository.serverCards()

        assertThat(servers).hasSize(3)
        assertThat(servers.any { it.isOnline }).isTrue()
        assertThat(servers.any { !it.isOnline }).isTrue()
        assertThat(servers.first().name).isEqualTo("Vanilla Survival")
    }

    @Test
    fun settingsSections_focusOnAppLevelPreferencesInsteadOfServerProperties() {
        val sectionTitles = McGoSampleRepository.settingsSections().map { it.title }

        assertThat(sectionTitles).containsExactly(
            "界面与外观",
            "通知与提醒",
            "下载与存储",
            "日志与诊断",
            "实验性功能"
        ).inOrder()
    }
}
