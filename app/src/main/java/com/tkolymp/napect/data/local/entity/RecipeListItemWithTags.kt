package com.tkolymp.napect.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class RecipeListItemWithTags(
    @Embedded val recipe: RecipeListItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(RecipeTagCrossRef::class, parentColumn = "recipe_id", entityColumn = "tag_id")
    )
    val tags: List<TagEntity> = emptyList(),
)
