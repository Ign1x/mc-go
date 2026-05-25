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
            allFilesAccessGranted = false,
            serverDirectorySelected = false,
            batteryOptimizationIgnored = false,
        )

        assertThat(state.summaryLabel).contains("权限")
        assertThat(state.permissionItems.map { it.title }).containsAtLeast(
            "前台服务通知",
            "保持 CPU 唤醒",
            "前台服务运行",
            "全部文件访问",
            "服务器目录",
            "电池优化白名单",
        )
        assertThat(state.permissionItems.mapNotNull { it.androidPermission }).containsAtLeast(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.WAKE_LOCK",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
        )

        val notification = state.permissionItems.single { it.androidPermission == "android.permission.POST_NOTIFICATIONS" }
        assertThat(notification.status).isEqualTo(RuntimePermissionStatus.NeedsRequest)
        assertThat(notification.statusLabel).isEqualTo("未授权")
        assertThat(notification.actionLabel).isEqualTo("申请")
        assertThat(notification.actionPlacement).isEqualTo(PermissionActionPlacement.StatusRowEnd)

        val wakeLock = state.permissionItems.single { it.androidPermission == "android.permission.WAKE_LOCK" }
        assertThat(wakeLock.status).isEqualTo(RuntimePermissionStatus.Granted)
        assertThat(wakeLock.statusLabel).isEqualTo("已授权")
        assertThat(wakeLock.actionLabel).isNull()

        val allFilesAccess = state.permissionItems.single { it.androidPermission == "android.permission.MANAGE_EXTERNAL_STORAGE" }
        assertThat(allFilesAccess.id).isEqualTo("all-files-access")
        assertThat(allFilesAccess.status).isEqualTo(RuntimePermissionStatus.NeedsRequest)
        assertThat(allFilesAccess.required).isTrue()
        assertThat(allFilesAccess.actionLabel).isEqualTo("设置")
        assertThat(allFilesAccess.description).contains("直接写入")

        val directory = state.permissionItems.single { it.title == "服务器目录" }
        assertThat(directory.status).isEqualTo(RuntimePermissionStatus.NeedsRequest)
        assertThat(directory.required).isTrue()
        assertThat(directory.actionLabel).isEqualTo("授权")
        assertThat(directory.description).contains("首次使用必须授权服务器目录")
        assertThat(directory.detail).contains("默认选择内部存储根目录的 MCGO 文件夹")
    }

    @Test
    fun allFilesAccessPermissionIsNotRequiredBeforeAndroidR() {
        val state = defaultRuntimePermissionState(
            postNotificationsGranted = true,
            wakeLockGranted = true,
            foregroundServiceGranted = true,
            allFilesAccessGranted = false,
            allFilesAccessRequired = false,
            serverDirectorySelected = true,
            batteryOptimizationIgnored = true,
        )

        val allFilesAccess = state.permissionItems.single { it.id == "all-files-access" }
        assertThat(allFilesAccess.required).isFalse()
        assertThat(allFilesAccess.status).isEqualTo(RuntimePermissionStatus.Granted)
        assertThat(allFilesAccess.actionLabel).isNull()
    }

    @Test
    fun grantedRuntimePermissionItemsHideApplyButtons() {
        val state = defaultRuntimePermissionState(
            postNotificationsGranted = true,
            wakeLockGranted = true,
            foregroundServiceGranted = true,
            allFilesAccessGranted = true,
            serverDirectorySelected = true,
            batteryOptimizationIgnored = true,
        )

        assertThat(state.permissionItems.all { it.status == RuntimePermissionStatus.Granted }).isTrue()
        assertThat(state.permissionItems.map { it.actionLabel }).containsExactly(null, null, null, null, null, null)
    }
}
