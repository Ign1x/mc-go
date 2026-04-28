package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class FormattersTest {

    @Test
    fun formatRuntime_returnsCompactHourMinuteLabel() {
        assertThat(formatRuntime(totalMinutes = 125)).isEqualTo("2h 05m")
    }

    @Test
    fun formatPlayerCapacity_returnsOnlineAndMaxPlayers() {
        assertThat(formatPlayerCapacity(onlinePlayers = 5, maxPlayers = 20))
            .isEqualTo("5/20 人")
    }

    @Test
    fun formatBatteryCurrent_addsExplicitSign() {
        assertThat(formatBatteryCurrent(currentMilliAmps = 1240)).isEqualTo("+1240 mA")
        assertThat(formatBatteryCurrent(currentMilliAmps = -980)).isEqualTo("-980 mA")
    }
}
