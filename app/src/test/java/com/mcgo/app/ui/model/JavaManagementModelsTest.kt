package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class JavaManagementModelsTest {

    @Test
    fun defaultJavaManagementState_prefersAppManagedRuntimeInsteadOfSystemPathJava() {
        val state = defaultJavaManagementState()

        assertThat(state.summaryLabel).contains("应用内")
        assertThat(state.runtimeOptions.map { it.title }).containsExactly(
            "应用内 Java Runtime",
            "导入 Runtime",
            "服务器工作目录",
        ).inOrder()
        assertThat(state.runtimeOptions.joinToString("\n") { it.description }).contains("不依赖 Android 系统 PATH")
    }

    @Test
    fun defaultJavaManagementState_coversLongRunningAndroidServerShapeWithoutBroadStoragePermission() {
        val state = defaultJavaManagementState()

        assertThat(state.permissionItems.map { it.androidPermission }).containsAtLeast(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.WAKE_LOCK",
            "android.permission.FOREGROUND_SERVICE",
        )
        assertThat(state.permissionItems.mapNotNull { it.androidPermission }).doesNotContain("android.permission.MANAGE_EXTERNAL_STORAGE")
        assertThat(state.permissionItems.map { it.title }).containsAtLeast(
            "前台服务通知",
            "保持 CPU 唤醒",
            "用户选择目录",
            "电池优化白名单",
        )
    }
}
