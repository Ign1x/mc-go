package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertFailsWith

class JavaRuntimeImportTest {
    @Test
    fun classifyJavaRuntimeArchiveName_acceptsPojavApk() {
        assertThat(classifyJavaRuntimeArchiveName("PojavLauncher-gladiolus.apk"))
            .isEqualTo(JavaRuntimeArchiveKind.PojavApk)
    }

    @Test
    fun classifyJavaRuntimeArchiveName_acceptsTarXzArchives() {
        assertThat(classifyJavaRuntimeArchiveName("android-jre-17.tar.xz"))
            .isEqualTo(JavaRuntimeArchiveKind.TarXz)
        assertThat(classifyJavaRuntimeArchiveName("android-jre-21.txz"))
            .isEqualTo(JavaRuntimeArchiveKind.TarXz)
    }

    @Test
    fun classifyJavaRuntimeArchiveName_rejectsUnsupportedFiles() {
        val error = assertFailsWith<JavaRuntimeInstallException> {
            classifyJavaRuntimeArchiveName("desktop-jre.zip")
        }

        assertThat(error).hasMessageThat().contains("仅支持")
        assertThat(error).hasMessageThat().contains("APK")
        assertThat(error).hasMessageThat().contains("tar.xz")
    }

    @Test
    fun javaRuntimeArchiveTempSuffix_preservesSupportedExtensions() {
        assertThat(javaRuntimeArchiveTempSuffix("Pojav.apk")).isEqualTo(".apk")
        assertThat(javaRuntimeArchiveTempSuffix("jre-17.tar.xz")).isEqualTo(".tar.xz")
        assertThat(javaRuntimeArchiveTempSuffix("jre-21.txz")).isEqualTo(".txz")
        assertThat(javaRuntimeArchiveTempSuffix("unknown.bin")).isEqualTo(".archive")
    }
}
