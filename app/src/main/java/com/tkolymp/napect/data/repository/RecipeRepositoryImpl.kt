package com.tkolymp.napect.data.repository

import com.tkolymp.napect.data.local.dao.RecipeDao
import com.tkolymp.napect.data.local.dao.TagDao
import com.tkolymp.napect.data.ai.TagSuggester
import com.tkolymp.napect.data.ai.TagSuggestion
import com.tkolymp.napect.data.local.entity.TagEntity
import com.tkolymp.napect.domain.model.TagGroup
import com.tkolymp.napect.domain.model.Category
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
        // Normalize name: trim and Title-case to reduce duplicates
        val canonical = name.trim().replace(Regex("\\s+"), " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val entity = TagEntity(name = canonical, group = group.name, isAiGenerated = 0, isUserCreated = 1)
        val id = tagDao.insertTag(entity)
        // if insert ignored (existing), fetch it (case-insensitive lookup)
        val final = if (id <= 0) tagDao.getTagByName(canonical)!! else entity.copy(id = id)
        return final.toDomain()
    }

    override suspend fun suggestTagsForRecipe(recipe: Recipe): TagSuggestion {
        val text = listOfNotNull(recipe.title, recipe.summary).joinToString(" ") + " " + recipe.ingredients.joinToString(" ") { it.name } + " " + recipe.steps.joinToString(" ") { it.instruction }
        val suggestions = TagSuggester.suggest(text)
        val confirmed = mutableListOf<com.tkolymp.napect.domain.model.Tag>()
        val created = mutableListOf<com.tkolymp.napect.domain.model.Tag>()
        for ((name, group) in suggestions) {
            // Normalize suggestion name to canonical capitalization before lookup/insert
            val canonical = name.trim().replace(Regex("\\s+"), " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val existing = tagDao.getTagByName(canonical)
            if (existing != null) {
                confirmed.add(existing.toDomain())
            } else {
                val newEntity = TagEntity(name = canonical, group = group.name, isAiGenerated = 1, isUserCreated = 0)
                val newId = tagDao.insertTag(newEntity)
                val final = if (newId <= 0) tagDao.getTagByName(canonical)!! else newEntity.copy(id = newId)
                created.add(final.toDomain())
            }
        }
        return TagSuggestion(confirmed = confirmed, newlyCreated = created)
    }

    override suspend fun deleteTag(id: Long) {
        // Remove cross references first, then delete the tag row
        tagDao.deleteRecipeTagsByTagId(id)
        tagDao.deleteTagById(id)
    }

    override suspend fun ensureDefaultTags(): Int {
        // Insert missing default tags from centralized list. Return number of inserted tags.
        var inserted = 0
        for ((name, group) in com.tkolymp.napect.data.local.DEFAULT_TAGS) {
            // use canonical capitalization
            val canonical = name.trim().replace(Regex("\\s+"), " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val entity = TagEntity(name = canonical, group = group.name, isAiGenerated = 0, isUserCreated = 0)
            val id = tagDao.insertTag(entity)
            if (id > 0) inserted++
        }
        return inserted
    }

    override suspend fun saveRecipeWithTags(recipe: Recipe, tagIds: List<Long>): Long {
        // Derive category from tags when possible. Priority:
        // 1) Tag with TagGroup.CATEGORY
        // 2) Any tag whose name maps to Category enum
        // 3) Any DIET-group tag -> Category.DIET
        // 4) Fallback to recipe.category
        val derivedCategory = if (tagIds.isNotEmpty()) {
            val tagEntities = tagDao.getTagsByIds(tagIds)
            // first try explicit CATEGORY-tag
            val catTag = tagEntities.firstOrNull { it.group == TagGroup.CATEGORY.name }
            if (catTag != null) {
                val normalized = catTag.name.trim().replace(Regex("\\s+"), "_").uppercase()
                try { Category.valueOf(normalized) } catch (e: Exception) { recipe.category }
            } else {
                // try mapping any tag name directly to Category
                val byName = tagEntities.mapNotNull { t ->
                    val norm = t.name.trim().replace(Regex("\\s+"), "_").uppercase()
                    try { Category.valueOf(norm) } catch (_: Exception) { null }
                }.firstOrNull()
                if (byName != null) byName
                else if (tagEntities.any { it.group == TagGroup.DIET.name }) Category.DIET
                else recipe.category
            }
        } else recipe.category

        val toSave = recipe.copy(category = derivedCategory)
        val id = if (toSave.id == 0L) {
            dao.insertRecipeWithDetails(toSave.toEntity(), toSave.ingredients.map { it.toEntity() }, toSave.steps.map { it.toEntity() })
        } else {
            dao.updateRecipe(toSave.toEntity())
            toSave.id
        }
        dao.setRecipeTags(id, tagIds)
        return id
    }
}
