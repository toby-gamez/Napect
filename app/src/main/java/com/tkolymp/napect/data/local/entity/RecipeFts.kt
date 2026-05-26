package com.tkolymp.napect.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = RecipeEntity::class)
@Entity(tableName = "recipe_fts")
data class RecipeFts(
    @ColumnInfo(name = "title_normalized") val titleNormalized: String = "",
    @ColumnInfo(name = "summary_normalized") val summaryNormalized: String = "",
)
