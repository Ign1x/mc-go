package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV02128Code138() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.128")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(138)
    }
}
