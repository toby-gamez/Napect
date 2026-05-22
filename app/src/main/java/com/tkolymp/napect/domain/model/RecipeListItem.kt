package com.tkolymp.napect.domain.model

import java.time.Instant

data class RecipeListItem(
    val id: Long = 0L,
    val title: String,
    val summary: String? = null,
    val photoPath: String? = null,
    val isFavorite: Boolean = false,
    val category: Category = Category.UNKNOWN,
    val tags: List<Tag> = emptyList(),
    val createdAt: Instant = Instant.now(),
)
