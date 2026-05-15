package com.tkolymp.napect.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ingredient_groups",
    foreignKeys = [ForeignKey(
        entity = RecipeEntity::class,
        parentColumns = ["id"],
        childColumns = ["recipe_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["recipe_id"])]
)
data class IngredientGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recipe_id") val recipeId: Long,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)
