package com.github.hyxf.projectmanager.feature.ui

import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.github.hyxf.projectmanager.feature.project.ProjectManagerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ProjectSearchDialog(
    private val project: Project,
    private val onOpen: (ProjectItem) -> Unit,
) {
    private val manager = service<ProjectManagerService>()
    private val search = SearchTextField(false)
    private val model = DefaultListModel<ProjectItem>()
    private val list = JBList(model)
    private val content = JPanel(BorderLayout(0, 8))
    private lateinit var popup: JBPopup

    init {
        search.border = JBUI.Borders.empty()
        search.textEditor.border = JBUI.Borders.empty(6, 8)
        search.textEditor.emptyText.text = "Enter project name, path, or tag"
        search.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = reload()
            override fun removeUpdate(e: DocumentEvent) = reload()
            override fun changedUpdate(e: DocumentEvent) = reload()
        })
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = ProjectListCellRenderer(project)
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && list.locationToIndex(e.point) >= 0) openSelected()
            }
        })
        bindKeys()
        content.preferredSize = Dimension(720, 460)
        content.add(search, BorderLayout.NORTH)
        content.add(JBScrollPane(list), BorderLayout.CENTER)
        reload()
    }

    fun show() {
        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, search.textEditor)
            .setTitle("Search Projects")
            .setFocusable(true)
            .setRequestFocus(true)
            .setResizable(true)
            .setMovable(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .setDimensionServiceKey(project, "ProjectManager.SearchPopup", false)
            .createPopup()
        popup.showCenteredInCurrentWindow(project)
    }

    private fun openSelected() {
        val selected = list.selectedValue ?: return
        popup.cancel()
        onOpen(selected)
    }

    private fun bindKeys() {
        bind(search.textEditor, KeyEvent.VK_DOWN, "selectNext") { selectRelative(1) }
        bind(search.textEditor, KeyEvent.VK_UP, "selectPrevious") { selectRelative(-1) }
        bind(search.textEditor, KeyEvent.VK_ENTER, "openSelected") { openSelected() }
        bind(list, KeyEvent.VK_ENTER, "openSelected") { openSelected() }
    }

    private fun bind(component: JComponent, key: Int, name: String, action: () -> Unit) {
        component.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name)
        component.actionMap.put(name, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = action()
        })
    }

    private fun selectRelative(offset: Int) {
        if (model.isEmpty) return
        list.selectedIndex = (list.selectedIndex + offset).coerceIn(0, model.size - 1)
        list.ensureIndexIsVisible(list.selectedIndex)
    }

    private fun reload() {
        val items = manager.sortProjects(manager.searchProjects(search.text), search.text)
        model.clear()
        items.forEach(model::addElement)
        if (!model.isEmpty) list.selectedIndex = 0
        list.emptyText.text = if (search.text.isBlank()) "No saved projects" else "No matching projects"
    }
}
