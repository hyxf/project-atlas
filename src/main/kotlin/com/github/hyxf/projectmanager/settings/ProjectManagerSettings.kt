package com.github.hyxf.projectmanager.settings

import com.github.hyxf.projectmanager.infrastructure.persistence.ProjectJsonStore
import com.intellij.openapi.components.service

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
            )
        }

    fun update(value: Data) {
        service<ProjectJsonStore>().replaceSettings(
            ProjectJsonStore.SettingsData(
                defaultOpenMode = value.defaultOpenMode.name,
                sortBy = value.sortBy.name,
                selectedFilter = value.listFilter.name,
                selectedView = value.viewMode.name,
                selectedListFilter = value.listFilter.name,
            ),
        )
    }

    fun updateSortBy(value: SortBy) = update(state.copy(sortBy = value))
    fun updateViewMode(value: ViewMode) = update(state.copy(viewMode = value))
    fun updateListFilter(value: ListFilter) = update(state.copy(listFilter = value))

    private fun parseSortBy(value: String) = when (value) {
        "RECENTLY_OPENED" -> SortBy.RECENT
        "SMART" -> SortBy.NAME
        else -> runCatching { SortBy.valueOf(value) }.getOrDefault(SortBy.NAME)
    }
}
