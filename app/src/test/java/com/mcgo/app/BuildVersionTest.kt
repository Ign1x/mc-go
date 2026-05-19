package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildVersionTest {
    @Test
    fun releaseVersion_isV0295Code105() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("0.2.95")
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(105)
    }
}
