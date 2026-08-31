package com.github.hyxf.projectmanager.infrastructure.persistence

import com.github.hyxf.projectmanager.feature.project.DuplicateProjectPathException
import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.github.hyxf.projectmanager.feature.project.ProjectRepository
import com.github.hyxf.projectmanager.infrastructure.filesystem.ProjectPaths
import com.intellij.openapi.components.service
import com.intellij.serviceContainer.NonInjectable
import java.nio.file.Path

class PersistentProjectRepository() : ProjectRepository {
    private var injectedStore: ProjectJsonStore? = null
    private val store: ProjectJsonStore get() = injectedStore ?: service()

    @NonInjectable
    constructor(store: ProjectJsonStore) : this() { injectedStore = store }

    override fun getAll(): List<ProjectItem> = store.projects()
    override fun findById(id: String): ProjectItem? = getAll().firstOrNull { it.id == id }
    override fun findByPath(path: Path): ProjectItem? {
        val normalized = ProjectPaths.normalize(path)
        return getAll().firstOrNull { ProjectPaths.normalize(it.path) == normalized }
    }
    override fun add(project: ProjectItem): ProjectItem {
        if (findByPath(project.path) != null) throw DuplicateProjectPathException(project.path)
        store.replaceProjects(getAll() + project.copy(path = ProjectPaths.normalize(project.path)))
        return project
    }
    override fun update(project: ProjectItem): ProjectItem {
        val projects = getAll().toMutableList()
        val index = projects.indexOfFirst { it.id == project.id }
        require(index >= 0) { "Unknown project: ${project.id}" }
        val duplicate = findByPath(project.path)
        if (duplicate != null && duplicate.id != project.id) throw DuplicateProjectPathException(project.path)
        projects[index] = project.copy(path = ProjectPaths.normalize(project.path))
        store.replaceProjects(projects)
        return project
    }
    override fun remove(id: String): Boolean {
        val projects = getAll()
        val remaining = projects.filterNot { it.id == id }
        if (remaining.size == projects.size) return false
        store.replaceProjects(remaining)
        return true
    }
    override fun replaceAll(projects: List<ProjectItem>) = store.replaceProjects(projects)
}
