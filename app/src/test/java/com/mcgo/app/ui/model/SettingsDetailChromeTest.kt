package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class SettingsDetailChromeTest {

    @Test
    fun appearanceDetail_placesBackActionAtTopRight() {
        val chrome = SettingsDetailChrome.forDestination(SettingsDestination.Appearance)

        assertThat(chrome.backActionPlacement).isEqualTo(SettingsBackActionPlacement.TopRight)
        assertThat(chrome.usesCompactActionButton).isTrue()
    }

    @Test
    fun javaManagementDetail_placesBackActionAtTopRight() {
        val chrome = SettingsDetailChrome.forDestination(SettingsDestination.JavaManagement)

        assertThat(chrome.backActionPlacement).isEqualTo(SettingsBackActionPlacement.TopRight)
        assertThat(chrome.usesCompactActionButton).isTrue()
    }


    @Test
    fun serverDirectoryDetail_placesBackActionAtTopRight() {
        val chrome = SettingsDetailChrome.forDestination(SettingsDestination.ServerDirectory)

        assertThat(chrome.backActionPlacement).isEqualTo(SettingsBackActionPlacement.TopRight)
        assertThat(chrome.usesCompactActionButton).isTrue()
    }

    @Test
    fun runtimePermissionsDetail_placesBackActionAtTopRight() {
        val chrome = SettingsDetailChrome.forDestination(SettingsDestination.RuntimePermissions)

        assertThat(chrome.backActionPlacement).isEqualTo(SettingsBackActionPlacement.TopRight)
        assertThat(chrome.usesCompactActionButton).isTrue()
    }

    @Test
    fun overview_omitsBackAction() {
        val chrome = SettingsDetailChrome.forDestination(SettingsDestination.Overview)

        assertThat(chrome.backActionPlacement).isEqualTo(SettingsBackActionPlacement.None)
        assertThat(chrome.usesCompactActionButton).isFalse()
    }
}
