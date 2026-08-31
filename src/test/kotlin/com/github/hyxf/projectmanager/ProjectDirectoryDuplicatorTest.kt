package com.github.hyxf.projectmanager

import com.github.hyxf.projectmanager.infrastructure.filesystem.ProjectDirectoryDuplicator
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectDirectoryDuplicatorTest {
    @Test
    fun `duplicates project contents into the next indexed sibling directory`() {
        val parent = Files.createTempDirectory("project-duplicator-test")
        val source = Files.createDirectory(parent.resolve("project"))
        source.resolve("README.md").writeText("content")
        Files.createDirectories(source.resolve("src/main"))
        source.resolve("src/main/App.kt").writeText("class App")
        Files.createDirectory(parent.resolve("project-1"))
        Files.createDirectory(parent.resolve("project-2"))

        val duplicated = ProjectDirectoryDuplicator.duplicate(source)

        assertEquals(parent.resolve("project-3"), duplicated)
        assertEquals("content", duplicated.resolve("README.md").readText())
        assertEquals("class App", duplicated.resolve("src/main/App.kt").readText())
    }

    @Test
    fun `rejects a missing source directory without creating a duplicate`() {
        val parent = Files.createTempDirectory("project-duplicator-missing-test")

        assertFailsWith<IllegalArgumentException> {
            ProjectDirectoryDuplicator.duplicate(parent.resolve("missing"))
        }
        assertTrue(Files.notExists(parent.resolve("missing-1")))
    }

    @Test
    fun `skips unix sockets while duplicating project contents`() {
        val shortTempRoot = Path.of("/tmp")
        if (!Files.isDirectory(shortTempRoot)) return
        val parent = Files.createTempDirectory(shortTempRoot, "pds")
        val source = Files.createDirectory(parent.resolve("project"))
        source.resolve("README.md").writeText("content")
        val socket = source.resolve(".port")
        val channel = runCatching { ServerSocketChannel.open(StandardProtocolFamily.UNIX) }.getOrNull() ?: return

        channel.use {
            it.bind(UnixDomainSocketAddress.of(socket))

            val duplicated = ProjectDirectoryDuplicator.duplicate(source)

            assertEquals("content", duplicated.resolve("README.md").readText())
            assertFalse(Files.exists(duplicated.resolve(".port")))
        }
    }

    @Test
    fun `removes partial target when duplication fails`() {
        val parent = Files.createTempDirectory("project-duplicator-failure-test")
        val source = Files.createDirectory(parent.resolve("project"))
        val unreadable = source.resolve("unreadable.txt")
        unreadable.writeText("content")
        if (!Files.getFileStore(unreadable).supportsFileAttributeView("posix")) return

        Files.setPosixFilePermissions(unreadable, emptySet<PosixFilePermission>())
        try {
            assertFailsWith<java.io.IOException> { ProjectDirectoryDuplicator.duplicate(source) }
            assertFalse(Files.exists(parent.resolve("project-1")))
        } finally {
            Files.setPosixFilePermissions(unreadable, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }
    }
}
