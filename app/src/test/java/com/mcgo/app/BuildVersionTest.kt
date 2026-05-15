package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0278Code88() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.78")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(88)
    }
}
