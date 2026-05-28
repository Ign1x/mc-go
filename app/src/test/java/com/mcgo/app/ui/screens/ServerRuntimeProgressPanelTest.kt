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
    fun visibleRuntimeProgressLogs_keepsOnlyLatestLineToAvoidTallCards() {
        assertThat(visibleRuntimeProgressLogs(emptyList())).isEmpty()
        assertThat(visibleRuntimeProgressLogs(listOf("启动中"))).containsExactly("启动中")
        assertThat(visibleRuntimeProgressLogs(listOf("准备目录", "下载中", "启动完成")))
            .containsExactly("启动完成")
    }

    @Test
    fun visibleRuntimeProgressLogs_collapsesPersistedMinecraftClassListingTailBeforeShowingLatestLine() {
        val noisyLogs = listOf(
            "[debug] JVM 启动参数已生成",
            "net/minecraft/world/level/block/entity/SignText.class",
            "  net/minecraft/world/level/block/entity/SkullBlockEntity.class",
            "net/minecraft/world/level/block/entity/trialspawner/",
        )

        assertThat(visibleRuntimeProgressLogs(noisyLogs)).containsExactly(
            "[MC-GO] 已省略 3 行 Minecraft class 清单输出（完整启动失败请看后续错误行）",
        )
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
