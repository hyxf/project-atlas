package com.github.hyxf.projectmanager

import com.github.hyxf.projectmanager.feature.project.DuplicateProjectPathException
import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.github.hyxf.projectmanager.feature.project.ProjectManagerService
import com.github.hyxf.projectmanager.feature.project.ProjectImportOutcome
import com.github.hyxf.projectmanager.feature.project.ProjectImportRequest
import com.github.hyxf.projectmanager.feature.project.ProjectRepository
import com.github.hyxf.projectmanager.settings.ProjectManagerSettings
import java.nio.file.Path
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectManagerServiceTest {
    private val repository = MemoryRepository()
    private val service = ProjectManagerService(repository, Clock.fixed(Instant.ofEpochMilli(42), ZoneOffset.UTC))

    @Test
    fun `repository supports add find update and remove`() {
        val saved = service.addProject("Alpha", Path.of("build/alpha"))
        assertEquals(saved, repository.findById(saved.id))
        assertEquals(saved, repository.findByPath(saved.path))

        val updated = service.updateProject(saved.id, "Beta", setOf("Work"), true)
        assertEquals("Beta", repository.findById(saved.id)?.name)
        assertTrue(updated.favorite)
        assertTrue(service.removeProject(saved.id))
        assertNull(repository.findById(saved.id))
    }

    @Test
    fun `duplicate normalized path is rejected`() {
        service.addProject("Alpha", Path.of("build/alpha"))
        assertFailsWith<DuplicateProjectPathException> {
            service.addProject("Duplicate", Path.of("build/../build/alpha"))
        }
    }

    @Test
    fun `favorite and last opened are maintained`() {
        val saved = service.addProject("Alpha", Path.of("build/alpha"))
        assertTrue(service.toggleFavorite(saved.id).favorite)
        assertEquals(42, service.updateLastOpened(saved.path)?.lastOpenedAt)
    }

    @Test
    fun `search matches name path and tag case insensitively with tag AND filter`() {
        service.addProject("Payment Service", Path.of("build/services/payment"), setOf("Java", "Backend"))
        service.addProject("Web", Path.of("build/frontend"), setOf("TypeScript"))

        assertEquals(1, service.searchProjects("PAYMENT").size)
        assertEquals(1, service.searchProjects("services").size)
        assertEquals(1, service.searchProjects("backend").size)
        assertEquals(1, service.searchProjects("", setOf("Java", "Backend")).size)
        assertTrue(service.searchProjects("", setOf("Java", "Frontend")).isEmpty())
        assertEquals(1, service.searchProjects("payment backend").size)
    }

    @Test
    fun `search ranking prefers name prefix over tag and path`() {
        val prefix = service.addProject("Payment", Path.of("build/one"))
        service.addProject("Backend", Path.of("build/payment"), setOf("Payment"))
        assertEquals(prefix.id, service.sortProjects(service.searchProjects("pay"), "pay").first().id)
    }

    @Test
    fun `welcome projects include all items ordered by recent then name`() {
        repository.add(project("never-b", "Beta", "build/beta", null))
        repository.add(project("recent", "Recent", "build/recent", 200))
        repository.add(project("older", "Older", "build/older", 100))
        repository.add(project("never-a", "Alpha", "build/alpha", null))

        assertEquals(
            listOf("recent", "older", "never-a", "never-b"),
            service.projectsByRecent().map(ProjectItem::id),
        )
    }

    @Test
    fun `projects can be sorted by each welcome screen option`() {
        repository.add(project("beta", "Beta", "build/a", 100))
        repository.add(project("alpha", "Alpha", "build/z", 200))

        assertEquals(listOf("alpha", "beta"), service.sortProjects(service.projects(), ProjectManagerSettings.SortBy.NAME).map(ProjectItem::id))
        assertEquals(listOf("beta", "alpha"), service.sortProjects(service.projects(), ProjectManagerSettings.SortBy.PATH).map(ProjectItem::id))
        assertEquals(listOf("alpha", "beta"), service.sortProjects(service.projects(), ProjectManagerSettings.SortBy.RECENT).map(ProjectItem::id))
    }

    @Test
    fun `missing project can be relocated without changing identity`() {
        val saved = service.addProject("Alpha", Path.of("build/old"))
        val relocated = service.relocateProject(saved.id, Path.of("build/new"))
        assertEquals(saved.id, relocated.id)
        assertTrue(relocated.path.endsWith("build/new"))
    }

    @Test
    fun `batch import adds updates and skips existing projects`() {
        val directory = Files.createTempDirectory("project-import-service-test")
        val alpha = Files.createDirectory(directory.resolve("alpha"))
        val added = service.importProjects(
            listOf(ProjectImportRequest(alpha, "Alpha", setOf("Java"), false)),
            updateExisting = false,
        )
        assertEquals(1, added.count(ProjectImportOutcome.ADDED))

        val skipped = service.importProjects(
            listOf(ProjectImportRequest(alpha, "Ignored", emptySet(), false)),
            updateExisting = false,
        )
        assertEquals(1, skipped.count(ProjectImportOutcome.SKIPPED))
        assertEquals("Alpha", service.findByPath(alpha)?.name)

        val updated = service.importProjects(
            listOf(ProjectImportRequest(alpha, "Renamed", setOf("Backend"), true)),
            updateExisting = true,
        )
        assertEquals(1, updated.count(ProjectImportOutcome.UPDATED))
        assertEquals("Renamed", service.findByPath(alpha)?.name)
        assertEquals(setOf("Backend"), service.findByPath(alpha)?.tags)
        assertTrue(service.findByPath(alpha)?.favorite == true)
    }
}

private fun project(id: String, name: String, path: String, lastOpenedAt: Long?) = ProjectItem(
    id = id,
    name = name,
    path = Path.of(path).toAbsolutePath().normalize(),
    lastOpenedAt = lastOpenedAt,
)

private class MemoryRepository : ProjectRepository {
    private val items = linkedMapOf<String, ProjectItem>()
    override fun getAll() = items.values.toList()
    override fun findById(id: String) = items[id]
    override fun findByPath(path: Path) = items.values.firstOrNull { it.path.toAbsolutePath().normalize() == path.toAbsolutePath().normalize() }
    override fun add(project: ProjectItem): ProjectItem {
        if (findByPath(project.path) != null) throw DuplicateProjectPathException(project.path)
        items[project.id] = project
        return project
    }
    override fun update(project: ProjectItem): ProjectItem {
        require(items.containsKey(project.id))
        items[project.id] = project
        return project
    }
    override fun remove(id: String) = items.remove(id) != null
    override fun replaceAll(projects: List<ProjectItem>) {
        items.clear()
        projects.forEach { items[it.id] = it }
    }
}
