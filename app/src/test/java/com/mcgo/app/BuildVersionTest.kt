package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0268Code78() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.68")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(78)
    }
}
