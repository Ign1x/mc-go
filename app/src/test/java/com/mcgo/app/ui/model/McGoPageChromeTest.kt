package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.R
import kotlin.test.Test

class McGoPageChromeTest {

    @Test
    fun headerForServersAndTunnels_usePageLabelsInsteadOfAppName() {
        val serversChrome = McGoPageChrome.forPage(McGoPage.Servers)
        val tunnelsChrome = McGoPageChrome.forPage(McGoPage.Tunnels)

        assertThat(serversChrome.titleRes).isEqualTo(R.string.nav_servers)
        assertThat(serversChrome.titleRes).isNotEqualTo(R.string.app_name)
        assertThat(tunnelsChrome.titleRes).isEqualTo(R.string.nav_tunnels)
        assertThat(tunnelsChrome.titleRes).isNotEqualTo(R.string.app_name)
    }

    @Test
    fun listPages_hideRedundantLeadCards() {
        assertThat(McGoPageChrome.forPage(McGoPage.Servers).showLeadCard).isFalse()
        assertThat(McGoPageChrome.forPage(McGoPage.Tunnels).showLeadCard).isFalse()
        assertThat(McGoPageChrome.forPage(McGoPage.Settings).showLeadCard).isFalse()
    }
}
