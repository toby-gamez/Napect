package com.tkolymp.napect.data.repository

import com.tkolymp.napect.data.local.dao.RecipeDao
import com.tkolymp.napect.data.local.dao.TagDao
import com.tkolymp.napect.data.ai.TagSuggester
import com.tkolymp.napect.data.ai.TagSuggestion
import com.tkolymp.napect.data.local.entity.TagEntity
import com.tkolymp.napect.domain.model.TagGroup
import com.tkolymp.napect.data.mapper.toDomain
import com.tkolymp.napect.data.mapper.toEntity
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecipeRepositoryImpl(
    private val dao: RecipeDao,
    private val tagDao: TagDao
) : RecipeRepository {
    override suspend fun createRecipe(recipe: Recipe): Long {
        val entity = recipe.toEntity()
        val ingredientEntities = recipe.ingredients.map { it.toEntity() }
        val stepEntities = recipe.steps.map { it.toEntity() }
        // insert recipe together with details in a transaction
        return dao.insertRecipeWithDetails(entity, ingredientEntities, stepEntities)
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

    override fun getAllTags() = tagDao.getAllTags().map { list -> list.map { it.toDomain() } }

    override suspend fun createUserTag(name: String, group: TagGroup): com.tkolymp.napect.domain.model.Tag {
        val entity = TagEntity(name = name, group = group.name, isAiGenerated = 0, isUserCreated = 1)
        val id = tagDao.insertTag(entity)
        // if insert ignored (existing), fetch it
        val final = if (id <= 0) tagDao.getTagByName(name)!! else entity.copy(id = id)
        return final.toDomain()
    }

    override suspend fun suggestTagsForRecipe(recipe: Recipe): TagSuggestion {
        val text = listOfNotNull(recipe.title, recipe.summary).joinToString(" ") + " " + recipe.ingredients.joinToString(" ") { it.name } + " " + recipe.steps.joinToString(" ") { it.instruction }
        val suggestions = TagSuggester.suggest(text)
        val confirmed = mutableListOf<com.tkolymp.napect.domain.model.Tag>()
        val created = mutableListOf<com.tkolymp.napect.domain.model.Tag>()
        for ((name, group) in suggestions) {
            val existing = tagDao.getTagByName(name)
            if (existing != null) {
                confirmed.add(existing.toDomain())
            } else {
                val newEntity = TagEntity(name = name, group = group.name, isAiGenerated = 1, isUserCreated = 0)
                val newId = tagDao.insertTag(newEntity)
                val final = if (newId <= 0) tagDao.getTagByName(name)!! else newEntity.copy(id = newId)
                created.add(final.toDomain())
            }
        }
        return TagSuggestion(confirmed = confirmed, newlyCreated = created)
    }

    override suspend fun saveRecipeWithTags(recipe: Recipe, tagIds: List<Long>): Long {
        val id = if (recipe.id == 0L) {
            dao.insertRecipeWithDetails(recipe.toEntity(), recipe.ingredients.map { it.toEntity() }, recipe.steps.map { it.toEntity() })
        } else {
            dao.updateRecipe(recipe.toEntity())
            recipe.id
        }
        dao.setRecipeTags(id, tagIds)
        return id
    }
}
