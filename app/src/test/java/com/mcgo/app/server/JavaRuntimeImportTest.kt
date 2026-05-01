package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class JavaRuntimeImportTest {
    @Test
    fun classifyJavaRuntimeArchiveName_acceptsPojavApk() {
        assertThat(classifyJavaRuntimeArchiveName("PojavLauncher-gladiolus.apk"))
            .isEqualTo(JavaRuntimeArchiveKind.PojavApk)
    }

    @Test
    fun classifyJavaRuntimeArchiveName_rejectsTarXzArchives() {
        val tarXzError = assertFailsWith<JavaRuntimeInstallException> {
            classifyJavaRuntimeArchiveName("android-jre-17.tar.xz")
        }
        val txzError = assertFailsWith<JavaRuntimeInstallException> {
            classifyJavaRuntimeArchiveName("android-jre-21.txz")
        }

        assertThat(tarXzError).hasMessageThat().contains("Pojav APK")
        assertThat(tarXzError).hasMessageThat().doesNotContain("tar.xz")
        assertThat(txzError).hasMessageThat().contains("Pojav APK")
    }

    @Test
    fun classifyJavaRuntimeArchiveName_rejectsUnsupportedFiles() {
        val error = assertFailsWith<JavaRuntimeInstallException> {
            classifyJavaRuntimeArchiveName("desktop-jre.zip")
        }

        assertThat(error).hasMessageThat().contains("仅支持")
        assertThat(error).hasMessageThat().contains("APK")
        assertThat(error).hasMessageThat().doesNotContain("tar.xz")
    }

    @Test
    fun javaRuntimeArchiveTempSuffix_preservesSupportedExtensions() {
        assertThat(javaRuntimeArchiveTempSuffix("Pojav.apk")).isEqualTo(".apk")
        assertThat(javaRuntimeArchiveTempSuffix("jre-17.tar.xz")).isEqualTo(".tar.xz")
        assertThat(javaRuntimeArchiveTempSuffix("jre-21.txz")).isEqualTo(".txz")
        assertThat(javaRuntimeArchiveTempSuffix("unknown.bin")).isEqualTo(".archive")
    }

    @Test
    fun validateRuntimeArchiveTrust_allowsPinnedOfficialPojavApk() {
        validateRuntimeArchiveTrust(
            archiveKind = JavaRuntimeArchiveKind.PojavApk,
            source = JavaRuntimeArchiveSource.OfficialDownload,
            sha256 = OfficialPojavLauncherApkSha256,
            displayName = "PojavLauncher.apk",
        )
    }

    @Test
    fun validateRuntimeArchiveTrust_rejectsUntrustedImportedTarXz() {
        val error = assertFailsWith<JavaRuntimeInstallException> {
            validateRuntimeArchiveTrust(
                archiveKind = JavaRuntimeArchiveKind.TarXz,
                source = JavaRuntimeArchiveSource.UserImport,
                sha256 = "deadbeef",
                displayName = "android-jre-21.tar.xz",
            )
        }

        assertThat(error).hasMessageThat().contains("当前仅允许")
    }

    @Test
    fun sha256Hex_readsFileDigest() {
        val file = Files.createTempFile("mcgo-sha256", ".bin")
        Files.write(file, "abc".toByteArray())

        assertThat(sha256Hex(file)).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}
