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
)

object McGoPageChrome {
    fun forPage(page: McGoPage): PageChrome = when (page) {
        McGoPage.Status -> PageChrome(
            titleRes = R.string.nav_status,
            subtitleRes = R.string.nav_status_subtitle,
        )
        McGoPage.Servers -> PageChrome(
            titleRes = R.string.nav_servers,
            subtitleRes = R.string.nav_servers_subtitle,
        )
        McGoPage.Tunnels -> PageChrome(
            titleRes = R.string.nav_tunnels,
            subtitleRes = R.string.nav_tunnels_subtitle,
        )
        McGoPage.Settings -> PageChrome(
            titleRes = R.string.nav_settings,
            subtitleRes = R.string.nav_settings_subtitle,
        )
    }
}
