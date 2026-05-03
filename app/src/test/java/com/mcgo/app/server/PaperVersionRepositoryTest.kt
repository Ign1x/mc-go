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
    fun fallbackVersions_includeOldAndModernMinecraftVersions_withoutHardPinningLatest26Patch() {
        assertThat(fallbackPaperVersions()).containsAtLeast("1.8.8", "1.12.2", "1.16.5", "1.20.1", "1.21.11")
        assertThat(fallbackPaperVersions()).doesNotContain("26.1.2")
    }

    @Test
    fun filterProvisionablePaperVersions_keepsPaperVersionsServedByManagedJava8or11or17or21or25() {
        assertThat(
            filterProvisionablePaperVersions(
                listOf("1.8.8", "1.12.2", "1.16.4", "1.16.5", "1.17.1", "1.20.1", "1.21.11", "26.1.2", "1.21.9-pre2"),
            ),
        ).containsExactly("1.8.8", "1.12.2", "1.16.4", "1.16.5", "1.17.1", "1.20.1", "1.21.11", "26.1.2").inOrder()
    }

    @Test
    fun resolveProvisionablePaperVersionOptions_filtersDialogDefaultsThroughSameProvisioningRule() {
        assertThat(
            resolveProvisionablePaperVersionOptions(
                listOf("1.12.2", "1.16.5", "1.17.1", "1.20.1", "1.21.11", "26.1.2"),
            ),
        ).containsExactly("1.12.2", "1.16.5", "1.17.1", "1.20.1", "1.21.11", "26.1.2").inOrder()
    }

    @Test
    fun initialProvisionablePaperVersion_usesFilteredNewestVersionAndFallsBackWhenNeeded() {
        assertThat(initialProvisionablePaperVersion(listOf("1.12.2", "1.20.1", "1.21.11"))).isEqualTo("1.21.11")
        assertThat(initialProvisionablePaperVersion(listOf("1.12.2", "1.16.5"))).isEqualTo("1.16.5")
        assertThat(initialProvisionablePaperVersion(listOf("1.21.11", "26.1.2"))).isEqualTo("26.1.2")
    }

    @Test
    fun parseLatestPaperDownloadsPageArtifact_extractsLatestStableFillDataDownload() {
        val html = """
            <astro-island props="{&quot;data&quot;:[0,{&quot;projectResult&quot;:[0,{&quot;value&quot;:[0,{&quot;latestStableVersion&quot;:[0,&quot;26.1.2&quot;],&quot;latestExperimentalVersion&quot;:[0,null],&quot;latestVersionGroup&quot;:[0,&quot;26.1&quot;]}]}],&quot;stableBuildsResult&quot;:[0,{&quot;value&quot;:[0,{&quot;latest&quot;:[0,{&quot;id&quot;:[0,53],&quot;downloads&quot;:[0,{&quot;server:default&quot;:[0,{&quot;name&quot;:[0,&quot;paper-26.1.2-53.jar&quot;],&quot;checksums&quot;:[0,{&quot;sha256&quot;:[0,&quot;6934188878fc351e1be5bfba5f2b8c4591224886e4b34e3de09dbec68a351caf&quot;]}],&quot;url&quot;:[0,&quot;https://fill-data.papermc.io/v1/objects/6934188878fc351e1be5bfba5f2b8c4591224886e4b34e3de09dbec68a351caf/paper-26.1.2-53.jar&quot;]}]}]}]}]}]}]}]}"></astro-island>
        """.trimIndent()

        val artifact = parseLatestPaperDownloadsPageArtifact(html)

        assertThat(artifact).isNotNull()
        assertThat(artifact?.version).isEqualTo("26.1.2")
        assertThat(artifact?.build).isEqualTo(53)
        assertThat(artifact?.downloadName).isEqualTo("paper-26.1.2-53.jar")
        assertThat(artifact?.sha256).isEqualTo("6934188878fc351e1be5bfba5f2b8c4591224886e4b34e3de09dbec68a351caf")
        assertThat(artifact?.downloadUrl).contains("fill-data.papermc.io")
    }
}
