package com.tkolymp.napect.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/** Holds an ingredient group and all its ingredients; used as a nested relation. */
data class IngredientGroupWithIngredients(
    @Embedded val group: IngredientGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "group_id")
    val ingredients: List<IngredientEntity> = emptyList(),
)

data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(
        entity = IngredientGroupEntity::class,
        parentColumn = "id",
        entityColumn = "recipe_id"
    )
    val ingredientGroups: List<IngredientGroupWithIngredients> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "recipe_id") val steps: List<StepEntity> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = androidx.room.Junction(RecipeTagCrossRef::class, parentColumn = "recipe_id", entityColumn = "tag_id")
    )
    val tags: List<TagEntity> = emptyList(),
)
