package com.mcgo.app.ui.model

enum class SettingsDestination {
    Overview,
    Appearance,
    JavaManagement,
    RuntimePermissions,
}

data class SettingsNavigationState(
    val destination: SettingsDestination = SettingsDestination.Overview,
) {
    val canNavigateBack: Boolean
        get() = destination != SettingsDestination.Overview

    fun openAppearance(): SettingsNavigationState = copy(destination = SettingsDestination.Appearance)

    fun openJavaManagement(): SettingsNavigationState = copy(destination = SettingsDestination.JavaManagement)

    fun openRuntimePermissions(): SettingsNavigationState = copy(destination = SettingsDestination.RuntimePermissions)

    fun navigateBack(): SettingsNavigationState = copy(destination = SettingsDestination.Overview)
}
