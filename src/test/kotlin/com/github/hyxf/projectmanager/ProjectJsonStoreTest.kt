package com.github.hyxf.projectmanager

import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.github.hyxf.projectmanager.infrastructure.persistence.PersistentProjectRepository
import com.github.hyxf.projectmanager.infrastructure.persistence.ProjectJsonStore
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ProjectJsonStoreTest {
    @Test
    fun `save and reload user json preserves projects`() {
        val directory = Files.createTempDirectory("project-manager-test")
        val file = directory.resolve("project.json")
        val store = ProjectJsonStore(file)
        val repository = PersistentProjectRepository(store)
        repository.add(ProjectItem("id-1", "Alpha", Path("/tmp/alpha"), setOf("Work"), true, 10, 1, 2))

        val reloaded = PersistentProjectRepository(ProjectJsonStore(file))
        assertEquals("Alpha", reloaded.findById("id-1")?.name)
        assertEquals(setOf("Work"), reloaded.findById("id-1")?.tags)
        assertTrue(Files.readString(file).contains("\"schemaVersion\": 2"))
    }

    @Test
    fun `external json edits are reloaded`() {
        val directory = Files.createTempDirectory("project-manager-reload-test")
        val file = directory.resolve("project.json")
        val store = ProjectJsonStore(file)
        store.ensureFile()
        Files.writeString(file, """{"schemaVersion":1,"projects":[],"tags":["Java"]}""")
        store.forceReload()
        assertEquals(setOf("Java"), store.tags())
        assertEquals("CURRENT_WINDOW", store.settings().defaultOpenMode)
    }

    @Test
    fun `settings preserve selected project filter`() {
        val directory = Files.createTempDirectory("project-manager-settings-test")
        val file = directory.resolve("project.json")
        ProjectJsonStore(file).replaceSettings(
            ProjectJsonStore.SettingsData(selectedListFilter = "FAVORITES"),
        )

        assertEquals("FAVORITES", ProjectJsonStore(file).settings().selectedListFilter)
    }

    @Test
    fun `migration handles null fields and preserves unknown data`() {
        val directory = Files.createTempDirectory("project-manager-migration-test")
        val file = directory.resolve("project.json")
        Files.writeString(file, """{
            "schemaVersion": 1,
            "customRoot": "keep-me",
            "projects": [{"id":"id-1","name":"Alpha","path":"/tmp/alpha","tags":null,"customProject":42}],
            "tags": null,
            "settings": {"defaultOpenMode":null,"importScanDepth":4,"customSetting":"keep-me"}
        }""")
        val store = ProjectJsonStore(file)

        assertEquals("Alpha", store.projects().single().name)
        assertEquals("CURRENT_WINDOW", store.settings().defaultOpenMode)
        store.replaceProjects(store.projects())

        val saved = Files.readString(file)
        assertTrue(saved.contains("\"schemaVersion\": 2"))
        assertTrue(saved.contains("\"customRoot\": \"keep-me\""))
        assertTrue(saved.contains("\"customProject\": 42"))
        assertTrue(saved.contains("\"customSetting\": \"keep-me\""))
        assertFalse(saved.contains("\"importScanDepth\""))
    }

    @Test
    fun `corrupted json keeps last valid data and exposes warning`() {
        val directory = Files.createTempDirectory("project-manager-corrupt-test")
        val file = directory.resolve("project.json")
        val store = ProjectJsonStore(file)
        val repository = PersistentProjectRepository(store)
        repository.add(ProjectItem("id-1", "Alpha", Path("/tmp/alpha"), createdAt = 1, updatedAt = 1))
        Files.writeString(file, "{broken")

        store.forceReload()

        assertEquals("Alpha", store.projects().single().name)
        assertTrue(store.consumeLoadWarning()?.contains("last valid data") == true)
    }

    @Test
    fun `corrupted json blocks writes until it is fixed and reloaded`() {
        val directory = Files.createTempDirectory("project-manager-corrupt-write-test")
        val file = directory.resolve("project.json")
        Files.writeString(file, "{broken")
        val store = ProjectJsonStore(file)

        assertFailsWith<IllegalStateException> { store.replaceTags(setOf("Java")) }
        assertEquals("{broken", Files.readString(file))

        Files.writeString(file, """{"schemaVersion":2,"projects":[],"tags":[]}""")
        store.forceReload()
        store.replaceTags(setOf("Java"))
        assertEquals(setOf("Java"), store.tags())
    }

    @Test
    fun `deleting a corrupted json file clears write protection`() {
        val directory = Files.createTempDirectory("project-manager-corrupt-delete-test")
        val file = directory.resolve("project.json")
        Files.writeString(file, "{broken")
        val store = ProjectJsonStore(file)
        store.forceReload()

        Files.delete(file)
        store.forceReload()
        store.replaceTags(setOf("Java"))

        assertEquals(setOf("Java"), store.tags())
    }

    @Test
    fun `saving preserves project entries that cannot be migrated`() {
        val directory = Files.createTempDirectory("project-manager-invalid-entry-test")
        val file = directory.resolve("project.json")
        Files.writeString(file, """{
            "schemaVersion": 2,
            "projects": [
                {"id":"id-1","name":"Alpha","path":"/tmp/alpha"},
                {"name":"Broken","path":"/tmp/broken","custom":"keep-me"},
                "unexpected"
            ],
            "tags": []
        }""")
        val store = ProjectJsonStore(file)

        store.replaceTags(setOf("Java"))

        val saved = Files.readString(file)
        assertTrue(saved.contains("\"custom\": \"keep-me\""))
        assertTrue(saved.contains("\"unexpected\""))
    }
}
