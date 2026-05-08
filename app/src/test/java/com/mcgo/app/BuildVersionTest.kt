package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0239Code49() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.39")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(49)
    }
}
