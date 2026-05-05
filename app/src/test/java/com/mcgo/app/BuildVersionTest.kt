package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0222Code32() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.22")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(32)
    }
}
