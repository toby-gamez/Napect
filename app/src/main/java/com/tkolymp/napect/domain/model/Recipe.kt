package com.tkolymp.napect.domain.model

import java.time.Instant

data class Recipe(
    val id: Long = 0L,
    val title: String,
    val summary: String? = null,
    val sourceUrl: String? = null,
    val sourceNote: String? = null,
    val isFavorite: Boolean = false,
    val category: Category = Category.UNKNOWN,
    val photo: ByteArray? = null,
    val photoPath: String? = null,
    val servingsBase: Int = 1,
    val ingredientGroups: List<IngredientGroup> = emptyList(),
    val steps: List<Step> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    /** Nutritional values per serving. Total = value * servingsBase. */
    val caloriesKcal: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
    val proteinsG: Double? = null,
    val nutriScore: String? = null,
) {
    /** Flat list of all ingredients across all groups, preserving group order. */
    val allIngredients: List<Ingredient> get() = ingredientGroups.flatMap { it.ingredients }
}

