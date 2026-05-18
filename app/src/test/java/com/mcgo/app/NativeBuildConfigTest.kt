package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class NativeBuildConfigTest {
    @Test
    fun appGradle_usesInstalledNdkVersionForReproducibleLocalNativeBuilds() {
        val buildFile = readTextFromExisting(
            listOf(
                Path.of("build.gradle.kts"),
                Path.of("app/build.gradle.kts"),
            ),
        )

        assertThat(buildFile).contains("ndkVersion = \"27.1.12297006\"")
    }

    private fun readTextFromExisting(candidates: List<Path>): String {
        val path = candidates.firstOrNull { Files.exists(it) } ?: error(
            "Expected one of these Gradle build files to exist: ${candidates.joinToString()}",
        )
        return String(Files.readAllBytes(path))
    }
}
