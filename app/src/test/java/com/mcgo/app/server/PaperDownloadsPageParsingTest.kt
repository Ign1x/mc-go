package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class PaperDownloadsPageParsingTest {
    @Test
    fun parseLatestPaperDownloadsPageArtifact_extracts26SeriesLatestBuildFromFillUiProps() {
        val html = """
            <astro-island props="{&quot;data&quot;:[0,{&quot;projectResult&quot;:[0,{&quot;value&quot;:[0,{&quot;latestStableVersion&quot;:[0,&quot;26.1.2&quot;],&quot;latestExperimentalVersion&quot;:[0,null],&quot;latestVersionGroup&quot;:[0,&quot;26.1&quot;]}]}],&quot;stableBuildsResult&quot;:[0,{&quot;value&quot;:[0,{&quot;latest&quot;:[0,{&quot;id&quot;:[0,53],&quot;downloads&quot;:[0,{&quot;server:default&quot;:[0,{&quot;name&quot;:[0,&quot;paper-26.1.2-53.jar&quot;],&quot;url&quot;:[0,&quot;https://api.papermc.io/v2/projects/paper/versions/26.1.2/builds/53/downloads/paper-26.1.2-53.jar&quot;],&quot;checksums&quot;:[0,{&quot;sha256&quot;:[0,&quot;0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef&quot;]}]}]}]}]}]}]}]}"></astro-island>
        """.trimIndent()

        val artifact = parseLatestPaperDownloadsPageArtifact(html)

        assertThat(artifact).isNotNull()
        assertThat(artifact?.version).isEqualTo("26.1.2")
        assertThat(artifact?.build).isEqualTo(53)
        assertThat(artifact?.downloadName).isEqualTo("paper-26.1.2-53.jar")
        assertThat(artifact?.sha256).isEqualTo("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    }
}
