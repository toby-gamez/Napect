package com.tkolymp.napect.domain.model

import java.util.*

data class Recipe(
    val id: Long = 0L,
    val title: String,
    val summary: String? = null,
    val sourceUrl: String? = null,
    val sourceNote: String? = null,
    val isFavorite: Boolean = false,
    val category: Category = Category.UNKNOWN,
    val photo: ByteArray? = null,
    val servingsBase: Int = 1,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<Step> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
)
