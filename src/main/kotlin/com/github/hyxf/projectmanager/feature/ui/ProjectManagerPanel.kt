package com.github.hyxf.projectmanager.feature.ui

import com.github.hyxf.projectmanager.ProjectManagerIcons
import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.github.hyxf.projectmanager.feature.project.ProjectManagerService
import com.github.hyxf.projectmanager.infrastructure.persistence.ProjectJsonStore
import com.github.hyxf.projectmanager.settings.ProjectManagerConfigurable
import com.github.hyxf.projectmanager.settings.ProjectManagerSettings
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.PopupHandler
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.ButtonGroup
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.JTree

class ProjectManagerPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {
    private val manager = service<ProjectManagerService>()
    private val settings = service<ProjectManagerSettings>()
    private var viewMode = settings.state.viewMode
    private var listFilter = settings.state.listFilter
    private var sortBy = settings.state.sortBy
    private val excludedTagFilters = linkedSetOf<String>()
    private val projectModel = DefaultListModel<ProjectItem>()
    private val projectListGroups = mutableListOf<String>()
    private val projectList = object : JBList<ProjectItem>(projectModel) {
        override fun getScrollableTracksViewportWidth(): Boolean = true
    }
    private val projectTree = Tree()
    private val projectCards = JPanel(CardLayout())
    private val filterButtons = mutableMapOf<ProjectManagerSettings.ListFilter, JToggleButton>()
    private val status = JBLabel()
    private val searchAction = object : AnAction(
        "Search Projects",
        "Search saved projects",
        AllIcons.Actions.Search,
    ) {
        override fun actionPerformed(e: AnActionEvent) = showSearch()
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    init {
        searchAction.registerCustomShortcutSet(
            CustomShortcutSet(KeyStroke.getKeyStroke(
                KeyEvent.VK_P,
                menuShortcutMask() or InputEvent.SHIFT_DOWN_MASK,
            )),
            this,
        )
        toolbar = createToolbar()
        setContent(createContent())
        configureProjects()
        refresh()
    }

    private fun createToolbar(): JComponent {
        val actions = DefaultActionGroup(
            object : AnAction("Save Current Project", "Save the current project", AllIcons.Actions.MenuSaveall) {
                override fun actionPerformed(e: AnActionEvent) = saveCurrentProject()

                override fun update(e: AnActionEvent) {
                    val currentPath = currentProjectPath()
                    val alreadySaved = currentPath?.let { manager.findByPath(it) != null } == true
                    e.presentation.isEnabled = currentPath != null && !alreadySaved
                    e.presentation.description = if (alreadySaved) {
                        "The current project is already saved"
                    } else {
                        "Save the current project"
                    }
                }

                override fun getActionUpdateThread() = ActionUpdateThread.BGT
            },
            object : AnAction("Import Local Projects", "Find and import projects from local folders", ProjectManagerIcons.ImportLocalProjects) {
                override fun actionPerformed(e: AnActionEvent) = ProjectImportUi.show(project) { refresh() }
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            },
            object : AnAction("Edit project.json", "Open the user-level project configuration", AllIcons.Actions.Edit) {
                override fun actionPerformed(e: AnActionEvent) = editConfiguration()
            },
            searchAction,
            object : AnAction("Refresh", "Reload project.json", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) = ProjectUiSupport.runInBackground(
                    project, "Refresh project.json", { service<ProjectJsonStore>().forceReload() }, { reloadFromStore() },
                )
            },
            sortActions(),
            viewAction(),
            manageTagsAction(),
            settingsAction(),
        )
        val actionToolbar = ActionManager.getInstance().createActionToolbar("ProjectManager.Toolbar", actions, true).apply {
            targetComponent = this@ProjectManagerPanel
        }.component
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 4)
            add(actionToolbar, BorderLayout.CENTER)
        }
    }

    private fun sortActions() = DefaultActionGroup("Sort", "Sort projects", AllIcons.ObjectBrowser.Sorted).apply {
        isPopup = true
        add(sortAction("Name", ProjectManagerSettings.SortBy.NAME))
        add(sortAction("Path", ProjectManagerSettings.SortBy.PATH))
        add(sortAction("Recent", ProjectManagerSettings.SortBy.RECENT))
        add(sortAction("Saved", ProjectManagerSettings.SortBy.SAVED))
    }

    private fun sortAction(text: String, value: ProjectManagerSettings.SortBy) = object : ToggleAction(text) {
        override fun isSelected(e: AnActionEvent) = sortBy == value
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (state) {
                sortBy = value
                ProjectUiSupport.runInBackground(project, "Save sort setting", { settings.updateSortBy(value) })
                refreshProjects()
            }
        }
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private fun viewAction() = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            viewMode = if (viewMode == ProjectManagerSettings.ViewMode.LIST) {
                ProjectManagerSettings.ViewMode.TAGS
            } else {
                ProjectManagerSettings.ViewMode.LIST
            }
            ProjectUiSupport.runInBackground(project, "Save view setting", { settings.updateViewMode(viewMode) })
            refreshProjects()
        }

        override fun update(e: AnActionEvent) {
            val listView = viewMode == ProjectManagerSettings.ViewMode.LIST
            e.presentation.icon = if (listView) ProjectManagerIcons.TagsView else ProjectManagerIcons.ListFiles
            e.presentation.text = if (listView) "Switch to Tags View" else "Switch to List View"
            e.presentation.description = e.presentation.text
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private fun settingsAction() = object : AnAction(
        "Project Atlas Settings",
        "Open Project Atlas settings",
        AllIcons.General.Settings,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, ProjectManagerConfigurable::class.java)
            projectList.revalidate()
            projectList.repaint()
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private fun manageTagsAction() = object : ActionGroup(
        "Filter by Tags",
        "Filter projects by tags",
        ProjectManagerIcons.FilterByTag,
    ) {
        init {
            isPopup = true
        }

        override fun getChildren(e: AnActionEvent?): Array<AnAction> {
            val tags = availableTagFilters()
            if (tags.isEmpty()) {
                return arrayOf(object : AnAction("No tags available") {
                    override fun actionPerformed(e: AnActionEvent) = Unit
                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = false
                    }
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                })
            }
            return tags.sortedBy(::tagFilterDisplayName).map { tag ->
                object : ToggleAction(tagFilterDisplayName(tag)) {
                    override fun isSelected(e: AnActionEvent) = tag !in excludedTagFilters

                    override fun setSelected(e: AnActionEvent, state: Boolean) {
                        if (state) excludedTagFilters.remove(tag) else excludedTagFilters.add(tag)
                        refreshProjects(null)
                    }

                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                }
            }.toTypedArray()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.description = if (excludedTagFilters.isEmpty()) {
                "Filter projects by tags"
            } else {
                "Some tags are excluded"
            }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private fun createContent(): JComponent = JPanel(BorderLayout()).apply {
            projectCards.add(createListView(), LIST_CARD)
            projectCards.add(JBScrollPane(projectTree), TAG_CARD)
            add(projectCards, BorderLayout.CENTER)
            add(status.apply { border = JBUI.Borders.empty(4, 8) }, BorderLayout.SOUTH)
        }

    private fun createListView() = JPanel(BorderLayout()).apply {
        add(createListFilterBar(), BorderLayout.NORTH)
        add(JBScrollPane(projectList), BorderLayout.CENTER)
    }

    private fun createListFilterBar(): JComponent {
        val group = ButtonGroup()
        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            border = JBUI.Borders.empty(2, 4)
            isOpaque = false
            ProjectManagerSettings.ListFilter.values().forEach { filter ->
                val button = FilterToggleButton(filter.displayName).apply {
                    isFocusable = false
                    isSelected = listFilter == filter
                    addActionListener {
                        if (isSelected && listFilter != filter) {
                            listFilter = filter
                            ProjectUiSupport.runInBackground(
                                project,
                                "Save list filter",
                                { settings.updateListFilter(filter) },
                            )
                            refreshProjects(null)
                        }
                    }
                }
                group.add(button)
                filterButtons[filter] = button
                add(button)
            }
        }
    }

    private class FilterToggleButton(text: String) : JToggleButton(text) {
        private val selectedForeground = JBColor.namedColor(
            "Link.activeForeground",
            JBColor(0x3574F0, 0x589DF6),
        )
        private val hoverBackground = JBColor.namedColor(
            "ActionButton.hoverBackground",
            JBColor(0xE8E8E8, 0x45494A),
        )

        init {
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            isRolloverEnabled = true
            border = JBUI.Borders.empty(1, 6, 3, 6)
            addItemListener { updateStyle() }
            updateStyle()
        }

        private fun updateStyle() {
            foreground = if (isSelected) {
                selectedForeground
            } else {
                JBColor.namedColor("Label.foreground", JBColor.foreground())
            }
            font = font.deriveFont(if (isSelected) Font.BOLD else Font.PLAIN)
            repaint()
        }

        override fun paintComponent(graphics: Graphics) {
            if (model.isRollover) {
                val graphics2D = graphics.create() as Graphics2D
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                graphics2D.color = hoverBackground
                val arc = JBUI.scale(6)
                graphics2D.fillRoundRect(0, 0, width, height, arc, arc)
                graphics2D.dispose()
            }
            super.paintComponent(graphics)
            if (isSelected) {
                val graphics2D = graphics.create() as Graphics2D
                graphics2D.color = selectedForeground
                graphics2D.fillRoundRect(
                    JBUI.scale(4),
                    height - JBUI.scale(2),
                    width - JBUI.scale(8),
                    JBUI.scale(2),
                    JBUI.scale(2),
                    JBUI.scale(2),
                )
                graphics2D.dispose()
            }
        }
    }

    private fun configureProjects() {
        projectList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        val projectRenderer = ProjectListCellRenderer(project, showTags = false)
        projectList.cellRenderer = javax.swing.ListCellRenderer { list, value, index, selected, hasFocus ->
            val item = projectRenderer.getListCellRendererComponent(list, value, index, selected, hasFocus)
            val group = projectListGroups.getOrNull(index)
            val startsGroup = index == 0 || group != projectListGroups.getOrNull(index - 1)
            if (!startsGroup || group == null) {
                item
            } else {
                JPanel(BorderLayout()).apply {
                    isOpaque = true
                    background = list.background
                    border = JBUI.Borders.emptyTop(if (index == 0) 4 else 10)
                    add(JBLabel(group).apply {
                        font = font.deriveFont(Font.BOLD)
                        foreground = JBColor.namedColor("Group.separatorForeground", JBColor.GRAY)
                        border = JBUI.Borders.compound(
                            JBUI.Borders.customLineTop(
                                JBColor.namedColor("Group.separatorColor", JBColor(0xD0D0D0, 0x515151)),
                            ),
                            JBUI.Borders.empty(6, 9, 4, 9),
                        )
                    }, BorderLayout.NORTH)
                    add(item, BorderLayout.CENTER)
                }
            }
        }
        projectList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && projectList.locationToIndex(e.point) >= 0) openSelected(defaultNewWindow())
            }
        })
        PopupHandler.installPopupMenu(projectList, contextActions(), "ProjectManager.ContextMenu")
        projectTree.isRootVisible = false
        projectTree.showsRootHandles = true
        projectTree.cellRenderer = object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
                                               leaf: Boolean, row: Int, hasFocus: Boolean) {
                val item = (value as? DefaultMutableTreeNode)?.userObject
                icon = null
                when (item) {
                    is ProjectItem -> {
                        val current = isCurrentProject(item)
                        if (current) icon = AllIcons.Actions.Checked
                        if (item.favorite) append("★ ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                        append(
                            item.name,
                            if (current) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                            else SimpleTextAttributes.REGULAR_ATTRIBUTES,
                        )
                        if (ProjectPathStatusCache.isDirectory(item.path) == false) {
                            append("  Missing", SimpleTextAttributes.ERROR_ATTRIBUTES)
                        } else if (ProjectPathStatusCache.isDirectory(item.path) == null) {
                            ProjectPathStatusCache.refresh(item.path) { projectTree.repaint() }
                        }
                    }
                    is TagNode -> append(item.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    else -> append(item?.toString().orEmpty())
                }
            }
        }
        projectTree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && selectedTreeProject() != null) open(selectedTreeProject()!!, defaultNewWindow())
            }
        })
        PopupHandler.installPopupMenu(projectTree, contextActions(), "ProjectManager.ContextMenu")
        bindKey(KeyEvent.VK_ENTER, 0, "open") { openSelected(defaultNewWindow()) }
        bindKey(KeyEvent.VK_ENTER, menuShortcutMask(), "openNew") { openSelected(true) }
        bindKey(KeyEvent.VK_SPACE, 0, "favorite") { toggleFavoriteSelected() }
        bindKey(KeyEvent.VK_DELETE, 0, "remove") { removeSelected() }
        bindKey(KeyEvent.VK_BACK_SPACE, 0, "removeBackspace") { removeSelected() }
    }

    fun refresh(preferredId: String? = selected()?.id) {
        refreshProjects(preferredId)
        service<ProjectJsonStore>().consumeLoadWarning()?.let {
            ProjectUiSupport.notify(project, it, com.intellij.notification.NotificationType.WARNING)
        }
    }

    fun reloadFromStore() {
        settings.state.let {
            viewMode = it.viewMode
            listFilter = it.listFilter
            sortBy = it.sortBy
        }
        filterButtons.forEach { (filter, button) -> button.isSelected = filter == listFilter }
        refresh()
    }

    private fun refreshProjects(preferredId: String? = selected()?.id) {
        val allTags = availableTagFilters()
        excludedTagFilters.retainAll(allTags)
        val selectedTags = allTags - excludedTagFilters
        val tagFilteredProjects = when {
            excludedTagFilters.isEmpty() -> manager.projects()
            selectedTags.isEmpty() -> emptyList()
            else -> manager.projects().filter { item ->
                item.tags.any(selectedTags::contains) ||
                    (item.tags.isEmpty() && UNTAGGED_FILTER_KEY in selectedTags)
            }
        }
        tagFilteredProjects.forEach { item ->
            ProjectPathStatusCache.refresh(item.path) {
                projectList.repaint()
                projectTree.repaint()
            }
        }
        if (isTagView()) {
            refreshTagTree(preferredId, tagFilteredProjects, selectedTags)
            (projectCards.layout as CardLayout).show(projectCards, TAG_CARD)
            return
        }
        (projectCards.layout as CardLayout).show(projectCards, LIST_CARD)
        val items = when (listFilter) {
            ProjectManagerSettings.ListFilter.RECENT -> manager.sortProjects(tagFilteredProjects.filter { it.lastOpenedAt != null })
            ProjectManagerSettings.ListFilter.FAVORITES -> manager.sortProjects(tagFilteredProjects.filter(ProjectItem::favorite))
            ProjectManagerSettings.ListFilter.ALL -> manager.sortProjects(tagFilteredProjects)
        }
        val groupedItems = buildList {
            (selectedTags - UNTAGGED_FILTER_KEY).sorted().forEach { tag ->
                items.filter { tag in it.tags }.forEach { add(tag to it) }
            }
            if (UNTAGGED_FILTER_KEY in selectedTags) {
                items.filter { it.tags.isEmpty() }.forEach { add(UNTAGGED_GROUP_NAME to it) }
            }
        }
        projectModel.clear()
        projectListGroups.clear()
        groupedItems.forEach { (group, item) ->
            projectListGroups.add(group)
            projectModel.addElement(item)
        }
        preferredId?.let { id ->
            groupedItems.indexOfFirst { it.second.id == id }.takeIf { it >= 0 }?.let { projectList.selectedIndex = it }
        }
        if (projectList.selectedIndex < 0 && !projectModel.isEmpty) projectList.selectedIndex = 0
        projectList.emptyText.text = when {
            listFilter == ProjectManagerSettings.ListFilter.RECENT -> "No recently opened projects"
            listFilter == ProjectManagerSettings.ListFilter.FAVORITES -> "No favorite projects"
            else -> "No saved projects. Use the Save icon to save the current project."
        }
        val groupCount = groupedItems.mapTo(linkedSetOf()) { it.first }.size
        status.text = "${items.size} project${if (items.size == 1) "" else "s"}  ·  $groupCount groups"
    }

    private fun refreshTagTree(preferredId: String?, projects: List<ProjectItem>, selectedTags: Set<String>) {
        val root = DefaultMutableTreeNode("Tags")
        var associationCount = 0
        val selectedRealTags = selectedTags - UNTAGGED_FILTER_KEY
        var groupCount = selectedRealTags.size
        selectedRealTags.sorted().forEach { tag ->
            val tagNode = DefaultMutableTreeNode(TagNode(tag))
            manager.sortProjects(projects.filter { tag in it.tags }).forEach {
                tagNode.add(DefaultMutableTreeNode(it)); associationCount++
            }
            root.add(tagNode)
        }
        val untaggedProjects = manager.sortProjects(projects.filter { it.tags.isEmpty() })
        if (UNTAGGED_FILTER_KEY in selectedTags && untaggedProjects.isNotEmpty()) {
            val untaggedNode = DefaultMutableTreeNode(TagNode(UNTAGGED_GROUP_NAME))
            untaggedProjects.forEach {
                untaggedNode.add(DefaultMutableTreeNode(it)); associationCount++
            }
            root.add(untaggedNode)
            groupCount++
        }
        projectTree.model = DefaultTreeModel(root)
        TreeUtil.expandAll(projectTree)
        preferredId?.let { selectProjectInTree(it) }
        projectTree.emptyText.text = "No tags"
        status.text = "$groupCount tag groups  ·  $associationCount project associations"
    }

    private fun availableTagFilters(): Set<String> = buildSet {
        addAll(manager.tags())
        if (manager.projects().any { it.tags.isEmpty() }) add(UNTAGGED_FILTER_KEY)
    }

    private fun tagFilterDisplayName(tag: String) =
        if (tag == UNTAGGED_FILTER_KEY) UNTAGGED_GROUP_NAME else tag

    private fun selectProjectInTree(id: String) {
        val root = projectTree.model.root as? DefaultMutableTreeNode ?: return
        val nodes = root.depthFirstEnumeration()
        while (nodes.hasMoreElements()) {
            val node = nodes.nextElement() as? DefaultMutableTreeNode ?: continue
            if ((node.userObject as? ProjectItem)?.id == id) {
                projectTree.selectionPath = javax.swing.tree.TreePath(node.path)
                return
            }
        }
    }

    private fun showSearch() = ProjectUiSupport.runInBackground(project, "Load projects", {
        service<ProjectJsonStore>().forceReload()
    }) {
        ProjectSearchDialog(project) { item -> open(item, defaultNewWindow()) }.show()
    }

    private fun saveCurrentProject() {
        val path = currentProjectPath() ?: return
        val existing = manager.findByPath(path)
        val dialog = ProjectEditDialog(project, path, existing, allowPathSelection = false)
        if (dialog.showAndGet()) ProjectUiSupport.runInBackground(project, "Save current project", {
            manager.saveProject(dialog.projectName, path, dialog.tags, dialog.favorite).also {
                manager.updateLastOpened(path)
            }
        }) { refresh(it.id) }
    }

    private fun editConfiguration() {
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching { service<ProjectJsonStore>().ensureFile() }
                .onSuccess { path -> ApplicationManager.getApplication().invokeLater {
                    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)?.let {
                        FileEditorManager.getInstance(project).openFile(it, true)
                    }
                } }
                .onFailure { ProjectUiSupport.report(project, "Open project.json", it) }
        }
    }

    private fun openSelected(newWindow: Boolean) { selected()?.let { open(it, newWindow) } }
    private fun open(item: ProjectItem, newWindow: Boolean) {
        val current = project.basePath?.let(java.nio.file.Path::of)?.toAbsolutePath()?.normalize() == item.path.toAbsolutePath().normalize()
        if (current) return
        if (ProjectUiSupport.open(item, project, newWindow)) {
            ProjectUiSupport.runInBackground(project, "Update recent project", { manager.updateLastOpened(item.path) })
        }
        refreshProjects(item.id)
    }

    private fun contextActions() = DefaultActionGroup().apply {
        add(action("Open") { openSelected(false) }); add(action("Open in New Window") { openSelected(true) }); addSeparator()
        add(action("Edit Project…") { editSelected() })
        add(action("Edit Tags…") { editTagsSelected() })
        add(object : ContextAction() {
            override fun update(e: AnActionEvent) { super.update(e); e.presentation.text = if (selected()?.favorite == true) "Remove from Favorites" else "Add to Favorites" }
            override fun perform() = toggleFavoriteSelected()
        }); addSeparator()
        add(action("Copy Path") { selected()?.let { CopyPasteManager.getInstance().setContents(StringSelection(it.path.toString())) } })
        add(action("Reveal in Finder / Explorer") { selected()?.let { RevealFileAction.openDirectory(it.path) } })
        add(action("Open in Terminal") { selected()?.let(::openInTerminal) })
        add(action("Locate Missing Project…", {
            selected()?.let { ProjectPathStatusCache.isDirectory(it.path) == false } == true
        }) { locateSelected() }); addSeparator()
        add(action("Remove from Project Atlas…") { removeSelected() })
    }

    private fun action(text: String, visible: () -> Boolean = { selected() != null }, action: () -> Unit) = object : ContextAction() {
        override fun update(e: AnActionEvent) { super.update(e); e.presentation.text = text; e.presentation.isVisible = visible() }
        override fun perform() = action()
    }
    private abstract inner class ContextAction : AnAction() {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) { e.presentation.isEnabled = selected() != null }
        final override fun actionPerformed(e: AnActionEvent) = perform()
        abstract fun perform()
    }

    private fun selected(): ProjectItem? = if (isTagView()) selectedTreeProject() else projectList.selectedValue
    private fun selectedTreeProject() =
        (projectTree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? ProjectItem
    private fun isTagView() = viewMode == ProjectManagerSettings.ViewMode.TAGS

    private fun openInTerminal(item: ProjectItem) {
        val terminalAction = ActionManager.getInstance().getAction("Terminal.OpenInTerminal")
        if (terminalAction == null) {
            ProjectUiSupport.notify(project, "The Terminal plugin is not available", NotificationType.WARNING)
            return
        }
        ProjectUiSupport.runInBackground(project, "Open in Terminal", {
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(item.path)
        }) { directory ->
            if (directory == null || !directory.isDirectory) {
                ProjectUiSupport.notify(project, "Project path is missing or is not a directory: ${item.path}", NotificationType.WARNING)
                return@runInBackground
            }
            val dataContext = DataContext { dataId ->
                when (dataId) {
                    CommonDataKeys.PROJECT.name -> project
                    CommonDataKeys.VIRTUAL_FILE.name -> directory
                    else -> null
                }
            }
            ActionUtil.invokeAction(terminalAction, dataContext, ActionPlaces.UNKNOWN, null, null)
        }
    }

    private fun editSelected() {
        val item = selected() ?: return
        val dialog = ProjectEditDialog(project, item.path, item)
        if (dialog.showAndGet()) ProjectUiSupport.runInBackground(project, "Edit project", {
            if (dialog.projectPath.toAbsolutePath().normalize() != item.path.toAbsolutePath().normalize()) {
                manager.relocateProject(item.id, dialog.projectPath)
            }
            manager.updateProject(item.id, dialog.projectName, dialog.tags, dialog.favorite)
        }) { refresh(it.id) }
    }
    private fun editTagsSelected() {
        val item = selected() ?: return
        val dialog = ProjectTagsDialog(project, item.name, manager.tags(), item.tags)
        if (dialog.showAndGet()) ProjectUiSupport.runInBackground(project, "Edit project tags", {
            manager.updateProject(item.id, item.name, dialog.selectedTags, item.favorite)
        }) { refresh(it.id) }
    }
    private fun locateSelected() {
        val item = selected() ?: return
        val path = ProjectUiSupport.chooseDirectory(project) ?: return
        ProjectPathStatusCache.invalidate(item.path)
        ProjectPathStatusCache.invalidate(path)
        ProjectUiSupport.runInBackground(project, "Locate project", { manager.relocateProject(item.id, path) }) {
            refresh(it.id)
        }
    }
    private fun removeSelected() {
        val item = selected() ?: return
        if (Messages.showYesNoDialog(project, "Remove ${item.name} from Project Atlas?\nThe directory will not be deleted.",
                "Remove Project", Messages.getQuestionIcon()) == Messages.YES) {
            ProjectUiSupport.runInBackground(project, "Remove project", { manager.removeProject(item.id) }) { refresh() }
        }
    }
    private fun toggleFavoriteSelected() {
        val item = selected() ?: return
        ProjectUiSupport.runInBackground(project, "Update favorite", { manager.toggleFavorite(item.id) }) { refresh(it.id) }
    }
    private fun defaultNewWindow() = service<ProjectManagerSettings>().state.defaultOpenMode == ProjectManagerSettings.OpenMode.NEW_WINDOW
    private fun currentProjectPath() = project.basePath?.let(java.nio.file.Path::of)
    private fun isCurrentProject(item: ProjectItem) =
        currentProjectPath()?.toAbsolutePath()?.normalize() == item.path.toAbsolutePath().normalize()
    private fun bindKey(key: Int, modifiers: Int, name: String, action: () -> Unit) {
        projectList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, modifiers), name)
        projectList.actionMap.put(name, object : AbstractAction() { override fun actionPerformed(e: ActionEvent?) = action() })
    }
    private fun menuShortcutMask() = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    private data class TagNode(val name: String)
    private companion object {
        const val LIST_CARD = "projects"
        const val TAG_CARD = "tags"
        const val UNTAGGED_GROUP_NAME = "Untagged"
        const val UNTAGGED_FILTER_KEY = "\u0000untagged"
    }
}
