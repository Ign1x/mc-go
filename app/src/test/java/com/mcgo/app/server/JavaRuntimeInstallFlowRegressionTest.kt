package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.downloadVerifiedFileFromAnyUrl
import java.io.ByteArrayInputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.test.Test

class JavaRuntimeInstallFlowRegressionTest {

    @Test
    fun downloadVerifiedFileFromAnyUrl_retriesNextUrlWhenChecksumMismatchAfterFullDownload() {
        val target = Files.createTempFile("mcgo-runtime-download", ".tar.xz")
        val expectedSha256 = sha256Hex(ByteArrayInputStream("good-runtime".toByteArray()))
        val attemptedUrls = mutableListOf<String>()

        downloadVerifiedFileFromAnyUrl(
            urls = listOf("https://mirror.invalid/runtime.tar.xz", "https://origin.invalid/runtime.tar.xz"),
            target = target,
            expectedSha256 = expectedSha256,
            expectedDisplayName = "jre-25/bin-arm64.tar.xz",
            downloader = { url: String, path: Path, onProgress: (Int) -> Unit ->
                attemptedUrls += url
                Files.write(path, if (url.contains("mirror")) "bad-runtime".toByteArray() else "good-runtime".toByteArray())
                onProgress(100)
            },
        )

        assertThat(attemptedUrls).containsExactly(
            "https://mirror.invalid/runtime.tar.xz",
            "https://origin.invalid/runtime.tar.xz",
        ).inOrder()
        assertThat(String(Files.readAllBytes(target))).isEqualTo("good-runtime")
    }

    @Test
    fun moveInstalledRuntimeIntoPlace_fallsBackWhenAtomicDirectoryMoveIsUnsupported() {
        val filesDir = Files.createTempDirectory("mcgo-runtime-move")
        val stagingRoot = filesDir.resolve("jre")
        Files.createDirectories(stagingRoot)
        val tempDir = Files.createTempDirectory(stagingRoot, "java-17-install-")
        Files.createDirectories(tempDir.resolve("bin"))
        Files.write(tempDir.resolve("bin/java"), "#!/system/bin/sh\n".toByteArray())
        val target = stagingRoot.resolve("java-17")

        var firstAttempt = true
        moveInstalledRuntimeIntoPlace(tempDir, target) { source: Path, destination: Path, options: Array<out CopyOption> ->
            if (firstAttempt && options.contains(StandardCopyOption.ATOMIC_MOVE)) {
                firstAttempt = false
                throw AtomicMoveNotSupportedException(source.toString(), destination.toString(), "directory atomic move unsupported")
            }
            Files.move(source, destination, *options)
        }

        assertThat(Files.exists(target.resolve("bin/java"))).isTrue()
        assertThat(Files.exists(tempDir)).isFalse()
    }

    @Test
    fun nativeLauncherSource_keepsNullTerminatedArgvForJliReexecCompatibility() {
        val source = String(Files.readAllBytes(nativeLauncherSourcePath()))

        assertThat(source).contains("argv.push_back(nullptr);")
    }

    private fun nativeLauncherSourcePath(): Path =
        Paths.get("src/main/cpp/paper_jli_launcher.cpp")
}
