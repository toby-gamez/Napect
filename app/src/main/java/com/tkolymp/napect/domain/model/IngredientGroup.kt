package com.tkolymp.napect.domain.model

data class IngredientGroup(
    val id: Long = 0L,
    val recipeId: Long = 0L,
    val name: String = "Ingredients",
    val sortOrder: Int = 0,
    val ingredients: List<Ingredient> = emptyList(),
)
