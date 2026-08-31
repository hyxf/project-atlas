package com.github.hyxf.projectmanager.feature.ui

import com.github.hyxf.projectmanager.feature.project.ProjectImportRequest
import com.github.hyxf.projectmanager.feature.project.ProjectManagerService
import com.github.hyxf.projectmanager.infrastructure.filesystem.DiscoveredProject
import com.github.hyxf.projectmanager.infrastructure.filesystem.ProjectDirectoryScanner
import com.intellij.notification.NotificationType
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.Timer
import javax.swing.table.AbstractTableModel

class ProjectImportPreviewDialog(
    private val project: Project,
    private val manager: ProjectManagerService,
) : DialogWrapper(project) {
    private data class Row(
        var selected: Boolean,
        var name: String,
        val path: Path,
        var tags: String,
        var favorite: Boolean,
        val existing: Boolean,
        val recognized: Boolean,
        val initialName: String = name,
        val initialTags: String = tags,
        val initialFavorite: Boolean = favorite,
    )

    private class ScanRequest(val generation: Int, val roots: List<Path>, val depth: Int) {
        private val canceled = AtomicBoolean()
        @Volatile private var indicator: ProgressIndicator? = null

        fun attach(indicator: ProgressIndicator) {
            this.indicator = indicator
            if (canceled.get()) indicator.cancel()
        }

        fun cancel() {
            canceled.set(true)
            indicator?.cancel()
        }
    }

    private val rows = mutableListOf<Row>()
    private val roots = mutableListOf<Path>()
    private val updateExisting = JBCheckBox("Update existing")
    private val model = ImportTableModel()
    private val table = JBTable(model)
    private val bulkTags = JBTextField()
    private val rootsLabel = JBLabel("Choose folders to scan")
    private val scanDepth = ComboBox((MIN_SCAN_DEPTH..MAX_SCAN_DEPTH).toList().toTypedArray()).apply {
        selectedItem = DEFAULT_SCAN_DEPTH
    }
    private val scanProgress = JProgressBar().apply {
        isIndeterminate = true
        isVisible = false
        preferredSize = Dimension(JBUI.scale(90), JBUI.scale(4))
    }
    private val scanningLabel = JBLabel()
    private val resultSummary = JBLabel("0 projects")
    private val cancelScan = JButton("Stop").apply {
        isVisible = false
        addActionListener { activeScan?.cancel() }
    }
    private val depthRescanTimer = Timer(400) { if (roots.isNotEmpty()) scanProjects() }.apply {
        isRepeats = false
    }
    private val scanFeedbackTimer = Timer(SCAN_FEEDBACK_DELAY_MS) {
        activeScan?.let(::showScanningFeedback)
    }.apply {
        isRepeats = false
    }
    private val scanGeneration = AtomicInteger()
    @Volatile private var activeScan: ScanRequest? = null

    private fun createRow(candidate: DiscoveredProject): Row {
        val existing = manager.findByPath(candidate.path)
        return Row(
            selected = existing == null && candidate.recognized,
            name = existing?.name ?: candidate.path.fileName?.toString().orEmpty(),
            path = candidate.path,
            tags = existing?.tags?.sorted()?.joinToString(", ").orEmpty(),
            favorite = existing?.favorite ?: false,
            existing = existing != null,
            recognized = candidate.recognized,
        )
    }

    init {
        title = "Import Local Projects"
        table.autoResizeMode = JBTable.AUTO_RESIZE_LAST_COLUMN
        table.setShowGrid(false)
        table.rowHeight = JBUI.scale(26)
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(48)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(170)
        table.columnModel.getColumn(2).preferredWidth = JBUI.scale(310)
        table.columnModel.getColumn(3).preferredWidth = JBUI.scale(150)
        table.columnModel.getColumn(4).preferredWidth = JBUI.scale(60)
        table.putClientProperty("terminateEditOnFocusLost", true)
        table.emptyText.text = "Choose folders above to find projects"
        updateExisting.toolTipText =
            "Allow existing projects to be selected so their name, tags, and favorite status can be updated"
        updateExisting.addActionListener {
            model.fireTableDataChanged()
            updateImportAction()
        }
        scanDepth.addActionListener { depthRescanTimer.restart() }
        init()
        updateImportAction()
    }

    val shouldUpdateExisting get() = updateExisting.isSelected
    val requests: List<ProjectImportRequest>
        get() = rows.filter(Row::selected).map {
            ProjectImportRequest(
                it.path,
                it.name.trim(),
                it.tags.split(',').map(String::trim).filter(String::isNotEmpty).toSet(),
                it.favorite,
            )
        }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(12))).apply {
        preferredSize = Dimension(880, 480)
        border = JBUI.Borders.emptyTop(4)
        add(createSourcePanel(), BorderLayout.NORTH)
        add(JPanel(BorderLayout(0, JBUI.scale(6))).apply {
            add(createSelectionActions(), BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(createBulkActions(), BorderLayout.SOUTH)
        }, BorderLayout.CENTER)
    }

    private fun createSourcePanel() = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
        add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(JButton("Choose Folders…").apply { addActionListener { chooseFolders() } }, BorderLayout.WEST)
            add(rootsLabel, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
                add(JBLabel("Depth"))
                add(scanDepth)
                add(updateExisting)
            }, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        add(JPanel(BorderLayout()).apply {
            add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
                add(scanProgress)
                add(scanningLabel)
                add(cancelScan)
            }, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
    }

    private fun chooseFolders() {
        val descriptor = FileChooserDescriptorFactory.createMultipleFoldersDescriptor()
            .withTitle("Choose Folders to Scan")
            .withDescription("Select project folders or parent folders that contain projects")
        val selected = FileChooser.chooseFiles(descriptor, project, null).map { it.toNioPath() }
        if (selected.isEmpty()) return
        depthRescanTimer.stop()
        roots.clear()
        roots.addAll(selected.distinct())
        rootsLabel.text = if (roots.size == 1) roots.single().toString() else "${roots.size} folders"
        rootsLabel.toolTipText = roots.joinToString("<br>", "<html>", "</html>")
        scanProjects()
    }

    private fun scanProjects() {
        if (roots.isEmpty()) return
        val request = ScanRequest(
            generation = scanGeneration.incrementAndGet(),
            roots = roots.toList(),
            depth = scanDepth.selectedItem as Int,
        )
        activeScan?.cancel()
        activeScan = request
        if (scanProgress.isVisible) {
            showScanningFeedback(request)
        } else {
            scanFeedbackTimer.restart()
        }
        updateImportAction(scanning = true)
        var scannedRows = emptyList<Row>()
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Scanning for Projects", true) {
            override fun run(indicator: ProgressIndicator) {
                request.attach(indicator)
                indicator.checkCanceled()
                indicator.isIndeterminate = true
                val candidatesByPath = linkedMapOf<Path, DiscoveredProject>()
                request.roots.forEach { root ->
                    indicator.checkCanceled()
                    (ProjectDirectoryScanner.selected(listOf(root)) +
                        ProjectDirectoryScanner.scan(root, request.depth, indicator))
                        .forEach { candidatesByPath[it.path] = it }
                }
                scannedRows = candidatesByPath.values.map(::createRow)
            }

            override fun onSuccess() {
                if (activeScan !== request) return
                val previousRows = rows.associateBy(Row::path)
                rows.clear()
                rows += scannedRows.map { scanned -> previousRows[scanned.path] ?: scanned }
                model.fireTableDataChanged()
                table.emptyText.text = if (rows.isEmpty()) {
                    "No projects found. Choose another folder or increase the folder depth."
                } else {
                    "No projects"
                }
                finishScanning(request)
            }

            override fun onCancel() = finishScanning(request, "Scan canceled")

            override fun onThrowable(error: Throwable) {
                if (activeScan === request) {
                    ProjectUiSupport.notify(project, "Project scan failed: ${error.message}", NotificationType.ERROR)
                }
                finishScanning(request, "Scan failed")
            }
        })
    }

    private fun showScanningFeedback(request: ScanRequest) {
        if (activeScan !== request) return
        scanProgress.isVisible = true
        cancelScan.isVisible = true
        scanningLabel.text = "Scanning ${request.roots.size} folder${if (request.roots.size == 1) "" else "s"} " +
            "at depth ${request.depth}…"
    }

    private fun finishScanning(request: ScanRequest, status: String? = null) {
        if (activeScan !== request) return
        activeScan = null
        scanFeedbackTimer.stop()
        scanProgress.isVisible = false
        cancelScan.isVisible = false
        scanningLabel.text = status.orEmpty()
        updateImportAction()
    }

    override fun doValidate(): ValidationInfo? = when {
        rows.none(Row::selected) -> ValidationInfo("Select at least one project to import", table)
        rows.any { it.selected && it.name.isBlank() } -> ValidationInfo("Enter a name for every selected project", table)
        else -> null
    }

    override fun doOKAction() {
        if (table.isEditing && !table.cellEditor.stopCellEditing()) return
        super.doOKAction()
    }

    override fun doCancelAction() {
        depthRescanTimer.stop()
        scanFeedbackTimer.stop()
        activeScan?.cancel()
        super.doCancelAction()
    }

    private fun createSelectionActions() = JPanel(BorderLayout()).apply {
        add(resultSummary, BorderLayout.WEST)
        add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
            add(JButton("Suggested").apply { addActionListener { selectRows { !it.existing && it.recognized } } })
            add(JButton("All").apply { addActionListener { selectRows { !it.existing || updateExisting.isSelected } } })
            add(JButton("None").apply { addActionListener { selectRows { false } } })
        }, BorderLayout.EAST)
    }

    private fun selectRows(predicate: (Row) -> Boolean) {
        rows.forEach { it.selected = predicate(it) }
        model.fireTableDataChanged()
        updateImportAction()
    }

    private fun updateImportAction(scanning: Boolean = activeScan != null) {
        val selectedCount = rows.count(Row::selected)
        resultSummary.text = "${rows.size} project${if (rows.size == 1) "" else "s"} · $selectedCount selected"
        setOKButtonText(if (selectedCount == 0) "Import" else
            "Import $selectedCount Project${if (selectedCount == 1) "" else "s"}")
        setOKActionEnabled(!scanning && selectedCount > 0)
    }

    private fun createBulkActions() = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
        border = JBUI.Borders.emptyTop(2)
        add(JBLabel("Selected:"))
        bulkTags.emptyText.text = "Tags"
        bulkTags.columns = 16
        add(bulkTags)
        add(JButton("Add Tags").apply { addActionListener { addTagsToSelected() } })
        add(JButton("Favorite").apply { addActionListener { setFavoriteForSelected(true) } })
        add(JButton("Unfavorite").apply { addActionListener { setFavoriteForSelected(false) } })
        add(JButton("Reset").apply { addActionListener { resetSelected() } })
    }

    private fun addTagsToSelected() {
        val additions = bulkTags.text.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
        if (additions.isEmpty()) return
        rows.filter(Row::selected).forEach { row ->
            row.tags = (row.tags.split(',').map(String::trim).filter(String::isNotEmpty) + additions)
                .distinct().sorted().joinToString(", ")
        }
        model.fireTableDataChanged()
        updateImportAction()
    }

    private fun setFavoriteForSelected(value: Boolean) {
        rows.filter(Row::selected).forEach { it.favorite = value }
        model.fireTableDataChanged()
        updateImportAction()
    }

    private fun resetSelected() {
        rows.filter(Row::selected).forEach { row ->
            row.name = row.initialName
            row.tags = row.initialTags
            row.favorite = row.initialFavorite
        }
        bulkTags.text = ""
        model.fireTableDataChanged()
        updateImportAction()
    }

    private inner class ImportTableModel : AbstractTableModel() {
        private val columns = arrayOf("", "Name", "Location", "Tags", "Favorite", "Status")
        override fun getRowCount() = rows.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(column: Int) = columns[column]
        override fun getColumnClass(columnIndex: Int): Class<*> = if (columnIndex == 0 || columnIndex == 4)
            java.lang.Boolean::class.java else String::class.java

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.selected
                1 -> row.name
                2 -> row.path.toString()
                3 -> row.tags
                4 -> row.favorite
                else -> when {
                    row.existing && updateExisting.isSelected -> "Update"
                    row.existing -> "Existing"
                    row.recognized -> "Ready"
                    else -> "Unknown"
                }
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            val row = rows[rowIndex]
            val existingAllowed = !row.existing || updateExisting.isSelected
            return existingAllowed && when (columnIndex) {
                0 -> true
                1, 3, 4 -> row.selected
                else -> false
            }
        }

        override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
            val row = rows[rowIndex]
            when (columnIndex) {
                0 -> row.selected = value as Boolean
                1 -> row.name = value?.toString().orEmpty()
                3 -> row.tags = value?.toString().orEmpty()
                4 -> row.favorite = value as Boolean
            }
            fireTableRowsUpdated(rowIndex, rowIndex)
            updateImportAction()
        }
    }

    companion object {
        private const val MIN_SCAN_DEPTH = 1
        private const val MAX_SCAN_DEPTH = 5
        private const val DEFAULT_SCAN_DEPTH = 1
        private const val SCAN_FEEDBACK_DELAY_MS = 200
    }
}
