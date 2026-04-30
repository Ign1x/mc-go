package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class JavaManagementModelsTest {

    @Test
    fun defaultJavaManagementState_listsAppManagedJreVersionsForMinecraftWithoutPermissionItems() {
        val state = defaultJavaManagementState()

        assertThat(state.summaryLabel).contains("JRE")
        assertThat(state.sectionTitle).isEqualTo("托管 JRE")
        assertThat(state.runtimeOptions.map { it.majorVersion }).containsExactly(8, 11, 17, 21, 25).inOrder()
        assertThat(state.runtimeOptions.map { it.title }).containsExactly(
            "Java 8",
            "Java 11",
            "Java 17",
            "Java 21",
            "Java 25",
        ).inOrder()
        assertThat(state.runtimeOptions.all { it.managedByApp }).isTrue()
        assertThat(state.runtimeOptions.map { it.statusLabel }).containsExactly("未安装", "未安装", "未安装", "未安装", "未安装")
        assertThat(state.runtimeOptions.map { it.primaryActionLabel }).containsExactly("下载安装", "下载安装", "下载安装", "下载安装", "下载安装")
        assertThat(state.runtimeOptions.map { it.deleteActionLabel }).containsExactly(null, null, null, null, null)
        assertThat(state.runtimeOptions.joinToString("\n") { it.description }).contains("Minecraft")
        assertThat(state.runtimeOptions.filter { it.onlineInstallAvailable }.map { it.majorVersion }).containsExactly(8, 11, 17, 21, 25).inOrder()
        assertThat(state.runtimeOptions.joinToString("\n") { it.description }).doesNotContain("系统 " + "PATH")
    }

    @Test
    fun javaManagementCopyDoesNotExposeRuntimeStrategyOrAndroidPermissionRows() {
        val state = defaultJavaManagementState()
        val visibleText = buildString {
            appendLine(state.sectionTitle)
            appendLine(state.summaryLabel)
            state.runtimeOptions.forEach { option ->
                appendLine(option.title)
                appendLine(option.description)
                appendLine(option.statusLabel)
            }
        }

        assertThat(visibleText).doesNotContain("Runtime " + "策略")
        assertThat(visibleText).doesNotContain("内置")
        assertThat(visibleText).doesNotContain("主线")
        assertThat(visibleText).doesNotContain("推荐")
        assertThat(visibleText).doesNotContain("POST_NOTIFICATIONS")
        assertThat(visibleText).doesNotContain("WAKE_LOCK")
        assertThat(visibleText).doesNotContain("FOREGROUND_SERVICE")
    }

    @Test
    fun javaDownloadRowsExposeProgress() {
        val state = defaultJavaManagementState(downloadProgressByMajor = mapOf(21 to 42))
        val java21 = state.runtimeOptions.single { it.majorVersion == 21 }

        assertThat(java21.statusLabel).isEqualTo("下载中 42%")
        assertThat(java21.primaryActionLabel).isNull()
        assertThat(java21.downloadProgressPercent).isEqualTo(42)
        assertThat(java21.downloadSourceLabel).contains("镜像")
    }

    @Test
    fun installedJreRowsShowDeleteWithoutInstallAction() {
        val state = defaultJavaManagementState(installedVersions = setOf(17, 21))
        val java17 = state.runtimeOptions.single { it.majorVersion == 17 }
        val java21 = state.runtimeOptions.single { it.majorVersion == 21 }

        assertThat(java17.statusLabel).isEqualTo("已安装")
        assertThat(java17.primaryActionLabel).isNull()
        assertThat(java17.deleteActionLabel).isEqualTo("删除")
        assertThat(java21.statusLabel).isEqualTo("已安装")
        assertThat(java21.deleteActionLabel).isEqualTo("删除")
    }
}
