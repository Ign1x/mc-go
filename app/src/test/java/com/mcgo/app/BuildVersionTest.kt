package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0219Code29() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.19")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(29)
    }
}
