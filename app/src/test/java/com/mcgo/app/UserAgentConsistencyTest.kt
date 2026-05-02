package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class UserAgentConsistencyTest {
    @Test
    fun mcGoUserAgent_matchesCurrentBuildVersion() {
        assertThat(McGoUserAgent).isEqualTo("MC-GO/${BuildConfig.VERSION_NAME}")
    }

    @Test
    fun mainSources_doNotHardcodeMcGoVersionStrings() {
        val root = projectRoot()
        val sourceRoot = root.resolve("app/src/main/java")
        val offenders = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .filter { path ->
                    val source = String(Files.readAllBytes(path))
                    Regex("MC-GO/\\d+\\.\\d+\\.\\d+").containsMatchIn(source)
                }
                .map { root.relativize(it).toString() }
                .toList()
        }

        assertThat(offenders).isEmpty()
    }

    private fun projectRoot(): Path =
        listOf(Path.of("."), Path.of(".."))
            .map { it.toAbsolutePath().normalize() }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
