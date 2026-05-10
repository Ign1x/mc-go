package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class SettingsHelpNavigationTest {
    @Test
    fun openHelpAndDebug_movesIntoHelpDetailPage() {
        val state = SettingsNavigationState().openHelpAndDebug()

        assertThat(state.destination).isEqualTo(SettingsDestination.HelpAndDebug)
        assertThat(state.canNavigateBack).isTrue()
    }

    @Test
    fun helpAndDebugDetail_placesBackActionAtTopRight() {
        val chrome = SettingsDetailChrome.forDestination(SettingsDestination.HelpAndDebug)

        assertThat(chrome.backActionPlacement).isEqualTo(SettingsBackActionPlacement.TopRight)
        assertThat(chrome.usesCompactActionButton).isTrue()
    }
}