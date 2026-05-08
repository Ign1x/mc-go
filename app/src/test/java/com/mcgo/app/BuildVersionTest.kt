package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0243Code53() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.43")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(53)
    }
}
