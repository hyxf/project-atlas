package com.github.hyxf.projectmanager.infrastructure.filesystem

import java.awt.Desktop
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

object ProjectDirectoryDeletion {
    fun deleteDirectly(path: Path) {
        val normalized = requireProjectDirectory(path)
        Files.walkFileTree(normalized, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun moveToTrash(path: Path) {
        val normalized = requireProjectDirectory(path)
        check(Desktop.isDesktopSupported()) {
            "System Trash is unavailable. Delete the directory manually, then use Remove from Project Atlas."
        }
        val desktop = Desktop.getDesktop()
        check(desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            "System Trash is unavailable. Delete the directory manually, then use Remove from Project Atlas."
        }
        check(desktop.moveToTrash(normalized.toFile())) {
            "Could not move the project to Trash. Close programs using it, check directory permissions, and try again."
        }
    }

    private fun requireProjectDirectory(path: Path): Path {
        val normalized = path.toAbsolutePath().normalize()
        require(normalized.parent != null) { "The filesystem root cannot be deleted" }
        require(Files.isDirectory(normalized)) { "Project path is missing or is not a directory: $normalized" }
        return normalized
    }
}
