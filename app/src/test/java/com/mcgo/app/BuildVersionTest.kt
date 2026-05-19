package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0288Code98() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.88")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(98)
    }
}
