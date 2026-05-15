package com.tkolymp.napect.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tkolymp.napect.data.local.dao.RecipeDao
import com.tkolymp.napect.data.local.entity.IngredientEntity
import com.tkolymp.napect.data.local.entity.IngredientGroupEntity
import com.tkolymp.napect.data.local.entity.RecipeEntity
import com.tkolymp.napect.data.local.entity.StepEntity
import com.tkolymp.napect.data.local.entity.TagEntity
import com.tkolymp.napect.data.local.entity.RecipeTagCrossRef
import com.tkolymp.napect.data.local.dao.TagDao

@Database(
    entities = [
        RecipeEntity::class,
        IngredientGroupEntity::class,
        IngredientEntity::class,
        StepEntity::class,
        TagEntity::class,
        RecipeTagCrossRef::class
    ],
    // bumped to 6 to fix tags index naming (idx_tags_name → index_tags_name) so
    // Room's onValidateSchema no longer fails and tags are visible in the app
    version = 6,
    exportSchema = false
)
abstract class NapectDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun tagDao(): TagDao
}
