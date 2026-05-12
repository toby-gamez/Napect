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

    // Tags
    fun getAllTags(): Flow<List<com.tkolymp.napect.domain.model.Tag>>
    suspend fun createUserTag(name: String, group: com.tkolymp.napect.domain.model.TagGroup): com.tkolymp.napect.domain.model.Tag
    suspend fun suggestTagsForRecipe(recipe: Recipe): com.tkolymp.napect.data.ai.TagSuggestion
    suspend fun saveRecipeWithTags(recipe: Recipe, tagIds: List<Long>): Long
}
