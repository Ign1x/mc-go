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
    fun sourceManifest_disablesSystemBackupForSensitiveLocalProfiles() {
        val manifest = readTextFromExisting(
            listOf(
                Path.of("src/main/AndroidManifest.xml"),
                Path.of("app/src/main/AndroidManifest.xml"),
            ),
        )

        assertThat(manifest).contains("android:allowBackup=\"false\"")
        assertThat(manifest).doesNotContain("android:allowBackup=\"true\"")
    }

    @Test
    fun sourceManifest_forcesNativeLibExtraction_forBundledExecutableFrpc() {
        val manifest = readTextFromExisting(
            listOf(
                Path.of("src/main/AndroidManifest.xml"),
                Path.of("app/src/main/AndroidManifest.xml"),
            ),
        )

        assertThat(manifest).contains("android:extractNativeLibs=\"true\"")
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
        assertThat(manifest).contains("android:extractNativeLibs=\"true\"")
    }

    @Test
    fun sourceManifest_registersMultiplePaperRuntimeProcessesForConcurrentServers() {
        val manifest = readTextFromExisting(
            listOf(
                Path.of("src/main/AndroidManifest.xml"),
                Path.of("app/src/main/AndroidManifest.xml"),
            ),
        )

        assertThat(manifest).contains("android:process=\":paper_runtime\"")
        assertThat(manifest).contains("android:process=\":paper_runtime_2\"")
        assertThat(manifest).contains("android:process=\":paper_runtime_3\"")
        assertThat(manifest).contains("android:process=\":paper_runtime_4\"")
    }

    private fun readTextFromExisting(candidates: List<Path>): String {
        val path = candidates.firstOrNull { Files.exists(it) } ?: error(
            "Expected one of these manifest paths to exist: ${candidates.joinToString()}"
        )
        return String(Files.readAllBytes(path))
    }
}
