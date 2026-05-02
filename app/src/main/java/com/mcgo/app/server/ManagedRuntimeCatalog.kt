package com.mcgo.app.server

data class TrustedJavaRuntimeTarball(
    val displayName: String,
    val url: String,
    val sha256: String,
)

private const val TrustedJavaRuntimeRepoCommit = "663faf38121ba7be7dd82567fc1595a6b9b60179"
private const val TrustedJavaRuntimeBaseUrl =
    "https://raw.githubusercontent.com/aaaapai/android-openjdk-autobuild/$TrustedJavaRuntimeRepoCommit/LatestJre"
private const val DirectRuntimeReleaseTag = "20260223"
private const val DirectRuntimeReleaseBaseUrl =
    "https://github.com/aaaapai/android-openjdk-build/releases/download/$DirectRuntimeReleaseTag"

fun javaRuntimeTarballKey(majorVersion: Int, archiveName: String): String = "jre-$majorVersion/$archiveName"

fun trustedJavaRuntimeTarball(majorVersion: Int, archiveName: String): TrustedJavaRuntimeTarball? =
    TrustedJavaRuntimeTarballMetadata[javaRuntimeTarballKey(majorVersion, archiveName)]

val TrustedJavaRuntimeTarballMetadata: Map<String, TrustedJavaRuntimeTarball> = buildMap {
    fun register(majorVersion: Int, archiveName: String, sha256: String) {
        val displayName = javaRuntimeTarballKey(majorVersion, archiveName)
        put(
            displayName,
            TrustedJavaRuntimeTarball(
                displayName = displayName,
                url = "$TrustedJavaRuntimeBaseUrl/jre-$majorVersion/$archiveName",
                sha256 = sha256,
            ),
        )
    }

    fun registerDirect(majorVersion: Int, archiveName: String, fileName: String, sha256: String) {
        val displayName = javaRuntimeTarballKey(majorVersion, archiveName)
        put(
            displayName,
            TrustedJavaRuntimeTarball(
                displayName = displayName,
                url = "$DirectRuntimeReleaseBaseUrl/$fileName",
                sha256 = sha256,
            ),
        )
    }

    register(8, "universal.tar.xz", "50411aa5eea5a8719de722c8fb214e6f81d9414a8aa4f0a6229be954e2e4d0e7")
    register(8, "bin-arm64.tar.xz", "589c5f395aa4683d709ea0e28a77bad15b4875b0490827dbc9fd94d7a539025d")
    register(8, "bin-arm.tar.xz", "a614899e2c556e32841a4b81505f2a1243191dad3a04b0c4e9a547b882afe9be")
    register(8, "bin-x86.tar.xz", "a6aac49a246c455f8bccffbb73baa2d6fb9e3887ec7a16ff73bb59d90eba32c6")
    register(8, "bin-x86_64.tar.xz", "9208bab3dd1fa9554bc298edb28e6dba2b1223297094b3f7c7dbe84ad4f0a33e")

    register(11, "universal.tar.xz", "5b6e843e2ddd02ec7dd40b6f604bf2ca9104ccf8a0cc19d3d5a3126e1fba8907")
    register(11, "bin-arm64.tar.xz", "ca06ce207e810877a3fee64c88afded0a6386bb6f8af9d7f5ae9de4c4d5dbaca")
    register(11, "bin-arm.tar.xz", "d1fea7b8bd9bf6037dcef209f335b01d913d71b6b69c7323931209e2ff27c831")
    register(11, "bin-x86.tar.xz", "3f66a28d2ca505b304006b2b033ded1ded5fed880cece60e8570c41e020e13e0")
    register(11, "bin-x86_64.tar.xz", "fc02ae1317c4435591d989108dadb3b68fb4a08d37af933d18839bfa355a9387")

    register(17, "universal.tar.xz", "dc12be432983cf85e5d5120382975e847f033fe5185a86ed5cc6223c27578642")
    register(17, "bin-arm64.tar.xz", "cf611eea8d76e82209a1bc259b793885784023ee1ce872a1c7603f33fc144db2")
    register(17, "bin-arm.tar.xz", "df2e772b07a8dcee375bec4f0717e609f74380d3de268e34d01246aac5a559b8")
    register(17, "bin-x86.tar.xz", "92d5dc08bf112274d0f754b59b118a24f1e319905671ed5b7fac56e29ea502e6")
    register(17, "bin-x86_64.tar.xz", "2550104936c5fe1614de01f005ce41303293e5f6cd95700d9ef8306ac33869dc")

    register(21, "universal.tar.xz", "6f16687b7937ce8af1ff5e884933af4b6d111a33498e57cf4b380cffd8e8de7c")
    register(21, "bin-arm64.tar.xz", "e974bce2c5be78f0848470e4e234b7f10492d8cccaa6afc446d258c46ffc2df5")
    register(21, "bin-arm.tar.xz", "46b9e0a84e7d5d1692a5a5896fe9f3a40010fbe9806c9cd06daabad6d370a246")
    register(21, "bin-x86.tar.xz", "6c0c076cedccdaa5ffd5390206ccd4cedf6b87534bc7be3a0fce1eb7d42a334d")
    register(21, "bin-x86_64.tar.xz", "d18bb65ff99fa73042d2bec04673eeba79620cdee8d81703f8422de331e84950")

    registerDirect(
        25,
        archiveName = "bin-arm64.tar.xz",
        fileName = "jre25-arm64-20260223-release.tar.xz",
        sha256 = "0fdf6d19fe66ea61c12caa24bd655227ddb0d7d9c16c0f13281a7c2878635286",
    )
}

data class LatestPaperDownloadsPageArtifact(
    val version: String,
    val build: Int,
    val downloadName: String,
    val sha256: String,
    val downloadUrl: String,
)

fun parseLatestPaperDownloadsPageArtifact(html: String): LatestPaperDownloadsPageArtifact? {
    val latestVersion = Regex("""latestStableVersion(?:&quot;|\"):\[0,(?:&quot;|\")([^\"&]+)(?:&quot;|\")]""")
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val build = Regex("""stableBuildsResult.*?latest(?:&quot;|\"):\[0,\{.*?id(?:&quot;|\"):\[0,(\d+)]""", RegexOption.DOT_MATCHES_ALL)
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: return null
    val downloadName = Regex("""server:default(?:&quot;|\"):\[0,\{.*?name(?:&quot;|\"):\[0,(?:&quot;|\")([^\"&]+)(?:&quot;|\")]""", RegexOption.DOT_MATCHES_ALL)
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val downloadUrl = Regex("""server:default(?:&quot;|\"):\[0,\{.*?url(?:&quot;|\"):\[0,(?:&quot;|\")([^\"&]+)(?:&quot;|\")]""", RegexOption.DOT_MATCHES_ALL)
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val sha256 = Regex("""checksums(?:&quot;|\"):\[0,\{.*?sha256(?:&quot;|\"):\[0,(?:&quot;|\")([A-Fa-f0-9]{64})(?:&quot;|\")]""", RegexOption.DOT_MATCHES_ALL)
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase()
        ?: return null
    return LatestPaperDownloadsPageArtifact(
        version = latestVersion,
        build = build,
        downloadName = downloadName,
        sha256 = sha256,
        downloadUrl = downloadUrl,
    )
}
