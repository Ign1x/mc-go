package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV02131Code141() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.131")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(141)
    }
}
