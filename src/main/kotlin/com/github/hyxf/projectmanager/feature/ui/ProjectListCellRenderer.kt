package com.github.hyxf.projectmanager.feature.ui

import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class ProjectListCellRenderer(
    private val currentProject: Project,
    private val showTags: Boolean = true,
) : ListCellRenderer<ProjectItem> {
    override fun getListCellRendererComponent(
        list: JList<out ProjectItem>, value: ProjectItem, index: Int, selected: Boolean, hasFocus: Boolean,
    ): Component {
        val current = currentProject.basePath?.let(Path::of)?.toAbsolutePath()?.normalize() == value.path.toAbsolutePath().normalize()
        val missing = ProjectPathStatusCache.isDirectory(value.path) == false
        if (ProjectPathStatusCache.isDirectory(value.path) == null) {
            ProjectPathStatusCache.refresh(value.path) { list.repaint() }
        }
        val background = if (selected) list.selectionBackground else list.background
        val rowForeground = if (selected) list.selectionForeground else list.foreground

        val title = JBLabel(buildString {
            if (value.favorite) append("★ ")
            append(value.name)
        }).apply {
            font = font.deriveFont(java.awt.Font.BOLD)
            foreground = rowForeground
            if (current) {
                icon = AllIcons.Actions.Checked
                toolTipText = "Current project"
            }
        }
        val state = JBLabel().apply {
            if (missing) {
                text = "Missing"
                foreground = JBColor.RED
            }
        }
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(title, BorderLayout.CENTER)
            add(state, BorderLayout.EAST)
        }
        val path = JBLabel(value.path.toString()).apply {
            foreground = if (selected) rowForeground else JBColor.GRAY
            toolTipText = value.path.toString()
            if (!showTags) border = JBUI.Borders.emptyTop(6)
        }
        val body = JPanel(GridLayout(0, 1, 0, if (showTags) JBUI.scale(2) else 0)).apply {
            isOpaque = false
            add(header)
            add(path)
            if (showTags) {
                add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(5), 0)).apply {
                    isOpaque = false
                    border = JBUI.Borders.emptyTop(3)
                    value.tags.ifEmpty { setOf(UNTAGGED_LABEL) }.sorted().forEach { tag ->
                        add(TagChip(tag, selected, rowForeground))
                    }
                })
            }
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = true
            this.background = background
            border = BorderFactory.createEmptyBorder(JBUI.scale(7), JBUI.scale(9), JBUI.scale(7), JBUI.scale(9))
            add(body)
        }
    }

    private class TagChip(text: String, selected: Boolean, rowForeground: Color) : JBLabel(text) {
        private val chipBackground = if (selected) {
            Color(rowForeground.red, rowForeground.green, rowForeground.blue, 42)
        } else {
            JBColor.namedColor("ProjectManager.Tag.background", JBColor(0xDCE7F5, 0x3B4D63))
        }
        private val chipBorder = if (selected) null else
            JBColor.namedColor("ProjectManager.Tag.borderColor", JBColor(0xA9BCD3, 0x607A99))

        init {
            isOpaque = false
            foreground = if (selected) {
                rowForeground
            } else {
                JBColor.namedColor("ProjectManager.Tag.foreground", JBColor(0x294F7A, 0xD5E5F7))
            }
            border = JBUI.Borders.empty(1, 6)
        }

        override fun paintComponent(graphics: Graphics) {
            val graphics2D = graphics.create() as Graphics2D
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics2D.color = chipBackground
            graphics2D.fillRoundRect(0, 0, width - 1, height - 1, height, height)
            chipBorder?.let {
                graphics2D.color = it
                graphics2D.stroke = BasicStroke(JBUI.scale(1).toFloat())
                graphics2D.drawRoundRect(0, 0, width - 1, height - 1, height, height)
            }
            graphics2D.dispose()
            super.paintComponent(graphics)
        }
    }

    private companion object {
        const val UNTAGGED_LABEL = "Untagged"
    }
}
