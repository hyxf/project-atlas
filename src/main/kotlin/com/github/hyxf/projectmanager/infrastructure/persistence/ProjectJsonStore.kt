package com.github.hyxf.projectmanager.infrastructure.persistence

import com.github.hyxf.projectmanager.feature.project.ProjectItem
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.serviceContainer.NonInjectable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path

class ProjectJsonStore() {
    data class ProjectData(
        var id: String = "",
        var name: String = "",
        var path: String = "",
        var tags: MutableList<String> = mutableListOf(),
        var favorite: Boolean = false,
        var lastOpenedAt: Long? = null,
    )

    data class Data(
        var schemaVersion: Int = CURRENT_SCHEMA_VERSION,
        var projects: MutableList<ProjectData> = mutableListOf(),
        var settings: SettingsData = SettingsData(),
    )

    data class SettingsData(
        var defaultOpenMode: String = "CURRENT_WINDOW",
        var sortBy: String = "NAME",
        var selectedFilter: String = "ALL",
        var selectedView: String = "LIST",
        var selectedListFilter: String = "ALL",
        var tagProjectSpacing: Int = 4,
        var listProjectSpacing: Int = 4,
    )

    private val logger = Logger.getInstance(ProjectJsonStore::class.java)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private var file: Path = defaultFile()
    private var data = Data()
    private var loaded = false
    private var lastModified = -1L
    private var sourceJson = JsonObject()
    private var loadWarning: String? = null
    private var loadFailed = false

    @NonInjectable
    constructor(file: Path) : this() {
        this.file = file
    }

    @Synchronized
    fun projects(): List<ProjectItem> {
        reloadIfChanged()
        return data.projects.mapNotNull(::toModel)
    }

    @Synchronized
    fun replaceProjects(projects: List<ProjectItem>) {
        reloadIfChanged()
        checkWritable()
        data.projects = projects.map(::toState).toMutableList()
        save()
    }

    @Synchronized
    fun settings(): SettingsData {
        reloadIfChanged()
        return data.settings.copy()
    }

    @Synchronized
    fun replaceSettings(settings: SettingsData) {
        reloadIfChanged()
        checkWritable()
        data.settings = settings
        save()
    }

    @Synchronized
    fun ensureFile(): Path {
        reloadIfChanged()
        if (!Files.exists(file)) save()
        return file
    }

    @Synchronized
    fun forceReload() {
        loaded = false
        reloadIfChanged()
    }

    @Synchronized
    fun consumeLoadWarning(): String? = loadWarning.also { loadWarning = null }

    private fun reloadIfChanged() {
        if (ApplicationManager.getApplication()?.isDispatchThread == true) return
        val modified = runCatching { if (Files.exists(file)) Files.getLastModifiedTime(file).toMillis() else -1L }.getOrDefault(-1L)
        if (loaded && modified == lastModified) return
        if (!Files.exists(file)) {
            data = Data()
            sourceJson = JsonObject()
            loaded = true
            lastModified = -1L
            loadWarning = null
            loadFailed = false
            return
        }
        runCatching {
            val root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).asJsonObject
            data = migrate(root)
            sourceJson = root.deepCopy()
            loaded = true
            lastModified = modified
            loadWarning = null
            loadFailed = false
        }.onFailure {
            logger.warn("[ProjectRepository] Cannot reload project.json; keeping last valid data", it)
            loadWarning = "Could not read project.json; the last valid data is still in use. Open the file to fix it."
            loadFailed = true
        }
    }

    private fun checkWritable() {
        check(!loadFailed) {
            "project.json could not be read. Fix the file and refresh before making changes."
        }
    }

    private fun save() {
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, "project", ".json.tmp")
        try {
            val json = mergeWithUnknownFields()
            Files.writeString(temporary, gson.toJson(json), StandardCharsets.UTF_8)
            runCatching {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
            loaded = true
            lastModified = Files.getLastModifiedTime(file).toMillis()
            sourceJson = json.deepCopy()
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun migrate(root: JsonObject): Data {
        val projects = root.elements("projects").mapNotNull(::parseProject).toMutableList()
        val settingsJson = root.objectValue("settings")
        val settings = SettingsData(
            defaultOpenMode = settingsJson?.string("defaultOpenMode", "CURRENT_WINDOW") ?: "CURRENT_WINDOW",
            sortBy = settingsJson?.string("sortBy", "NAME") ?: "NAME",
            selectedFilter = settingsJson?.string("selectedFilter", "ALL") ?: "ALL",
            selectedView = settingsJson?.string("selectedView", "LIST") ?: "LIST",
            selectedListFilter = settingsJson?.string("selectedListFilter", "ALL") ?: "ALL",
            tagProjectSpacing = settingsJson?.int("tagProjectSpacing", 4) ?: 4,
            listProjectSpacing = settingsJson?.int("listProjectSpacing", 4) ?: 4,
        )
        return Data(CURRENT_SCHEMA_VERSION, projects, settings)
    }

    private fun mergeWithUnknownFields(): JsonObject {
        val root = sourceJson.deepCopy()
        root.addProperty("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.remove("tags")
        val oldProjects = sourceJson.elements("projects").mapNotNull { element ->
            runCatching { element.asJsonObject }.getOrNull()?.let { it.string("id") to it }
        }.toMap()
        val projectsJson = com.google.gson.JsonArray()
        data.projects.forEach { project ->
            val merged = oldProjects[project.id]?.deepCopy() ?: JsonObject()
            merged.remove("createdAt")
            merged.remove("updatedAt")
            gson.toJsonTree(project).asJsonObject.entrySet().forEach { (key, value) -> merged.add(key, value) }
            projectsJson.add(merged)
        }
        sourceJson.elements("projects")
            .filter { parseProject(it) == null }
            .forEach { projectsJson.add(it.deepCopy()) }
        root.add("projects", projectsJson)
        val settingsJson = sourceJson.objectValue("settings")?.let { it.deepCopy() } ?: JsonObject()
        settingsJson.remove("importScanDepth")
        gson.toJsonTree(data.settings).asJsonObject.entrySet().forEach { (key, value) -> settingsJson.add(key, value) }
        root.add("settings", settingsJson)
        return root
    }

    private fun parseProject(element: com.google.gson.JsonElement): ProjectData? = runCatching {
        val value = element.asJsonObject
        ProjectData(
            id = value.string("id"),
            name = value.string("name"),
            path = value.string("path"),
            tags = value.stringList("tags").toMutableList(),
            favorite = value.boolean("favorite"),
            lastOpenedAt = value.longOrNull("lastOpenedAt"),
        ).takeIf { it.id.isNotBlank() && it.path.isNotBlank() }
    }.getOrNull()

    private fun JsonObject.array(name: String) = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonObject.elements(name: String) = array(name)?.let { values ->
        (0 until values.size()).map { index -> values[index] }
    }.orEmpty()
    private fun JsonObject.objectValue(name: String) = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.string(name: String, default: String = "") =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: default
    private fun JsonObject.boolean(name: String, default: Boolean = false) = booleanOrNull(name) ?: default
    private fun JsonObject.booleanOrNull(name: String) = runCatching { get(name)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull()
    private fun JsonObject.longOrNull(name: String) = runCatching { get(name)?.takeIf { !it.isJsonNull }?.asLong }.getOrNull()
    private fun JsonObject.int(name: String, default: Int = 0) =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: default
    private fun JsonObject.stringList(name: String) = elements(name).mapNotNull {
        runCatching { it.takeIf { value -> value.isJsonPrimitive }?.asString }.getOrNull()
    }

    private fun toModel(value: ProjectData): ProjectItem? = runCatching {
        ProjectItem(value.id, value.name, Path(value.path), value.tags.toSet(), value.favorite,
            value.lastOpenedAt)
    }.getOrNull()

    private fun toState(value: ProjectItem) = ProjectData(
        value.id, value.name, value.path.toString(), value.tags.sorted().toMutableList(), value.favorite,
        value.lastOpenedAt,
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 4
        fun defaultFile(): Path = Path(System.getProperty("user.home"), ".project-manager", "project.json")
    }
}
