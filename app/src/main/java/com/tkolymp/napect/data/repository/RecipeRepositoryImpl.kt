package com.tkolymp.napect.data.repository

import com.tkolymp.napect.data.local.dao.IngredientGroupInsert
import com.tkolymp.napect.data.local.dao.RecipeDao
import com.tkolymp.napect.data.local.dao.TagDao
import com.tkolymp.napect.data.ai.TagSuggester
import com.tkolymp.napect.data.ai.TagSuggestion
import com.tkolymp.napect.data.local.entity.IngredientGroupEntity
import com.tkolymp.napect.data.local.entity.TagEntity
import com.tkolymp.napect.domain.model.IngredientGroup
import com.tkolymp.napect.domain.model.TagGroup
import com.tkolymp.napect.domain.model.Category
import com.tkolymp.napect.data.mapper.toDomain
import com.tkolymp.napect.data.mapper.toEntity
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.util.Log
import java.text.Normalizer

class RecipeRepositoryImpl(
    private val dao: RecipeDao,
    private val tagDao: TagDao
) : RecipeRepository {

    // ─── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Convert domain ingredient groups into the DAO insert containers, ensuring every
     * recipe has at least a default "Ingredients" section.
     * Note: [IngredientGroupEntity.recipeId] is set here to `this.id` (0 for new recipes)
     * but the DAO always overwrites it with the real generated recipe ID before inserting.
     */
    private fun Recipe.toGroupInserts(): List<IngredientGroupInsert> {
        val thisRecipeId = this.id
        val groups = if (ingredientGroups.isEmpty()) {
            listOf(IngredientGroup(name = ""))
        } else {
            ingredientGroups
        }
        return groups.mapIndexed { idx, group ->
            IngredientGroupInsert(
                group = IngredientGroupEntity(
                    id = group.id,
                    recipeId = thisRecipeId,
                    name = group.name.trim(),
                    sortOrder = idx,
                ),
                ingredients = group.ingredients.mapIndexed { iIdx, ing ->
                    ing.toEntity().copy(sortOrder = iIdx)
                },
            )
        }
    }

    // ─── CRUD ───────────────────────────────────────────────────────────────────

    override suspend fun createRecipe(recipe: Recipe): Long {
        return dao.insertRecipeWithDetails(
            recipe = recipe.toEntity(),
            ingredientGroups = recipe.toGroupInserts(),
            steps = recipe.steps.map { it.toEntity() },
        )
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        dao.updateRecipe(recipe.toEntity())
        dao.replaceRecipeDetails(
            recipeId = recipe.id,
            ingredientGroups = recipe.toGroupInserts(),
            steps = recipe.steps.map { it.toEntity() },
        )
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

    // ─── Tags ────────────────────────────────────────────────────────────────────

    override fun getAllTags() = tagDao.getAllTags().map { list -> list.map { it.toDomain() } }

    override suspend fun createUserTag(name: String, group: TagGroup): com.tkolymp.napect.domain.model.Tag {
        val canonical = name.trim().replace(Regex("\\s+"), " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val entity = TagEntity(name = canonical, group = group.name, isAiGenerated = 0, isUserCreated = 1)
        val id = tagDao.insertTag(entity)
        val final = if (id <= 0) tagDao.getTagByName(canonical)!! else entity.copy(id = id)
        return final.toDomain()
    }

    override suspend fun suggestTagsForRecipe(recipe: Recipe): TagSuggestion {
        val text = listOfNotNull(recipe.title, recipe.summary).joinToString(" ") +
                " " + recipe.allIngredients.joinToString(" ") { it.name } +
                " " + recipe.steps.joinToString(" ") { it.instruction }

        val suggestions = TagSuggester.suggest(text).toMutableSet()
        val existingDifficulties = suggestions.filter { it.second == TagGroup.DIFFICULTY }.toSet()
        if (existingDifficulties.isNotEmpty()) suggestions.removeAll(existingDifficulties)

        fun extractEstimatedMinutes(s: String): Int? {
            val lower = s.lowercase()
            val hMinMatch = Regex("(\\d{1,2})\\s*h(?:ours?)?\\s*(\\d{1,2})\\s*min").find(lower)
            if (hMinMatch != null) {
                val h = hMinMatch.groupValues[1].toIntOrNull() ?: 0
                val m = hMinMatch.groupValues[2].toIntOrNull() ?: 0
                return h * 60 + m
            }
            val hourMatch = Regex("(\\d{1,2}(?:\\.\\d+)?)\\s*h(?:ours?)?").find(lower)
            if (hourMatch != null) {
                val h = hourMatch.groupValues[1].toDoubleOrNull() ?: return null
                return (h * 60).toInt()
            }
            val minMatch = Regex("(\\d{1,3})\\s*min").find(lower)
            if (minMatch != null) return minMatch.groupValues[1].toIntOrNull()
            return null
        }

        val ingCount = recipe.allIngredients.size
        val stepCount = recipe.steps.size
        val estimatedMins = extractEstimatedMinutes(text)
        var score = 0
        if (ingCount <= 6) score-- else if (ingCount > 12) score++
        if (stepCount <= 4) score-- else if (stepCount > 8) score++
        if (estimatedMins != null) {
            if (estimatedMins <= 20) score-- else if (estimatedMins >= 90) score++
        }
        var difficulty = when {
            score <= -1 -> "Easy"
            score >= 1  -> "Hard"
            else        -> "Medium"
        }

        val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
        val normalizedWs = normalized.replace('-', ' ')
        if (normalizedWs.contains("no bake") || normalizedWs.contains("nobake") || normalizedWs.contains("nepecen")) {
            difficulty = "Easy"
        }

        try { Log.d("RecipeRepo", "Inferred difficulty for '${recipe.title}': $difficulty (score=$score, ing=$ingCount, steps=$stepCount, mins=$estimatedMins)") } catch (_: Exception) { }
        suggestions.add(difficulty to TagGroup.DIFFICULTY)

        val confirmed = mutableListOf<com.tkolymp.napect.domain.model.Tag>()
        val created = mutableListOf<com.tkolymp.napect.domain.model.Tag>()
        for ((name, group) in suggestions) {
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
        tagDao.deleteRecipeTagsByTagId(id)
        tagDao.deleteTagById(id)
    }

    override suspend fun ensureDefaultTags(): Int {
        var inserted = 0
        for ((name, group) in com.tkolymp.napect.data.local.DEFAULT_TAGS) {
            val canonical = name.trim().replace(Regex("\\s+"), " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val entity = TagEntity(name = canonical, group = group.name, isAiGenerated = 0, isUserCreated = 0)
            val id = tagDao.insertTag(entity)
            if (id > 0) inserted++
        }
        return inserted
    }

    override suspend fun saveRecipeWithTags(recipe: Recipe, tagIds: List<Long>): Long {
        // Derive category from selected tags when possible.
        val derivedCategory = if (tagIds.isNotEmpty()) {
            val tagEntities = tagDao.getTagsByIds(tagIds)
            val catTag = tagEntities.firstOrNull { it.group == TagGroup.CATEGORY.name }
            if (catTag != null) {
                val normalized = catTag.name.trim().replace(Regex("\\s+"), "_").uppercase()
                try { Category.valueOf(normalized) } catch (e: Exception) { recipe.category }
            } else {
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
            dao.insertRecipeWithDetails(
                recipe = toSave.toEntity(),
                ingredientGroups = toSave.toGroupInserts(),
                steps = toSave.steps.map { it.toEntity() },
            )
        } else {
            dao.updateRecipe(toSave.toEntity())
            dao.replaceRecipeDetails(
                recipeId = toSave.id,
                ingredientGroups = toSave.toGroupInserts(),
                steps = toSave.steps.map { it.toEntity() },
            )
            toSave.id
        }
        dao.setRecipeTags(id, tagIds)
        return id
    }

}
