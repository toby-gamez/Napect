package com.tkolymp.napect.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "recipe_tags",
    primaryKeys = ["recipe_id", "tag_id"],
    indices = [Index("recipe_id"), Index("tag_id")]
)
data class RecipeTagCrossRef(
    @ColumnInfo(name = "recipe_id") val recipeId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)
