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
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class ProjectManagerConfigurable : Configurable {
    private val openMode = ComboBox(ProjectManagerSettings.OpenMode.values())
    private val tagProjectSpacing = JSpinner(SpinnerNumberModel(
        ProjectManagerSettings.DEFAULT_TAG_PROJECT_SPACING,
        ProjectManagerSettings.MIN_TAG_PROJECT_SPACING,
        ProjectManagerSettings.MAX_TAG_PROJECT_SPACING,
        1,
    ))
    private val listProjectSpacing = JSpinner(SpinnerNumberModel(
        ProjectManagerSettings.DEFAULT_LIST_PROJECT_SPACING,
        ProjectManagerSettings.MIN_LIST_PROJECT_SPACING,
        ProjectManagerSettings.MAX_LIST_PROJECT_SPACING,
        1,
    ))
    private var panel: JPanel? = null

    override fun getDisplayName() = "Project Atlas"
    override fun createComponent(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("Default open mode:"), openMode)
        .addLabeledComponent(JBLabel("Project spacing in List view:"), listProjectSpacing)
        .addLabeledComponent(JBLabel("Project spacing in Tags view:"), tagProjectSpacing)
        .addComponentFillVertically(JPanel(), 0).panel.also { panel = it }

    override fun isModified(): Boolean = service<ProjectManagerSettings>().state.let {
        openMode.selectedItem != it.defaultOpenMode ||
            (listProjectSpacing.value as Number).toInt() != it.listProjectSpacing ||
            (tagProjectSpacing.value as Number).toInt() != it.tagProjectSpacing
    }

    override fun apply() {
        val settings = service<ProjectManagerSettings>()
        val value = ProjectManagerSettings.Data(
            defaultOpenMode = openMode.selectedItem as ProjectManagerSettings.OpenMode,
            sortBy = settings.state.sortBy,
            viewMode = settings.state.viewMode,
            listFilter = settings.state.listFilter,
            tagProjectSpacing = (tagProjectSpacing.value as Number).toInt(),
            listProjectSpacing = (listProjectSpacing.value as Number).toInt(),
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
            listProjectSpacing.value = it.listProjectSpacing
            tagProjectSpacing.value = it.tagProjectSpacing
        }
    }

    override fun disposeUIResources() { panel = null }
}
