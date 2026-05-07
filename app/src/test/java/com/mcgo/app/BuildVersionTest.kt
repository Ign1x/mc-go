package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0232Code42() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.32")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(42)
    }
}
