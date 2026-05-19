package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0291Code101() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.91")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(101)
    }
}
