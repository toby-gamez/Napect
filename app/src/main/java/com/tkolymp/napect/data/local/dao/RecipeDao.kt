package com.tkolymp.napect.data.local.dao

import androidx.room.*
import com.tkolymp.napect.data.local.entity.IngredientEntity
import com.tkolymp.napect.data.local.entity.RecipeEntity
import com.tkolymp.napect.data.local.entity.RecipeWithDetails
import com.tkolymp.napect.data.local.entity.StepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY created_at DESC")
    fun getAllRecipesWithDetails(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun getRecipeWithDetails(id: Long): Flow<RecipeWithDetails?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<StepEntity>)

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: Long)

    @Query("UPDATE recipes SET is_favorite = :fav WHERE id = :id")
    suspend fun updateFavorite(id: Long, fav: Boolean)

    @Transaction
    suspend fun insertRecipeWithDetails(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        steps: List<StepEntity>
    ): Long {
        val id = insertRecipe(recipe)
        if (ingredients.isNotEmpty()) {
            insertIngredients(ingredients.map { it.copy(recipeId = id) })
        }
        if (steps.isNotEmpty()) {
            insertSteps(steps.map { it.copy(recipeId = id) })
        }
        return id
    }

    @Transaction
    @Query("SELECT * FROM recipes WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' ORDER BY created_at DESC")
    fun search(query: String): Flow<List<RecipeWithDetails>>
}
