package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.BuildConfig
import com.mcgo.app.McGoUserAgent
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.model.createFabricServer
import com.mcgo.app.ui.model.createForgeServer
import com.mcgo.app.ui.model.createNeoForgeServer
import com.mcgo.app.ui.model.createPaperServer
import com.mcgo.app.ui.model.createPurpurServer
import com.mcgo.app.ui.model.createQuiltServer
import com.mcgo.app.ui.model.createVanillaServer
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PaperServerRuntimeTest {

    @Test
    fun buildPaperDownloadUrl_usesLatestBuildNameAndSha256FromApi() {
        val build = parseLatestPaperBuild(
            """
                {"project_id":"paper","project_name":"Paper","version":"1.21.4","builds":[1,2,227]}
            """.trimIndent(),
        )
        val sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val download = parsePaperDownloadName(
            """
                {"downloads":{"application":{"name":"paper-1.21.4-227.jar","sha256":"$sha256"}}}
            """.trimIndent(),
        )
        val parsedSha256 = parsePaperDownloadSha256(
            """
                {"downloads":{"application":{"name":"paper-1.21.4-227.jar","sha256":"$sha256"}}}
            """.trimIndent(),
        )

        assertThat(build).isEqualTo(227)
        assertThat(download).isEqualTo("paper-1.21.4-227.jar")
        assertThat(parsedSha256).isEqualTo(sha256)
        val url = "https://api.papermc.io/v2/projects/paper/versions/1.21.4/builds/227/downloads/paper-1.21.4-227.jar"
        assertThat(buildPaperDownloadUrl("1.21.4", build, download)).isEqualTo(url)
        val artifact = PaperDownloadArtifact("1.21.4", build, download, sha256, url)
        assertThat(artifact.sha256).isEqualTo(sha256)
        assertThat(artifact.downloadUrl).isEqualTo(url)
    }

    @Test
    fun serverPropertiesHelpers_liveInDedicatedHelperFile() {
        val runtimeSource = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerRuntime.kt")))
        val propertiesSource = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/ManagedServerProperties.kt")))

        assertThat(runtimeSource).contains("Files.write(propertiesPath, buildServerProperties(server).toByteArray())")
        assertThat(runtimeSource).doesNotContain("fun buildManagedServerProperties(")
        assertThat(runtimeSource).doesNotContain("private fun mergeManagedServerProperties(")
        assertThat(runtimeSource).doesNotContain("private val RuntimeOwnedServerPropertyKeys")
        assertThat(propertiesSource).contains("fun buildPaperEula(): String")
        assertThat(propertiesSource).contains("fun buildServerProperties(server: ServerCardState): String")
        assertThat(propertiesSource).contains("fun buildManagedServerProperties(server: ServerCardState): String")
        assertThat(propertiesSource).contains("server-port")
        assertThat(propertiesSource).contains("RuntimeOwnedServerPropertyKeys")
    }

    @Test
    fun preparePaperServerFiles_writesEulaAndServerProperties() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime")
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25566)
            .copy(
                worldName = "world_nether",
                onlineMode = false,
                pvpEnabled = false,
                gameMode = PaperGameMode.Creative,
                difficulty = PaperDifficulty.Hard,
            )

        val prepared = preparePaperServerFiles(server, workDir)

        assertThat(String(Files.readAllBytes(prepared.eulaPath))).contains("eula=true")
        val properties = String(Files.readAllBytes(prepared.serverPropertiesPath))
        assertThat(properties).contains("server-port=25566")
        assertThat(properties).contains("max-players=20")
        assertThat(properties).contains("level-name=world_nether")
        assertThat(properties).contains("gamemode=creative")
        assertThat(properties).contains("difficulty=hard")
        assertThat(properties).contains("online-mode=false")
        assertThat(properties).contains("pvp=false")
        assertThat(prepared.jarPath.fileName.toString()).isEqualTo("paper-1.21.4.jar")
    }

    @Test
    fun preparePaperServerFiles_writesSparkConfigThatDisablesBackgroundNativeProfiler() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime-spark")
        val server = createPaperServer("性能服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25566)

        val prepared = preparePaperServerFiles(server, workDir)
        val sparkConfigPath = prepared.workDir.resolve("plugins/spark/config.json")

        assertThat(Files.isRegularFile(sparkConfigPath)).isTrue()
        val sparkConfig = String(Files.readAllBytes(sparkConfigPath))
        assertThat(sparkConfig).contains("\"backgroundProfiler\": false")
        assertThat(sparkConfig).contains("\"backgroundProfilerEngine\": \"java\"")
    }

    @Test
    fun preparePaperServerFiles_preservesExistingSparkSettingsWhileForcingAndroidSafeProfilerMode() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime-spark-merge")
        val server = createPaperServer("性能服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25566)
        val serverDir = workDir.resolve(server.id)
        val sparkConfigPath = serverDir.resolve("plugins/spark/config.json")
        Files.createDirectories(sparkConfigPath.parent)
        Files.write(
            sparkConfigPath,
            """
            {
              "backgroundProfiler": true,
              "backgroundProfilerEngine": "async",
              "extraMetric": true
            }
            """.trimIndent().toByteArray(),
        )

        val prepared = preparePaperServerFiles(server, workDir)
        val sparkConfig = String(Files.readAllBytes(prepared.workDir.resolve("plugins/spark/config.json")))

        assertThat(sparkConfig).contains("\"backgroundProfiler\": false")
        assertThat(sparkConfig).contains("\"backgroundProfilerEngine\": \"java\"")
        assertThat(sparkConfig).contains("\"extraMetric\": true")
    }

    @Test
    fun preparePaperServerFiles_onlyOverridesTopLevelSparkProfilerKeys_notNestedLookalikes() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime-spark-nested")
        val server = createPaperServer("性能服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25566)
        val serverDir = workDir.resolve(server.id)
        val sparkConfigPath = serverDir.resolve("plugins/spark/config.json")
        Files.createDirectories(sparkConfigPath.parent)
        Files.write(
            sparkConfigPath,
            """
            {
              "nested": {
                "backgroundProfiler": true,
                "backgroundProfilerEngine": "async"
              },
              "backgroundProfiler": true,
              "backgroundProfilerEngine": "async",
              "extraMetric": true
            }
            """.trimIndent().toByteArray(),
        )

        val prepared = preparePaperServerFiles(server, workDir)
        val sparkConfig = String(Files.readAllBytes(prepared.workDir.resolve("plugins/spark/config.json")))

        assertThat(sparkConfig).contains("\"nested\": {")
        val nestedProfilerTrueIndex = sparkConfig.indexOf("\"backgroundProfiler\": true")
        val nestedProfilerEngineAsyncIndex = sparkConfig.indexOf("\"backgroundProfilerEngine\": \"async\"")
        val topLevelProfilerFalseIndex = sparkConfig.lastIndexOf("\"backgroundProfiler\": false")
        val topLevelProfilerEngineJavaIndex = sparkConfig.lastIndexOf("\"backgroundProfilerEngine\": \"java\"")
        assertThat(nestedProfilerTrueIndex).isAtLeast(0)
        assertThat(nestedProfilerEngineAsyncIndex).isAtLeast(0)
        assertThat(topLevelProfilerFalseIndex).isAtLeast(0)
        assertThat(topLevelProfilerEngineJavaIndex).isAtLeast(0)
        assertThat(nestedProfilerTrueIndex).isLessThan(topLevelProfilerFalseIndex)
        assertThat(nestedProfilerEngineAsyncIndex).isLessThan(topLevelProfilerEngineJavaIndex)
        assertThat(sparkConfig).contains("\"extraMetric\": true")
    }

    @Test
    fun preparePaperServerFiles_doesNotWriteSparkConfigForVanillaServer() {
        val workDir = Files.createTempDirectory("mcgo-vanilla-runtime-spark")
        val server = createVanillaServer("原版服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25566)

        val prepared = preparePaperServerFiles(server, workDir)

        assertThat(Files.exists(prepared.workDir.resolve("plugins/spark/config.json"))).isFalse()
    }

    @Test
    fun androidSparkConfigHelpers_liveInDedicatedHelperFile() {
        val runtimeSource = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerRuntime.kt")))
        val sparkSource = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/ManagedServerSparkConfig.kt")))

        assertThat(runtimeSource).contains("prepareAndroidCompatibleSparkConfig(workDir, server)")
        assertThat(runtimeSource).doesNotContain("private fun prepareAndroidCompatibleSparkConfig(")
        assertThat(runtimeSource).doesNotContain("private fun mergeAndroidCompatibleSparkConfig(")
        assertThat(runtimeSource).doesNotContain("private fun upsertTopLevelJsonScalarProperty(")
        assertThat(runtimeSource).doesNotContain("private fun findTopLevelJsonValueRange(")
        assertThat(sparkSource).contains("internal fun prepareAndroidCompatibleSparkConfig(")
        assertThat(sparkSource).contains("backgroundProfiler")
        assertThat(sparkSource).contains("backgroundProfilerEngine")
        assertThat(sparkSource).contains("findTopLevelJsonValueRange(")
        assertThat(sparkSource).contains("findJsonStringEnd(")
    }

    @Test
    fun preparePaperServerFiles_usesVanillaJarNameForVanillaServerType() {
        val workDir = Files.createTempDirectory("mcgo-vanilla-runtime")
        val server = createVanillaServer("原版服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25566)

        val prepared = preparePaperServerFiles(server, workDir)

        assertThat(prepared.jarPath.fileName.toString()).isEqualTo("vanilla-1.21.4.jar")
        assertThat(String(Files.readAllBytes(prepared.serverPropertiesPath))).contains("server-port=25566")
    }

    @Test
    fun preparePaperServerFiles_usesPurpurJarNameForPurpurServerType() {
        val workDir = Files.createTempDirectory("mcgo-purpur-runtime")
        val server = createPurpurServer("Purpur服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25567)

        val prepared = preparePaperServerFiles(server, workDir)

        assertThat(prepared.jarPath.fileName.toString()).isEqualTo("purpur-1.21.4.jar")
        assertThat(String(Files.readAllBytes(prepared.serverPropertiesPath))).contains("server-port=25567")
    }

    @Test
    fun preparePaperServerFiles_usesFabricForgeNeoForgeAndQuiltJarNamesAndModInstallerTargetsModsDirectory() {
        val workDir = Files.createTempDirectory("mcgo-modded-runtime")
        val fabricServer = createFabricServer("Fabric服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25568)
        val forgeServer = createForgeServer("Forge服", "1.21.4", maxPlayers = 20, memoryMb = 3072, port = 25569)
        val neoForgeServer = createNeoForgeServer("NeoForge服", "1.21.4", maxPlayers = 20, memoryMb = 3072, port = 25570)
        val quiltServer = createQuiltServer("Quilt服", "1.21.4", maxPlayers = 20, memoryMb = 3072, port = 25571)
        val fabricPrepared = preparePaperServerFiles(fabricServer, workDir)
        val forgePrepared = preparePaperServerFiles(forgeServer, workDir)
        val neoForgePrepared = preparePaperServerFiles(neoForgeServer, workDir)
        val quiltPrepared = preparePaperServerFiles(quiltServer, workDir)
        val modFile = Files.createTempFile("fabric-api", ".jar")
        Files.write(modFile, "fabric-mod".toByteArray())

        assertThat(fabricPrepared.jarPath.fileName.toString()).isEqualTo("fabric-1.21.4.jar")
        assertThat(forgePrepared.jarPath.fileName.toString()).isEqualTo("forge-1.21.4.jar")
        assertThat(neoForgePrepared.jarPath.fileName.toString()).isEqualTo("neoforge-1.21.4.jar")
        assertThat(quiltPrepared.jarPath.fileName.toString()).isEqualTo("quilt-1.21.4.jar")
        assertThat(String(Files.readAllBytes(fabricPrepared.serverPropertiesPath))).contains("server-port=25568")

        val installedMod = installManagedServerModFile(modFile, fabricPrepared.workDir)
        assertThat(installedMod.parent.fileName.toString()).isEqualTo("mods")
        assertThat(installedMod.fileName.toString()).isEqualTo(modFile.fileName.toString())
        assertThat(String(Files.readAllBytes(installedMod))).isEqualTo("fabric-mod")

        val customNamedTarget = installManagedServerModFile(modFile, forgePrepared.workDir, targetFileName = "mod.jar")
        assertThat(customNamedTarget.fileName.toString()).isEqualTo("mod.jar")
    }

    @Test
    fun installManagedServerModFile_rejectsPathTraversalTargetFileName() {
        val serverWorkDir = Files.createTempDirectory("mcgo-mod-target")
        val modFile = Files.createTempFile("fabric-api", ".jar")
        Files.write(modFile, "fabric-mod".toByteArray())

        assertFailsWith<IllegalArgumentException> {
            installManagedServerModFile(modFile, serverWorkDir, targetFileName = "../escape.jar")
        }
        assertFailsWith<IllegalArgumentException> {
            installManagedServerModFile(modFile, serverWorkDir, targetFileName = "nested/escape.jar")
        }

        assertThat(Files.exists(serverWorkDir.resolve("escape.jar"))).isFalse()
        assertThat(Files.exists(serverWorkDir.resolve("mods/nested/escape.jar"))).isFalse()
    }

    @Test
    fun preparePaperServerFiles_prefersExplicitServerPropertiesOverride() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime-override")
        val overrideText = "motd=Custom MOTD\nonline-mode=false\npvp=false\n"
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25565)
            .copy(serverPropertiesOverride = overrideText)

        val prepared = preparePaperServerFiles(server, workDir)
        val properties = String(Files.readAllBytes(prepared.serverPropertiesPath))

        assertThat(properties).contains("server-port=25565")
        assertThat(properties).contains("motd=Custom MOTD")
        assertThat(properties).contains("online-mode=false")
        assertThat(properties).contains("pvp=false")
    }

    @Test
    fun preparePaperServerFiles_mergesOverrideButKeepsManagedServerPort() {
        val workDir = Files.createTempDirectory("mcgo-paper-runtime-override-merge")
        val overrideText = "server-port=24444\nmotd=Custom MOTD\nonline-mode=false\n"
        val server = createPaperServer("生存服", "1.21.4", maxPlayers = 20, memoryMb = 2048, port = 25566)
            .copy(serverPropertiesOverride = overrideText)

        val prepared = preparePaperServerFiles(server, workDir)
        val properties = String(Files.readAllBytes(prepared.serverPropertiesPath))

        assertThat(properties).contains("server-port=25566")
        assertThat(properties).contains("motd=Custom MOTD")
        assertThat(properties).contains("online-mode=false")
        assertThat(properties).doesNotContain("server-port=24444")
    }

    @Test
    fun importManagedServerModpackArchive_unpacksScriptsAndPreservesServerFiles() {
        val zipFile = Files.createTempFile("mcgo-modpack", ".zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("run.sh"))
            zip.write("#!/bin/sh\necho start\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("mods/example.jar"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val targetDir = Files.createTempDirectory("mcgo-modpack-target")

        importManagedServerModpackArchive(zipFile, targetDir)

        assertThat(Files.isRegularFile(targetDir.resolve("run.sh"))).isTrue()
        assertThat(Files.isRegularFile(targetDir.resolve("mods/example.jar"))).isTrue()
        assertThat(targetDir.resolve("run.sh").toFile().canExecute()).isTrue()
    }

    @Test
    fun copyManagedServerImportStreamToTempFile_reportsProgressAndByteCount() {
        val sourceBytes = ByteArray(10_000) { index -> (index % 251).toByte() }
        val target = Files.createTempFile("mcgo-modpack-copy-progress", ".zip")
        val reported = mutableListOf<Pair<Int, String>>()

        val copiedBytes = copyManagedServerImportStreamToTempFile(
            input = sourceBytes.inputStream(),
            targetFile = target,
            onProgress = { progress, message -> reported += progress to message },
        )

        assertThat(copiedBytes).isEqualTo(sourceBytes.size.toLong())
        assertThat(Files.readAllBytes(target).toList()).containsExactlyElementsIn(sourceBytes.toList()).inOrder()
        assertThat(reported.map { it.first }).containsAtLeast(1, 100).inOrder()
        assertThat(reported.last().first).isEqualTo(100)
        assertThat(reported.joinToString("\n") { it.second }).contains("正在缓存整合包文件")
        assertThat(reported.last().second).contains("10000 bytes")
    }

    @Test
    fun copyManagedServerImportStreamToTempFile_ignoresProgressCallbackFailures() {
        val sourceBytes = "pack-bytes".toByteArray()
        val target = Files.createTempFile("mcgo-modpack-copy-progress-failure", ".zip")

        val copiedBytes = copyManagedServerImportStreamToTempFile(
            input = sourceBytes.inputStream(),
            targetFile = target,
            onProgress = { _, _ -> error("progress sink failed") },
        )

        assertThat(copiedBytes).isEqualTo(sourceBytes.size.toLong())
        assertThat(String(Files.readAllBytes(target))).isEqualTo("pack-bytes")
    }

    @Test
    fun countingInputStream_countsSingleAndBulkReads() {
        val input = CountingInputStream("abcdef".byteInputStream())
        val buffer = ByteArray(3)

        assertThat(input.read()).isEqualTo('a'.code)
        assertThat(input.bytesRead).isEqualTo(1L)
        assertThat(input.read(buffer, 0, buffer.size)).isEqualTo(3)
        assertThat(String(buffer)).isEqualTo("bcd")
        assertThat(input.bytesRead).isEqualTo(4L)
        assertThat(input.read(ByteArray(8), 0, 8)).isEqualTo(2)
        assertThat(input.bytesRead).isEqualTo(6L)
        assertThat(input.read()).isEqualTo(-1)
        assertThat(input.bytesRead).isEqualTo(6L)
    }

    @Test
    fun managedServerArchiveExtractionSummary_reportsArchiveReadProgressAndRate() {
        val message = ManagedServerArchiveExtractionSummary(
            fileCount = 1,
            directoryCount = 0,
            totalBytes = 5L * 1024L * 1024L,
            skippedReservedEntryCount = 0,
            elapsedMillis = 2_000L,
            archiveBytesRead = 512L * 1024L * 1024L,
            archiveTotalBytes = 1L * 1024L * 1024L * 1024L,
        ).toDiagnosticExtractionProgressMessage()

        assertThat(message).contains("正在解压整合包文件")
        assertThat(message).contains("读取=512.0 MB/1.0 GB (50%)")
        assertThat(message).contains("解压=5.0 MB")
        assertThat(message).contains("速率=256.0 MB/s")
        assertThat(formatModpackExtractionRate(10L * 1024L, 2_000L)).isEqualTo("5.0 KB/s")
        assertThat(
            estimateZipFileEntryArchiveBytesRead(
                completedArchiveBytes = 128L,
                entryCompressedSize = 1024L,
                entryUncompressedSize = 1024L * 1024L,
                entryUncompressedBytesCopied = 1024L,
                archiveTotalBytes = 4096L,
            ),
        ).isEqualTo(129L)
        assertThat(
            ManagedServerArchiveExtractionSummary(
                archiveBytesRead = 512L * 1024L * 1024L,
                archiveTotalBytes = 1L * 1024L * 1024L * 1024L,
            ).toImportProgress(start = 6, end = 70),
        ).isEqualTo(38)
    }

    @Test
    fun importManagedServerModpackArchive_usesZipFileRandomAccessForCachedArchive() {
        val runtimeSource = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerRuntime.kt")))
        val unzipSlice = runtimeSource.substringAfter("private fun unzipManagedServerArchive(")
            .substringBefore("internal fun resolveNeoForgeMinecraftVersions(")

        assertThat(unzipSlice).contains("ZipFile(archiveFile.toFile()).use")
        assertThat(unzipSlice).contains("zipFile.getInputStream(entry)")
        assertThat(unzipSlice).contains("BufferedInputStream(input, ManagedServerImportBufferBytes)")
        assertThat(unzipSlice).doesNotContain("ZipInputStream(counting)")
    }

    @Test
    fun importManagedServerModpackArchive_reportsProgressAcrossDirectExtractionForFreshTarget() {
        val zipFile = Files.createTempFile("mcgo-modpack-progress", ".zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("config/"))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("config/settings.txt"))
            zip.write("alpha".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("mods/example.jar"))
            zip.write(byteArrayOf(1, 2, 3, 4))
            zip.closeEntry()
        }
        val targetDir = Files.createTempDirectory("mcgo-modpack-progress-target")
        val reportedProgress = mutableListOf<Int>()
        val reportedMessages = mutableListOf<String>()

        importManagedServerModpackArchive(
            archiveFile = zipFile,
            serverWorkDir = targetDir,
            onProgress = { progress, message ->
                reportedProgress += progress
                reportedMessages += message
            },
        )

        val messageText = reportedMessages.joinToString("\n")
        assertThat(reportedProgress).isNotEmpty()
        assertThat(reportedProgress.first()).isAtLeast(1)
        assertThat(reportedProgress.last()).isEqualTo(100)
        assertThat(reportedProgress).isInOrder()
        assertThat(messageText).contains("正在检查整合包导入目标")
        assertThat(messageText).contains("目标目录为空，直接导入整合包")
        assertThat(messageText).contains("正在解压整合包到目标目录")
        assertThat(messageText).contains("正在解压整合包文件")
        assertThat(messageText).contains("速率=")
        assertThat(messageText.indexOf("正在解压整合包文件")).isLessThan(
            messageText.indexOf("整合包导入摘要"),
        )
        assertThat(messageText).contains("整合包导入摘要")
        assertThat(messageText).contains("files=2")
        assertThat(messageText).contains("directories=1")
        assertThat(messageText).contains("bytes=9")
        assertThat(messageText).doesNotContain("正在复制整合包文件")
    }

    @Test
    fun importManagedServerModpackArchive_ignoresProgressCallbackFailures() {
        val zipFile = Files.createTempFile("mcgo-modpack-progress-failure", ".zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("server.jar"))
            zip.write(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
            zip.closeEntry()
        }
        val targetDir = Files.createTempDirectory("mcgo-modpack-progress-failure-target")

        importManagedServerModpackArchive(
            archiveFile = zipFile,
            serverWorkDir = targetDir,
            onProgress = { _, _ -> error("progress sink failed") },
        )

        assertThat(Files.isRegularFile(targetDir.resolve("server.jar"))).isTrue()
    }

    @Test
    fun importManagedServerModpackArchive_keepsStagingCopyWhenReplacingExistingWorkspace() {
        val zipFile = Files.createTempFile("mcgo-modpack-replace", ".zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("server.jar"))
            zip.write(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
            zip.closeEntry()
        }
        val targetDir = Files.createTempDirectory("mcgo-modpack-existing-target")
        Files.write(targetDir.resolve("server.jar"), byteArrayOf(9, 9, 9))
        val reportedMessages = mutableListOf<String>()

        importManagedServerModpackArchive(
            archiveFile = zipFile,
            serverWorkDir = targetDir,
            onProgress = { _, message -> reportedMessages += message },
        )

        val copyProgressMessages = reportedMessages.filter { it.startsWith("正在复制整合包文件 ·") }
        assertThat(copyProgressMessages).isNotEmpty()
        assertThat(copyProgressMessages.last()).contains("files=1/1")
        assertThat(copyProgressMessages.last()).contains("bytes=4")
        assertThat(Files.readAllBytes(targetDir.resolve("server.jar")).map { it.toInt() }).containsExactly(0x50, 0x4b, 0x03, 0x04).inOrder()
    }

    @Test
    fun importManagedServerModpackArchive_stripsReservedSetupApprovalMarkersFromArchive() {
        val zipFile = Files.createTempFile("mcgo-modpack-markers", ".zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("setup.sh"))
            zip.write("#!/bin/sh\necho hi\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry(".mcgo-modpack-setup-approved"))
            zip.write("approved\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry(".mcgo-modpack-setup-complete"))
            zip.write("done\n".toByteArray())
            zip.closeEntry()
        }
        val targetDir = Files.createTempDirectory("mcgo-modpack-marker-target")

        importManagedServerModpackArchive(zipFile, targetDir)

        assertThat(Files.exists(targetDir.resolve(".mcgo-modpack-setup-approved"))).isFalse()
        assertThat(Files.exists(targetDir.resolve(".mcgo-modpack-setup-complete"))).isFalse()
        assertThat(requiresManagedServerSetupApproval(targetDir)?.fileName?.toString()).isEqualTo("setup.sh")
        approveManagedServerSetupScript(targetDir, "setup.sh")
        assertThat(requiresManagedServerSetupApproval(targetDir)).isNull()
    }

    @Test
    fun importManagedServerModpackArchive_doesNotDeleteExistingWorkspaceWhenArchiveIsInvalid() {
        val zipFile = Files.createTempFile("mcgo-modpack-invalid", ".zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("../escape.txt"))
            zip.write("boom".toByteArray())
            zip.closeEntry()
        }
        val targetDir = Files.createTempDirectory("mcgo-modpack-existing")
        Files.write(targetDir.resolve("server.jar"), "keep-me".toByteArray())

        val error = kotlin.runCatching { importManagedServerModpackArchive(zipFile, targetDir) }.exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(Files.isRegularFile(targetDir.resolve("server.jar"))).isTrue()
        assertThat(String(Files.readAllBytes(targetDir.resolve("server.jar")))).isEqualTo("keep-me")
    }

    @Test
    fun importManagedServerModpackArchive_cleansFreshDirectTargetWhenArchiveIsInvalid() {
        val zipFile = Files.createTempFile("mcgo-modpack-invalid-fresh", ".zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("mods/example.jar"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("../escape.txt"))
            zip.write("boom".toByteArray())
            zip.closeEntry()
        }
        val targetDir = Files.createTempDirectory("mcgo-modpack-invalid-fresh-target")

        val error = kotlin.runCatching { importManagedServerModpackArchive(zipFile, targetDir) }.exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(Files.exists(targetDir)).isFalse()
    }

    @Test
    fun managedServerSetupScriptSelection_livesInDedicatedHelperFileWithoutHardcodedNameAllowList() {
        val runtimeSource = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerRuntime.kt")))
        val setupSource = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/ManagedServerSetupScripts.kt")))

        assertThat(runtimeSource).doesNotContain("listOf(\"server-setup.sh\", \"setup.sh\", \"install.sh\")")
        assertThat(runtimeSource).doesNotContain("resolve(\"startserver.sh\")")
        assertThat(runtimeSource).doesNotContain("fun discoverManagedServerSetupScripts(")
        assertThat(runtimeSource).doesNotContain("fun resolveManagedServerSetupScript(")
        assertThat(runtimeSource).doesNotContain("fun runManagedServerSetupScriptIfNeeded(")
        assertThat(runtimeSource).doesNotContain("fun rewriteManagedInstallerBootstrapScriptForAndroid(")
        assertThat(setupSource).contains("internal fun discoverManagedServerSetupScripts(")
        assertThat(setupSource).contains("internal fun resolveManagedServerSetupScript(")
        assertThat(setupSource).contains("scriptRelativePath: String")
        assertThat(setupSource).contains("fun runManagedServerSetupScriptIfNeeded(")
        assertThat(setupSource).contains("rewriteManagedInstallerBootstrapScriptForAndroid(")
        assertThat(setupSource).contains("ATM10_INSTALL_ONLY")
        assertThat(setupSource).contains("MaxManagedServerSetupScriptProbeBytes")
        assertThat(setupSource).contains("Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)")
        assertThat(setupSource).contains("Files.newInputStream(script, LinkOption.NOFOLLOW_LINKS).use(::sha256Hex)")
        assertThat(setupSource).contains("writeManagedServerMarker(")
        val approvalReaderSlice = setupSource
            .substringAfter("private fun readManagedServerSetupApprovalRecord(serverWorkDir: Path): ManagedServerSetupApprovalRecord? {")
            .substringBefore("internal fun approvedManagedServerSetupScript(")
        assertThat(approvalReaderSlice).contains("readManagedServerSetupFileBounded(marker, MaxManagedServerSetupApprovalMarkerBytes)")
        assertThat(approvalReaderSlice).doesNotContain("Files.readAllLines(marker)")
        assertThat(approvalReaderSlice).doesNotContain("Files.isRegularFile(marker))")
    }

    @Test
    fun approveManagedServerSetupScript_usesUserProvidedRelativePathInsteadOfNameGuessing() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-user-script")
        Files.write(targetDir.resolve("setup.sh"), "#!/bin/sh\necho wrong > selected-script.txt\n".toByteArray())
        val customScript = targetDir.resolve("pack scripts/run custom.sh")
        Files.createDirectories(customScript.parent)
        Files.write(
            customScript,
            "#!/bin/sh\necho selected-custom\necho custom > selected-script.txt\necho payload > server.jar\n".toByteArray(),
        )
        customScript.toFile().setExecutable(true, false)

        approveManagedServerSetupScript(targetDir, "pack scripts/run custom.sh")
        val observedLines = mutableListOf<String>()
        val executed = runManagedServerSetupScriptIfNeeded(
            serverWorkDir = targetDir,
            shellBinary = "/bin/sh",
            onOutputLine = { line -> observedLines += line },
        )

        assertThat(executed).isTrue()
        assertThat(requiresManagedServerSetupApproval(targetDir)).isNull()
        assertThat(String(Files.readAllBytes(targetDir.resolve("selected-script.txt"))).trim()).isEqualTo("custom")
        assertThat(observedLines).contains("selected-custom")
    }

    @Test
    fun approveManagedServerSetupScript_rejectsEscapingUserProvidedPath() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-user-script-escape")
        Files.write(targetDir.resolve("setup.sh"), "#!/bin/sh\necho ok\n".toByteArray())

        assertFailsWith<IllegalArgumentException> {
            approveManagedServerSetupScript(targetDir, "../setup.sh")
        }
        assertFailsWith<IllegalArgumentException> {
            approveManagedServerSetupScript(targetDir, "/tmp/setup.sh")
        }
    }

    @Test
    fun approveManagedServerSetupScript_rejectsSymlinkUserProvidedPath() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-user-script-link")
        val outsideScript = Files.createTempFile("mcgo-outside-setup", ".sh")
        Files.write(outsideScript, "#!/bin/sh\necho escaped\n".toByteArray())
        val linkedScript = targetDir.resolve("linked.sh")
        Files.createSymbolicLink(linkedScript, outsideScript)
        val outsideDir = Files.createTempDirectory("mcgo-outside-setup-dir")
        Files.write(outsideDir.resolve("nested.sh"), "#!/bin/sh\necho escaped dir\n".toByteArray())
        Files.createSymbolicLink(targetDir.resolve("linked-dir"), outsideDir)

        assertFailsWith<IllegalArgumentException> {
            approveManagedServerSetupScript(targetDir, "linked.sh")
        }
        assertFailsWith<IllegalArgumentException> {
            approveManagedServerSetupScript(targetDir, "linked-dir/nested.sh")
        }
        assertThat(discoverManagedServerSetupScripts(targetDir)).isEmpty()
    }

    @Test
    fun discoverManagedServerSetupScripts_recognizesUserNamedInstallerBootstrapScripts() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-startserver-detect")
        val script = targetDir.resolve("pack-scripts/bootstrap-server")
        Files.createDirectories(script.parent)
        Files.write(
            script,
            """#!/bin/sh
INSTALLER="neoforge-21.1.224-installer.jar"
if [ ! -d libraries ]; then
  java -jar "${'$'}INSTALLER" -installServer
fi
""".toByteArray(),
        )
        Files.write(targetDir.resolve("neoforge-21.1.224-installer.jar"), byteArrayOf(1, 2, 3))

        assertThat(discoverManagedServerSetupScripts(targetDir)).contains(script)
        assertThat(isInstallerBootstrapScript(script, targetDir)).isTrue()
    }

    @Test
    fun isInstallerBootstrapScript_readsOnlyBoundedPrefixForLargeScripts() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-large-script")
        val script = targetDir.resolve("bootstrap-server")
        Files.write(
            script,
            """#!/bin/sh
INSTALLER="neoforge-21.1.224-installer.jar"
java -jar "${'$'}INSTALLER" -installServer
""".toByteArray() + ByteArray(256 * 1024) { 'x'.code.toByte() },
        )
        Files.write(targetDir.resolve("neoforge-21.1.224-installer.jar"), byteArrayOf(1, 2, 3))

        assertThat(isInstallerBootstrapScript(script, targetDir)).isTrue()
    }

    @Test
    fun approvedManagedServerSetupScript_rejectsSymlinkApprovalMarker() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-approval-marker-link")
        val script = targetDir.resolve("setup.sh")
        Files.write(script, "#!/bin/sh\necho safe\n".toByteArray())
        approveManagedServerSetupScript(targetDir, script.fileName.toString())
        val marker = managedServerSetupApprovalMarker(targetDir)
        val externalMarker = Files.createTempFile("mcgo-approval-marker", ".txt")
        Files.write(externalMarker, Files.readAllBytes(marker))
        Files.delete(marker)
        Files.createSymbolicLink(marker, externalMarker)

        assertThat(approvedManagedServerSetupScript(targetDir)).isNull()
        assertThat(requiresManagedServerSetupApproval(targetDir)).isEqualTo(script)
    }

    @Test
    fun approveManagedServerSetupScript_rejectsPreexistingSymlinkApprovalMarker() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-approval-marker-preexisting-link")
        val script = targetDir.resolve("setup.sh")
        Files.write(script, "#!/bin/sh\necho safe\n".toByteArray())
        val externalMarker = Files.createTempFile("mcgo-approval-marker-destination", ".txt")
        Files.write(externalMarker, "keep-me\n".toByteArray())
        Files.createSymbolicLink(managedServerSetupApprovalMarker(targetDir), externalMarker)

        assertFailsWith<IllegalArgumentException> {
            approveManagedServerSetupScript(targetDir, script.fileName.toString())
        }
        assertThat(String(Files.readAllBytes(externalMarker))).isEqualTo("keep-me\n")
    }

    @Test
    fun requiresManagedServerSetupApproval_ignoresSymlinkCompletionMarker() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-completion-marker-approval-link")
        val script = targetDir.resolve("setup.sh")
        Files.write(script, "#!/bin/sh\necho safe\n".toByteArray())
        val externalMarker = Files.createTempFile("mcgo-completion-marker-ui", ".txt")
        Files.write(externalMarker, "done\n".toByteArray())
        Files.createSymbolicLink(managedServerSetupCompletionMarker(targetDir), externalMarker)

        assertThat(requiresManagedServerSetupApproval(targetDir)).isEqualTo(script)
    }

    @Test
    fun runManagedServerSetupScriptIfNeeded_rejectsPreexistingSymlinkCompletionMarker() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-completion-marker-link")
        val script = targetDir.resolve("setup.sh")
        Files.write(script, "#!/bin/sh\necho payload > server.jar\n".toByteArray())
        approveManagedServerSetupScript(targetDir, script.fileName.toString())
        val externalMarker = Files.createTempFile("mcgo-completion-marker-destination", ".txt")
        Files.write(externalMarker, "keep-me\n".toByteArray())
        Files.createSymbolicLink(managedServerSetupCompletionMarker(targetDir), externalMarker)

        val error = assertFailsWith<IllegalStateException> {
            runManagedServerSetupScriptIfNeeded(targetDir, shellBinary = "/bin/sh")
        }

        assertThat(error).hasMessageThat().contains("整合包安装标记不能是符号链接")
        assertThat(String(Files.readAllBytes(externalMarker))).isEqualTo("keep-me\n")
    }

    @Test
    fun runManagedServerSetupScriptIfNeeded_executesOnlyOnceAndCreatesMarker() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-setup-run")
        val script = targetDir.resolve("setup.sh")
        Files.write(
            script,
            "#!/bin/sh\nif [ -f setup-count.txt ]; then\n  echo 2 > setup-count.txt\nelse\n  echo 1 > setup-count.txt\nfi\necho payload > server.jar\n".toByteArray(),
        )
        script.toFile().setExecutable(true, false)
        approveManagedServerSetupScript(targetDir, script.fileName.toString())

        val firstRun = runManagedServerSetupScriptIfNeeded(targetDir, shellBinary = "/bin/sh")
        val secondRun = runManagedServerSetupScriptIfNeeded(targetDir, shellBinary = "/bin/sh")

        assertThat(firstRun).isTrue()
        assertThat(secondRun).isFalse()
        assertThat(String(Files.readAllBytes(targetDir.resolve("setup-count.txt"))).trim()).isEqualTo("1")
        assertThat(Files.isRegularFile(targetDir.resolve("server.jar.sha256"))).isTrue()
        assertThat(Files.isRegularFile(targetDir.resolve(".mcgo-modpack-setup-complete"))).isTrue()
    }

    @Test
    fun requiresManagedServerSetupApproval_rejectsStaleApprovalWhenScriptContentsChange() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-setup-stale")
        val script = targetDir.resolve("setup.sh")
        Files.write(script, "#!/bin/sh\necho first\n".toByteArray())
        approveManagedServerSetupScript(targetDir, script.fileName.toString())
        Files.write(script, "#!/bin/sh\necho second\n".toByteArray())

        assertThat(requiresManagedServerSetupApproval(targetDir)).isEqualTo(script)
    }

    @Test
    fun requiresManagedServerSetupApproval_rechecksTheUserSelectedScriptWhenContentsChange() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-setup-selected-stale")
        val setup = targetDir.resolve("setup.sh")
        Files.write(setup, "#!/bin/sh\necho setup\n".toByteArray())
        val customScript = targetDir.resolve("custom-start.sh")
        Files.write(customScript, "#!/bin/sh\necho custom first\n".toByteArray())
        approveManagedServerSetupScript(targetDir, customScript.fileName.toString())
        Files.write(customScript, "#!/bin/sh\necho custom second\n".toByteArray())

        assertThat(requiresManagedServerSetupApproval(targetDir)).isEqualTo(customScript)
    }

    @Test
    fun runManagedServerSetupScriptIfNeeded_requiresApprovalMarkerBeforeExecuting() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-setup-approval")
        val script = targetDir.resolve("setup.sh")
        Files.write(
            script,
            "#!/bin/sh\necho should-not-run > setup-count.txt\n".toByteArray(),
        )
        script.toFile().setExecutable(true, false)

        val error = assertFailsWith<IllegalStateException> {
            runManagedServerSetupScriptIfNeeded(targetDir, shellBinary = "/bin/sh")
        }

        assertThat(error).hasMessageThat().contains("请先输入并确认整合包安装脚本")
        assertThat(Files.exists(targetDir.resolve("setup-count.txt"))).isFalse()
        assertThat(Files.exists(targetDir.resolve(".mcgo-modpack-setup-complete"))).isFalse()
    }

    @Test
    fun runManagedServerSetupScriptIfNeeded_marksCurseforgeStartserverAsInstallOnly() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-startserver-run")
        val script = targetDir.resolve("startserver.sh")
        Files.write(
            script,
            """#!/bin/sh
# bootstrap neoforge installer -installServer
printf '%s\n' "ATM10_INSTALL_ONLY=${'$'}{ATM10_INSTALL_ONLY:-missing}" > install-env.txt
printf '%s\n' "ATM10_RESTART=${'$'}{ATM10_RESTART:-missing}" >> install-env.txt
echo payload > server.jar
exit 0
""".toByteArray(),
        )
        Files.write(targetDir.resolve("neoforge-21.1.224-installer.jar"), byteArrayOf(1, 2, 3))
        script.toFile().setExecutable(true, false)
        approveManagedServerSetupScript(targetDir, script.fileName.toString())

        val executed = runManagedServerSetupScriptIfNeeded(targetDir, shellBinary = "/bin/sh")

        assertThat(executed).isTrue()
        assertThat(String(Files.readAllBytes(targetDir.resolve("install-env.txt")))).contains("ATM10_INSTALL_ONLY=true")
        assertThat(String(Files.readAllBytes(targetDir.resolve("install-env.txt")))).contains("ATM10_RESTART=false")
    }

    @Test
    fun runManagedServerSetupScriptIfNeeded_streamsScriptOutputToLogFileAndCallback() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-setup-log")
        val script = targetDir.resolve("setup.sh")
        Files.write(
            script,
            "#!/bin/sh\necho install-step-1\necho install-step-2\necho payload > server.jar\n".toByteArray(),
        )
        script.toFile().setExecutable(true, false)
        approveManagedServerSetupScript(targetDir, script.fileName.toString())
        val logFile = targetDir.resolve("logs/mcgo-latest.log")
        val observedLines = mutableListOf<String>()

        val executed = runManagedServerSetupScriptIfNeeded(
            serverWorkDir = targetDir,
            shellBinary = "/bin/sh",
            logFile = logFile,
            onOutputLine = { line -> observedLines += line },
        )

        assertThat(executed).isTrue()
        val logText = String(Files.readAllBytes(logFile))
        assertThat(logText).contains("[debug]")
        assertThat(logText).contains("准备执行整合包安装脚本")
        assertThat(logText).contains("install-step-1")
        assertThat(logText).contains("install-step-2")
        assertThat(logText).contains("整合包安装脚本执行完成")
        assertThat(observedLines).containsExactly("install-step-1", "install-step-2").inOrder()
    }

    @Test
    fun runManagedServerSetupScriptIfNeeded_summarizesNoisyMinecraftClassListingsInsteadOfFloodingCallbacksOrLogs() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-setup-class-listing")
        val script = targetDir.resolve("setup.sh")
        Files.write(
            script,
            """#!/bin/sh
echo setup-started
echo '  net/minecraft/world/entity/monster/EnderMan.class'
echo 'net/minecraft/world/entity/monster/Slime.class'
echo setup-finished
echo payload > server.jar
""".toByteArray(),
        )
        script.toFile().setExecutable(true, false)
        approveManagedServerSetupScript(targetDir, script.fileName.toString())
        val logFile = targetDir.resolve("logs/mcgo-setup.log")
        val observedLines = mutableListOf<String>()

        val executed = runManagedServerSetupScriptIfNeeded(
            serverWorkDir = targetDir,
            shellBinary = "/bin/sh",
            logFile = logFile,
            onOutputLine = { line -> observedLines += line },
        )

        assertThat(executed).isTrue()
        val logText = String(Files.readAllBytes(logFile))
        assertThat(logText).contains("setup-started")
        assertThat(logText).contains("已省略 2 行 Minecraft class 清单输出")
        assertThat(logText).contains("setup-finished")
        assertThat(logText).doesNotContain("EnderMan.class")
        assertThat(logText).doesNotContain("Slime.class")
        assertThat(observedLines).containsExactly(
            "setup-started",
            "[MC-GO] 已省略 2 行 Minecraft class 清单输出（完整启动失败请看后续错误行）",
            "setup-finished",
        ).inOrder()
    }

    @Test
    fun runManagedServerSetupScriptIfNeeded_doesNotInjectNonExecutableBinJavaIntoInstallerBootstrapEnv() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-setup-java-env")
        val script = targetDir.resolve("startserver.sh")
        Files.write(
            script,
            """#!/bin/sh
# bootstrap neoforge installer -installServer
printf '%s\n' "ATM10_JAVA=${'$'}{ATM10_JAVA:-missing}" > install-java.txt
printf '%s\n' "JAVA_HOME=${'$'}{JAVA_HOME:-missing}" >> install-java.txt
echo payload > server.jar
exit 0
""".toByteArray(),
        )
        Files.write(targetDir.resolve("neoforge-21.1.224-installer.jar"), byteArrayOf(1, 2, 3))
        script.toFile().setExecutable(true, false)
        approveManagedServerSetupScript(targetDir, script.fileName.toString())

        runManagedServerSetupScriptIfNeeded(
            serverWorkDir = targetDir,
            shellBinary = "/bin/sh",
            environment = listOf("JAVA_HOME=/data/user/0/com.mcgo.app/files/jre/java-21"),
        )

        val envText = String(Files.readAllBytes(targetDir.resolve("install-java.txt")))
        assertThat(envText).contains("JAVA_HOME=/data/user/0/com.mcgo.app/files/jre/java-21")
        assertThat(envText).doesNotContain("ATM10_JAVA=/data/user/0/com.mcgo.app/files/jre/java-21/bin/java")
    }

    @Test
    fun runManagedServerSetupScriptIfNeeded_rewritesInstallerBootstrapJavaInvocationsToAppProcessManagedLaunch() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-setup-java-wrapper-env")
        val script = targetDir.resolve("startserver.sh")
        Files.write(
            script,
            """#!/bin/sh
# bootstrap neoforge installer -installServer
if ! command -v "${'$'}{ATM10_JAVA:-java}" >/dev/null 2>&1; then
  echo missing-java > install-java.txt
  exit 9
fi
"${'$'}{ATM10_JAVA:-java}" -jar neoforge-21.1.224-installer.jar -installServer > install-java.txt
echo payload > server.jar
exit 0
""".toByteArray(),
        )
        Files.write(targetDir.resolve("neoforge-21.1.224-installer.jar"), byteArrayOf(1, 2, 3))
        script.toFile().setExecutable(true, false)
        approveManagedServerSetupScript(targetDir, script.fileName.toString())

        runManagedServerSetupScriptIfNeeded(
            serverWorkDir = targetDir,
            shellBinary = "/bin/sh",
            environment = listOf(
                "CLASSPATH=/data/app/com.mcgo.app/base.apk",
                "JAVA_HOME=/data/user/0/com.mcgo.app/files/jre/java-21",
                "HOME=/storage/emulated/0/MCGO/servers/neoforge-pack",
                "TMPDIR=/data/user/0/com.mcgo.app/cache",
                "MCGO_JAVA_APP_PROCESS=/bin/echo",
                "MCGO_JAVA_MAIN_CLASS=com.mcgo.app.server.ManagedJavaCli",
                "MCGO_JAVA_CLASSPATH=/data/app/com.mcgo.app/base.apk",
                "MCGO_JAVA_HOME=/data/user/0/com.mcgo.app/files/jre/java-21",
                "MCGO_JAVA_NATIVE_LAUNCHER_LIB=/data/app/com.mcgo.app/lib/arm64/libpaper_jli_launcher.so",
                "PATH=/system/bin",
            ),
        )

        val envText = String(Files.readAllBytes(targetDir.resolve("install-java.txt")))
        assertThat(envText).contains("-Dmcgo.paperJvmLauncher.absoluteLibPath=/data/app/com.mcgo.app/lib/arm64/libpaper_jli_launcher.so")
        assertThat(envText).contains("-Djava.io.tmpdir=/data/user/0/com.mcgo.app/cache")
        assertThat(envText).contains("-Duser.home=/storage/emulated/0/MCGO/servers/neoforge-pack")
        assertThat(envText).contains("/system/bin com.mcgo.app.server.ManagedJavaCli /data/user/0/com.mcgo.app/files/jre/java-21 -jar neoforge-21.1.224-installer.jar -installServer")
        assertThat(envText).doesNotContain("missing-java")
        val generatedBootstrapScripts = Files.list(targetDir).use { children ->
            children
                .map { child -> child.fileName.toString() }
                .filter { name -> name.contains(".mcgo-android-") }
                .toList()
        }
        assertThat(generatedBootstrapScripts).isEmpty()
    }

    @Test
    fun runManagedServerSetupScriptIfNeeded_deletesGeneratedBootstrapScriptWhenProcessStartFails() {
        val targetDir = Files.createTempDirectory("mcgo-modpack-setup-java-wrapper-start-fail")
        val script = targetDir.resolve("startserver.sh")
        Files.write(
            script,
            """#!/bin/sh
# bootstrap neoforge installer -installServer
if ! command -v "${'$'}{ATM10_JAVA:-java}" >/dev/null 2>&1; then
  exit 9
fi
"${'$'}{ATM10_JAVA:-java}" -jar neoforge-21.1.224-installer.jar -installServer > install-java.txt
exit 0
""".toByteArray(),
        )
        Files.write(targetDir.resolve("neoforge-21.1.224-installer.jar"), byteArrayOf(1, 2, 3))
        script.toFile().setExecutable(true, false)
        approveManagedServerSetupScript(targetDir, script.fileName.toString())

        assertFailsWith<java.io.IOException> {
            runManagedServerSetupScriptIfNeeded(
                serverWorkDir = targetDir,
                shellBinary = "/definitely/missing/sh",
                environment = listOf(
                    "CLASSPATH=/data/app/com.mcgo.app/base.apk",
                    "JAVA_HOME=/data/user/0/com.mcgo.app/files/jre/java-21",
                    "MCGO_JAVA_APP_PROCESS=/bin/echo",
                    "MCGO_JAVA_MAIN_CLASS=com.mcgo.app.server.ManagedJavaCli",
                    "MCGO_JAVA_CLASSPATH=/data/app/com.mcgo.app/base.apk",
                    "MCGO_JAVA_HOME=/data/user/0/com.mcgo.app/files/jre/java-21",
                    "MCGO_JAVA_NATIVE_LAUNCHER_LIB=/data/app/com.mcgo.app/lib/arm64/libpaper_jli_launcher.so",
                    "PATH=/system/bin",
                ),
            )
        }
        val generatedBootstrapScripts = Files.list(targetDir).use { children ->
            children
                .map { child -> child.fileName.toString() }
                .filter { name -> name.contains(".mcgo-android-") }
                .toList()
        }
        assertThat(generatedBootstrapScripts).isEmpty()
    }

    @Test
    fun buildManagedJavaProcessCommand_prefersAppProcessManagedLaunchMetadataOverRawBinJava() {
        val command = buildManagedJavaProcessCommand(
            fallbackJavaBinary = "/data/user/0/com.mcgo.app/files/jre/java-21/bin/java",
            environment = listOf(
                "MCGO_JAVA_APP_PROCESS=/system/bin/app_process",
                "MCGO_JAVA_MAIN_CLASS=com.mcgo.app.server.ManagedJavaCli",
                "MCGO_JAVA_HOME=/data/user/0/com.mcgo.app/files/jre/java-21",
                "MCGO_JAVA_NATIVE_LAUNCHER_LIB=/data/app/com.mcgo.app/lib/arm64/libpaper_jli_launcher.so",
            ),
            javaArguments = listOf("-jar", "installer.jar", "--installServer"),
        )

        assertThat(command).containsAtLeast(
            "/system/bin/app_process",
            "-Dmcgo.paperJvmLauncher.absoluteLibPath=/data/app/com.mcgo.app/lib/arm64/libpaper_jli_launcher.so",
            "/system/bin",
            "com.mcgo.app.server.ManagedJavaCli",
            "/data/user/0/com.mcgo.app/files/jre/java-21",
            "-jar",
            "installer.jar",
            "--installServer",
        )
        assertThat(command.first() == "/data/user/0/com.mcgo.app/files/jre/java-21/bin/java").isFalse()
    }

    @Test
    fun resolveNeoForgeMinecraftVersions_mapsArtifactPrefixesBackToMinecraftVersions() {
        val metadata = """
            <metadata>
              <versioning>
                <versions>
                  <version>21.4.157</version>
                  <version>20.6.129-beta</version>
                  <version>26.1.2</version>
                </versions>
              </versioning>
            </metadata>
        """.trimIndent()

        val versions = resolveNeoForgeMinecraftVersions(
            metadataXml = metadata,
            availableMinecraftVersions = listOf("1.21.4", "1.20.6", "26.1.2"),
        )

        assertThat(versions).containsExactly("1.20.6", "1.21.4", "26.1.2").inOrder()
    }

    @Test
    fun markerFileDetection_readsOnlyPrefixWithoutNeedingWholeJarTextDecode() {
        val marker = Files.createTempFile("mcgo-marker", ".jar")
        Files.write(marker, "installed\nrest".toByteArray())
        val realJar = Files.createTempFile("mcgo-real", ".jar")
        Files.write(realJar, byteArrayOf(0x50, 0x4b, 0x03, 0x04))

        assertThat(isInstalledPayloadMarkerFile(marker)).isTrue()
        assertThat(isInstalledPayloadMarkerFile(realJar)).isFalse()
    }

    @Test
    fun importManagedServerModpackArchive_createsMissingParentDirectoryBeforeDirectExtraction() {
        val zipFile = Files.createTempFile("mcgo-modpack-parent", ".zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("server.jar"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val rootDir = Files.createTempDirectory("mcgo-modpack-parent-root")
        val targetDir = rootDir.resolve("servers/demo")

        importManagedServerModpackArchive(zipFile, targetDir)

        assertThat(Files.isRegularFile(targetDir.resolve("server.jar"))).isTrue()
        assertThat(Files.isRegularFile(targetDir.resolve("server.jar.sha256"))).isTrue()
    }

    @Test
    fun importManagedServerModpackArchive_writesShaForResolvedFabricLaunchJar() {
        val zipFile = Files.createTempFile("mcgo-modpack-fabric-sha", ".zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("server.jar"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("fabric-server-launch.jar"))
            zip.write(byteArrayOf(4, 5, 6))
            zip.closeEntry()
        }
        val targetDir = Files.createTempDirectory("mcgo-modpack-fabric-target")

        importManagedServerModpackArchive(zipFile, targetDir)

        val launchJar = targetDir.resolve("fabric-server-launch.jar")
        assertThat(Files.isRegularFile(paperJarSha256File(launchJar))).isTrue()
        assertThat(Files.exists(targetDir.resolve("server.jar.sha256"))).isFalse()
    }

    @Test
    fun importManagedServerModpackArchive_writesShaForResolvedQuiltLaunchJar() {
        val zipFile = Files.createTempFile("mcgo-modpack-quilt-sha", ".zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("server.jar"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("quilt-server-launch.jar"))
            zip.write(byteArrayOf(4, 5, 6))
            zip.closeEntry()
        }
        val targetDir = Files.createTempDirectory("mcgo-modpack-quilt-target")

        importManagedServerModpackArchive(zipFile, targetDir)

        val launchJar = targetDir.resolve("quilt-server-launch.jar")
        assertThat(Files.isRegularFile(paperJarSha256File(launchJar))).isTrue()
        assertThat(Files.exists(targetDir.resolve("server.jar.sha256"))).isFalse()
    }

    @Test
    fun detectImportedModpackServerMetadata_prefersFabricLaunchJarAndRecommendedJava() {
        val serverWorkDir = Files.createTempDirectory("mcgo-detect-fabric")
        Files.write(serverWorkDir.resolve("fabric-server-launch.jar"), byteArrayOf(1, 2, 3))
        val clueJar = serverWorkDir.resolve("libraries/net/minecraft/server/1.18.2/server-1.18.2.jar")
        Files.createDirectories(clueJar.parent)
        Files.write(clueJar, byteArrayOf(4, 5, 6))

        val metadata = detectImportedModpackServerMetadata(serverWorkDir)

        assertThat(metadata.serverType).isEqualTo(com.mcgo.app.ui.model.MinecraftServerType.Fabric)
        assertThat(metadata.minecraftVersion).isEqualTo("1.18.2")
        assertThat(metadata.javaMajorVersion).isEqualTo(17)
    }

    @Test
    fun detectImportedModpackServerMetadata_detectsQuiltLaunchJar() {
        val serverWorkDir = Files.createTempDirectory("mcgo-detect-quilt")
        Files.write(serverWorkDir.resolve("quilt-server-launch.jar"), byteArrayOf(1, 2, 3))
        val clueJar = serverWorkDir.resolve("libraries/net/minecraft/server/1.20.6/server-1.20.6.jar")
        Files.createDirectories(clueJar.parent)
        Files.write(clueJar, byteArrayOf(4, 5, 6))

        val metadata = detectImportedModpackServerMetadata(serverWorkDir)

        assertThat(metadata.serverType).isEqualTo(com.mcgo.app.ui.model.MinecraftServerType.Quilt)
        assertThat(metadata.minecraftVersion).isEqualTo("1.20.6")
        assertThat(metadata.javaMajorVersion).isEqualTo(21)
    }

    @Test
    fun detectImportedModpackServerMetadata_detectsForgeUnixArgsVersion() {
        val serverWorkDir = Files.createTempDirectory("mcgo-detect-forge")
        val argsFile = serverWorkDir.resolve("libraries/net/minecraftforge/forge/1.20.1-47.3.0/unix_args.txt")
        Files.createDirectories(argsFile.parent)
        Files.write(argsFile, "--launchTarget forgeserver\n".toByteArray())

        val metadata = detectImportedModpackServerMetadata(serverWorkDir)

        assertThat(metadata.serverType).isEqualTo(com.mcgo.app.ui.model.MinecraftServerType.Forge)
        assertThat(metadata.minecraftVersion).isEqualTo("1.20.1")
        assertThat(metadata.javaMajorVersion).isEqualTo(21)
    }

    @Test
    fun detectImportedModpackServerMetadata_detectsNeoForgeUnixArgsVersion() {
        val serverWorkDir = Files.createTempDirectory("mcgo-detect-neoforge")
        val argsFile = serverWorkDir.resolve("libraries/net/neoforged/neoforge/21.4.157/unix_args.txt")
        Files.createDirectories(argsFile.parent)
        Files.write(argsFile, "--launchTarget neoforgeserver\n".toByteArray())

        val metadata = detectImportedModpackServerMetadata(serverWorkDir)

        assertThat(metadata.serverType).isEqualTo(com.mcgo.app.ui.model.MinecraftServerType.NeoForge)
        assertThat(metadata.minecraftVersion).isEqualTo("1.21.4")
        assertThat(metadata.javaMajorVersion).isEqualTo(21)
    }

    @Test
    fun detectImportedModpackServerMetadata_preservesNeoForgeModernVersionNumbering() {
        val serverWorkDir = Files.createTempDirectory("mcgo-detect-neoforge-modern")
        val argsFile = serverWorkDir.resolve("libraries/net/neoforged/neoforge/26.1.2/unix_args.txt")
        Files.createDirectories(argsFile.parent)
        Files.write(argsFile, "--launchTarget neoforgeserver\n".toByteArray())

        val metadata = detectImportedModpackServerMetadata(serverWorkDir)

        assertThat(metadata.serverType).isEqualTo(com.mcgo.app.ui.model.MinecraftServerType.NeoForge)
        assertThat(metadata.minecraftVersion).isEqualTo("26.1.2")
        assertThat(metadata.javaMajorVersion).isEqualTo(25)
    }

    @Test
    fun detectImportedModpackServerMetadata_detectsCurseforgeNeoForgeInstallerPackBeforeLibrariesExist() {
        val serverWorkDir = Files.createTempDirectory("mcgo-detect-neoforge-installer-pack")
        Files.write(
            serverWorkDir.resolve("startserver.sh"),
            """#!/bin/sh
NEOFORGE_VERSION=21.1.224
INSTALLER="neoforge-${'$'}NEOFORGE_VERSION-installer.jar"
if [ ! -d libraries ]; then
  java -jar "${'$'}INSTALLER" -installServer
fi
""".toByteArray(),
        )
        Files.write(serverWorkDir.resolve("neoforge-21.1.224-installer.jar"), byteArrayOf(1, 2, 3))

        val metadata = detectImportedModpackServerMetadata(serverWorkDir)

        assertThat(metadata.serverType).isEqualTo(com.mcgo.app.ui.model.MinecraftServerType.NeoForge)
        assertThat(metadata.minecraftVersion).isEqualTo("1.21.1")
        assertThat(metadata.javaMajorVersion).isEqualTo(21)
    }

    @Test
    fun detectImportedModpackServerMetadata_fallsBackToPaperWhenNoLoaderArtifactsFound() {
        val serverWorkDir = Files.createTempDirectory("mcgo-detect-paper")
        Files.write(serverWorkDir.resolve("server.jar"), byteArrayOf(1, 2, 3))

        val metadata = detectImportedModpackServerMetadata(serverWorkDir)

        assertThat(metadata.serverType).isEqualTo(com.mcgo.app.ui.model.MinecraftServerType.Paper)
    }

    @Test
    fun resolveInstalledPayloadJar_supportsQuiltLaunchJar() {
        val serverWorkDir = Files.createTempDirectory("mcgo-quilt-payload")
        val marker = serverWorkDir.resolve("quilt-1.21.4.jar")
        Files.write(marker, "launcher=quilt-server-launch.jar\n".toByteArray())
        val launchJar = serverWorkDir.resolve("quilt-server-launch.jar")
        Files.write(launchJar, byteArrayOf(1, 2, 3))

        assertThat(resolveInstalledPayloadJar(serverWorkDir, marker)).isEqualTo(launchJar)
    }

    @Test
    fun resolveInstalledPayloadJar_prefersFabricServerLaunchJarForImportedFabricWorkspace() {
        val serverWorkDir = Files.createTempDirectory("mcgo-fabric-payload")
        val marker = serverWorkDir.resolve("fabric-1.21.4.jar")
        Files.write(marker, "installed\n".toByteArray())
        val serverJar = serverWorkDir.resolve("server.jar")
        val launchJar = serverWorkDir.resolve("fabric-server-launch.jar")
        Files.write(serverJar, byteArrayOf(1, 2, 3))
        Files.write(launchJar, byteArrayOf(4, 5, 6))

        assertThat(resolveInstalledPayloadJar(serverWorkDir, marker)).isEqualTo(launchJar)
    }

    @Test
    fun shouldReuseInstalledServerPayload_acceptsResolvedForgePayloadWhenPayloadShaExists() {
        val serverWorkDir = Files.createTempDirectory("mcgo-payload-reuse-positive")
        val marker = serverWorkDir.resolve("forge-1.21.4.jar")
        Files.write(marker, "installed\n".toByteArray())
        val serverJar = serverWorkDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.16/forge-1.21.4-54.1.16-server.jar")
        Files.createDirectories(serverJar.parent)
        Files.write(serverJar, "verified-payload".toByteArray())
        Files.write(serverWorkDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.16/unix_args.txt"), "--launchTarget forgeserver\n".toByteArray())
        Files.write(paperJarSha256File(serverJar), (sha256Hex(serverJar) + "\n").toByteArray())

        assertThat(shouldReuseInstalledServerPayload(serverWorkDir, marker)).isTrue()
    }

    @Test
    fun resolveInstalledPayloadJar_prefersRealLaunchArtifactsOverMarkerFiles() {
        val serverWorkDir = Files.createTempDirectory("mcgo-payload-jar")
        val marker = serverWorkDir.resolve("forge-1.21.4.jar")
        Files.write(marker, "installed\n".toByteArray())
        val serverJar = serverWorkDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.16/forge-1.21.4-54.1.16-server.jar")
        Files.createDirectories(serverJar.parent)
        Files.write(serverJar, byteArrayOf(1, 2, 3))

        assertThat(resolveInstalledPayloadJar(serverWorkDir, marker)).isEqualTo(serverJar)
    }

    @Test
    fun resolveInstalledPayloadJar_prefersForgeServerJarOverRootServerJar() {
        val serverWorkDir = Files.createTempDirectory("mcgo-forge-root-server")
        val marker = serverWorkDir.resolve("forge-1.21.4.jar")
        Files.write(marker, "installed\n".toByteArray())
        val rootServerJar = serverWorkDir.resolve("server.jar")
        Files.write(rootServerJar, byteArrayOf(9, 9, 9))
        val forgeServerJar = serverWorkDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.16/forge-1.21.4-54.1.16-server.jar")
        Files.createDirectories(forgeServerJar.parent)
        Files.write(forgeServerJar, byteArrayOf(1, 2, 3))

        assertThat(resolveInstalledPayloadJar(serverWorkDir, marker)).isEqualTo(forgeServerJar)
    }

    @Test
    fun resolveInstalledPayloadJar_prefersForgeServerJarOverRealForgeTargetJar() {
        val serverWorkDir = Files.createTempDirectory("mcgo-forge-real-target")
        val targetJar = serverWorkDir.resolve("forge-1.21.4.jar")
        Files.write(targetJar, byteArrayOf(7, 7, 7))
        val forgeServerJar = serverWorkDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.16/forge-1.21.4-54.1.16-server.jar")
        Files.createDirectories(forgeServerJar.parent)
        Files.write(forgeServerJar, byteArrayOf(1, 2, 3))

        assertThat(resolveInstalledPayloadJar(serverWorkDir, targetJar)).isEqualTo(forgeServerJar)
    }

    @Test
    fun resolveInstalledPayloadJar_prefersNeoForgeUniversalJarOverRootServerJar() {
        val serverWorkDir = Files.createTempDirectory("mcgo-neoforge-root-server")
        val marker = serverWorkDir.resolve("neoforge-1.21.4.jar")
        Files.write(marker, "installed\n".toByteArray())
        val rootServerJar = serverWorkDir.resolve("server.jar")
        Files.write(rootServerJar, byteArrayOf(9, 9, 9))
        val neoForgeJar = serverWorkDir.resolve("libraries/net/neoforged/neoforge/21.4.157/neoforge-21.4.157-universal.jar")
        Files.createDirectories(neoForgeJar.parent)
        Files.write(neoForgeJar, byteArrayOf(1, 2, 3))

        assertThat(resolveInstalledPayloadJar(serverWorkDir, marker)).isEqualTo(neoForgeJar)
    }

    @Test
    fun resolveInstalledPayloadJar_prefersNeoForgeUniversalJarOverRealNeoForgeTargetJar() {
        val serverWorkDir = Files.createTempDirectory("mcgo-neoforge-real-target")
        val targetJar = serverWorkDir.resolve("neoforge-1.21.4.jar")
        Files.write(targetJar, byteArrayOf(7, 7, 7))
        val neoForgeJar = serverWorkDir.resolve("libraries/net/neoforged/neoforge/21.4.157/neoforge-21.4.157-universal.jar")
        Files.createDirectories(neoForgeJar.parent)
        Files.write(neoForgeJar, byteArrayOf(1, 2, 3))

        assertThat(resolveInstalledPayloadJar(serverWorkDir, targetJar)).isEqualTo(neoForgeJar)
    }

    @Test
    fun shouldReuseInstalledServerPayload_rejectsForgeWorkspaceWithoutUnixArgsEvenIfPayloadShaExists() {
        val serverWorkDir = Files.createTempDirectory("mcgo-forge-missing-args")
        val marker = serverWorkDir.resolve("forge-1.21.4.jar")
        Files.write(marker, "installed\n".toByteArray())
        val forgeServerJar = serverWorkDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.16/forge-1.21.4-54.1.16-server.jar")
        Files.createDirectories(forgeServerJar.parent)
        Files.write(forgeServerJar, "verified-payload".toByteArray())
        Files.write(paperJarSha256File(forgeServerJar), (sha256Hex(forgeServerJar) + "\n").toByteArray())

        assertThat(shouldReuseInstalledServerPayload(serverWorkDir, marker)).isFalse()
    }

    @Test
    fun shouldReuseInstalledServerPayload_rejectsRealForgeTargetJarWithoutUnixArgs() {
        val serverWorkDir = Files.createTempDirectory("mcgo-forge-real-target-missing-args")
        val targetJar = serverWorkDir.resolve("forge-1.21.4.jar")
        Files.write(targetJar, "managed-forge".toByteArray())
        Files.write(paperJarSha256File(targetJar), (sha256Hex(targetJar) + "\n").toByteArray())

        assertThat(shouldReuseInstalledServerPayload(serverWorkDir, targetJar)).isFalse()
    }

    @Test
    fun shouldReuseInstalledServerPayload_acceptsNeoForgeWorkspaceWithUniversalJarAndUnixArgs() {
        val serverWorkDir = Files.createTempDirectory("mcgo-neoforge-reuse-positive")
        val marker = serverWorkDir.resolve("neoforge-1.21.4.jar")
        Files.write(marker, "installed\n".toByteArray())
        val rootServerJar = serverWorkDir.resolve("server.jar")
        Files.write(rootServerJar, "vanilla".toByteArray())
        val neoForgeJar = serverWorkDir.resolve("libraries/net/neoforged/neoforge/21.4.157/neoforge-21.4.157-universal.jar")
        Files.createDirectories(neoForgeJar.parent)
        Files.write(neoForgeJar, "verified-neoforge".toByteArray())
        Files.write(serverWorkDir.resolve("libraries/net/neoforged/neoforge/21.4.157/unix_args.txt"), "--launchTarget neoforgeserver\n".toByteArray())
        Files.write(paperJarSha256File(neoForgeJar), (sha256Hex(neoForgeJar) + "\n").toByteArray())

        assertThat(shouldReuseInstalledServerPayload(serverWorkDir, marker)).isTrue()
    }

    @Test
    fun shouldReuseInstalledServerPayload_rejectsRealNeoForgeTargetJarWithoutUnixArgs() {
        val serverWorkDir = Files.createTempDirectory("mcgo-neoforge-real-target-missing-args")
        val targetJar = serverWorkDir.resolve("neoforge-1.21.4.jar")
        Files.write(targetJar, "managed-neoforge".toByteArray())
        Files.write(paperJarSha256File(targetJar), (sha256Hex(targetJar) + "\n").toByteArray())

        assertThat(shouldReuseInstalledServerPayload(serverWorkDir, targetJar)).isFalse()
    }

    @Test
    fun shouldReuseInstalledServerPayload_acceptsDirectManagedFabricJar() {
        val serverWorkDir = Files.createTempDirectory("mcgo-fabric-reuse-positive")
        val targetJar = serverWorkDir.resolve("fabric-1.21.4.jar")
        Files.write(targetJar, "verified-fabric".toByteArray())
        Files.write(paperJarSha256File(targetJar), (sha256Hex(targetJar) + "\n").toByteArray())

        assertThat(shouldReuseInstalledServerPayload(serverWorkDir, targetJar)).isTrue()
    }

    @Test
    fun shouldReuseInstalledServerPayload_rejectsQuiltWorkspaceWithoutLaunchJarEvenIfRootServerJarShaExists() {
        val serverWorkDir = Files.createTempDirectory("mcgo-quilt-missing-launch")
        val marker = serverWorkDir.resolve("quilt-1.21.4.jar")
        Files.write(marker, "installed\n".toByteArray())
        val serverJar = serverWorkDir.resolve("server.jar")
        Files.write(serverJar, "verified-payload".toByteArray())
        Files.write(paperJarSha256File(serverJar), (sha256Hex(serverJar) + "\n").toByteArray())

        assertThat(shouldReuseInstalledServerPayload(serverWorkDir, marker)).isFalse()
    }

    @Test
    fun shouldReuseInstalledServerPayload_rejectsRealQuiltTargetJarWithoutLaunchJar() {
        val serverWorkDir = Files.createTempDirectory("mcgo-quilt-real-target-missing-launch")
        val targetJar = serverWorkDir.resolve("quilt-1.21.4.jar")
        Files.write(targetJar, "managed-quilt".toByteArray())
        Files.write(paperJarSha256File(targetJar), (sha256Hex(targetJar) + "\n").toByteArray())

        assertThat(shouldReuseInstalledServerPayload(serverWorkDir, targetJar)).isFalse()
    }

    @Test
    fun resolveInstalledPayloadJar_prefersQuiltLaunchJarOverRealQuiltTargetJar() {
        val serverWorkDir = Files.createTempDirectory("mcgo-quilt-real-target")
        val targetJar = serverWorkDir.resolve("quilt-1.21.4.jar")
        Files.write(targetJar, byteArrayOf(7, 7, 7))
        val quiltLaunchJar = serverWorkDir.resolve("quilt-server-launch.jar")
        Files.write(quiltLaunchJar, byteArrayOf(1, 2, 3))

        assertThat(resolveInstalledPayloadJar(serverWorkDir, targetJar)).isEqualTo(quiltLaunchJar)
    }

    @Test
    fun shouldReuseInstalledServerPayload_rejectsMarkerWithoutRealPayloadJar() {
        val serverWorkDir = Files.createTempDirectory("mcgo-payload-reuse")
        val marker = serverWorkDir.resolve("forge-1.21.4.jar")
        Files.write(marker, "installed\n".toByteArray())
        Files.write(paperJarSha256File(marker), (sha256Hex(marker) + "\n").toByteArray())

        assertThat(shouldReuseInstalledServerPayload(serverWorkDir, marker)).isFalse()
    }

    @Test
    fun launchCompatibilityHelpers_liveInDedicatedHelperFile() {
        val runtimeSource = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerRuntime.kt")))
        val compatibilitySource = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/ManagedServerLaunchCompatibility.kt")))

        listOf(
            "fun requireManagedJavaHome(",
            "fun buildPaperJvmArguments(",
            "fun detectServerJnaVersion(",
            "fun isBundledAndroidJnaCompatibleWithServerJar(",
            "fun validateBundledAndroidJnaCompatibility(",
            "fun validateBundledAndroidJnaCompatibilityForLaunchTarget(",
        ).forEach { oldDefinition -> assertThat(runtimeSource).doesNotContain(oldDefinition) }
        assertThat(compatibilitySource).contains("readServerJnaVersion(targetJar).let")
        assertThat(compatibilitySource).contains("fun shouldReusePaperJar(targetJar: Path): Boolean")
        assertThat(compatibilitySource).contains("fun shouldReuseInstalledServerPayload(serverWorkDir: Path, targetJar: Path): Boolean")
        assertThat(compatibilitySource).contains("private const val BundledAndroidJnaVersion")
        assertThat(compatibilitySource).contains("fun buildPaperJvmArguments(server: ServerCardState, javaHome: Path? = null): List<String>")
        assertThat(compatibilitySource).contains("-DPaper.IgnoreJavaVersion=true")
        assertThat(compatibilitySource).contains("META-INF/libraries.list")
        assertThat(compatibilitySource).contains("JNA ${'$'}serverJnaVersion")
    }

    @Test
    fun validateBundledAndroidJnaCompatibilityForLaunchTarget_usesResolvedPayloadJar() {
        val serverWorkDir = Files.createTempDirectory("mcgo-jna-payload")
        val marker = serverWorkDir.resolve("forge-1.21.4.jar")
        Files.write(marker, "installed\n".toByteArray())
        val payloadJar = createServerJarWithLibrariesList(
            """
            deadbeef\tnet.java.dev.jna:jna:5.19.0\tnet/java/dev/jna/jna/5.19.0/jna-5.19.0.jar
            """.trimIndent(),
        )
        val installedJar = serverWorkDir.resolve("libraries/net/minecraftforge/forge/1.21.4-54.1.16/forge-1.21.4-54.1.16-server.jar")
        Files.createDirectories(installedJar.parent)
        Files.copy(payloadJar, installedJar)
        val server = createForgeServer("Forge服", "1.21.4", maxPlayers = 20, memoryMb = 3072, port = 25569)

        val error = assertFailsWith<JavaRuntimeInstallException> {
            validateBundledAndroidJnaCompatibilityForLaunchTarget(server, serverWorkDir, marker)
        }

        assertThat(error).hasMessageThat().contains("JNA 5.19.0")
    }

    @Test
    fun artifactVersionHelpers_compareNumericallyAndMapNeoForgePrefixes() {
        assertThat(compareArtifactVersions("1.21.4-54.1.16", "1.21.4-54.1.8")).isGreaterThan(0)
        assertThat(compareArtifactVersions("21.4.125", "21.4.9")).isGreaterThan(0)
        assertThat(compareArtifactVersions("26.1.2.43-beta", "26.1.2.9-beta")).isGreaterThan(0)
        assertThat(neoforgeArtifactPrefixForMinecraftVersion("1.21.4")).isEqualTo("21.4")
        assertThat(neoforgeArtifactPrefixForMinecraftVersion("1.20.6")).isEqualTo("20.6")
        assertThat(neoforgeArtifactPrefixForMinecraftVersion("26.1.2")).isEqualTo("26.1.2")
    }

    @Test
    fun installerChecksumParsers_extractExpectedHashes() {
        val neoforgeIndex = """
            <html><body>
            <a href="./neoforge-21.4.157-installer.jar.sha1">neoforge-21.4.157-installer.jar.sha1</a>
            <a href="./neoforge-21.4.157-installer.jar.sha256">neoforge-21.4.157-installer.jar.sha256</a>
            </body></html>
        """.trimIndent()
        val quiltInstallerMeta = """
            [{"version":"0.12.1","url":"https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/0.12.1/quilt-installer-0.12.1.jar","hashes":{"sha1":"77f7053a2e6a83f902c8b8ad3ca0b71a84893455","sha256":"8b716edc692a2fa1fb78dbc2f432643be1bc6c867e5605f36f691f44257120ca"}}]
        """.trimIndent()

        assertThat(
            resolveNeoForgeInstallerChecksumUrl(
                "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.4.157/neoforge-21.4.157-installer.jar",
                neoforgeIndex,
                algorithm = "sha256",
            ),
        ).isEqualTo("https://maven.neoforged.net/releases/net/neoforged/neoforge/21.4.157/neoforge-21.4.157-installer.jar.sha256")
        assertThat(
            resolveNeoForgeInstallerChecksumUrl(
                "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.4.157/neoforge-21.4.157-installer.jar",
                neoforgeIndex,
                algorithm = "sha1",
            ),
        ).isEqualTo("https://maven.neoforged.net/releases/net/neoforged/neoforge/21.4.157/neoforge-21.4.157-installer.jar.sha1")
        assertThat(parseQuiltInstallerSha256(quiltInstallerMeta, "0.12.1")).isEqualTo("8b716edc692a2fa1fb78dbc2f432643be1bc6c867e5605f36f691f44257120ca")
        assertThat(parseQuiltInstallerSha1(quiltInstallerMeta, "0.12.1")).isEqualTo("77f7053a2e6a83f902c8b8ad3ca0b71a84893455")
    }
    @Test
    fun managedPaperServerPaths_useAppPrivateDirectoryAndLogFile() {
        val filesDir = Files.createTempDirectory("mcgo-paper-paths")

        val serverDir = managedPaperServerDirectory(filesDir, "server-demo")
        val logFile = managedPaperServerLogFile(filesDir, "server-demo")

        assertThat(serverDir).isEqualTo(filesDir.resolve("servers/server-demo"))
        assertThat(logFile).isEqualTo(filesDir.resolve("servers/server-demo/logs/mcgo-latest.log"))
    }

    @Test
    fun requireManagedJavaHome_returnsJavaHomeOnlyWhenBinaryExistsAndExecutable() {
        val filesDir = Files.createTempDirectory("mcgo-managed-java-ready")
        val javaHome = filesDir.resolve("jre/java-21")
        val javaBinary = javaHome.resolve("bin/java")
        Files.createDirectories(javaBinary.parent)
        Files.write(javaBinary, byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
        javaBinary.toFile().setExecutable(true, false)

        assertThat(requireManagedJavaHome(filesDir, 21)).isEqualTo(javaHome)
        assertThat(isRuntimeReady(filesDir, 21)).isTrue()
    }

    @Test
    fun requireManagedJavaHome_rejectsMissingJavaBinary() {
        val filesDir = Files.createTempDirectory("mcgo-managed-java-missing")

        val error = kotlin.runCatching { requireManagedJavaHome(filesDir, 17) }.exceptionOrNull()

        assertThat(error).isInstanceOf(JavaRuntimeInstallException::class.java)
        assertThat(error).hasMessageThat().contains("Java 17")
        assertThat(isRuntimeReady(filesDir, 17)).isFalse()
    }

    @Test
    fun paperDownloadUserAgentUsesCurrentVersion() {
        assertThat(McGoUserAgent).isEqualTo("MC-GO/${BuildConfig.VERSION_NAME}")
        assertThat(PaperDownloadUserAgent).isEqualTo(McGoUserAgent)
    }

    @Test
    fun scaledProgressReporterMapsInnerDownloadRange() {
        val events = mutableListOf<Int>()
        val reporter = scaledPaperDownloadProgressReporter(20, 80) { events += it }

        reporter(0)
        reporter(50)
        reporter(100)

        assertThat(events).containsExactly(20, 50, 80).inOrder()
    }

    @Test
    fun shouldReusePaperJar_requiresMatchingRecordedSha256() {
        val tempDir = Files.createTempDirectory("mcgo-paper-jar-reuse")
        val missing = tempDir.resolve("missing.jar")
        val empty = tempDir.resolve("empty.jar")
        val valid = tempDir.resolve("paper.jar")
        Files.write(empty, byteArrayOf())
        Files.write(valid, "verified-paper".toByteArray())

        assertThat(shouldReusePaperJar(missing)).isFalse()
        assertThat(shouldReusePaperJar(empty)).isFalse()
        assertThat(shouldReusePaperJar(valid)).isFalse()
        Files.write(paperJarSha256File(valid), "deadbeef\n".toByteArray())
        assertThat(shouldReusePaperJar(valid)).isFalse()
        Files.write(paperJarSha256File(valid), (sha256Hex(valid) + "\n").toByteArray())
        assertThat(shouldReusePaperJar(valid)).isTrue()
    }

    @Test
    fun shouldReusePaperJar_rejectsRecordedJarWhenBundledAndroidJnaIsTooOldForServer() {
        val jarPath = createServerJarWithLibrariesList(
            """
            deadbeef\tnet.java.dev.jna:jna:5.19.0\tnet/java/dev/jna/jna/5.19.0/jna-5.19.0.jar
            """.trimIndent(),
        )
        Files.write(paperJarSha256File(jarPath), (sha256Hex(jarPath) + "\n").toByteArray())

        assertThat(shouldReusePaperJar(jarPath)).isFalse()
    }

    @Test
    fun detectServerJnaVersion_readsVersionFromPaperLibrariesList() {
        val jarPath = createServerJarWithLibrariesList(
            """
            deadbeef\tnet.java.dev.jna:jna:5.18.1\tnet/java/dev/jna/jna/5.18.1/jna-5.18.1.jar
            cafebabe\tcom.github.oshi:oshi-core:6.9.0\tcom/github/oshi/oshi-core/6.9.0/oshi-core-6.9.0.jar
            """.trimIndent(),
        )

        assertThat(detectServerJnaVersion(jarPath)).isEqualTo("5.18.1")
    }

    @Test
    fun detectServerJnaVersion_boundsLibrariesListMetadataProbe() {
        val source = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/ManagedServerLaunchCompatibility.kt")))
        val detectionSlice = source
            .substringAfter("fun detectServerJnaVersion(")
            .substringBefore("fun isBundledAndroidJnaCompatibleWithServerJar(")

        assertThat(detectionSlice).contains("MaxServerLibrariesListProbeBytes")
        assertThat(detectionSlice).contains("readZipEntryTextBounded(input, MaxServerLibrariesListProbeBytes)")
        assertThat(detectionSlice).doesNotContain("bufferedReader()")
        assertThat(detectionSlice).doesNotContain("lineSequence()")
    }

    @Test
    fun shouldReusePaperJar_rejectsJarWithOversizedLibrariesListMetadata() {
        val jarPath = createServerJarWithLibrariesList("x".repeat(MaxServerLibrariesListProbeBytes + 1))
        Files.write(paperJarSha256File(jarPath), (sha256Hex(jarPath) + "\n").toByteArray())

        assertThat(detectServerJnaVersion(jarPath)).isNull()
        assertThat(shouldReusePaperJar(jarPath)).isFalse()
    }

    @Test
    fun validateBundledAndroidJnaCompatibility_rejectsOversizedLibrariesListMetadata() {
        val jarPath = createServerJarWithLibrariesList("x".repeat(MaxServerLibrariesListProbeBytes + 1))
        val server = createPaperServer("兼容服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val error = assertFailsWith<JavaRuntimeInstallException> {
            validateBundledAndroidJnaCompatibility(server, jarPath)
        }

        assertThat(error).hasMessageThat().contains("libraries.list 元数据过大")
    }

    @Test
    fun validateBundledAndroidJnaCompatibility_allowsSameOrOlderServerJnaMinor() {
        val jarPath = createServerJarWithLibrariesList(
            """
            deadbeef\tnet.java.dev.jna:jna:5.17.0\tnet/java/dev/jna/jna/5.17.0/jna-5.17.0.jar
            """.trimIndent(),
        )
        val server = createPaperServer("兼容服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25565)

        validateBundledAndroidJnaCompatibility(server, jarPath)
    }

    @Test
    fun validateBundledAndroidJnaCompatibility_rejectsNewerServerJnaMinorWithActionableMessage() {
        val jarPath = createServerJarWithLibrariesList(
            """
            deadbeef\tnet.java.dev.jna:jna:5.19.0\tnet/java/dev/jna/jna/5.19.0/jna-5.19.0.jar
            """.trimIndent(),
        )
        val server = createPaperServer("兼容服", "26.1.2", maxPlayers = 20, memoryMb = 2048, port = 25565)

        val error = assertFailsWith<JavaRuntimeInstallException> {
            validateBundledAndroidJnaCompatibility(server, jarPath)
        }

        assertThat(error).hasMessageThat().contains("JNA 5.19.0")
        assertThat(error).hasMessageThat().contains("5.18.1")
        assertThat(error).hasMessageThat().contains("更新 MC-GO")
    }

    private fun createServerJarWithLibrariesList(librariesList: String): java.nio.file.Path {
        val jarPath = Files.createTempFile("mcgo-paper-libraries", ".jar")
        ZipOutputStream(Files.newOutputStream(jarPath)).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/libraries.list"))
            zip.write(librariesList.replace("\\t", "\t").toByteArray())
            zip.closeEntry()
        }
        return jarPath
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
