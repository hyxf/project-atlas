package com.github.hyxf.projectmanager.feature.recent

import com.github.hyxf.projectmanager.feature.project.ProjectManagerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.nio.file.Path

class ProjectOpenedActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.basePath?.let { service<ProjectManagerService>().updateLastOpened(Path.of(it)) }
    }
}
