package com.github.hyxf.projectmanager.infrastructure.filesystem

import com.intellij.openapi.progress.ProgressIndicator
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

data class DiscoveredProject(val path: Path, val recognized: Boolean)

object ProjectDirectoryScanner {
    private val markers = setOf(
        ".idea", ".git", "pom.xml", "build.gradle", "build.gradle.kts",
        "settings.gradle", "settings.gradle.kts", "package.json", "Cargo.toml", "go.mod",
    )
    private val skippedDirectories = setOf(".git", ".idea", "node_modules", ".gradle", "build", "out", "target")

    fun scan(root: Path, maxDepth: Int, indicator: ProgressIndicator? = null): List<DiscoveredProject> {
        val normalizedRoot = ProjectPaths.normalize(root)
        if (!Files.isDirectory(normalizedRoot)) return emptyList()
        val found = mutableListOf<DiscoveredProject>()
        Files.walkFileTree(normalizedRoot, emptySet(), maxDepth + 1, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                indicator?.checkCanceled()
                val directoryName = dir.fileName?.toString().orEmpty()
                if (dir != normalizedRoot && (directoryName.startsWith('.') || directoryName in skippedDirectories)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                indicator?.text2 = dir.toString()
                if (isProjectDirectory(dir)) found += DiscoveredProject(ProjectPaths.normalize(dir), true)
                return FileVisitResult.CONTINUE
            }
        })
        return found.distinctBy(DiscoveredProject::path).sortedBy { it.path.toString().lowercase() }
    }

    fun selected(paths: List<Path>): List<DiscoveredProject> = paths
        .filter(Files::isDirectory)
        .map { DiscoveredProject(ProjectPaths.normalize(it), isProjectDirectory(it)) }
        .distinctBy(DiscoveredProject::path)

    fun isProjectDirectory(path: Path): Boolean = markers.any { Files.exists(path.resolve(it)) }
}
