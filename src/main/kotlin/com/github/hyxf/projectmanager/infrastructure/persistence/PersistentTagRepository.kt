package com.github.hyxf.projectmanager.infrastructure.persistence

import com.github.hyxf.projectmanager.feature.tag.TagRepository
import com.intellij.openapi.components.service

class PersistentTagRepository : TagRepository {
    private val store: ProjectJsonStore
        get() = service()

    override fun getAll(): Set<String> = store.tags()
    override fun add(tag: String) {
        if (tag.isNotBlank()) store.replaceTags(store.tags() + tag)
    }
    override fun rename(oldTag: String, newTag: String) {
        remove(oldTag); add(newTag)
    }
    override fun remove(tag: String) { store.replaceTags(store.tags() - tag) }
}
