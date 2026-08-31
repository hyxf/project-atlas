package com.github.hyxf.projectmanager.feature.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.github.hyxf.projectmanager.infrastructure.persistence.ProjectJsonStore

class ProjectManagerToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ProjectManagerPanel(project)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(panel, "", false))
        ApplicationManager.getApplication().executeOnPooledThread {
            service<ProjectJsonStore>().forceReload()
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) panel.reloadFromStore()
            }
        }
    }
}
