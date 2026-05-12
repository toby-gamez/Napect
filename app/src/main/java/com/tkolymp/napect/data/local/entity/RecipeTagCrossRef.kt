package com.tkolymp.napect.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "recipe_tags",
    primaryKeys = ["recipe_id", "tag_id"]
)
data class RecipeTagCrossRef(
    @ColumnInfo(name = "recipe_id") val recipeId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)
