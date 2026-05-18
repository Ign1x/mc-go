package com.mcgo.app.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.mcgo.app.McGoUserAgent
import com.mcgo.app.server.JavaRuntimeArchiveKind
import com.mcgo.app.server.JavaRuntimeArchiveSource
import com.mcgo.app.server.JavaRuntimeInstallException
import com.mcgo.app.server.OfficialPojavLauncherCertSha256
import com.mcgo.app.server.TrustedJavaRuntimeTarball
import com.mcgo.app.server.abiArchiveName
import com.mcgo.app.server.classifyJavaRuntimeArchiveName
import com.mcgo.app.server.extractTarXzSafely
import com.mcgo.app.server.installPojavRuntimeFromApk
import com.mcgo.app.server.installRuntimeFromTarXz
import com.mcgo.app.server.installRuntimeWithStaging
import com.mcgo.app.server.javaRuntimeArchiveTempSuffix
import com.mcgo.app.server.resolvePojavRuntimeComponent
import com.mcgo.app.server.sha256Hex
import com.mcgo.app.server.trustedRuntimeArchivesForVersion
import com.mcgo.app.server.validateRuntimeArchiveTrust
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.util.jar.JarFile

internal fun downloadAndInstallPojavRuntime(
    context: Context,
    majorVersion: Int,
    onProgress: (Int) -> Unit = {},
): Path {
    val filesDir = context.filesDir.toPath()
    val archives = trustedRuntimeArchivesForVersion(
        majorVersion = majorVersion,
        abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
    )
    val tempFiles = mutableListOf<Path>()
    try {
        fun downloadArchive(archive: TrustedJavaRuntimeTarball, start: Int, end: Int): Path {
            val suffix = archive.url.substringAfterLast('/').let { if (it.endsWith(".tar.xz")) ".tar.xz" else ".archive" }
            val tempFile = Files.createTempFile(context.cacheDir.toPath(), "mcgo-runtime-", suffix)
            tempFiles.add(tempFile)
            downloadVerifiedFileToPath(
                urls = runtimeDownloadUrlsForRegion(context, archive.url),
                target = tempFile,
                expectedArchive = archive,
            ) { progress ->
                val mapped = start + ((end - start) * progress.coerceIn(0, 100) / 100)
                onProgress(mapped.coerceIn(start, end))
            }
            return tempFile
        }

        if (majorVersion == 25) {
            val arm64Archive = archives.single()
            val tempArchive = downloadArchive(arm64Archive, start = 1, end = 90)
            onProgress(94)
            return installRuntimeWithStaging(filesDir = filesDir, majorVersion = majorVersion) { tempDir ->
                Files.newInputStream(tempArchive).use { input -> extractTarXzSafely(input, tempDir) }
            }
        }

        val universalArchive = archives.first { it.displayName.endsWith("universal.tar.xz") }
        val abiArchive = archives.first { it != universalArchive }
        val universalTemp = downloadArchive(universalArchive, start = 1, end = 48)
        val abiTemp = downloadArchive(abiArchive, start = 49, end = 86)
        onProgress(90)
        return installRuntimeWithStaging(filesDir = filesDir, majorVersion = majorVersion) { tempDir ->
            Files.newInputStream(universalTemp).use { input -> extractTarXzSafely(input, tempDir) }
            Files.newInputStream(abiTemp).use { input -> extractTarXzSafely(input, tempDir) }
        }
    } finally {
        onProgress(100)
        tempFiles.forEach { Files.deleteIfExists(it) }
    }
}

private fun downloadFileToPath(urls: List<String>, target: Path, onProgress: (Int) -> Unit = {}) {
    var lastError: Exception? = null
    urls.distinct().forEach { url ->
        try {
            downloadSingleFileToPath(url, target, onProgress)
            return
        } catch (error: Exception) {
            lastError = error
        }
    }
    throw JavaRuntimeInstallException("下载 JRE 失败", lastError)
}

private fun downloadVerifiedFileToPath(
    urls: List<String>,
    target: Path,
    expectedArchive: TrustedJavaRuntimeTarball,
    onProgress: (Int) -> Unit = {},
) {
    downloadVerifiedFileFromAnyUrl(
        urls = urls,
        target = target,
        expectedSha256 = expectedArchive.sha256,
        expectedDisplayName = expectedArchive.displayName,
        downloader = ::downloadSingleFileToPath,
        onProgress = onProgress,
    )
}

internal fun downloadVerifiedFileFromAnyUrl(
    urls: List<String>,
    target: Path,
    expectedSha256: String,
    expectedDisplayName: String,
    downloader: (String, Path, (Int) -> Unit) -> Unit,
    onProgress: (Int) -> Unit = {},
) {
    var lastError: Exception? = null
    urls.distinct().forEach { url ->
        try {
            Files.deleteIfExists(target)
            downloader(url, target, onProgress)
            val actualSha256 = sha256Hex(target)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                throw JavaRuntimeInstallException(
                    "JRE 安装包可信校验失败：$expectedDisplayName 的 SHA-256 与预期不匹配",
                )
            }
            return
        } catch (error: Exception) {
            lastError = error
        }
    }
    throw JavaRuntimeInstallException("下载 JRE 失败", lastError)
}

private fun downloadSingleFileToPath(url: String, target: Path, onProgress: (Int) -> Unit) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 60_000
        requestMethod = "GET"
        setRequestProperty("User-Agent", McGoUserAgent)
    }
    try {
        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            throw JavaRuntimeInstallException("下载 JRE 失败：HTTP $statusCode")
        }
        val contentLength = connection.contentLengthLong.takeIf { it > 0L }
        connection.inputStream.use { input ->
            Files.newOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    contentLength?.let { onProgress(((copied * 100) / it).toInt().coerceIn(1, 100)) }
                }
                if (contentLength == null) onProgress(100)
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun runtimeDownloadUrlsForRegion(context: Context, canonicalUrl: String): List<String> {
    val mirror = "https://gh-proxy.com/$canonicalUrl"
    val language = context.resources.configuration.locales.get(0).language.lowercase()
    return if (language == "zh") listOf(mirror, canonicalUrl) else listOf(canonicalUrl, mirror)
}

internal fun installJavaRuntimeFromUri(
    context: Context,
    uri: Uri,
    majorVersion: Int,
): Path {
    val displayName = uri.displayName(context).ifBlank { "java-runtime.archive" }
    val archiveKind = classifyJavaRuntimeArchiveName(displayName)
    val tempFile = copyUriToTempFile(
        context = context,
        uri = uri,
        suffix = javaRuntimeArchiveTempSuffix(displayName),
    )
    return try {
        validateRuntimeArchiveTrust(
            archiveKind = archiveKind,
            source = JavaRuntimeArchiveSource.UserImport,
            sha256 = sha256Hex(tempFile),
            displayName = displayName,
            signerCertSha256 = when (archiveKind) {
                JavaRuntimeArchiveKind.PojavApk -> pojavRuntimeComponentSignerCertSha256(tempFile, majorVersion)
                JavaRuntimeArchiveKind.TarXz -> null
            },
        )
        when (archiveKind) {
            JavaRuntimeArchiveKind.PojavApk -> installPojavRuntimeFromApk(
                apkPath = tempFile,
                filesDir = context.filesDir.toPath(),
                majorVersion = majorVersion,
            )
            JavaRuntimeArchiveKind.TarXz -> installRuntimeFromTarXz(
                archivePath = tempFile,
                filesDir = context.filesDir.toPath(),
                majorVersion = majorVersion,
            )
        }
    } finally {
        Files.deleteIfExists(tempFile)
    }
}

private fun pojavRuntimeComponentSignerCertSha256(apkPath: Path, majorVersion: Int): String? = runCatching {
    JarFile(apkPath.toFile(), true).use { jar ->
        val component = resolvePojavRuntimeComponent(jar.asZipFile(), majorVersion)
        val targetEntries = listOf(
            "assets/components/$component/universal.tar.xz",
            "assets/components/$component/${abiArchiveName(Build.SUPPORTED_ABIS.firstOrNull().orEmpty())}",
        )
        for (entryName in targetEntries) {
            val entry = jar.getJarEntry(entryName) ?: return@runCatching null
            jar.getInputStream(entry).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (input.read(buffer) >= 0) {
                    // consume to trigger certificate verification
                }
            }
            val certificate = entry.certificates
                ?.firstOrNull()
                ?.let { it as? X509Certificate }
                ?: return@runCatching null
            val digest = sha256Hex(certificate.encoded.inputStream())
            if (digest != OfficialPojavLauncherCertSha256) return@runCatching digest
        }
        OfficialPojavLauncherCertSha256
    }
}.getOrNull()

private fun JarFile.asZipFile(): java.util.zip.ZipFile = this

private fun copyUriToTempFile(
    context: Context,
    uri: Uri,
    suffix: String,
): Path {
    val tempFile = Files.createTempFile(context.cacheDir.toPath(), "mcgo-java-runtime-", suffix)
    try {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw JavaRuntimeInstallException("无法读取选择的 JRE 文件")
            Files.copy(input, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return tempFile
    } catch (error: Exception) {
        Files.deleteIfExists(tempFile)
        if (error is JavaRuntimeInstallException) throw error
        throw JavaRuntimeInstallException("复制 JRE 文件失败", error)
    }
}

internal fun Uri.displayName(context: Context): String {
    context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex).orEmpty()
        }
    }
    return lastPathSegment.orEmpty()
}

internal fun Throwable.userFacingInstallMessage(majorVersion: Int): String {
    val baseMessage = message ?: "安装失败"
    return if (this is JavaRuntimeInstallException) {
        "Java $majorVersion 安装失败：$baseMessage"
    } else {
        "Java $majorVersion 安装失败：${baseMessage.take(80)}"
    }
}
