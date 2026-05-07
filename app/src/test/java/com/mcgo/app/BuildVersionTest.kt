package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0233Code43() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.33")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(43)
    }
}
