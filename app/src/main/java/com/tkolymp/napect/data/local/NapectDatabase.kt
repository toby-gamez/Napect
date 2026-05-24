package com.tkolymp.napect.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tkolymp.napect.data.local.dao.RecipeDao
import com.tkolymp.napect.data.local.entity.IngredientEntity
import com.tkolymp.napect.data.local.entity.IngredientGroupEntity
import com.tkolymp.napect.data.local.entity.RecipeEntity
import com.tkolymp.napect.data.local.entity.StepEntity
import com.tkolymp.napect.data.local.entity.TagEntity
import com.tkolymp.napect.data.local.entity.RecipeFts
import com.tkolymp.napect.data.local.entity.RecipeTagCrossRef
import com.tkolymp.napect.data.local.dao.TagDao

@Database(
    entities = [
        RecipeEntity::class,
        IngredientGroupEntity::class,
        IngredientEntity::class,
        StepEntity::class,
        TagEntity::class,
        RecipeTagCrossRef::class,
        RecipeFts::class
    ],
    // version 6: fix tags index naming (idx_tags_name → index_tags_name)
    // version 7: add photo_path column for file-based photo storage
    // version 8: add FTS4 full-text search (recipe_fts)
    // version 9: drop custom FTS triggers from v8 migration (conflict with Room auto-generated ones)
    // version 10: add nutrition columns (calories_kcal, fat_g, carbs_g, proteins_g, nutri_score)
    version = 10,
    exportSchema = true
)
abstract class NapectDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun tagDao(): TagDao
}
