package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.R
import kotlin.test.Test

class McGoPageChromeTest {

    @Test
    fun headerForServers_usesPageLabelInsteadOfAppName() {
        val chrome = McGoPageChrome.forPage(McGoPage.Servers)

        assertThat(chrome.titleRes).isEqualTo(R.string.nav_servers)
        assertThat(chrome.titleRes).isNotEqualTo(R.string.app_name)
    }

    @Test
    fun listPages_hideRedundantLeadCards() {
        assertThat(McGoPageChrome.forPage(McGoPage.Servers).showLeadCard).isFalse()
        assertThat(McGoPageChrome.forPage(McGoPage.Settings).showLeadCard).isFalse()
    }
}
