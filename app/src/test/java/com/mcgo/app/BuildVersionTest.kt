package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV02103Code113() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.103")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(113)
    }
}
