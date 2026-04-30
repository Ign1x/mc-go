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
    fun pagesExposeOnlyHeaderCopyWithoutLeadCardFlags() {
        assertThat(McGoPageChrome.forPage(McGoPage.Servers).subtitleRes).isEqualTo(R.string.nav_servers_subtitle)
        assertThat(McGoPageChrome.forPage(McGoPage.Tunnels).subtitleRes).isEqualTo(R.string.nav_tunnels_subtitle)
        assertThat(McGoPageChrome.forPage(McGoPage.Settings).subtitleRes).isEqualTo(R.string.nav_settings_subtitle)
    }
}
