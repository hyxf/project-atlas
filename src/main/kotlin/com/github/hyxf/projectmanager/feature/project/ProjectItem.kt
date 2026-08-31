package com.github.hyxf.projectmanager.feature.project

import java.nio.file.Path

data class ProjectItem(
    val id: String,
    val name: String,
    val path: Path,
    val tags: Set<String> = emptySet(),
    val favorite: Boolean = false,
    val lastOpenedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
