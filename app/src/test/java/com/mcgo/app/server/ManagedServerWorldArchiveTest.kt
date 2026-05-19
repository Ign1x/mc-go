package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ManagedServerWorldArchiveTest {
    @Test
    fun detectImportedWorldDirectory_prefersSingleTopLevelWorldFolder() {
        val root = Files.createTempDirectory("mcgo-world-import")
        val worldDir = root.resolve("my_world")
        Files.createDirectories(worldDir)
        Files.write(worldDir.resolve("level.dat"), byteArrayOf(1, 2, 3))

        val detected = detectImportedWorldDirectory(root)

        assertThat(detected).isEqualTo(worldDir)
    }

    @Test
    fun detectImportedWorldDirectory_acceptsRootLevelWorldContents() {
        val root = Files.createTempDirectory("mcgo-world-import-flat")
        Files.write(root.resolve("level.dat"), byteArrayOf(1, 2, 3))

        val detected = detectImportedWorldDirectory(root)

        assertThat(detected).isEqualTo(root)
    }

    @Test
    fun exportManagedServerWorldArchiveToStream_skipsSymlinkedWorldFiles() {
        val root = Files.createTempDirectory("mcgo-world-export")
        val worldDir = root.resolve("world")
        Files.createDirectories(worldDir)
        Files.write(worldDir.resolve("level.dat"), byteArrayOf(1, 2, 3))
        val outsideSecret = Files.createTempFile("mcgo-world-secret", ".txt")
        Files.write(outsideSecret, "outside-secret".toByteArray())
        val symlink = worldDir.resolve("leaked.txt")
        if (runCatching { Files.createSymbolicLink(symlink, outsideSecret) }.isFailure) return
        val archiveOutput = ByteArrayOutputStream()

        exportManagedServerWorldArchiveToStream(worldDir, archiveOutput)

        val entries = unzipEntries(archiveOutput.toByteArray())
        assertThat(entries.keys).contains("world/level.dat")
        assertThat(entries.keys).doesNotContain("world/leaked.txt")
        assertThat(entries.values.map { String(it) }).doesNotContain("outside-secret")
    }

    @Test
    fun exportManagedServerWorldArchiveToStream_skipsSymlinkedWorldDirectories() {
        val root = Files.createTempDirectory("mcgo-world-export-dir")
        val worldDir = root.resolve("world")
        val outsideDir = Files.createTempDirectory("mcgo-world-secret-dir")
        Files.createDirectories(worldDir)
        Files.write(worldDir.resolve("level.dat"), byteArrayOf(1, 2, 3))
        Files.write(outsideDir.resolve("secret.txt"), "outside-dir-secret".toByteArray())
        val symlink = worldDir.resolve("linked-dir")
        if (runCatching { Files.createSymbolicLink(symlink, outsideDir) }.isFailure) return
        val archiveOutput = ByteArrayOutputStream()

        exportManagedServerWorldArchiveToStream(worldDir, archiveOutput)

        val entries = unzipEntries(archiveOutput.toByteArray())
        assertThat(entries.keys).contains("world/level.dat")
        assertThat(entries.keys).doesNotContain("world/linked-dir/")
        assertThat(entries.keys).doesNotContain("world/linked-dir/secret.txt")
        assertThat(entries.values.map { String(it) }).doesNotContain("outside-dir-secret")
    }

    @Test
    fun exportManagedServerWorldArchiveToStream_rejectsSymlinkedWorldRoot() {
        val realWorldDir = Files.createTempDirectory("mcgo-world-real")
        Files.write(realWorldDir.resolve("level.dat"), byteArrayOf(1, 2, 3))
        val symlinkRoot = Files.createTempDirectory("mcgo-world-link-parent").resolve("world-link")
        if (runCatching { Files.createSymbolicLink(symlinkRoot, realWorldDir) }.isFailure) return
        val archiveOutput = ByteArrayOutputStream()

        val error = assertFailsWith<IllegalArgumentException> {
            exportManagedServerWorldArchiveToStream(symlinkRoot, archiveOutput)
        }

        assertThat(error.message).contains("世界存档目录不存在")
    }

    @Test
    fun exportManagedServerWorldArchive_usesNoFollowLinksForWorldEntries() {
        val source = String(
            Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/ManagedServerWorldArchive.kt")),
        )
        val exportSlice = source
            .substringAfter("internal fun exportManagedServerWorldArchiveToStream(")
            .substringBefore("fun importManagedServerWorldArchive(")

        assertThat(exportSlice).contains("Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)")
        assertThat(exportSlice).contains("Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)")
        assertThat(exportSlice).contains("Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)")
        assertThat(exportSlice).doesNotContain("Files.isDirectory(path))")
        assertThat(exportSlice).doesNotContain("Files.isRegularFile(path))")
    }

    private fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = ByteArrayOutputStream()
                if (!entry.isDirectory) zip.copyTo(output)
                entries[entry.name] = output.toByteArray()
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("Project root not found")
}
