package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0286Code96() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.86")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(96)
    }
}
