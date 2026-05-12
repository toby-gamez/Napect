package com.tkolymp.napect.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipe_id") val ingredients: List<IngredientEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "recipe_id") val steps: List<StepEntity> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = androidx.room.Junction(RecipeTagCrossRef::class, parentColumn = "recipe_id", entityColumn = "tag_id")
    )
    val tags: List<TagEntity> = emptyList(),
)
