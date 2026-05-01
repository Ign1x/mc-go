package com.mcgo.app.server

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest

enum class JavaRuntimeArchiveSource {
    OfficialDownload,
    UserImport,
}

const val OfficialPojavLauncherApkSha256 = "cc8479e1600e3a094d2184bbb88b19809ce41a0f8f7882aefd4527c9d032fc56"

fun sha256Hex(path: Path): String = Files.newInputStream(path).use(::sha256Hex)

fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    DigestInputStream(input, digest).use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (stream.read(buffer) >= 0) {
            // consume
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun validateRuntimeArchiveTrust(
    archiveKind: JavaRuntimeArchiveKind,
    source: JavaRuntimeArchiveSource,
    sha256: String,
    displayName: String,
) {
    val normalizedSha = sha256.lowercase()
    when (archiveKind) {
        JavaRuntimeArchiveKind.PojavApk -> {
            if (normalizedSha != OfficialPojavLauncherApkSha256) {
                throw JavaRuntimeInstallException(
                    "JRE 安装包可信校验失败：$displayName 的 SHA-256 不匹配官方 Pojav 发行版",
                )
            }
        }
        JavaRuntimeArchiveKind.TarXz -> {
            throw JavaRuntimeInstallException(
                when (source) {
                    JavaRuntimeArchiveSource.OfficialDownload -> "当前没有可校验的官方 tar.xz 运行时清单，已阻止安装"
                    JavaRuntimeArchiveSource.UserImport -> "当前仅允许通过可信校验的官方 Pojav APK 导入运行时；tar.xz/txz 直导入已暂时禁用"
                },
            )
        }
    }
}
