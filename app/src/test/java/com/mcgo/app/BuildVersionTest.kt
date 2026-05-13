package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0277Code87() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.77")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(87)
    }
}
