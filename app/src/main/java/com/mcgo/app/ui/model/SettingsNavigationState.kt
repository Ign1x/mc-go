package com.mcgo.app.ui.model

enum class SettingsDestination {
    Overview,
    Appearance,
}

data class SettingsNavigationState(
    val destination: SettingsDestination = SettingsDestination.Overview,
) {
    val canNavigateBack: Boolean
        get() = destination != SettingsDestination.Overview

    fun openAppearance(): SettingsNavigationState = copy(destination = SettingsDestination.Appearance)

    fun navigateBack(): SettingsNavigationState = copy(destination = SettingsDestination.Overview)
}
