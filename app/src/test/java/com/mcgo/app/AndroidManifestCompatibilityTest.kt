package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class AndroidManifestCompatibilityTest {
    @Test
    fun manifest_disablesNativeHeapPointerTagging_forEmbeddedHotSpotCompatibility() {
        val manifestPath = listOf(
            Path.of("src/main/AndroidManifest.xml"),
            Path.of("app/src/main/AndroidManifest.xml"),
        ).firstOrNull { Files.exists(it) } ?: error("AndroidManifest.xml not found")

        val manifest = String(Files.readAllBytes(manifestPath))

        assertThat(manifest).contains("android:allowNativeHeapPointerTagging=\"false\"")
    }
}
