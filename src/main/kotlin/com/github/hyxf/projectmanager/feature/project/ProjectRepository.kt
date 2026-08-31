package com.github.hyxf.projectmanager.feature.project

import java.nio.file.Path

interface ProjectRepository {
    fun getAll(): List<ProjectItem>
    fun findById(id: String): ProjectItem?
    fun findByPath(path: Path): ProjectItem?
    fun add(project: ProjectItem): ProjectItem
    fun update(project: ProjectItem): ProjectItem
    fun remove(id: String): Boolean
    fun replaceAll(projects: List<ProjectItem>)
}

class DuplicateProjectPathException(path: Path) : IllegalArgumentException("Project already exists: $path")
