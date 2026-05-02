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
const val OfficialPojavLauncherCertSha256 = "d0d0886a0d7e3486e6627f9b8011027fe3c6b0fb09424530b6d7be14f8c2cc33"

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
    signerCertSha256: String? = null,
) {
    val normalizedSha = sha256.lowercase()
    when (archiveKind) {
        JavaRuntimeArchiveKind.PojavApk -> {
            when (source) {
                JavaRuntimeArchiveSource.OfficialDownload -> {
                    if (normalizedSha != OfficialPojavLauncherApkSha256) {
                        throw JavaRuntimeInstallException(
                            "JRE 安装包可信校验失败：$displayName 的 SHA-256 不匹配官方 Pojav 发行版",
                        )
                    }
                }
                JavaRuntimeArchiveSource.UserImport -> {
                    val normalizedSigner = signerCertSha256?.lowercase()
                    if (normalizedSigner != OfficialPojavLauncherCertSha256) {
                        throw JavaRuntimeInstallException(
                            "JRE 安装包可信校验失败：$displayName 的签名证书与官方 Pojav APK 不匹配",
                        )
                    }
                }
            }
        }
        JavaRuntimeArchiveKind.TarXz -> {
            when (source) {
                JavaRuntimeArchiveSource.OfficialDownload -> {
                    val expected = TrustedJavaRuntimeTarballMetadata.values
                        .firstOrNull { it.displayName == displayName }
                        ?.sha256
                    if (expected == null || normalizedSha != expected) {
                        throw JavaRuntimeInstallException(
                            "JRE 安装包可信校验失败：$displayName 的 SHA-256 不匹配受信任的 Android 运行时清单",
                        )
                    }
                }
                JavaRuntimeArchiveSource.UserImport -> {
                    throw JavaRuntimeInstallException(
                        "当前仅允许导入受信任官方源下载的 Android 运行时；本地 tar.xz/txz 直导入仍未开放",
                    )
                }
            }
        }
    }
}
