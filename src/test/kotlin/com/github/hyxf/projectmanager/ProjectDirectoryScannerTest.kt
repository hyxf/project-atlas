package com.github.hyxf.projectmanager

import com.github.hyxf.projectmanager.infrastructure.filesystem.ProjectDirectoryScanner
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectDirectoryScannerTest {
    @Test
    fun `scanner finds nested projects within depth and skips generated directories`() {
        val root = Files.createTempDirectory("project-import-scan-test")
        val direct = Files.createDirectory(root.resolve("direct"))
        Files.createFile(direct.resolve("pom.xml"))
        val nestedParent = Files.createDirectory(root.resolve("workspace"))
        val nested = Files.createDirectory(nestedParent.resolve("nested"))
        Files.createFile(nested.resolve("package.json"))
        val generated = Files.createDirectory(root.resolve("node_modules"))
        Files.createFile(generated.resolve("package.json"))

        val shallow = ProjectDirectoryScanner.scan(root, 1)
        assertTrue(shallow.any { it.path == direct.toAbsolutePath().normalize() })
        assertFalse(shallow.any { it.path == nested.toAbsolutePath().normalize() })

        val deep = ProjectDirectoryScanner.scan(root, 3)
        assertTrue(deep.any { it.path == nested.toAbsolutePath().normalize() })
        assertFalse(deep.any { it.path == generated.toAbsolutePath().normalize() })
    }

    @Test
    fun `selected directories retain unrecognized projects for manual import`() {
        val directory = Files.createTempDirectory("project-import-unrecognized-test")
        val selected = ProjectDirectoryScanner.selected(listOf(directory))
        assertEquals(1, selected.size)
        assertFalse(selected.single().recognized)
    }
}
