package com.tkolymp.napect.domain.usecase

import androidx.paging.PagingData
import com.tkolymp.napect.data.ai.TagSuggestion
import com.tkolymp.napect.domain.model.Category
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.RecipeListItem
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.domain.model.TagGroup
import com.tkolymp.napect.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRecipeRepository : RecipeRepository {
    val savedRecipes = mutableListOf<Recipe>()
    private val tagAssociations = mutableMapOf<Long, MutableList<Long>>()
    private var nextId = 1L
    private val tagStore = mutableMapOf<Long, Tag>()
    private var nextTagId = 1L

    var suggestTagsResult: TagSuggestion = TagSuggestion(emptyList(), emptyList())

    // ── full recipe queries ──
    override fun getAllRecipes(): Flow<List<Recipe>> = MutableStateFlow(savedRecipes.toList())

    override fun getRecipeById(id: Long): Flow<Recipe?> = MutableStateFlow(savedRecipes.find { it.id == id })

    override fun search(query: String): Flow<List<Recipe>> = MutableStateFlow(
        savedRecipes.filter {
            (it.title?.contains(query, ignoreCase = true) ?: false)
        }
    )

    // ── lightweight list queries ──
    override fun getAllRecipeListItems(): Flow<List<RecipeListItem>> =
        MutableStateFlow(savedRecipes.map { it.toListItem() })

    override fun getRecipeListItemsByTag(tagId: Long): Flow<List<RecipeListItem>> =
        MutableStateFlow(savedRecipes.filter { r -> r.tags.any { it.id == tagId } }.map { it.toListItem() })

    override fun searchRecipeListItems(query: String): Flow<List<RecipeListItem>> =
        MutableStateFlow(savedRecipes.filter {
            (it.title?.contains(query, ignoreCase = true) ?: false)
        }.map { it.toListItem() })

    override fun searchRecipeListItemsByTag(tagId: Long, query: String): Flow<List<RecipeListItem>> =
        MutableStateFlow(savedRecipes.filter {
            (it.title?.contains(query, ignoreCase = true) ?: false) && it.tags.any { t -> t.id == tagId }
        }.map { it.toListItem() })

    override fun getPagedRecipeListItems(tagId: Long?, query: String): Flow<PagingData<RecipeListItem>> =
        throw UnsupportedOperationException("Paging not implemented in fake")

    // ── mutations ──
    override suspend fun createRecipe(recipe: Recipe): Long {
        val id = nextId++
        savedRecipes.add(recipe.copy(id = id))
        return id
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        val idx = savedRecipes.indexOfFirst { it.id == recipe.id }
        if (idx >= 0) savedRecipes[idx] = recipe
    }

    override suspend fun deleteRecipe(id: Long) {
        savedRecipes.removeAll { it.id == id }
    }

    override suspend fun toggleFavorite(id: Long, value: Boolean) {
        val idx = savedRecipes.indexOfFirst { it.id == id }
        if (idx >= 0) savedRecipes[idx] = savedRecipes[idx].copy(isFavorite = value)
    }

    override suspend fun saveRecipeWithTags(recipe: Recipe, tagIds: List<Long>): Long {
        savedRecipes.removeAll { it.id == recipe.id }
        val id = if (recipe.id == 0L) nextId++ else recipe.id
        // ensure each tagId has a Tag entry in the store
        val tags = tagIds.map { tid -> tagStore.getOrPut(tid) { Tag(id = tid, name = "tag_$tid", group = TagGroup.OTHER) } }
        savedRecipes.add(recipe.copy(id = id, tags = tags))
        tagAssociations[id] = tagIds.toMutableList()
        return id
    }

    override suspend fun updatePhotoPath(id: Long, path: String?) {
        val idx = savedRecipes.indexOfFirst { it.id == id }
        if (idx >= 0) savedRecipes[idx] = savedRecipes[idx].copy(photoPath = path)
    }

    override suspend fun suggestTagsForRecipe(recipe: Recipe): TagSuggestion = suggestTagsResult

    // ── tags ──
    override fun getAllTags(): Flow<List<Tag>> = MutableStateFlow(emptyList())

    override suspend fun createUserTag(name: String, group: TagGroup): Tag = Tag(name = name, group = group)

    override suspend fun deleteTag(id: Long) {}

    override suspend fun ensureDefaultTags(): Int = 0

    override suspend fun migrateEnglishTagsToCzech(): Int = 0

    private fun Recipe.toListItem() = RecipeListItem(
        id = id,
        title = title ?: "",
        summary = summary,
        photoPath = photoPath,
        isFavorite = isFavorite,
        category = category,
        tags = tags,
        createdAt = createdAt,
    )
}
