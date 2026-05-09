package com.mcgo.app

import com.google.common.truth.Truth.assertThat
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.test.Test
import java.util.zip.ZipFile

class JnaAndroidPackagingContractTest {

    @Test
    fun buildGradle_packagesAndroidJnaAarForEmbeddedJvmNativeDispatch() {
        val buildGradle = Paths.get("build.gradle.kts").readText()

        assertThat(buildGradle).contains("implementation(files(\"libs/jna-5.18.1.aar\"))")
        assertThat(buildGradle.contains("implementation(\"net.java.dev.jna:jna:")).isFalse()
    }

    @Test
    fun bundledJnaAar_containsAndroidArm64NativeDispatchLibrary() {
        ZipFile("libs/jna-5.18.1.aar").use { zip ->
            assertThat(zip.getEntry("jni/arm64-v8a/libjnidispatch.so")).isNotNull()
        }
    }
}
