package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV02109Code119() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.109")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(119)
    }
}
