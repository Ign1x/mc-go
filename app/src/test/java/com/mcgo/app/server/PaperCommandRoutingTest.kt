package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class PaperCommandRoutingTest {
    @Test
    fun runtimeCommandMessage_formatsUserFacingAuditLine() {
        assertThat(runtimeCommandMessage("list")).contains("list")
        assertThat(runtimeCommandMessage("list")).contains("控制台")
    }
}
