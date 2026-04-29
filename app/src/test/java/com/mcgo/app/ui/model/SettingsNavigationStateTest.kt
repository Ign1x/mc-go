package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class SettingsNavigationStateTest {

    @Test
    fun defaultState_startsOnOverviewWithoutBackNavigation() {
        val state = SettingsNavigationState()

        assertThat(state.destination).isEqualTo(SettingsDestination.Overview)
        assertThat(state.canNavigateBack).isFalse()
    }

    @Test
    fun openAppearance_movesIntoDetailPage() {
        val state = SettingsNavigationState().openAppearance()

        assertThat(state.destination).isEqualTo(SettingsDestination.Appearance)
        assertThat(state.canNavigateBack).isTrue()
    }

    @Test
    fun navigateBack_fromAppearanceReturnsToOverview() {
        val state = SettingsNavigationState(
            destination = SettingsDestination.Appearance,
        ).navigateBack()

        assertThat(state.destination).isEqualTo(SettingsDestination.Overview)
        assertThat(state.canNavigateBack).isFalse()
    }
}
