package com.github.hyxf.projectmanager.feature.ui

import com.github.hyxf.projectmanager.feature.project.DuplicateProjectPathException
import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.file.Files
import java.nio.file.Path

object ProjectUiSupport {
    private val logger = Logger.getInstance(ProjectUiSupport::class.java)
    private val persistenceExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
        "Project Atlas Persistence",
        1,
    )

    fun chooseDirectory(project: Project?): Path? = FileChooser.chooseFile(
        FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null
    )?.toNioPath()

    fun open(item: ProjectItem, currentProject: Project?, newWindow: Boolean): Boolean {
        if (!Files.isDirectory(item.path)) {
            notify(currentProject, "Project path is missing or is not a directory: ${item.path}", NotificationType.WARNING)
            return false
        }
        if (LocalFileSystem.getInstance().refreshAndFindFileByNioFile(item.path) == null) {
            notify(currentProject, "Project path cannot be accessed: ${item.path}", NotificationType.ERROR)
            return false
        }
        return runCatching {
            var task = OpenProjectTask.build().withForceOpenInNewFrame(newWindow)
            if (!newWindow && currentProject != null) task = task.withProjectToClose(currentProject)
            ProjectManagerEx.getInstanceEx().openProject(item.path, task) != null
        }.onFailure {
            logger.warn("[ProjectOpen] Failed to open a saved project", it)
            notify(currentProject, "Could not open ${item.name}: ${it.message.orEmpty()}", NotificationType.ERROR)
        }.getOrDefault(false)
    }

    fun report(project: Project?, operation: String, error: Throwable) {
        logger.warn("[ProjectManager] $operation failed", error)
        val message = when (error) {
            is DuplicateProjectPathException -> "Project already exists"
            else -> error.message ?: "Unexpected error"
        }
        notify(project, message, NotificationType.ERROR)
    }

    fun notify(project: Project?, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup("ProjectManager")
            .createNotification(message, type).notify(project)
    }

    fun <T> runInBackground(
        project: Project?,
        operation: String,
        task: () -> T,
        onSuccess: (T) -> Unit = {},
    ) {
        persistenceExecutor.execute {
            runCatching(task)
                .onSuccess { result -> ApplicationManager.getApplication().invokeLater {
                    if (project == null || !project.isDisposed) onSuccess(result)
                } }
                .onFailure { report(project, operation, it) }
        }
    }
}
