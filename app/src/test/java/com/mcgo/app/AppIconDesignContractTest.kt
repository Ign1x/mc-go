package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class AppIconDesignContractTest {
    @Test
    fun sourceManifest_usesAdaptiveLauncherIcons() {
        val manifest = readTextFromExisting(
            listOf(
                Path.of("src/main/AndroidManifest.xml"),
                Path.of("app/src/main/AndroidManifest.xml"),
            ),
        )

        assertThat(manifest).contains("android:icon=\"@mipmap/ic_launcher\"")
        assertThat(manifest).contains("android:roundIcon=\"@mipmap/ic_launcher_round\"")
    }

    @Test
    fun adaptiveIcon_resourcesUseNewDarkGradientCubeForegroundAndThemedMonochrome() {
        val launcher = readTextFromExisting(
            listOf(
                Path.of("src/main/res/mipmap-anydpi-v26/ic_launcher.xml"),
                Path.of("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"),
            ),
        )
        val launcherRound = readTextFromExisting(
            listOf(
                Path.of("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"),
                Path.of("app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"),
            ),
        )
        val foreground = readTextFromExisting(
            listOf(
                Path.of("src/main/res/drawable/ic_launcher_foreground.xml"),
                Path.of("app/src/main/res/drawable/ic_launcher_foreground.xml"),
            ),
        )
        val background = readTextFromExisting(
            listOf(
                Path.of("src/main/res/drawable/ic_launcher_background.xml"),
                Path.of("app/src/main/res/drawable/ic_launcher_background.xml"),
            ),
        )
        val monochrome = readTextFromExisting(
            listOf(
                Path.of("src/main/res/drawable/ic_launcher_monochrome.xml"),
                Path.of("app/src/main/res/drawable/ic_launcher_monochrome.xml"),
            ),
        )

        assertThat(launcher).contains("android:drawable=\"@drawable/ic_launcher_background\"")
        assertThat(launcher).contains("android:drawable=\"@drawable/ic_launcher_foreground\"")
        assertThat(launcher).contains("android:drawable=\"@drawable/ic_launcher_monochrome\"")
        assertThat(launcherRound).contains("android:drawable=\"@drawable/ic_launcher_background\"")
        assertThat(launcherRound).contains("android:drawable=\"@drawable/ic_launcher_foreground\"")

        assertThat(foreground).contains("android:viewportWidth=\"108\"")
        assertThat(foreground).contains("android:viewportHeight=\"108\"")
        assertThat(foreground).contains("android:fillColor=\"#8be067\"")
        assertThat(foreground).contains("android:fillColor=\"#64b543\"")
        assertThat(foreground).contains("android:fillColor=\"#549f3c\"")
        assertThat(foreground).contains("android:fillColor=\"#7d5938\"")
        assertThat(foreground).contains("android:fillColor=\"#65432a\"")
        assertThat(foreground).contains("android:pathData=\"M54,25L78,39L78,69L54,83L30,69L30,39Z\"")
        assertThat(foreground).contains("android:pathData=\"M54,34L67,41L54,48L41,41Z\"")
        assertThat(foreground).doesNotContain("android:fillColor=\"#39A845\"")
        assertThat(background).contains("android:startColor=\"#182019\"")
        assertThat(background).contains("android:centerColor=\"#1f4024\"")
        assertThat(background).contains("android:endColor=\"#0f2e42\"")
        assertThat(monochrome).contains("android:fillColor=\"#FFFFFFFF\"")
        assertThat(monochrome).contains("android:pathData=\"M54,25L78,39L78,69L54,83L30,69L30,39Z M54,34L67,41L54,48L41,41Z\"")
        assertThat(monochrome).doesNotContain("android:pathData=\"M34,36h40\"")
    }

    private fun readTextFromExisting(candidates: List<Path>): String {
        val path = candidates.firstOrNull { Files.exists(it) } ?: error(
            "Expected one of these resource paths to exist: ${candidates.joinToString()}"
        )
        return String(Files.readAllBytes(path))
    }
}
