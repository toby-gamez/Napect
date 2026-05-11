package com.tkolymp.napect.data.repository

import com.tkolymp.napect.data.local.dao.RecipeDao
import com.tkolymp.napect.data.mapper.toDomain
import com.tkolymp.napect.data.mapper.toEntity
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecipeRepositoryImpl(
    private val dao: RecipeDao
) : RecipeRepository {
    override suspend fun createRecipe(recipe: Recipe): Long {
        val entity = recipe.toEntity()
        // we don't handle ingredients/steps yet from domain; minimal create
        return dao.insertRecipe(entity)
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        dao.updateRecipe(recipe.toEntity())
    }

    override suspend fun deleteRecipe(id: Long) {
        dao.deleteRecipeById(id)
    }

    override fun getAllRecipes(): Flow<List<Recipe>> =
        dao.getAllRecipesWithDetails().map { list -> list.map { it.toDomain() } }

    override fun getRecipeById(id: Long): Flow<Recipe?> =
        dao.getRecipeWithDetails(id).map { it?.toDomain() }

    override fun search(query: String): Flow<List<Recipe>> =
        dao.search(query).map { list -> list.map { it.toDomain() } }

    override suspend fun toggleFavorite(id: Long, value: Boolean) {
        dao.updateFavorite(id, value)
    }
}
