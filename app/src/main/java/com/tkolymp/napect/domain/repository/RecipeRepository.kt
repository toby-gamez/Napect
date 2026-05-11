package com.tkolymp.napect.domain.repository

import com.tkolymp.napect.domain.model.Recipe
import kotlinx.coroutines.flow.Flow

interface RecipeRepository {
    suspend fun createRecipe(recipe: Recipe): Long
    suspend fun updateRecipe(recipe: Recipe)
    suspend fun deleteRecipe(id: Long)
    fun getAllRecipes(): Flow<List<Recipe>>
    fun getRecipeById(id: Long): Flow<Recipe?>
    fun search(query: String): Flow<List<Recipe>>
    suspend fun toggleFavorite(id: Long, value: Boolean)
}
