package com.github.hyxf.projectmanager.feature.ui

import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent

class ProjectEditDialog(
    owner: Project?,
    path: Path,
    item: ProjectItem? = null,
    allowPathSelection: Boolean = true,
) : DialogWrapper(owner) {
    private val nameField = JBTextField(item?.name ?: path.fileName?.toString().orEmpty())
    private val pathField = TextFieldWithBrowseButton(JBTextField(path.toString())).apply {
        addBrowseFolderListener(
            "Select Project Directory",
            "Choose the local project directory",
            owner,
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        )
        isEnabled = allowPathSelection
    }
    private val tagsField = JBTextField(item?.tags?.sorted()?.joinToString(", ").orEmpty())
    private val favoriteField = JBCheckBox("Favorite", item?.favorite ?: false)

    init {
        title = if (item == null) "Add Project" else "Edit Project"
        init()
    }

    val projectName get() = nameField.text.trim()
    val projectPath: Path get() = Path.of(pathField.text.trim())
    val tags get() = tagsField.text.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
    val favorite get() = favoriteField.isSelected

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Project name:", nameField)
        .addLabeledComponent("Path:", pathField)
        .addLabeledComponent("Tags (comma separated):", tagsField)
        .addComponent(favoriteField).panel

    override fun doValidate(): ValidationInfo? {
        if (projectName.isBlank()) return ValidationInfo("Project name must not be empty", nameField)
        val selectedPath = runCatching { projectPath }.getOrNull()
            ?: return ValidationInfo("Select a valid project directory", pathField)
        if (!Files.isDirectory(selectedPath)) return ValidationInfo("Project path must be an existing directory", pathField)
        return null
    }
}
