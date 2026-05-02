package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class JavaRuntimeLayoutTest {

    @Test
    fun resolveManagedJavaRuntimeLayout_buildsLibraryPathAndBootstrapLibraries() {
        val javaHome = createRuntimeLayout(
            osArch = "aarch64",
            withDedicatedJliDirectory = true,
        )

        val layout = resolveManagedJavaRuntimeLayout(
            javaHome = javaHome,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(layout.javaHome).isEqualTo(javaHome)
        assertThat(layout.libjliPath).isEqualTo(javaHome.resolve("lib/aarch64/jli/libjli.so"))
        assertThat(layout.libjvmPath).isEqualTo(javaHome.resolve("lib/aarch64/server/libjvm.so"))
        assertThat(layout.libraryPath).contains(javaHome.resolve("lib/aarch64").toString())
        assertThat(layout.libraryPath).contains(javaHome.resolve("lib/aarch64/jli").toString())
        assertThat(layout.libraryPath).contains(javaHome.resolve("lib/aarch64/server").toString())
        assertThat(layout.libraryPath).contains("/system/lib64")
        assertThat(layout.bootstrapLibraries.map { it.fileName.toString() }).containsAtLeast(
            "libjli.so",
            "libjvm.so",
            "libverify.so",
            "libjava.so",
            "libnet.so",
            "libnio.so",
        )
    }

    @Test
    fun resolveManagedJavaRuntimeLayout_fallsBackToJavaLibDirWhenJliFolderMissing() {
        val javaHome = createRuntimeLayout(
            osArch = "aarch64",
            withDedicatedJliDirectory = false,
        )

        val layout = resolveManagedJavaRuntimeLayout(
            javaHome = javaHome,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )

        assertThat(layout.libjliPath).isEqualTo(javaHome.resolve("lib/aarch64/libjli.so"))
        assertThat(layout.libraryPath).contains(javaHome.resolve("lib/aarch64").toString())
    }

    @Test
    fun resolveManagedJavaRuntimeLayout_keepsLibjvmAheadOfLibjavaForJava8Layouts() {
        val javaHome = createRuntimeLayout(
            osArch = "aarch64",
            withDedicatedJliDirectory = true,
        )

        val layout = resolveManagedJavaRuntimeLayout(
            javaHome = javaHome,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )
        val names = layout.bootstrapLibraries.map { it.fileName.toString() }

        assertThat(names.indexOf("libjvm.so")).isLessThan(names.indexOf("libjava.so"))
        assertThat(names.indexOf("libverify.so")).isLessThan(names.indexOf("libjava.so"))
    }

    @Test
    fun resolveManagedJavaRuntimeLayout_preloadsDependenciesBeforeLibzip() {
        val javaHome = createRuntimeLayout(
            osArch = "aarch64",
            withDedicatedJliDirectory = true,
        ).also { home ->
            Files.write(home.resolve("lib/aarch64/libzip.so"), byteArrayOf(1))
            Files.write(home.resolve("lib/aarch64/libjimage.so"), byteArrayOf(1))
        }

        val layout = resolveManagedJavaRuntimeLayout(
            javaHome = javaHome,
            nativeLibraryDir = "/data/app/com.mcgo.app/lib/arm64",
            is64BitProcess = true,
        )
        val names = layout.bootstrapLibraries.map { it.fileName.toString() }

        assertThat(names.indexOf("libjava.so")).isLessThan(names.indexOf("libzip.so"))
        assertThat(names.indexOf("libjvm.so")).isLessThan(names.indexOf("libzip.so"))
        assertThat(names.indexOf("libnet.so")).isLessThan(names.indexOf("libnio.so"))
    }

    private fun createRuntimeLayout(
        osArch: String,
        withDedicatedJliDirectory: Boolean,
    ): Path {
        val javaHome = Files.createTempDirectory("mcgo-java-layout")
        Files.write(
            javaHome.resolve("release"),
            "OS_ARCH=\"$osArch\"\n".toByteArray(),
        )
        Files.createDirectories(javaHome.resolve("bin"))
        Files.write(javaHome.resolve("bin/java"), byteArrayOf(1))
        javaHome.resolve("bin/java").toFile().setExecutable(true, false)

        val javaLibDir = javaHome.resolve("lib/aarch64")
        val serverDir = javaLibDir.resolve("server")
        Files.createDirectories(serverDir)
        Files.write(serverDir.resolve("libjvm.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libverify.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libjava.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libnet.so"), byteArrayOf(1))
        Files.write(javaLibDir.resolve("libnio.so"), byteArrayOf(1))
        if (withDedicatedJliDirectory) {
            val jliDir = javaLibDir.resolve("jli")
            Files.createDirectories(jliDir)
            Files.write(jliDir.resolve("libjli.so"), byteArrayOf(1))
        } else {
            Files.write(javaLibDir.resolve("libjli.so"), byteArrayOf(1))
        }
        return javaHome
    }
}