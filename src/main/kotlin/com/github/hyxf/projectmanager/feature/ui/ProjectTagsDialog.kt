package com.github.hyxf.projectmanager.feature.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class ProjectTagsDialog(
    project: Project,
    private val projectName: String,
    availableTags: Set<String>,
    currentTags: Set<String>,
) : DialogWrapper(project) {
    private val tagList = CheckBoxList<String>()
    private val selectedCount = JBLabel("0", SwingConstants.RIGHT)
    private val totalCount = JBLabel(availableTags.size.toString(), SwingConstants.RIGHT)
    private val selectionStatus = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        add(selectedCount)
        add(JBLabel(" of "))
        add(totalCount)
        add(JBLabel(" tags selected"))
        components.filterIsInstance<JBLabel>().forEach { it.foreground = JBColor.GRAY }
    }

    init {
        title = "Edit Tags"
        availableTags.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { tag ->
            tagList.addItem(tag, tag, tag in currentTags)
        }
        tagList.border = JBUI.Borders.empty(6, 8)
        tagList.emptyText.text = "No tags available"
        tagList.setCheckBoxListListener { _, _ -> updateSelectionStatus() }
        configureCountWidths()
        updateSelectionStatus()
        init()
    }

    val selectedTags: Set<String>
        get() = (0 until tagList.model.size)
            .filter(tagList::isItemSelected)
            .mapNotNull(tagList::getItemAt)
            .toSet()

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(12))).apply {
        preferredSize = Dimension(400, 300)
        border = JBUI.Borders.empty(4, 0)
        add(JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JBLabel(projectName).apply { font = JBFont.label().asBold() }, BorderLayout.NORTH)
            add(JBLabel("Select the tags assigned to this project.").apply {
                foreground = JBColor.GRAY
            }, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        add(JBScrollPane(tagList).apply {
            border = JBUI.Borders.customLine(JBColor.border())
        }, BorderLayout.CENTER)
        add(selectionStatus, BorderLayout.SOUTH)
    }

    override fun getPreferredFocusedComponent(): JComponent = tagList

    private fun updateSelectionStatus() {
        val selectedCount = (0 until tagList.model.size).count(tagList::isItemSelected)
        this.selectedCount.text = selectedCount.toString()
        totalCount.text = tagList.model.size.toString()
    }

    private fun configureCountWidths() {
        val digits = tagList.model.size.toString().length.coerceAtLeast(1)
        val fontMetrics = selectedCount.getFontMetrics(selectedCount.font)
        val width = (0..9).maxOf { digit -> fontMetrics.stringWidth(digit.toString().repeat(digits)) }
        listOf(selectedCount, totalCount).forEach { label ->
            label.preferredSize = Dimension(width, label.preferredSize.height)
            label.minimumSize = label.preferredSize
            label.maximumSize = label.preferredSize
        }
    }
}
