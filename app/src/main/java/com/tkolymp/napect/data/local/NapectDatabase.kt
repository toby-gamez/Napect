package com.tkolymp.napect.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tkolymp.napect.data.local.dao.RecipeDao
import com.tkolymp.napect.data.local.entity.IngredientEntity
import com.tkolymp.napect.data.local.entity.RecipeEntity
import com.tkolymp.napect.data.local.entity.StepEntity

@Database(
    entities = [RecipeEntity::class, IngredientEntity::class, StepEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NapectDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
}
