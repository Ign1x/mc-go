package com.mcgo.app.ui.model

import com.mcgo.app.R

enum class McGoPage {
    Status,
    Servers,
    Tunnels,
    Settings,
}

data class PageChrome(
    val titleRes: Int,
    val subtitleRes: Int,
    val showLeadCard: Boolean,
)

object McGoPageChrome {
    fun forPage(page: McGoPage): PageChrome = when (page) {
        McGoPage.Status -> PageChrome(
            titleRes = R.string.nav_status,
            subtitleRes = R.string.nav_status_subtitle,
            showLeadCard = false,
        )
        McGoPage.Servers -> PageChrome(
            titleRes = R.string.nav_servers,
            subtitleRes = R.string.nav_servers_subtitle,
            showLeadCard = false,
        )
        McGoPage.Tunnels -> PageChrome(
            titleRes = R.string.nav_tunnels,
            subtitleRes = R.string.nav_tunnels_subtitle,
            showLeadCard = false,
        )
        McGoPage.Settings -> PageChrome(
            titleRes = R.string.nav_settings,
            subtitleRes = R.string.nav_settings_subtitle,
            showLeadCard = false,
        )
    }
}
