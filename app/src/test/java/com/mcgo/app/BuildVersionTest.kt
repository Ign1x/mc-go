package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0294Code104() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.94")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(104)
    }
}
