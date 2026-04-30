package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class PaperVersionRepositoryTest {

    @Test
    fun parseVersions_extractsAllPaperVersionsInApiOrder() {
        val body = """
            {"project_id":"paper","versions":["1.8.8","1.12.2","1.16.5","1.20.1","1.21.4"]}
        """.trimIndent()

        assertThat(parsePaperVersions(body)).containsExactly("1.8.8", "1.12.2", "1.16.5", "1.20.1", "1.21.4").inOrder()
    }

    @Test
    fun fallbackVersions_includeOldAndModernMinecraftVersions() {
        assertThat(fallbackPaperVersions()).containsAtLeast("1.8.8", "1.12.2", "1.16.5", "1.20.1", "1.21.4")
    }
}
