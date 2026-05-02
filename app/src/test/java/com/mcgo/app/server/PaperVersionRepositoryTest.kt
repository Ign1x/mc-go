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

    @Test
    fun filterProvisionablePaperVersions_keepsPaperVersionsServedByManagedJava8or11or17or21() {
        assertThat(
            filterProvisionablePaperVersions(
                listOf("1.8.8", "1.12.2", "1.16.4", "1.16.5", "1.17.1", "1.20.1", "1.21.4", "1.21.9-pre2"),
            ),
        ).containsExactly("1.8.8", "1.12.2", "1.16.4", "1.16.5", "1.17.1", "1.20.1", "1.21.4").inOrder()
    }

    @Test
    fun resolveProvisionablePaperVersionOptions_filtersDialogDefaultsThroughSameProvisioningRule() {
        assertThat(
            resolveProvisionablePaperVersionOptions(
                listOf("1.12.2", "1.16.5", "1.17.1", "1.20.1", "1.21.4"),
            ),
        ).containsExactly("1.12.2", "1.16.5", "1.17.1", "1.20.1", "1.21.4").inOrder()
    }

    @Test
    fun initialProvisionablePaperVersion_usesFilteredNewestVersionAndFallsBackWhenNeeded() {
        assertThat(initialProvisionablePaperVersion(listOf("1.12.2", "1.20.1", "1.21.4"))).isEqualTo("1.21.4")
        assertThat(initialProvisionablePaperVersion(listOf("1.12.2", "1.16.5"))).isEqualTo("1.16.5")
    }
}
