package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class AndroidManifestCompatibilityTest {
    @Test
    fun sourceManifest_disablesNativeHeapPointerTagging_forEmbeddedHotSpotCompatibility() {
        val manifest = readTextFromExisting(
            listOf(
                Path.of("src/main/AndroidManifest.xml"),
                Path.of("app/src/main/AndroidManifest.xml"),
            ),
        )

        assertThat(manifest).contains("android:allowNativeHeapPointerTagging=\"false\"")
    }

    @Test
    fun mergedDebugManifest_keepsNativeHeapPointerTaggingDisabled() {
        val manifest = readTextFromExisting(
            listOf(
                Path.of("app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"),
                Path.of("build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"),
                Path.of("app/build/intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml"),
                Path.of("build/intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml"),
            ),
        )

        assertThat(manifest).contains("android:allowNativeHeapPointerTagging=\"false\"")
    }

    private fun readTextFromExisting(candidates: List<Path>): String {
        val path = candidates.firstOrNull { Files.exists(it) } ?: error(
            "Expected one of these manifest paths to exist: ${candidates.joinToString()}"
        )
        return String(Files.readAllBytes(path))
    }
}
