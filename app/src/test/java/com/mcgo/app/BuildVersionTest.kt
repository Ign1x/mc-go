package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0220Code30() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.20")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(30)
    }
}
