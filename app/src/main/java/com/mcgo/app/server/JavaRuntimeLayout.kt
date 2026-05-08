package com.mcgo.app.server

import com.mcgo.app.ui.model.ServerCardState
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class ManagedJavaRuntimeLayout(
    val javaHome: Path,
    val javaBinary: Path,
    val javaLibDir: Path,
    val jliLibDir: Path,
    val jvmLibDir: Path,
    val libjliPath: Path,
    val libjvmPath: Path,
    val bootstrapLibraries: List<Path>,
    val libraryPath: String,
)

data class ManagedPaperLaunchConfig(
    val workingDirectory: Path,
    val jarPath: Path,
    val logFile: Path,
    val arguments: List<String>,
    val environment: List<String>,
    val bootstrapLibraries: List<Path>,
    val libjliPath: Path,
    val launcherFullVersion: String,
    val launcherDotVersion: String,
)

fun managedPaperServerDirectory(filesDir: Path, serverId: String): Path =
    filesDir.resolve("servers").resolve(sanitizeManagedServerId(serverId))

fun managedPaperServerLogFile(filesDir: Path, serverId: String): Path =
    managedPaperServerDirectory(filesDir, serverId).resolve("logs/mcgo-latest.log")

fun managedPaperServerFrpcLogFile(filesDir: Path, serverId: String): Path =
    managedPaperServerDirectory(filesDir, serverId).resolve("logs/frpc.log")

fun buildManagedPaperLaunchConfig(
    server: ServerCardState,
    filesDir: Path,
    cacheDir: Path,
    nativeLibraryDir: String,
    is64BitProcess: Boolean,
): ManagedPaperLaunchConfig {
    val javaHome = requireManagedJavaHome(filesDir, server.javaMajorVersion)
    ensureAndroidLegacyLibCompat(javaHome)
    val runtimeLayout = resolveManagedJavaRuntimeLayout(
        javaHome = javaHome,
        nativeLibraryDir = nativeLibraryDir,
        is64BitProcess = is64BitProcess,
    )
    val preparedFiles = preparePaperServerFiles(server, filesDir.resolve("servers"))
    val logFile = managedPaperServerLogFile(filesDir, server.id)
    Files.createDirectories(logFile.parent)
    Files.write(logFile, byteArrayOf())
    val launcherFullVersion = runtimeReleaseJavaVersion(javaHome)
    val launcherDotVersion = runtimeLauncherDotVersion(launcherFullVersion)
    val arguments = buildList {
        add(runtimeLayout.javaBinary.toString())
        addAll(buildPaperJvmArguments(server, javaHome))
        add("-Djava.awt.headless=true")
        add("-Djava.io.tmpdir=$cacheDir")
        add("-Duser.home=${preparedFiles.workDir}")
        add("-jar")
        add(preparedFiles.jarPath.toString())
        add("nogui")
    }
    val environment = buildList {
        add("JAVA_HOME=$javaHome")
        add("HOME=${preparedFiles.workDir}")
        add("TMPDIR=$cacheDir")
        add("PATH=${runtimeLayout.javaBinary.parent}:${defaultProcessPath()}")
        add("LD_LIBRARY_PATH=${runtimeLayout.libraryPath}")
    }
    return ManagedPaperLaunchConfig(
        workingDirectory = preparedFiles.workDir,
        jarPath = preparedFiles.jarPath,
        logFile = logFile,
        arguments = arguments,
        environment = environment,
        bootstrapLibraries = runtimeLayout.bootstrapLibraries,
        libjliPath = runtimeLayout.libjliPath,
        launcherFullVersion = launcherFullVersion,
        launcherDotVersion = launcherDotVersion,
    )
}

fun resolveManagedJavaRuntimeLayout(
    javaHome: Path,
    nativeLibraryDir: String,
    is64BitProcess: Boolean,
): ManagedJavaRuntimeLayout {
    val releaseProperties = readReleaseProperties(javaHome)
    val javaLibRelative = resolveJavaLibRelative(javaHome, releaseProperties["OS_ARCH"])
    val javaLibDir = javaHome.resolve(javaLibRelative)
    val legacyLibDir = javaHome.resolve("lib")
    val preferredJavaLibDir = if (Files.isRegularFile(legacyLibDir.resolve("server/libjvm.so"))) legacyLibDir else javaLibDir
    val jliLibDir = when {
        Files.exists(preferredJavaLibDir.resolve("jli/libjli.so")) -> preferredJavaLibDir.resolve("jli")
        Files.exists(preferredJavaLibDir.resolve("libjli.so")) -> preferredJavaLibDir
        Files.exists(javaLibDir.resolve("jli/libjli.so")) -> javaLibDir.resolve("jli")
        else -> javaLibDir
    }
    val jvmLibDir = when {
        Files.exists(preferredJavaLibDir.resolve("server/libjvm.so")) -> preferredJavaLibDir.resolve("server")
        Files.exists(preferredJavaLibDir.resolve("client/libjvm.so")) -> preferredJavaLibDir.resolve("client")
        Files.exists(javaLibDir.resolve("server/libjvm.so")) -> javaLibDir.resolve("server")
        else -> javaLibDir.resolve("client")
    }
    val libjliPath = jliLibDir.resolve("libjli.so")
    val libjvmPath = jvmLibDir.resolve("libjvm.so")
    if (!Files.isRegularFile(libjliPath)) {
        throw JavaRuntimeInstallException("托管 JRE 缺少 libjli.so：$libjliPath")
    }
    if (!Files.isRegularFile(libjvmPath)) {
        throw JavaRuntimeInstallException("托管 JRE 缺少 libjvm.so：$libjvmPath")
    }
    val bootstrapLibraries = buildList {
        add(libjliPath)
        add(libjvmPath)
        listOf("libverify.so", "libjava.so", "libnet.so", "libnio.so", "libzip.so", "libjimage.so")
            .map(javaLibDir::resolve)
            .filter { Files.isRegularFile(it) }
            .forEach(::add)
    }.distinct()
    val libraryPath = buildList {
        add(jvmLibDir.toString())
        add(jliLibDir.toString())
        add(javaLibDir.toString())
        add(Paths.get("/system", if (is64BitProcess) "lib64" else "lib").toString())
        add(Paths.get("/vendor", if (is64BitProcess) "lib64" else "lib").toString())
        add(Paths.get("/vendor", if (is64BitProcess) "lib64" else "lib", "hw").toString())
        add(Paths.get("/system_ext", if (is64BitProcess) "lib64" else "lib").toString())
        add(nativeLibraryDir)
    }.distinct().joinToString(":")
    return ManagedJavaRuntimeLayout(
        javaHome = javaHome,
        javaBinary = javaHome.resolve("bin/java"),
        javaLibDir = javaLibDir,
        jliLibDir = jliLibDir,
        jvmLibDir = jvmLibDir,
        libjliPath = libjliPath,
        libjvmPath = libjvmPath,
        bootstrapLibraries = bootstrapLibraries,
        libraryPath = libraryPath,
    )
}

fun sanitizeManagedServerId(serverId: String): String = serverId
    .replace(Regex("[^A-Za-z0-9._-]+"), "-")
    .trim('-', '.')
    .ifBlank { "paper-server" }

private fun defaultProcessPath(): String = System.getenv("PATH")
    ?.takeIf { it.isNotBlank() }
    ?: "/system/bin:/system/xbin"

internal fun readReleaseProperties(javaHome: Path): Map<String, String> {
    val releaseFile = javaHome.resolve("release")
    if (!Files.isRegularFile(releaseFile)) {
        throw JavaRuntimeInstallException("托管 JRE 缺少 release 文件：$releaseFile")
    }
    return Files.readAllLines(releaseFile)
        .mapNotNull { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return@mapNotNull null
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim().trim('"')
            key.takeIf { it.isNotBlank() }?.let { it to value }
        }
        .toMap()
}

private fun runtimeReleaseJavaVersion(javaHome: Path): String =
    readReleaseProperties(javaHome)["JAVA_VERSION"]
        ?.takeIf { it.isNotBlank() }
        ?: throw JavaRuntimeInstallException("托管 JRE 缺少 JAVA_VERSION：$javaHome/release")

private fun runtimeLauncherDotVersion(fullVersion: String): String = if (fullVersion.startsWith("1.8")) {
    "1.8"
} else {
    fullVersion.substringBefore('.')
}

internal fun resolveJavaLibRelative(javaHome: Path, osArch: String?): Path {
    val normalizedCandidates = when (osArch?.trim()?.trim('"')) {
        null, "" -> emptyList()
        "x86" -> listOf("i386", "i486", "i586", "x86")
        else -> osArch.split('/').map(String::trim).filter(String::isNotBlank)
    }
    normalizedCandidates.forEach { candidate ->
        val candidatePath = javaHome.resolve("lib").resolve(candidate)
        if (Files.isDirectory(candidatePath)) {
            return Paths.get("lib", candidate)
        }
    }
    if (Files.isDirectory(javaHome.resolve("lib"))) {
        return Paths.get("lib")
    }
    throw JavaRuntimeInstallException("托管 JRE 缺少 lib 目录：$javaHome")
}
