package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class RuntimePermissionModelsTest {

    @Test
    fun defaultRuntimePermissionState_isSeparateFromJavaManagementAndShowsStatusAndActions() {
        val state = defaultRuntimePermissionState(
            postNotificationsGranted = false,
            wakeLockGranted = true,
            foregroundServiceGranted = true,
            serverDirectorySelected = false,
            batteryOptimizationIgnored = false,
        )

        assertThat(state.summaryLabel).contains("权限")
        assertThat(state.permissionItems.map { it.title }).containsAtLeast(
            "前台服务通知",
            "保持 CPU 唤醒",
            "前台服务运行",
            "服务器目录",
            "电池优化白名单",
        )
        assertThat(state.permissionItems.mapNotNull { it.androidPermission }).containsAtLeast(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.WAKE_LOCK",
            "android.permission.FOREGROUND_SERVICE",
        )
        assertThat(state.permissionItems.mapNotNull { it.androidPermission }).doesNotContain("android.permission.MANAGE_EXTERNAL_STORAGE")

        val notification = state.permissionItems.single { it.androidPermission == "android.permission.POST_NOTIFICATIONS" }
        assertThat(notification.status).isEqualTo(RuntimePermissionStatus.NeedsRequest)
        assertThat(notification.statusLabel).isEqualTo("未授权")
        assertThat(notification.actionLabel).isEqualTo("申请")
        assertThat(notification.actionPlacement).isEqualTo(PermissionActionPlacement.StatusRowEnd)

        val wakeLock = state.permissionItems.single { it.androidPermission == "android.permission.WAKE_LOCK" }
        assertThat(wakeLock.status).isEqualTo(RuntimePermissionStatus.Granted)
        assertThat(wakeLock.statusLabel).isEqualTo("已授权")
        assertThat(wakeLock.actionLabel).isNull()

        val directory = state.permissionItems.single { it.title == "服务器目录" }
        assertThat(directory.status).isEqualTo(RuntimePermissionStatus.NeedsRequest)
        assertThat(directory.actionLabel).isEqualTo("选择")
    }

    @Test
    fun grantedRuntimePermissionItemsHideApplyButtons() {
        val state = defaultRuntimePermissionState(
            postNotificationsGranted = true,
            wakeLockGranted = true,
            foregroundServiceGranted = true,
            serverDirectorySelected = true,
            batteryOptimizationIgnored = true,
        )

        assertThat(state.permissionItems.all { it.status == RuntimePermissionStatus.Granted }).isTrue()
        assertThat(state.permissionItems.map { it.actionLabel }).containsExactly(null, null, null, null, null)
    }
}
