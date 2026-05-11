package com.tkolymp.napect.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "ingredients",
    foreignKeys = [ForeignKey(
        entity = RecipeEntity::class,
        parentColumns = ["id"],
        childColumns = ["recipe_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recipe_id") val recipeId: Long,
    val amount: Double = 0.0,
    val unit: String? = null,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)
