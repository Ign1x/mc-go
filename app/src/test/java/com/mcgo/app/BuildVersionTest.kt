package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0229Code39() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.29")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(39)
    }
}
