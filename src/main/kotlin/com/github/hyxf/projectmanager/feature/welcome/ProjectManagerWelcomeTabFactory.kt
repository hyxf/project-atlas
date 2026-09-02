package com.github.hyxf.projectmanager.feature.welcome

import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.github.hyxf.projectmanager.feature.project.ProjectManagerService
import com.github.hyxf.projectmanager.feature.ui.ProjectUiSupport
import com.github.hyxf.projectmanager.infrastructure.persistence.ProjectJsonStore
import com.github.hyxf.projectmanager.settings.ProjectManagerSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.WelcomeScreenTab
import com.intellij.openapi.wm.WelcomeTabFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ProjectManagerWelcomeTabFactory : WelcomeTabFactory {
    override fun createWelcomeTabs(
        ws: WelcomeScreen,
        parentDisposable: Disposable,
    ): List<WelcomeScreenTab> = listOf(ProjectManagerWelcomeTab(parentDisposable))
}

private class ProjectManagerWelcomeTab(parentDisposable: Disposable) : WelcomeScreenTab {
    private val keyComponent = JBUI.Panels.simplePanel()
        .addToLeft(JBLabel("Project Atlas", SwingConstants.LEFT))
        .withBorder(JBUI.Borders.empty(8, 0))
    private val projectPanel = ProjectManagerWelcomePanel(parentDisposable)

    override fun getKeyComponent(parent: JComponent): JComponent = keyComponent

    override fun getAssociatedComponent(): JComponent = projectPanel

    override fun updateComponent() = projectPanel.reload()
}

private class ProjectManagerWelcomePanel(
    private val parentDisposable: Disposable,
) : JPanel(BorderLayout()) {
    private val model = DefaultListModel<ProjectItem>()
    private val projectList = JBList(model)
    private val contentLayout = CardLayout()
    private val content = JPanel(contentLayout)
    private val status = JBLabel("Loading saved projects…", SwingConstants.CENTER)
    private val count = JBLabel()
    private val sort = JComboBox(SortOption.values())
    private val search = SearchTextField(false)
    private var projects = emptyList<ProjectItem>()
    private var loadVersion = 0
    private var disposed = false

    init {
        Disposer.register(parentDisposable, Disposable { disposed = true })
        border = JBUI.Borders.empty(20, 24)
        background = UIUtil.getListBackground()

        sort.selectedItem = SortOption.RECENT
        sort.toolTipText = "Sort projects"
        sort.addActionListener { showProjects() }
        search.border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
        search.background = background
        search.textEditor.background = background
        search.textEditor.border = JBUI.Borders.empty(6, 8)
        search.textEditor.emptyText.text = "Search by name, path, or tag"
        search.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = showProjects()
            override fun removeUpdate(e: DocumentEvent) = showProjects()
            override fun changedUpdate(e: DocumentEvent) = showProjects()
        })
        val headerActions = JBUI.Panels.simplePanel(JBUI.scale(12), 0)
            .addToLeft(count)
            .addToRight(sort)
        headerActions.background = background
        val header = JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
            background = this@ProjectManagerWelcomePanel.background
            border = JBUI.Borders.emptyBottom(12)
            add(search, BorderLayout.CENTER)
            add(headerActions, BorderLayout.EAST)
        }
        header.background = background
        add(header, BorderLayout.NORTH)

        projectList.apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = ProjectItemRenderer()
            visibleRowCount = 12
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) openSelectedProject()
                }
            })
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), OPEN_PROJECT_ACTION)
            actionMap.put(OPEN_PROJECT_ACTION, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) = openSelectedProject()
            })
        }

        content.background = background
        content.add(JBScrollPane(projectList).apply { border = JBUI.Borders.empty() }, PROJECTS_CARD)
        content.add(status, STATUS_CARD)
        add(content, BorderLayout.CENTER)
        reload()
    }

    fun reload() {
        val version = ++loadVersion
        status.text = "Loading saved projects…"
        contentLayout.show(content, STATUS_CARD)
        ProjectUiSupport.runInBackground(null, "Load projects for Welcome Screen", {
            service<ProjectJsonStore>().forceReload()
            service<ProjectManagerService>().projectsByRecent()
        }) { projects ->
            if (version != loadVersion || disposed) return@runInBackground
            this.projects = projects
            if (projects.isEmpty()) {
                count.text = "0 projects"
                status.text = "No saved projects"
                contentLayout.show(content, STATUS_CARD)
            } else {
                showProjects()
            }
        }
    }

    private fun showProjects() {
        if (projects.isEmpty()) return
        val selectedPath = projectList.selectedValue?.path
        val query = search.text
        val sortBy = (sort.selectedItem as? SortOption ?: SortOption.RECENT).sortBy
        val manager = service<ProjectManagerService>()
        val filtered = manager.searchProjects(projects, query)
        val sorted = manager.sortProjects(filtered, sortBy)
        model.clear()
        sorted.forEach(model::addElement)
        count.text = if (query.isBlank()) {
            "${projects.size} projects"
        } else {
            "${filtered.size} of ${projects.size} projects"
        }
        if (sorted.isEmpty()) {
            status.text = "No matching projects"
            contentLayout.show(content, STATUS_CARD)
        } else {
            projectList.selectedIndex = sorted.indexOfFirst { it.path == selectedPath }.takeIf { it >= 0 } ?: 0
            contentLayout.show(content, PROJECTS_CARD)
        }
    }

    private fun openSelectedProject() {
        val selected = projectList.selectedValue ?: return
        if (ProjectUiSupport.open(selected, currentProject = null, newWindow = true)) {
            ProjectUiSupport.runInBackground(null, "Update recent project", {
                service<ProjectManagerService>().updateLastOpened(selected.path)
            })
        }
    }

    private class ProjectItemRenderer : JPanel(BorderLayout()), ListCellRenderer<ProjectItem> {
        private val nameLabel = JBLabel()
        private val pathLabel = JBLabel()
        private val iconLabel = JBLabel()
        private val details = JPanel(BorderLayout())

        init {
            border = JBUI.Borders.empty(10, 12)
            nameLabel.font = nameLabel.font.deriveFont(Font.BOLD)
            pathLabel.border = JBUI.Borders.emptyTop(6)
            details.isOpaque = false
            details.add(nameLabel, BorderLayout.NORTH)
            details.add(pathLabel, BorderLayout.SOUTH)
            iconLabel.border = JBUI.Borders.emptyRight(10)
            add(iconLabel, BorderLayout.WEST)
            add(details, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: javax.swing.JList<out ProjectItem>,
            value: ProjectItem,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            nameLabel.text = value.name
            pathLabel.text = FileUtil.toSystemDependentName(value.path.toString())
            background = if (isSelected) UIUtil.getListSelectionBackground(cellHasFocus) else list.background
            nameLabel.foreground = if (isSelected) UIUtil.getListSelectionForeground(cellHasFocus) else list.foreground
            pathLabel.foreground = if (isSelected) {
                UIUtil.getListSelectionForeground(cellHasFocus)
            } else {
                JBColor.GRAY
            }
            iconLabel.icon = ProjectInitialIcon(projectInitial(value.name), isSelected, cellHasFocus)
            toolTipText = pathLabel.text
            return this
        }

        private fun projectInitial(name: String): String {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) return "?"
            val firstCharacterEnd = trimmedName.offsetByCodePoints(0, 1)
            return trimmedName.substring(0, firstCharacterEnd).uppercase()
        }
    }

    private class ProjectInitialIcon(
        private val initial: String,
        private val selected: Boolean,
        private val focused: Boolean,
    ) : Icon {
        private val size = JBUI.scale(28)

        override fun getIconWidth(): Int = size

        override fun getIconHeight(): Int = size

        override fun paintIcon(component: Component, graphics: Graphics, x: Int, y: Int) {
            val graphics2D = graphics.create() as Graphics2D
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics2D.color = if (selected) {
                UIUtil.getListSelectionForeground(focused)
            } else {
                JBColor.namedColor("ProjectManager.ProjectIcon.background", JBColor(0xD9E7F7, 0x365C83))
            }
            graphics2D.fillRoundRect(x, y, size, size, JBUI.scale(7), JBUI.scale(7))
            if (!selected) {
                graphics2D.color = JBColor.namedColor(
                    "ProjectManager.ProjectIcon.borderColor",
                    JBColor(0x9AB8D6, 0x78A9D4),
                )
                graphics2D.stroke = BasicStroke(JBUI.scale(1).toFloat())
                graphics2D.drawRoundRect(x, y, size - 1, size - 1, JBUI.scale(7), JBUI.scale(7))
            }
            graphics2D.color = if (selected) {
                UIUtil.getListSelectionBackground(focused)
            } else {
                JBColor.namedColor("ProjectManager.ProjectIcon.foreground", JBColor(0x245A91, 0xF2F7FC))
            }
            graphics2D.font = component.font.deriveFont(Font.BOLD, JBUI.scale(13).toFloat())
            val metrics = graphics2D.fontMetrics
            val textX = x + (size - metrics.stringWidth(initial)) / 2
            val textY = y + (size - metrics.height) / 2 + metrics.ascent
            graphics2D.drawString(initial, textX, textY)
            graphics2D.dispose()
        }
    }

    private companion object {
        const val OPEN_PROJECT_ACTION = "projectManager.openWelcomeProject"
        const val PROJECTS_CARD = "projects"
        const val STATUS_CARD = "status"
    }

    private enum class SortOption(
        private val label: String,
        val sortBy: ProjectManagerSettings.SortBy,
    ) {
        NAME("Name", ProjectManagerSettings.SortBy.NAME),
        PATH("Path", ProjectManagerSettings.SortBy.PATH),
        RECENT("Recent", ProjectManagerSettings.SortBy.RECENT);

        override fun toString(): String = label
    }
}
