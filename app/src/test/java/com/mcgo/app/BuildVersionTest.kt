package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0249Code59() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.49")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(59)
    }
}
