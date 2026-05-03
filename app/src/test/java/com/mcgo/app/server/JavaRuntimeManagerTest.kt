package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream

class JavaRuntimeManagerTest {

    @Test
    fun abiArchiveName_mapsAndroidAbiToPojavComponentArchive() {
        assertThat(abiArchiveName("arm64-v8a")).isEqualTo("bin-arm64.tar.xz")
        assertThat(abiArchiveName("armeabi-v7a")).isEqualTo("bin-arm.tar.xz")
        assertThat(abiArchiveName("x86")).isEqualTo("bin-x86.tar.xz")
        assertThat(abiArchiveName("x86_64")).isEqualTo("bin-x86_64.tar.xz")
        assertFailsWith<JavaRuntimeInstallException> { abiArchiveName("mips") }
    }

    @Test
    fun extractTarXzSafely_rejectsAbsoluteAndParentTraversalPaths() {
        val root = Files.createTempDirectory("mcgo-jre-safe")
        val outside = root.parent.resolve("escape.txt")

        assertFailsWith<JavaRuntimeInstallException> {
            extractTarXzSafely(ByteArrayInputStream(tarXz(file("../escape.txt", "bad"))), root)
        }
        assertFailsWith<JavaRuntimeInstallException> {
            extractTarXzSafely(ByteArrayInputStream(rawSingleFileTarXz("/escape.txt", "bad")), root)
        }

        assertThat(Files.exists(outside)).isFalse()
    }

    @Test
    fun extractTarXzSafely_handlesLeadingDotAndRelativeSymlinkInsideTarget() {
        val root = Files.createTempDirectory("mcgo-jre-symlink")

        extractTarXzSafely(
            ByteArrayInputStream(
                tarXz(
                    directory("./lib"),
                    directory("./lib/server"),
                    file("./lib/libjsig.so", "native"),
                    symlink("./lib/server/libjsig.so", "../libjsig.so"),
                ),
            ),
            root,
        )

        assertThat(String(Files.readAllBytes(root.resolve("lib/libjsig.so")))).isEqualTo("native")
        assertThat(Files.isSymbolicLink(root.resolve("lib/server/libjsig.so"))).isTrue()
        assertThat(Files.readSymbolicLink(root.resolve("lib/server/libjsig.so")).toString()).isEqualTo("../libjsig.so")
    }

    @Test
    fun installPojavRuntimeFromApk_extractsUniversalAndAbiArchivesAndPreservesExecutableBit() {
        val filesDir = Files.createTempDirectory("mcgo-jre-install")
        val apk = filesDir.resolve("pojav.apk")
        writeFakePojavApk(
            apk = apk,
            component = "jre-new",
            universal = tarXz(
                directory("./conf"),
                file("./conf/net.properties", "managed=true\n"),
            ),
            abiArchiveName = "bin-arm64.tar.xz",
            abi = tarXz(
                file("./release", "JAVA_VERSION=\"17.0.14\"\nOS_ARCH=\"aarch64\"\n"),
                directory("./bin"),
                file("./bin/java", "#!/system/bin/sh\n", mode = 0b111_101_101),
            ),
        )

        val javaHome = installPojavRuntimeFromApk(
            apkPath = apk,
            filesDir = filesDir,
            majorVersion = 17,
            androidAbi = "arm64-v8a",
        )

        assertThat(javaHome).isEqualTo(filesDir.resolve("jre/java-17"))
        assertThat(String(Files.readAllBytes(javaHome.resolve("release")))).contains("17.0.14")
        assertThat(String(Files.readAllBytes(javaHome.resolve("conf/net.properties")))).contains("managed=true")
        assertThat(Files.exists(javaHome.resolve("bin/java"))).isTrue()
        assertThat(javaHome.resolve("bin/java").toFile().canExecute()).isTrue()
        assertThat(scanInstalledJavaVersions(filesDir)).contains(17)
    }

    @Test
    fun installPojavRuntimeFromApk_detectsJava11ComponentByRuntimeReleaseVersion() {
        val filesDir = Files.createTempDirectory("mcgo-jre-install-java11")
        val apk = filesDir.resolve("pojav-java11.apk")
        writeFakePojavApk(
            apk = apk,
            component = "jre-11",
            universal = tarXz(directory("./conf")),
            abiArchiveName = "bin-arm64.tar.xz",
            abi = tarXz(
                file("./release", "JAVA_VERSION=\"11.0.25\"\nOS_ARCH=\"aarch64\"\n"),
                directory("./bin"),
                file("./bin/java", "#!/system/bin/sh\n", mode = 0b111_101_101),
            ),
        )

        val javaHome = installPojavRuntimeFromApk(
            apkPath = apk,
            filesDir = filesDir,
            majorVersion = 11,
            androidAbi = "arm64-v8a",
        )

        assertThat(javaHome).isEqualTo(filesDir.resolve("jre/java-11"))
        assertThat(String(Files.readAllBytes(javaHome.resolve("release")))).contains("11.0.25")
        assertThat(scanInstalledJavaVersions(filesDir)).contains(11)
    }

    @Test
    fun installPojavRuntimeFromApk_rejectsPreferredComponentWhenReleaseVersionDoesNotMatchRequestedSlot() {
        val filesDir = Files.createTempDirectory("mcgo-jre-install-java11-mismatch")
        val apk = filesDir.resolve("pojav-java11-mismatch.apk")
        writeFakePojavApk(
            apk = apk,
            component = "jre-11",
            universal = tarXz(directory("./conf")),
            abiArchiveName = "bin-arm64.tar.xz",
            abi = tarXz(
                file("./release", "JAVA_VERSION=\"17.0.14\"\nOS_ARCH=\"aarch64\"\n"),
                directory("./bin"),
                file("./bin/java", "#!/system/bin/sh\n", mode = 0b111_101_101),
            ),
        )

        val error = assertFailsWith<JavaRuntimeInstallException> {
            installPojavRuntimeFromApk(
                apkPath = apk,
                filesDir = filesDir,
                majorVersion = 11,
                androidAbi = "arm64-v8a",
            )
        }

        assertThat(error).hasMessageThat().contains("Java 11")
    }

    @Test
    fun installPojavRuntimeFromApk_detectsJava21WhenReleaseExistsOnlyInAbiArchive() {
        val filesDir = Files.createTempDirectory("mcgo-jre-install-java21")
        val apk = filesDir.resolve("pojav-java21.apk")
        writeFakePojavApk(
            apk = apk,
            component = "jre-21",
            universal = tarXz(directory("./legal")),
            abiArchiveName = "bin-arm64.tar.xz",
            abi = tarXz(
                file("./release", "JAVA_VERSION=\"21.0.6\"\nOS_ARCH=\"aarch64\"\n"),
                directory("./bin"),
                file("./bin/java", "#!/system/bin/sh\n", mode = 0b111_101_101),
            ),
        )

        val javaHome = installPojavRuntimeFromApk(
            apkPath = apk,
            filesDir = filesDir,
            majorVersion = 21,
            androidAbi = "arm64-v8a",
        )

        assertThat(javaHome).isEqualTo(filesDir.resolve("jre/java-21"))
        assertThat(String(Files.readAllBytes(javaHome.resolve("release")))).contains("21.0.6")
        assertThat(scanInstalledJavaVersions(filesDir)).contains(21)
    }

    @Test
    fun installRuntimeFromTarXz_installsJava11FromTrustedDirectRuntimeArchive() {
        val filesDir = Files.createTempDirectory("mcgo-jre-install-java11-tarxz")
        val archive = filesDir.resolve("jre11.tar.xz")
        Files.write(
            archive,
            tarXz(
                file("./release", "JAVA_VERSION=\"11.0.25\"\nOS_ARCH=\"aarch64\"\n"),
                directory("./bin"),
                file("./bin/java", "#!/system/bin/sh\n", mode = 0b111_101_101),
            ),
        )

        val javaHome = installRuntimeFromTarXz(
            archivePath = archive,
            filesDir = filesDir,
            majorVersion = 11,
        )

        assertThat(javaHome).isEqualTo(filesDir.resolve("jre/java-11"))
        assertThat(String(Files.readAllBytes(javaHome.resolve("release")))).contains("11.0.25")
        assertThat(scanInstalledJavaVersions(filesDir)).contains(11)
    }

    @Test
    fun deleteJavaRuntime_removesOnlyRequestedManagedRuntime() {
        val filesDir = Files.createTempDirectory("mcgo-jre-delete")
        Files.createDirectories(filesDir.resolve("jre/java-17/bin"))
        Files.write(filesDir.resolve("jre/java-17/bin/java"), byteArrayOf(1))
        Files.createDirectories(filesDir.resolve("jre/java-21/bin"))
        Files.write(filesDir.resolve("jre/java-21/bin/java"), byteArrayOf(1))

        deleteJavaRuntime(filesDir, 17)

        assertThat(Files.exists(filesDir.resolve("jre/java-17"))).isFalse()
        assertThat(Files.exists(filesDir.resolve("jre/java-21/bin/java"))).isTrue()
    }

    @Test
    fun trustedRuntimeArchivesForVersion_supportsDirectJava25Arm64CatalogEntry() {
        val archives = trustedRuntimeArchivesForVersion(25, "arm64-v8a")

        assertThat(archives).hasSize(1)
        assertThat(archives.single().displayName).contains("jre-25")
        assertThat(archives.single().url).contains("jre25-arm64-20260223-release.tar.xz")
        assertThat(archives.single().sha256).isEqualTo("0fdf6d19fe66ea61c12caa24bd655227ddb0d7d9c16c0f13281a7c2878635286")
    }

    @Test
    fun trustedRuntimeArchivesForVersion_rejectsJava25OnUnsupportedAbi() {
        val error = assertFailsWith<JavaRuntimeInstallException> {
            trustedRuntimeArchivesForVersion(25, "x86_64")
        }

        assertThat(error).hasMessageThat().contains("Java 25")
        assertThat(error).hasMessageThat().contains("ARM64")
    }

    @Test
    fun installRuntimeFromTarXz_createsLegacyLibCompatibilityLinksForAndroidJliLaunchers() {
        val filesDir = Files.createTempDirectory("mcgo-jre-install-java17-compat")
        val archive = filesDir.resolve("jre17-compat.tar.xz")
        Files.write(
            archive,
            tarXz(
                file("./release", "JAVA_VERSION=\"17.0.14\"\nOS_ARCH=\"aarch64\"\n"),
                directory("./bin"),
                file("./bin/java", "#!/system/bin/sh\n", mode = 0b111_101_101),
                directory("./lib"),
                directory("./lib/aarch64"),
                directory("./lib/aarch64/server"),
                directory("./lib/aarch64/jli"),
                file("./lib/aarch64/server/libjvm.so", "jvm"),
                file("./lib/aarch64/jli/libjli.so", "jli"),
                file("./lib/aarch64/libjava.so", "java"),
                file("./lib/aarch64/libverify.so", "verify"),
                file("./lib/aarch64/libnet.so", "net"),
                file("./lib/aarch64/libnio.so", "nio"),
            ),
        )

        val javaHome = installRuntimeFromTarXz(
            archivePath = archive,
            filesDir = filesDir,
            majorVersion = 17,
        )

        assertThat(Files.exists(javaHome.resolve("lib/server/libjvm.so"))).isTrue()
        assertThat(Files.exists(javaHome.resolve("lib/jli/libjli.so"))).isTrue()
        assertThat(Files.exists(javaHome.resolve("lib/libjava.so"))).isTrue()
        assertThat(Files.exists(javaHome.resolve("lib/libverify.so"))).isTrue()
        assertThat(Files.exists(javaHome.resolve("lib/libnet.so"))).isTrue()
        assertThat(Files.exists(javaHome.resolve("lib/libnio.so"))).isTrue()
    }

    private data class TarSpec(
        val name: String,
        val bytes: ByteArray = byteArrayOf(),
        val mode: Int = 0b110_100_100,
        val directory: Boolean = false,
        val symlinkTarget: String? = null,
    )

    private fun file(name: String, text: String, mode: Int = 0b110_100_100) = TarSpec(
        name = name,
        bytes = text.toByteArray(),
        mode = mode,
    )

    private fun directory(name: String) = TarSpec(name = name, directory = true, mode = 0b111_101_101)

    private fun symlink(name: String, target: String) = TarSpec(name = name, symlinkTarget = target)

    private fun tarXz(vararg specs: TarSpec): ByteArray {
        val out = ByteArrayOutputStream()
        XZCompressorOutputStream(out).use { xz ->
            TarArchiveOutputStream(xz).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                specs.forEach { spec ->
                    val entry = when {
                        spec.symlinkTarget != null -> TarArchiveEntry(spec.name, TarArchiveEntry.LF_SYMLINK).apply {
                            linkName = spec.symlinkTarget
                            mode = 0b111_101_101
                        }
                        spec.directory -> TarArchiveEntry(spec.name.ensureTrailingSlash()).apply {
                            mode = spec.mode
                        }
                        else -> TarArchiveEntry(spec.name).apply {
                            size = spec.bytes.size.toLong()
                            mode = spec.mode
                        }
                    }
                    tar.putArchiveEntry(entry)
                    if (!spec.directory && spec.symlinkTarget == null) {
                        tar.write(spec.bytes)
                    }
                    tar.closeArchiveEntry()
                }
                tar.finish()
            }
        }
        return out.toByteArray()
    }

    private fun rawSingleFileTarXz(name: String, text: String): ByteArray {
        val out = ByteArrayOutputStream()
        XZCompressorOutputStream(out).use { xz ->
            val payload = text.toByteArray()
            xz.write(rawTarHeader(name = name, size = payload.size.toLong(), mode = 0b110_100_100, typeFlag = '0'))
            xz.write(payload)
            val padding = (512 - (payload.size % 512)) % 512
            if (padding > 0) xz.write(ByteArray(padding))
            xz.write(ByteArray(1024))
        }
        return out.toByteArray()
    }

    private fun rawTarHeader(name: String, size: Long, mode: Int, typeFlag: Char): ByteArray {
        val header = ByteArray(512)
        header.writeAscii(0, 100, name)
        header.writeOctal(100, 8, mode.toLong())
        header.writeOctal(108, 8, 0)
        header.writeOctal(116, 8, 0)
        header.writeOctal(124, 12, size)
        header.writeOctal(136, 12, 0)
        for (index in 148 until 156) header[index] = ' '.code.toByte()
        header[156] = typeFlag.code.toByte()
        header.writeAscii(257, 6, "ustar")
        header.writeAscii(263, 2, "00")
        val checksum = header.sumOf { it.toInt() and 0xff }.toLong()
        header.writeOctal(148, 8, checksum)
        header[154] = 0
        header[155] = ' '.code.toByte()
        return header
    }

    private fun ByteArray.writeAscii(offset: Int, length: Int, value: String) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        bytes.copyInto(this, destinationOffset = offset, endIndex = minOf(bytes.size, length))
    }

    private fun ByteArray.writeOctal(offset: Int, length: Int, value: Long) {
        val encoded = value.toString(8).toByteArray(Charsets.US_ASCII)
        val start = offset + length - encoded.size - 1
        for (index in offset until start) this[index] = '0'.code.toByte()
        encoded.copyInto(this, destinationOffset = start)
        this[offset + length - 1] = 0
    }

    private fun writeFakePojavApk(
        apk: Path,
        component: String,
        universal: ByteArray,
        abiArchiveName: String,
        abi: ByteArray,
    ) {
        java.util.zip.ZipOutputStream(Files.newOutputStream(apk)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("assets/components/$component/universal.tar.xz"))
            zip.write(universal)
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("assets/components/$component/$abiArchiveName"))
            zip.write(abi)
            zip.closeEntry()
        }
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
}
