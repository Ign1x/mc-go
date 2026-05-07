package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0230Code40() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.30")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(40)
    }
}
