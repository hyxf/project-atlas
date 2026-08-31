package com.github.hyxf.projectmanager.feature.project

import java.nio.file.Path

data class ProjectImportRequest(
    val path: Path,
    val name: String,
    val tags: Set<String>,
    val favorite: Boolean,
)

enum class ProjectImportOutcome { ADDED, UPDATED, SKIPPED, FAILED }

data class ProjectImportResult(
    val path: Path,
    val outcome: ProjectImportOutcome,
    val message: String = "",
)

data class ProjectImportSummary(val results: List<ProjectImportResult>) {
    fun count(outcome: ProjectImportOutcome) = results.count { it.outcome == outcome }
}
