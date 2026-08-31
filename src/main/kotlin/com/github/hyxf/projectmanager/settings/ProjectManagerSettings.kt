package com.github.hyxf.projectmanager.settings

import com.github.hyxf.projectmanager.infrastructure.persistence.ProjectJsonStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic

fun interface ProjectManagerSettingsListener {
    fun settingsChanged(settings: ProjectManagerSettings.Data)

    companion object {
        val TOPIC = Topic.create("Project Atlas settings changed", ProjectManagerSettingsListener::class.java)
    }
}

class ProjectManagerSettings {
    enum class OpenMode { CURRENT_WINDOW, NEW_WINDOW }
    enum class SortBy { NAME, PATH, RECENT, SAVED }
    enum class ViewMode { LIST, TAGS }
    enum class ListFilter(val displayName: String) {
        ALL("All"), RECENT("Recent"), FAVORITES("Favorites")
    }
    data class Data(
        var defaultOpenMode: OpenMode = OpenMode.CURRENT_WINDOW,
        var sortBy: SortBy = SortBy.NAME,
        var viewMode: ViewMode = ViewMode.LIST,
        var listFilter: ListFilter = ListFilter.ALL,
        var tagProjectSpacing: Int = DEFAULT_TAG_PROJECT_SPACING,
        var listProjectSpacing: Int = DEFAULT_LIST_PROJECT_SPACING,
    )

    val state: Data
        get() = service<ProjectJsonStore>().settings().let { stored ->
            Data(
                runCatching { OpenMode.valueOf(stored.defaultOpenMode) }.getOrDefault(OpenMode.CURRENT_WINDOW),
                parseSortBy(stored.sortBy),
                runCatching { ViewMode.valueOf(stored.selectedView) }.getOrElse {
                    if (stored.selectedFilter == "TAG") ViewMode.TAGS else ViewMode.LIST
                },
                runCatching { ListFilter.valueOf(stored.selectedListFilter) }.getOrElse {
                    runCatching { ListFilter.valueOf(stored.selectedFilter) }.getOrDefault(ListFilter.ALL)
                },
                stored.tagProjectSpacing.coerceIn(MIN_TAG_PROJECT_SPACING, MAX_TAG_PROJECT_SPACING),
                stored.listProjectSpacing.coerceIn(MIN_LIST_PROJECT_SPACING, MAX_LIST_PROJECT_SPACING),
            )
        }

    fun update(value: Data) {
        val normalized = value.copy(
            tagProjectSpacing = value.tagProjectSpacing.coerceIn(MIN_TAG_PROJECT_SPACING, MAX_TAG_PROJECT_SPACING),
            listProjectSpacing = value.listProjectSpacing.coerceIn(MIN_LIST_PROJECT_SPACING, MAX_LIST_PROJECT_SPACING),
        )
        service<ProjectJsonStore>().replaceSettings(
            ProjectJsonStore.SettingsData(
                defaultOpenMode = normalized.defaultOpenMode.name,
                sortBy = normalized.sortBy.name,
                selectedFilter = normalized.listFilter.name,
                selectedView = normalized.viewMode.name,
                selectedListFilter = normalized.listFilter.name,
                tagProjectSpacing = normalized.tagProjectSpacing,
                listProjectSpacing = normalized.listProjectSpacing,
            ),
        )
        ApplicationManager.getApplication().messageBus
            .syncPublisher(ProjectManagerSettingsListener.TOPIC)
            .settingsChanged(normalized)
    }

    fun updateSortBy(value: SortBy) = update(state.copy(sortBy = value))
    fun updateViewMode(value: ViewMode) = update(state.copy(viewMode = value))
    fun updateListFilter(value: ListFilter) = update(state.copy(listFilter = value))

    private fun parseSortBy(value: String) = when (value) {
        "RECENTLY_OPENED" -> SortBy.RECENT
        "SMART" -> SortBy.NAME
        else -> runCatching { SortBy.valueOf(value) }.getOrDefault(SortBy.NAME)
    }

    companion object {
        const val DEFAULT_TAG_PROJECT_SPACING = 4
        const val MIN_TAG_PROJECT_SPACING = 0
        const val MAX_TAG_PROJECT_SPACING = 32
        const val DEFAULT_LIST_PROJECT_SPACING = 4
        const val MIN_LIST_PROJECT_SPACING = 0
        const val MAX_LIST_PROJECT_SPACING = 32
    }
}
