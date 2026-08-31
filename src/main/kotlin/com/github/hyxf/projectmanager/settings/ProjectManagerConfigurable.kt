package com.github.hyxf.projectmanager.settings

import com.github.hyxf.projectmanager.infrastructure.persistence.ProjectJsonStore
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class ProjectManagerConfigurable : Configurable {
    private val openMode = ComboBox(ProjectManagerSettings.OpenMode.values())
    private var panel: JPanel? = null

    override fun getDisplayName() = "Project Atlas"
    override fun createComponent(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("Default open mode:"), openMode)
        .addComponentFillVertically(JPanel(), 0).panel.also { panel = it }

    override fun isModified(): Boolean = openMode.selectedItem != service<ProjectManagerSettings>().state.defaultOpenMode

    override fun apply() {
        val settings = service<ProjectManagerSettings>()
        val value = ProjectManagerSettings.Data(
            defaultOpenMode = openMode.selectedItem as ProjectManagerSettings.OpenMode,
            sortBy = settings.state.sortBy,
            viewMode = settings.state.viewMode,
            listFilter = settings.state.listFilter,
        )
        ProgressManager.getInstance().run(object : Task.Modal(null, "Saving Project Atlas Settings", false) {
            override fun run(indicator: ProgressIndicator) = settings.update(value)
        })
    }

    override fun reset() {
        ProgressManager.getInstance().run(object : Task.Modal(null, "Loading Project Atlas Settings", false) {
            override fun run(indicator: ProgressIndicator) = service<ProjectJsonStore>().forceReload()
        })
        service<ProjectManagerSettings>().state.let {
            openMode.selectedItem = it.defaultOpenMode
        }
    }

    override fun disposeUIResources() { panel = null }
}
