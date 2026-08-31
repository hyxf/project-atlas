package com.github.hyxf.projectmanager.feature.ui

import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class DeleteProjectDialog(owner: Project, private val item: ProjectItem) : DialogWrapper(owner) {
    private val directDeleteField = JBCheckBox("Delete directly (cannot be undone)", true)

    init {
        title = "Delete Project"
        setOKButtonText("Delete")
        init()
    }

    val deleteDirectly get() = directDeleteField.isSelected

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addComponent(JBLabel("Delete ${item.name}?"))
        .addLabeledComponent("Path:", JBLabel(item.path.toString()))
        .addComponent(directDeleteField)
        .panel
}
