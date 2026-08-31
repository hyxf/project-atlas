package com.github.hyxf.projectmanager.feature.tag

interface TagRepository {
    fun getAll(): Set<String>
    fun add(tag: String)
    fun rename(oldTag: String, newTag: String)
    fun remove(tag: String)
}
