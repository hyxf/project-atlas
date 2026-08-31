package com.github.hyxf.projectmanager.infrastructure.filesystem

import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

object ProjectDirectoryDuplicator {
    fun duplicate(source: Path): Path {
        val normalizedSource = source.toAbsolutePath().normalize()
        require(Files.isDirectory(normalizedSource)) { "Project path is missing or is not a directory: $normalizedSource" }
        val parent = requireNotNull(normalizedSource.parent) { "Project directory must have a parent: $normalizedSource" }
        val baseName = normalizedSource.fileName.toString()
        val target = createTargetDirectory(parent, baseName)

        try {
            Files.walkFileTree(normalizedSource, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir != normalizedSource) Files.createDirectory(target.resolve(normalizedSource.relativize(dir)))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isOther) return FileVisitResult.CONTINUE
                    Files.copy(
                        file,
                        target.resolve(normalizedSource.relativize(file)),
                        StandardCopyOption.COPY_ATTRIBUTES,
                        LinkOption.NOFOLLOW_LINKS,
                    )
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (error: Exception) {
            runCatching { deleteRecursively(target) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
        return target
    }

    private fun createTargetDirectory(parent: Path, baseName: String): Path {
        var suffix = 1
        while (true) {
            val candidate = parent.resolve("$baseName-$suffix")
            try {
                return Files.createDirectory(candidate)
            } catch (_: FileAlreadyExistsException) {
                suffix++
            }
        }
    }

    private fun deleteRecursively(root: Path) {
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
