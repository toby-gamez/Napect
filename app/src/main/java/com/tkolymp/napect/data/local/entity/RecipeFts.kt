package com.tkolymp.napect.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = RecipeEntity::class)
@Entity(tableName = "recipe_fts")
data class RecipeFts(
    val title: String = "",
    val summary: String = "",
)
