package com.mcgo.app.server

import android.os.Build
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipFile
import kotlin.io.path.absolutePathString
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

class JavaRuntimeInstallException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

interface TarXzArchive {
    fun openEntry(name: String): InputStream?
}

enum class JavaRuntimeArchiveKind {
    PojavApk,
    TarXz,
}

fun classifyJavaRuntimeArchiveName(name: String): JavaRuntimeArchiveKind {
    val normalizedName = name.lowercase()
    return when {
        normalizedName.endsWith(".apk") -> JavaRuntimeArchiveKind.PojavApk
        normalizedName.endsWith(".tar.xz") || normalizedName.endsWith(".txz") -> JavaRuntimeArchiveKind.TarXz
        else -> throw JavaRuntimeInstallException("当前仅支持官方 Pojav APK 或受信任 Android JRE tar.xz/txz 运行时包")
    }
}

fun javaRuntimeArchiveTempSuffix(name: String): String {
    val normalizedName = name.lowercase()
    return when {
        normalizedName.endsWith(".tar.xz") -> ".tar.xz"
        normalizedName.endsWith(".txz") -> ".txz"
        normalizedName.endsWith(".apk") -> ".apk"
        else -> ".archive"
    }
}

fun abiArchiveName(abi: String): String = when (abi) {
    "arm64-v8a" -> "bin-arm64.tar.xz"
    "armeabi-v7a" -> "bin-arm.tar.xz"
    "x86" -> "bin-x86.tar.xz"
    "x86_64" -> "bin-x86_64.tar.xz"
    else -> throw JavaRuntimeInstallException("不支持的 CPU 架构：$abi")
}

fun scanInstalledJavaVersions(filesDir: Path): Set<Int> {
    val jreRoot = filesDir.resolve(JreDirectoryName)
    if (!Files.isDirectory(jreRoot)) return emptySet()
    return Files.list(jreRoot).use { stream ->
        stream.iterator().asSequence()
            .filter { Files.isDirectory(it) }
            .map { it.fileName.toString() }
            .filter { it.startsWith(JavaHomePrefix) }
            .mapNotNull { name -> name.removePrefix(JavaHomePrefix).toIntOrNull() }
            .filter { isRuntimeReady(filesDir, it) }
            .toSet()
    }
}

fun deleteJavaRuntime(filesDir: Path, majorVersion: Int) {
    val javaHome = managedJavaHome(filesDir, majorVersion)
    if (!javaHome.startsWith(filesDir.resolve(JreDirectoryName))) {
        throw JavaRuntimeInstallException("拒绝删除非托管 JRE 目录")
    }
    deleteRecursively(javaHome)
}

fun installPojavRuntimeFromApk(
    apkPath: Path,
    filesDir: Path,
    majorVersion: Int,
    androidAbi: String = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
): Path = installRuntimeWithStaging(filesDir = filesDir, majorVersion = majorVersion) { tempDir ->
    val abiArchive = abiArchiveName(androidAbi)
    ZipFile(apkPath.toFile()).use { zip ->
        val component = resolvePojavRuntimeComponent(zip, majorVersion)
        fun extractComponentArchive(archiveName: String) {
            val entryName = "assets/components/$component/$archiveName"
            val entry = zip.getEntry(entryName)
                ?: throw JavaRuntimeInstallException("安装包缺少 $entryName")
            zip.getInputStream(entry).use { input -> extractTarXzSafely(input, tempDir) }
        }
        extractComponentArchive("universal.tar.xz")
        extractComponentArchive(abiArchive)
    }
}

fun installRuntimeFromTarXz(
    archivePath: Path,
    filesDir: Path,
    majorVersion: Int,
): Path = installRuntimeWithStaging(filesDir = filesDir, majorVersion = majorVersion) { tempDir ->
    Files.newInputStream(archivePath).use { input -> extractTarXzSafely(input, tempDir) }
}

fun installRuntimeWithStaging(
    filesDir: Path,
    majorVersion: Int,
    extractInto: (Path) -> Unit,
): Path {
    val javaHome = managedJavaHome(filesDir, majorVersion)
    val stagingRoot = filesDir.resolve(JreDirectoryName)
    Files.createDirectories(stagingRoot)
    val tempDir = Files.createTempDirectory(stagingRoot, "java-$majorVersion-install-")

    try {
        extractInto(tempDir)
        val javaBinary = tempDir.resolve("bin/java")
        if (!Files.exists(javaBinary)) {
            throw JavaRuntimeInstallException("JRE $majorVersion 缺少 bin/java")
        }
        javaBinary.toFile().setExecutable(true, false)
        ensureAndroidLegacyLibCompat(tempDir)
        deleteRecursively(javaHome)
        moveInstalledRuntimeIntoPlace(tempDir, javaHome)
        return javaHome
    } catch (error: JavaRuntimeInstallException) {
        deleteRecursively(tempDir)
        throw error
    } catch (error: Exception) {
        deleteRecursively(tempDir)
        throw JavaRuntimeInstallException("安装 JRE $majorVersion 失败", error)
    }
}

fun moveInstalledRuntimeIntoPlace(
    source: Path,
    destination: Path,
    mover: (Path, Path, Array<out CopyOption>) -> Path = { from, to, options -> Files.move(from, to, *options) },
) {
    try {
        mover(source, destination, arrayOf(StandardCopyOption.ATOMIC_MOVE))
    } catch (_: AtomicMoveNotSupportedException) {
        mover(source, destination, emptyArray())
    }
}

fun extractTarXzSafely(input: InputStream, targetDir: Path) {
    val normalizedTarget = targetDir.toAbsolutePath().normalize()
    Files.createDirectories(normalizedTarget)
    XZCompressorInputStream(input).use { xz ->
        TarArchiveInputStream(xz).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                extractTarEntrySafely(tar, entry, normalizedTarget)
                entry = tar.nextTarEntry
            }
        }
    }
}

fun managedJavaHome(filesDir: Path, majorVersion: Int): Path =
    filesDir.resolve(JreDirectoryName).resolve("$JavaHomePrefix$majorVersion")

fun runtimeJavaBinary(filesDir: Path, majorVersion: Int): Path =
    managedJavaHome(filesDir, majorVersion).resolve("bin/java")

fun isRuntimeReady(filesDir: Path, majorVersion: Int): Boolean =
    Files.isRegularFile(runtimeJavaBinary(filesDir, majorVersion)) &&
        Files.isExecutable(runtimeJavaBinary(filesDir, majorVersion))

private fun extractTarEntrySafely(
    tar: TarArchiveInputStream,
    entry: TarArchiveEntry,
    targetDir: Path,
) {
    val entryName = sanitizeEntryName(entry.name)
    if (entryName.isEmpty()) return
    val destination = resolveInsideTarget(targetDir, entryName)

    when {
        entry.isDirectory -> Files.createDirectories(destination)
        entry.isSymbolicLink -> createSafeSymlink(targetDir, destination, entry.linkName)
        entry.isFile -> {
            Files.createDirectories(destination.parent)
            Files.copy(tar, destination, StandardCopyOption.REPLACE_EXISTING)
            applyExecutableBit(destination, entry.mode)
        }
        else -> Unit
    }
}

private fun sanitizeEntryName(rawName: String): String {
    val unixName = rawName.replace('\\', '/')
    if (unixName.startsWith('/')) {
        throw JavaRuntimeInstallException("压缩包包含绝对路径：$rawName")
    }
    val trimmed = unixName
        .removePrefix("./")
        .trimEnd('/')
    if (trimmed.isBlank()) return ""
    if (trimmed.split('/').any { it == ".." || it.isBlank() }) {
        throw JavaRuntimeInstallException("压缩包包含不安全路径：$rawName")
    }
    return trimmed
}

private fun resolveInsideTarget(targetDir: Path, entryName: String): Path {
    val destination = targetDir.resolve(entryName).normalize()
    if (!destination.startsWith(targetDir)) {
        throw JavaRuntimeInstallException("压缩包路径越界：$entryName")
    }
    return destination
}

private fun createSafeSymlink(targetDir: Path, destination: Path, rawLinkName: String) {
    val linkName = rawLinkName.replace('\\', '/')
    if (linkName.isBlank()) {
        throw JavaRuntimeInstallException("压缩包包含空软链接：${destination.fileName}")
    }
    if (linkName.startsWith('/')) {
        throw JavaRuntimeInstallException("压缩包包含绝对软链接：$linkName")
    }
    val linkTarget = destination.parent.resolve(linkName).normalize()
    if (!linkTarget.startsWith(targetDir)) {
        throw JavaRuntimeInstallException("压缩包软链接越界：$linkName")
    }
    Files.createDirectories(destination.parent)
    try {
        Files.deleteIfExists(destination)
        Files.createSymbolicLink(destination, Paths.get(rawLinkName))
    } catch (error: UnsupportedOperationException) {
        throw JavaRuntimeInstallException("当前文件系统不支持软链接", error)
    } catch (error: FileAlreadyExistsException) {
        throw JavaRuntimeInstallException("软链接目标已存在：${destination.absolutePathString()}", error)
    } catch (error: IOException) {
        throw JavaRuntimeInstallException("创建软链接失败：${destination.absolutePathString()}", error)
    }
}

private fun applyExecutableBit(path: Path, mode: Int) {
    if (mode and 0b001_001_001 == 0) return
    path.toFile().setExecutable(true, false)
    runCatching {
        val permissions = Files.getPosixFilePermissions(path).toMutableSet()
        permissions += PosixFilePermission.OWNER_EXECUTE
        if (mode and 0b000_001_000 != 0) permissions += PosixFilePermission.GROUP_EXECUTE
        if (mode and 0b000_000_001 != 0) permissions += PosixFilePermission.OTHERS_EXECUTE
        Files.setPosixFilePermissions(path, permissions)
    }
}

private fun ensureAndroidLegacyLibCompat(javaHome: Path) {
    val releaseProperties = runCatching { readReleaseProperties(javaHome) }.getOrDefault(emptyMap())
    val javaLibRelative = runCatching { resolveJavaLibRelative(javaHome, releaseProperties["OS_ARCH"]) }.getOrNull() ?: return
    val javaLibDir = javaHome.resolve(javaLibRelative)
    val legacyLibDir = javaHome.resolve("lib")
    if (javaLibDir == legacyLibDir) return

    val compatLinks = listOf(
        "server/libjvm.so" to javaLibDir.resolve("server/libjvm.so"),
        "jli/libjli.so" to javaLibDir.resolve("jli/libjli.so"),
        "libjli.so" to javaLibDir.resolve("libjli.so"),
        "libjava.so" to javaLibDir.resolve("libjava.so"),
        "libverify.so" to javaLibDir.resolve("libverify.so"),
        "libnet.so" to javaLibDir.resolve("libnet.so"),
        "libnio.so" to javaLibDir.resolve("libnio.so"),
    )
    compatLinks.forEach { (relativePath, source) ->
        if (!Files.exists(source)) return@forEach
        val target = legacyLibDir.resolve(relativePath)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return@forEach
        Files.createDirectories(target.parent)
        runCatching {
            Files.createSymbolicLink(target, target.parent.relativize(source))
        }.getOrElse {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            if (Files.isExecutable(source)) {
                target.toFile().setExecutable(true, false)
            }
        }
    }
}

private fun pojavComponentName(majorVersion: Int): String = when (majorVersion) {
    8 -> "jre"
    11 -> "jre-11"
    17 -> "jre-new"
    21 -> "jre-21"
    25 -> throw JavaRuntimeInstallException("Java 25 暂未在 Pojav 安装包中提供组件，请使用 MC-GO 提供的 ARM64 归档运行时")
    else -> throw JavaRuntimeInstallException("Java $majorVersion 暂未在 Pojav 安装包中找到可用组件，请导入匹配的 Android JRE 包")
}

fun resolvePojavRuntimeComponent(zip: ZipFile, majorVersion: Int): String {
    val preferredComponent = runCatching { pojavComponentName(majorVersion) }.getOrNull()
    val availableComponents = zip.entries().asSequence()
        .map { it.name }
        .filter { it.startsWith("assets/components/") }
        .mapNotNull { entry -> entry.split('/').getOrNull(2) }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
    preferredComponent
        ?.takeIf { it in availableComponents }
        ?.takeIf { detectPojavComponentMajorVersion(zip, it) == majorVersion }
        ?.let { return it }
    val detectedComponent = availableComponents.firstOrNull { component ->
        detectPojavComponentMajorVersion(zip, component) == majorVersion
    }
    return detectedComponent
        ?: throw JavaRuntimeInstallException("Java $majorVersion 暂未在所选 Pojav APK 中找到可用组件，请导入匹配的 Android JRE 包")
}

private fun detectPojavComponentMajorVersion(zip: ZipFile, component: String): Int? {
    val candidateArchives = listOf(
        "bin-arm64.tar.xz",
        "bin-arm.tar.xz",
        "bin-x86.tar.xz",
        "bin-x86_64.tar.xz",
        "universal.tar.xz",
    )
    candidateArchives.forEach { archiveName ->
        val releaseEntry = zip.getEntry("assets/components/$component/$archiveName") ?: return@forEach
        val detectedVersion = zip.getInputStream(releaseEntry).use { input ->
            XZCompressorInputStream(input).use { xz ->
                TarArchiveInputStream(xz).use { tar ->
                    var entry = tar.nextTarEntry
                    while (entry != null) {
                        val normalized = entry.name.replace('\\', '/').removePrefix("./").trimEnd('/')
                        if (!entry.isDirectory && normalized == "release") {
                            val releaseText = tar.readBytes().toString(Charsets.UTF_8)
                            return@use parseJavaMajorVersionFromReleaseText(releaseText)
                        }
                        entry = tar.nextTarEntry
                    }
                    null
                }
            }
        }
        if (detectedVersion != null) return detectedVersion
    }
    return null
}

private fun parseJavaMajorVersionFromReleaseText(releaseText: String): Int? {
    val version = releaseText.lineSequence()
        .firstOrNull { it.trimStart().startsWith("JAVA_VERSION=") }
        ?.substringAfter('=')
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return if (version.startsWith("1.8")) 8 else version.substringBefore('.').toIntOrNull()
}

fun trustedRuntimeArchiveNameForAbi(abi: String): String = abiArchiveName(abi)

fun trustedRuntimeArchivesForVersion(majorVersion: Int, abi: String): List<TrustedJavaRuntimeTarball> {
    if (majorVersion == 25) {
        if (abi != "arm64-v8a") {
            throw JavaRuntimeInstallException("Java 25 当前仅提供 ARM64 受信任运行时归档，请在 arm64-v8a 设备上使用")
        }
        return listOf(
            trustedJavaRuntimeTarball(25, "bin-arm64.tar.xz")
                ?: throw JavaRuntimeInstallException("Java 25 缺少受信任运行时清单：bin-arm64.tar.xz")
        )
    }
    val archiveNames = listOf("universal.tar.xz", trustedRuntimeArchiveNameForAbi(abi))
    return archiveNames.map { archiveName ->
        trustedJavaRuntimeTarball(majorVersion, archiveName)
            ?: throw JavaRuntimeInstallException("Java $majorVersion 缺少受信任运行时清单：$archiveName")
    }
}

private fun deleteRecursively(path: Path) {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
    Files.walk(path).use { stream ->
        stream
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }
}

private const val JreDirectoryName = "jre"
private const val JavaHomePrefix = "java-"
