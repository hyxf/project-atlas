package com.github.hyxf.projectmanager.feature.action

import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.github.hyxf.projectmanager.feature.project.ProjectManagerService
import com.github.hyxf.projectmanager.feature.ui.ProjectEditDialog
import com.github.hyxf.projectmanager.feature.ui.ProjectManagerPanel
import com.github.hyxf.projectmanager.feature.ui.ProjectSearchDialog
import com.github.hyxf.projectmanager.feature.ui.ProjectUiSupport
import com.github.hyxf.projectmanager.feature.ui.ProjectImportUi
import com.github.hyxf.projectmanager.feature.ui.ProjectPathStatusCache
import com.github.hyxf.projectmanager.infrastructure.persistence.ProjectJsonStore
import com.github.hyxf.projectmanager.settings.ProjectManagerSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.nio.file.Path
import javax.swing.JList

abstract class ProjectManagerAction : AnAction(), DumbAware {
    protected val service get() = service<ProjectManagerService>()
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    protected fun refresh(project: Project?) {
        project ?: return
        val content = ToolWindowManager.getInstance(project).getToolWindow("Project Atlas")
            ?.contentManager?.contents?.firstOrNull()?.component
        (content as? ProjectManagerPanel)?.refresh()
    }
}

class SaveCurrentProjectAction : ProjectManagerAction() {
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = e.project?.basePath != null }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val path = project.basePath?.let(Path::of) ?: return
        val existing = service.findByPath(path)
        val dialog = ProjectEditDialog(project, path, existing, allowPathSelection = false)
        if (dialog.showAndGet()) ProjectUiSupport.runInBackground(project, "Save current project", {
            service.saveProject(dialog.projectName, path, dialog.tags, dialog.favorite)
            service.updateLastOpened(path)
        }) { refresh(project) }
    }
}

class AddProjectAction : ProjectManagerAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val path = ProjectUiSupport.chooseDirectory(e.project) ?: return
        if (service.findByPath(path) != null) {
            ProjectUiSupport.report(e.project, "Add project", IllegalArgumentException("Project already exists"))
            return
        }
        val dialog = ProjectEditDialog(e.project, path)
        if (dialog.showAndGet()) ProjectUiSupport.runInBackground(e.project, "Add project", {
            service.addProject(dialog.projectName, dialog.projectPath, dialog.tags, dialog.favorite)
        }) { refresh(e.project) }
    }
}

class ImportProjectsAction : ProjectManagerAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ProjectImportUi.show(project) { refresh(project) }
    }
}

open class QuickOpenProjectAction(private val newWindow: Boolean = false) : ProjectManagerAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ProjectUiSupport.runInBackground(e.project, "Load projects", {
            service<ProjectJsonStore>().forceReload()
            service.projects()
        }) { projects -> showChooser(e, service.sortProjects(projects)) }
    }

    private fun showChooser(e: AnActionEvent, projects: List<ProjectItem>) {
        if (projects.isEmpty()) {
            ProjectUiSupport.notify(e.project, "No saved projects. Add or save a project first.", com.intellij.notification.NotificationType.INFORMATION)
            return
        }
        JBPopupFactory.getInstance().createPopupChooserBuilder(projects)
            .setTitle(if (newWindow) "Open Project in New Window" else "Open Project")
            .setRenderer(object : ColoredListCellRenderer<ProjectItem>() {
                override fun customizeCellRenderer(list: JList<out ProjectItem>, value: ProjectItem?, index: Int,
                                                   selected: Boolean, hasFocus: Boolean) {
                    value ?: return
                    append(if (value.favorite) "★ ${value.name}" else value.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    if (ProjectPathStatusCache.isDirectory(value.path) == false) {
                        append("  Missing", SimpleTextAttributes.ERROR_ATTRIBUTES)
                    } else if (ProjectPathStatusCache.isDirectory(value.path) == null) {
                        ProjectPathStatusCache.refresh(value.path) { list.repaint() }
                    }
                    append("  ${value.path}", SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, JBColor.GRAY))
                    if (value.tags.isNotEmpty()) append("  ${value.tags.sorted().joinToString(" · ")}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }
            })
            .setNamerForFiltering { "${it.name} ${it.path} ${it.tags.joinToString(" ")}" }
            .setAdText("Enter: open  ·  Esc: cancel")
            .setItemChosenCallback {
                val current = e.project?.basePath?.let(Path::of)?.toAbsolutePath()?.normalize() == it.path.toAbsolutePath().normalize()
                if (current) {
                    ProjectUiSupport.notify(e.project, "${it.name} is already open.", com.intellij.notification.NotificationType.INFORMATION)
                } else if (ProjectUiSupport.open(it, e.project, newWindow)) {
                    ProjectUiSupport.runInBackground(e.project, "Update recent project", { service.updateLastOpened(it.path) })
                }
                refresh(e.project)
            }.createPopup().showInFocusCenter()
    }
}

class QuickOpenProjectInNewWindowAction : QuickOpenProjectAction(true)

class SearchProjectsAction : ProjectManagerAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ProjectUiSupport.runInBackground(project, "Load projects", {
            service<ProjectJsonStore>().forceReload()
        }) {
            ProjectSearchDialog(project) { item ->
                val currentPath = project.basePath?.let(Path::of)?.toAbsolutePath()?.normalize()
                if (currentPath == item.path.toAbsolutePath().normalize()) return@ProjectSearchDialog
                val newWindow = service<ProjectManagerSettings>().state.defaultOpenMode ==
                    ProjectManagerSettings.OpenMode.NEW_WINDOW
                if (ProjectUiSupport.open(item, project, newWindow)) {
                    ProjectUiSupport.runInBackground(project, "Update recent project", {
                        service.updateLastOpened(item.path)
                    }) { refresh(project) }
                }
            }.show()
        }
    }
}

class RefreshProjectsAction : ProjectManagerAction() {
    override fun actionPerformed(e: AnActionEvent) = refresh(e.project)
}

class OpenProjectManagerAction : ProjectManagerAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { ToolWindowManager.getInstance(it).getToolWindow("Project Atlas")?.show() }
    }
}
