package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0262Code72() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.62")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(72)
    }
}
