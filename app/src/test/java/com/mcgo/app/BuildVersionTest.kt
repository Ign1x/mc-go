package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0235Code45() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.35")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(45)
    }
}
