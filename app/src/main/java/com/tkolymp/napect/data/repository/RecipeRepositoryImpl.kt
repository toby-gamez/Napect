package com.tkolymp.napect.data.repository

import com.tkolymp.napect.data.local.dao.IngredientGroupInsert
import com.tkolymp.napect.data.local.dao.RecipeDao
import com.tkolymp.napect.data.local.NapectDatabase
import com.tkolymp.napect.data.local.RecipePagingSource
import com.tkolymp.napect.data.local.dao.TagDao
import com.tkolymp.napect.data.ai.TagSuggester
import com.tkolymp.napect.data.ai.TagSuggestion
import com.tkolymp.napect.data.local.entity.IngredientGroupEntity
import com.tkolymp.napect.data.local.entity.TagEntity
import com.tkolymp.napect.domain.model.IngredientGroup
import com.tkolymp.napect.domain.model.TagGroup
import com.tkolymp.napect.domain.model.Category
import com.tkolymp.napect.domain.usecase.DifficultyEstimator
import com.tkolymp.napect.data.mapper.toDomain
import com.tkolymp.napect.data.mapper.toEntity
import com.tkolymp.napect.data.mapper.toDomainListItem
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.RecipeListItem
import com.tkolymp.napect.domain.repository.RecipeRepository
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class RecipeRepositoryImpl(
    private val dao: RecipeDao,
    private val tagDao: TagDao,
    private val db: NapectDatabase,
) : RecipeRepository {

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private fun sanitizeFtsQuery(query: String): String {
        // Normalize diacritics (ř→r, á→a …) so the query matches the normalized FTS index,
        // strip FTS4 syntax chars, then append * to each token for prefix matching.
        val stripped = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        return stripped
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
    }

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

    override suspend fun updatePhotoPath(id: Long, path: String?) {
        dao.updatePhotoPath(id, path)
    }

    override fun getAllRecipes(): Flow<List<Recipe>> =
        dao.getAllRecipesWithDetails().map { list -> list.map { it.toDomain() } }

    override fun getRecipeById(id: Long): Flow<Recipe?> =
        dao.getRecipeWithDetails(id).map { it?.toDomain() }

    override fun search(query: String): Flow<List<Recipe>> =
        dao.search(sanitizeFtsQuery(query)).map { list -> list.map { it.toDomain() } }

    // ─── Lightweight list queries ───────────────────────────────────────────

    override fun getAllRecipeListItems(): Flow<List<RecipeListItem>> =
        dao.getAllRecipeListItems().map { list -> list.map { it.toDomainListItem() } }

    override fun getRecipeListItemsByTag(tagId: Long): Flow<List<RecipeListItem>> =
        dao.getRecipeListItemsByTag(tagId).map { list -> list.map { it.toDomainListItem() } }

    override fun searchRecipeListItems(query: String): Flow<List<RecipeListItem>> =
        dao.searchRecipeListItems(sanitizeFtsQuery(query)).map { list -> list.map { it.toDomainListItem() } }

    override fun searchRecipeListItemsByTag(tagId: Long, query: String): Flow<List<RecipeListItem>> =
        dao.searchRecipeListItemsByTag(tagId, sanitizeFtsQuery(query)).map { list -> list.map { it.toDomainListItem() } }

    // ─── Paged queries ──────────────────────────────────────────────────────

    override fun getPagedRecipeListItems(tagId: Long?, query: String): Flow<PagingData<RecipeListItem>> {
        val sanitized = if (query.isNotBlank()) sanitizeFtsQuery(query) else ""
        return Pager(PagingConfig(pageSize = 20)) {
            RecipePagingSource(dao, db, tagId, sanitized)
        }.flow
    }

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

        val difficulty = DifficultyEstimator.estimate(recipe)
        Timber.d("Inferred difficulty for '%s': %s", recipe.title, difficulty)
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

    override suspend fun migrateEnglishTagsToCzech(): Int {
        val mapping = mapOf(
            "Easy"         to "Jednoduché",
            "Medium"       to "Střední",
            "Hard"         to "Náročné",
            "Quick"        to "Rychlé",
            "Vegetarian"   to "Vegetariánské",
            "Gluten-Free"  to "Bez lepku",
            "Dairy-Free"   to "Bez mléka",
            "Healthy"      to "Zdravé",
            "Italian"      to "Italská",
            "Chinese"      to "Čínská",
            "Mexican"      to "Mexická",
            "Indian"       to "Indická",
            "French"       to "Francouzská",
            "Czech"        to "Česká",
            "American"     to "Americká",
            "Japanese"     to "Japonská",
            "Chicken"      to "Kuřecí",
            "Beef"         to "Hovězí",
            "Pork"         to "Vepřové",
            "Pasta"        to "Těstoviny",
            "Rice"         to "Rýže",
            "Dessert"      to "Dezert",
            "Soup"         to "Polévka",
            "Main"         to "Hlavní chod",
            "Breakfast"    to "Snídaně",
            "Lunch"        to "Oběd",
            "Dinner"       to "Večeře",
            "Snack"        to "Svačina",
            "Fried"        to "Smažené",
            "Baked"        to "Pečené",
            "Grilled"      to "Grilované",
            "Steamed"      to "Dušené",
            "Raw"          to "Syrové",
            "Baking"       to "Pečení",
            "Spicy"        to "Pálivé",
            "Budget"       to "Ekonomické",
            "Sweet"        to "Sladké",
            "Savory"       to "Slané",
            "Kid-Friendly" to "Pro děti",
            "One Pot"      to "Jednohrnec",
            "Meal Prep"    to "Příprava jídla",
            "Holiday"      to "Sváteční",
        )
        var migrated = 0
        for ((english, czech) in mapping) {
            val oldTag = tagDao.getTagByName(english) ?: continue
            val czechTag = tagDao.getTagByName(czech) ?: continue
            val recipeIds = tagDao.getRecipeIdsByTagId(oldTag.id)
            val existingCzechIds = tagDao.getRecipeIdsByTagId(czechTag.id).toSet()
            val newRefs = recipeIds
                .filter { it !in existingCzechIds }
                .map { com.tkolymp.napect.data.local.entity.RecipeTagCrossRef(recipeId = it, tagId = czechTag.id) }
            if (newRefs.isNotEmpty()) dao.insertRecipeTagCrossRefs(newRefs)
            tagDao.deleteRecipeTagsByTagId(oldTag.id)
            tagDao.deleteTagById(oldTag.id)
            migrated++
        }
        return migrated
    }

    override suspend fun saveRecipeWithTags(recipe: Recipe, tagIds: List<Long>): Long {
        Timber.d("saveRecipeWithTags: title='%s' tagIds=%s isNew=%b", recipe.title, tagIds, recipe.id == 0L)
        val derivedCategory = if (tagIds.isNotEmpty()) {
            val tagEntities = tagDao.getTagsByIds(tagIds)
            Timber.d("saveRecipeWithTags: tagEntities count=%d", tagEntities.size)
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
        Timber.d("saveRecipeWithTags: derivedCategory=%s", derivedCategory)

        val id = if (toSave.id == 0L) {
            val newId = dao.insertRecipeWithDetails(
                recipe = toSave.toEntity(),
                ingredientGroups = toSave.toGroupInserts(),
                steps = toSave.steps.map { it.toEntity() },
            )
            Timber.d("saveRecipeWithTags: inserted new recipe id=%d", newId)
            newId
        } else {
            dao.updateRecipe(toSave.toEntity())
            dao.replaceRecipeDetails(
                recipeId = toSave.id,
                ingredientGroups = toSave.toGroupInserts(),
                steps = toSave.steps.map { it.toEntity() },
            )
            Timber.d("saveRecipeWithTags: updated existing recipe id=%d", toSave.id)
            toSave.id
        }
        dao.setRecipeTags(id, tagIds)
        Timber.d("saveRecipeWithTags: done id=%d tagsSet=%d", id, tagIds.size)
        return id
    }

}
