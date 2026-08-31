package com.github.hyxf.projectmanager.feature.ui

import com.github.hyxf.projectmanager.infrastructure.filesystem.ProjectPaths
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

object ProjectPathStatusCache {
    private val statuses = ConcurrentHashMap<Path, Boolean>()
    private val pending = ConcurrentHashMap.newKeySet<Path>()

    fun isDirectory(path: Path): Boolean? = statuses[ProjectPaths.normalize(path)]

    fun refresh(path: Path, onUpdated: () -> Unit = {}) {
        val normalized = ProjectPaths.normalize(path)
        if (!pending.add(normalized)) return
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                statuses[normalized] = Files.isDirectory(normalized)
            } finally {
                pending.remove(normalized)
                onUpdated()
            }
        }
    }

    fun invalidate(path: Path) {
        statuses.remove(ProjectPaths.normalize(path))
    }
}
