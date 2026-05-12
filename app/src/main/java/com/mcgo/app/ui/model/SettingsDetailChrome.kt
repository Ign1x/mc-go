package com.mcgo.app.ui.model

enum class SettingsBackActionPlacement {
    None,
    TopRight,
}

data class SettingsDetailChromeState(
    val backActionPlacement: SettingsBackActionPlacement = SettingsBackActionPlacement.None,
    val usesCompactActionButton: Boolean = false,
)

object SettingsDetailChrome {
    fun forDestination(destination: SettingsDestination): SettingsDetailChromeState = when (destination) {
        SettingsDestination.Overview -> SettingsDetailChromeState()
        SettingsDestination.Appearance,
        SettingsDestination.JavaManagement,
        SettingsDestination.ServerDirectory,
        SettingsDestination.RuntimePermissions,
        SettingsDestination.HelpAndDebug -> SettingsDetailChromeState(
            backActionPlacement = SettingsBackActionPlacement.TopRight,
            usesCompactActionButton = true,
        )
    }
}
