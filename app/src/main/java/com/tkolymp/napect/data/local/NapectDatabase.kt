package com.tkolymp.napect.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tkolymp.napect.data.local.dao.RecipeDao
import com.tkolymp.napect.data.local.entity.IngredientEntity
import com.tkolymp.napect.data.local.entity.RecipeEntity
import com.tkolymp.napect.data.local.entity.StepEntity

@Database(
    entities = [RecipeEntity::class, IngredientEntity::class, StepEntity::class],
    // bumped to 2 to allow destructive migration during development when schema changed
    version = 2,
    exportSchema = false
)
abstract class NapectDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
}
