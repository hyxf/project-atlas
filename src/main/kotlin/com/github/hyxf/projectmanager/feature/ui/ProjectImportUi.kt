package com.github.hyxf.projectmanager.feature.ui

import com.github.hyxf.projectmanager.feature.project.ProjectImportOutcome
import com.github.hyxf.projectmanager.feature.project.ProjectImportSummary
import com.github.hyxf.projectmanager.feature.project.ProjectManagerService
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

object ProjectImportUi {
    fun show(project: Project, onImported: () -> Unit) {
        val manager = service<ProjectManagerService>()
        val dialog = ProjectImportPreviewDialog(project, manager)
        if (!dialog.showAndGet()) return
        var summary: ProjectImportSummary? = null
        var importError: Throwable? = null
        ProgressManager.getInstance().run(object : Task.Modal(project, "Importing Local Projects", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                runCatching { manager.importProjects(dialog.requests, dialog.shouldUpdateExisting) }
                    .onSuccess { summary = it }
                    .onFailure { importError = it }
            }
        })
        importError?.let {
            ProjectUiSupport.report(project, "Import projects", it)
            return
        }
        val completed = summary ?: return
        onImported()
        val failed = completed.results.filter { it.outcome == ProjectImportOutcome.FAILED }
        if (failed.isNotEmpty()) {
            val details = failed.joinToString("\n") { result ->
                "${result.path}: ${result.message.ifBlank { "Unknown error" }}"
            }
            Messages.showWarningDialog(
                project,
                "Some projects couldn't be imported:\n\n$details",
                "Import Completed with Errors",
            )
        }
        ProjectUiSupport.notify(
            project,
            "Added ${completed.count(ProjectImportOutcome.ADDED)} · " +
                "Updated ${completed.count(ProjectImportOutcome.UPDATED)} · " +
                "Skipped ${completed.count(ProjectImportOutcome.SKIPPED)} · " +
                "Failed ${completed.count(ProjectImportOutcome.FAILED)}",
            if (completed.count(ProjectImportOutcome.FAILED) > 0) NotificationType.WARNING else NotificationType.INFORMATION,
        )
    }

}
