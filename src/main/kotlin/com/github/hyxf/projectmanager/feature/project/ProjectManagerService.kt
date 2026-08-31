package com.github.hyxf.projectmanager.feature.project

import com.github.hyxf.projectmanager.infrastructure.filesystem.ProjectPaths
import com.github.hyxf.projectmanager.settings.ProjectManagerSettings
import com.github.hyxf.projectmanager.feature.tag.TagRepository
import com.intellij.openapi.components.service
import com.intellij.serviceContainer.NonInjectable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.UUID

class ProjectManagerService() {
    private var injectedRepository: ProjectRepository? = null
    private val repository: ProjectRepository
        get() = injectedRepository ?: service()
    private var clock: Clock = Clock.systemUTC()

    @NonInjectable
    constructor(repository: ProjectRepository, clock: Clock) : this() {
        this.injectedRepository = repository
        this.clock = clock
    }

    fun projects(): List<ProjectItem> = repository.getAll()

    fun projectsByRecent(): List<ProjectItem> = sortProjects(projects(), ProjectManagerSettings.SortBy.RECENT)

    fun saveProject(name: String, path: Path, tags: Set<String>, favorite: Boolean): ProjectItem {
        val normalized = ProjectPaths.normalize(path)
        val existing = repository.findByPath(normalized)
        return if (existing == null) addProject(name, normalized, tags, favorite)
        else updateProject(existing.id, name, tags, favorite)
    }

    fun addProject(name: String, path: Path, tags: Set<String> = emptySet(), favorite: Boolean = false): ProjectItem {
        val normalized = ProjectPaths.normalize(path)
        require(name.isNotBlank()) { "Project name must not be empty" }
        if (repository.findByPath(normalized) != null) throw DuplicateProjectPathException(normalized)
        val now = clock.millis()
        return repository.add(ProjectItem(UUID.randomUUID().toString(), name.trim(), normalized,
            cleanTags(tags), favorite, null, now, now))
    }

    fun updateProject(id: String, name: String, tags: Set<String>, favorite: Boolean): ProjectItem {
        val current = requireNotNull(repository.findById(id)) { "Unknown project: $id" }
        require(name.isNotBlank()) { "Project name must not be empty" }
        return repository.update(current.copy(name = name.trim(), tags = cleanTags(tags),
            favorite = favorite, updatedAt = clock.millis()))
    }

    fun relocateProject(id: String, path: Path): ProjectItem {
        val current = requireNotNull(repository.findById(id)) { "Unknown project: $id" }
        val normalized = ProjectPaths.normalize(path)
        val duplicate = repository.findByPath(normalized)
        if (duplicate != null && duplicate.id != id) throw DuplicateProjectPathException(normalized)
        return repository.update(current.copy(path = normalized, updatedAt = clock.millis()))
    }

    fun removeProject(id: String): Boolean = repository.remove(id)
    fun findProject(id: String): ProjectItem? = repository.findById(id)
    fun findByPath(path: Path): ProjectItem? = repository.findByPath(ProjectPaths.normalize(path))

    fun toggleFavorite(id: String): ProjectItem {
        val current = requireNotNull(repository.findById(id)) { "Unknown project: $id" }
        return repository.update(current.copy(favorite = !current.favorite, updatedAt = clock.millis()))
    }

    fun addTag(id: String, tag: String): ProjectItem {
        val current = requireNotNull(repository.findById(id)) { "Unknown project: $id" }
        return updateProject(id, current.name, current.tags + tag, current.favorite)
    }

    fun removeTag(id: String, tag: String): ProjectItem {
        val current = requireNotNull(repository.findById(id)) { "Unknown project: $id" }
        return updateProject(id, current.name, current.tags - tag, current.favorite)
    }

    fun renameTag(oldTag: String, newTag: String) {
        projects().filter { oldTag in it.tags }.forEach {
            updateProject(it.id, it.name, it.tags - oldTag + newTag, it.favorite)
        }
        tagRepository()?.rename(oldTag, newTag)
    }

    fun deleteTag(tag: String) {
        projects().filter { tag in it.tags }.forEach { removeTag(it.id, tag) }
        tagRepository()?.remove(tag)
    }

    fun createTag(tag: String) {
        val cleaned = tag.trim()
        require(cleaned.isNotEmpty()) { "Tag name must not be empty" }
        tagRepository()?.add(cleaned)
    }

    fun updateLastOpened(path: Path): ProjectItem? {
        val current = findByPath(path) ?: return null
        return repository.update(current.copy(lastOpenedAt = clock.millis()))
    }

    fun importProjects(requests: List<ProjectImportRequest>, updateExisting: Boolean): ProjectImportSummary {
        val projects = repository.getAll().toMutableList()
        val byPath = projects.associateBy { ProjectPaths.normalize(it.path) }.toMutableMap()
        val seen = mutableSetOf<Path>()
        val results = mutableListOf<ProjectImportResult>()
        var changed = false
        requests.forEach { request ->
            val path = ProjectPaths.normalize(request.path)
            when {
                !seen.add(path) -> results += ProjectImportResult(path, ProjectImportOutcome.SKIPPED, "Duplicate in import selection")
                !Files.isDirectory(path) -> results += ProjectImportResult(path, ProjectImportOutcome.FAILED, "Directory does not exist")
                request.name.isBlank() -> results += ProjectImportResult(path, ProjectImportOutcome.FAILED, "Project name is empty")
                byPath[path] != null && !updateExisting ->
                    results += ProjectImportResult(path, ProjectImportOutcome.SKIPPED, "Project already exists")
                byPath[path] != null -> {
                    val existing = byPath.getValue(path)
                    val updated = existing.copy(
                        name = request.name.trim(),
                        tags = cleanTags(request.tags),
                        favorite = request.favorite,
                        updatedAt = clock.millis(),
                    )
                    projects[projects.indexOfFirst { it.id == existing.id }] = updated
                    byPath[path] = updated
                    changed = true
                    results += ProjectImportResult(path, ProjectImportOutcome.UPDATED)
                }
                else -> {
                    val now = clock.millis()
                    val added = ProjectItem(
                        UUID.randomUUID().toString(), request.name.trim(), path, cleanTags(request.tags),
                        request.favorite, null, now, now,
                    )
                    projects += added
                    byPath[path] = added
                    changed = true
                    results += ProjectImportResult(path, ProjectImportOutcome.ADDED)
                }
            }
        }
        if (changed) repository.replaceAll(projects)
        return ProjectImportSummary(results)
    }

    fun searchProjects(query: String, requiredTags: Set<String> = emptySet()): List<ProjectItem> {
        return searchProjects(projects(), query, requiredTags)
    }

    fun searchProjects(
        items: List<ProjectItem>,
        query: String,
        requiredTags: Set<String> = emptySet(),
    ): List<ProjectItem> {
        val needles = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
        return items.filter { project ->
            val searchable = buildList {
                add(project.name.lowercase())
                add(project.path.toString().lowercase())
                addAll(project.tags.map(String::lowercase))
            }
            requiredTags.all(project.tags::contains) && needles.all { needle -> searchable.any { it.contains(needle) } }
        }
    }

    fun sortProjects(items: List<ProjectItem>, query: String = ""): List<ProjectItem> {
        if (query.isNotBlank()) return items.sortedWith(
            compareByDescending<ProjectItem> { matchScore(it, query) }
                .thenByDescending(ProjectItem::favorite)
                .thenByDescending { it.lastOpenedAt ?: 0L }
                .thenBy { it.name.lowercase() },
        )
        return sortProjects(items, service<ProjectManagerSettings>().state.sortBy)
    }

    fun sortProjects(items: List<ProjectItem>, sortBy: ProjectManagerSettings.SortBy): List<ProjectItem> = when (sortBy) {
        ProjectManagerSettings.SortBy.NAME -> items.sortedWith(
            compareBy<ProjectItem> { it.name.lowercase() }.thenBy { it.path.toString().lowercase() },
        )
        ProjectManagerSettings.SortBy.PATH -> items.sortedBy { it.path.toString().lowercase() }
        ProjectManagerSettings.SortBy.RECENT -> items.sortedWith(
            compareByDescending<ProjectItem> { it.lastOpenedAt ?: Long.MIN_VALUE }
                .thenBy { it.name.lowercase() }
                .thenBy { it.path.toString().lowercase() },
        )
        ProjectManagerSettings.SortBy.SAVED -> items.sortedWith(
            compareByDescending<ProjectItem>(ProjectItem::updatedAt)
                .thenBy { it.name.lowercase() }
                .thenBy { it.path.toString().lowercase() },
        )
    }

    internal fun matchScore(project: ProjectItem, query: String): Int = query.trim().lowercase()
        .split(Regex("\\s+")).filter(String::isNotEmpty).fold(0) { score, needle ->
            val name = project.name.lowercase()
            val points = when {
                name.startsWith(needle) -> 100
                name.contains(needle) -> 70
                project.tags.any { it.lowercase().contains(needle) } -> 45
                project.path.toString().lowercase().contains(needle) -> 25
                else -> 0
            }
            score + points
        }

    fun tags(): Set<String> = projects().flatMapTo(sortedSetOf()) { it.tags }.apply {
        tagRepository()?.getAll()?.let(::addAll)
    }

    private fun tagRepository(): TagRepository? = if (injectedRepository != null) null else service()

    private fun cleanTags(tags: Set<String>): Set<String> = tags.map(String::trim).filter(String::isNotEmpty).toSet()
}
