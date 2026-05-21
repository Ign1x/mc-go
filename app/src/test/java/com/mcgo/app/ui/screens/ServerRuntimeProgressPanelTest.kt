package com.mcgo.app.ui.screens

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.model.ServerLaunchStatus
import kotlin.test.Test

class ServerRuntimeProgressPanelTest {
    @Test
    fun modpackImportProgressActive_matchesImportStateAndDiagnosticLogWording() {
        assertThat(isModpackImportProgressActive(modpackImportInProgress = true, latestRuntimeLog = "正在解压文件"))
            .isTrue()
        assertThat(isModpackImportProgressActive(modpackImportInProgress = false, latestRuntimeLog = "正在解压整合包到目标目录"))
            .isTrue()
        assertThat(isModpackImportProgressActive(modpackImportInProgress = false, latestRuntimeLog = "整合包导入摘要 | files=12 directories=3 bytes=456 skippedReserved=0"))
            .isTrue()
        assertThat(isModpackImportProgressActive(modpackImportInProgress = false, latestRuntimeLog = "整合包导入完成"))
            .isTrue()
        assertThat(isModpackImportProgressActive(modpackImportInProgress = false, latestRuntimeLog = "服务端进程启动中"))
            .isFalse()
    }

    @Test
    fun runtimeProgressTitle_prefersStoppingThenImportThenLaunch() {
        assertThat(runtimeProgressTitle(ServerLaunchStatus.Stopping, importProgressActive = true))
            .isEqualTo("停止进度")
        assertThat(runtimeProgressTitle(ServerLaunchStatus.Launching, importProgressActive = true))
            .isEqualTo("导入进度")
        assertThat(runtimeProgressTitle(ServerLaunchStatus.Launching, importProgressActive = false))
            .isEqualTo("启动进度")
    }
}
