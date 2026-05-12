package com.mcgo.app.ui.model

enum class SettingsDestination {
    Overview,
    Appearance,
    JavaManagement,
    ServerDirectory,
    RuntimePermissions,
    HelpAndDebug,
}

data class SettingsNavigationState(
    val destination: SettingsDestination = SettingsDestination.Overview,
) {
    val canNavigateBack: Boolean
        get() = destination != SettingsDestination.Overview

    fun openAppearance(): SettingsNavigationState = copy(destination = SettingsDestination.Appearance)

    fun openJavaManagement(): SettingsNavigationState = copy(destination = SettingsDestination.JavaManagement)

    fun openServerDirectory(): SettingsNavigationState = copy(destination = SettingsDestination.ServerDirectory)

    fun openRuntimePermissions(): SettingsNavigationState = copy(destination = SettingsDestination.RuntimePermissions)

    fun openHelpAndDebug(): SettingsNavigationState = copy(destination = SettingsDestination.HelpAndDebug)

    fun navigateBack(): SettingsNavigationState = copy(destination = SettingsDestination.Overview)
}
