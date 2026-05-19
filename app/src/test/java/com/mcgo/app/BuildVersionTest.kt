package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0283Code93() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.83")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(93)
    }
}
