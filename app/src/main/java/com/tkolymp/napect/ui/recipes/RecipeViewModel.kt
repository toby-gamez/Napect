package com.tkolymp.napect.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.RecipeListItem
import com.tkolymp.napect.domain.repository.RecipeRepository
import com.tkolymp.napect.domain.model.Category
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.asStateFlow
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.data.ai.AiClient
import timber.log.Timber
import com.tkolymp.napect.domain.model.TagGroup

@HiltViewModel
class RecipeViewModel @Inject constructor(private val repo: RecipeRepository, private val ai: AiClient) : ViewModel() {
    // Lightweight list items (no BLOB, no ingredient/step JOINs)
    val recipeListItems: StateFlow<List<RecipeListItem>> = repo.getAllRecipeListItems()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchListItems: StateFlow<List<RecipeListItem>> = _searchQuery
        .filter { it.length >= 2 || it.isBlank() }
        .debounce(300)
        .flatMapLatest { q -> if (q.isBlank()) repo.getAllRecipeListItems() else repo.searchRecipeListItems(q) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    val searchQuery = _searchQuery.asStateFlow()

    // Combined tag + search filtering (pushed to Room queries)
    private val _selectedTagId = MutableStateFlow<Long?>(null)
    val filteredRecipeListItems: StateFlow<List<RecipeListItem>> = combine(_searchQuery, _selectedTagId) { q, tagId ->
        q to tagId
    }.flatMapLatest { (q, tagId) ->
        val query = if (q.length < 2) "" else q
        val listFlow = when {
            tagId != null && query.isNotBlank() -> repo.searchRecipeListItemsByTag(tagId, query)
            tagId != null -> repo.getRecipeListItemsByTag(tagId)
            query.isNotBlank() -> repo.searchRecipeListItems(query)
            else -> repo.getAllRecipeListItems()
        }
        listFlow
    }.catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Paged recipe list (HOME tab) ────────────────────────────────────────
    val pagedRecipes: Flow<PagingData<RecipeListItem>> = combine(_searchQuery, _selectedTagId) { q, tagId ->
        q to tagId
    }.flatMapLatest { (q, tagId) ->
        val query = if (q.length < 2) "" else q
        repo.getPagedRecipeListItems(tagId, query)
    }.cachedIn(viewModelScope)

    val selectedTagId: StateFlow<Long?> = _selectedTagId.asStateFlow()
    fun setSelectedTagId(tagId: Long?) { _selectedTagId.value = tagId }

    private fun classifyRecipe(title: String?, ingredients: List<String>, steps: List<String>): Category {
        val text = (listOfNotNull(title) + ingredients + steps).joinToString(" ").lowercase()
        val map = mapOf(
            Category.SOUP to listOf("soup", "broth", "bouillon", "polév"),
            Category.DESSERT to listOf("cake", "cookie", "dessert", "pudding", "sweet", "cukr", "koláč"),
            Category.BAKING to listOf("bake", "bread", "yeast", "oven", "pečení", "chléb"),
            Category.BREAKFAST to listOf("breakfast", "porridge", "muesli", "snídan"),
            Category.QUICK to listOf("quick", "30 min", "15 min", "fast", "rychl"),
            Category.DIET to listOf("gluten", "vegan", "vegetarian", "keto", "low carb", "bezlepk")
        )
        for ((cat, keys) in map) {
            for (k in keys) if (text.contains(k)) return cat
        }
        return Category.MAIN
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun createRecipe(recipe: Recipe, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val allIng = recipe.allIngredients
                val category = if (recipe.category == Category.UNKNOWN)
                    classifyRecipe(recipe.title, allIng.map { it.name }, recipe.steps.map { it.instruction })
                else recipe.category
                val summary = recipe.summary ?: ai.generateSummary(recipe.title, allIng, recipe.steps)
                val id = repo.createRecipe(recipe.copy(category = category, summary = summary))
                _error.value = null
                onComplete(id)
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun getRecipeById(id: Long) = repo.getRecipeById(id)

    // Tags
    val allTags = repo.getAllTags()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _suggestedTags = MutableStateFlow<com.tkolymp.napect.data.ai.TagSuggestion?>(null)
    val suggestedTags = _suggestedTags.asStateFlow()

    fun suggestTagsForRecipe(recipe: Recipe) {
        viewModelScope.launch {
            try {
                val suggestion = repo.suggestTagsForRecipe(recipe)
                _suggestedTags.value = suggestion
                Timber.d("suggestTagsForRecipe -> confirmed=%s newly=%s", suggestion.confirmed.map { it.name }, suggestion.newlyCreated.map { it.name })
            } catch (e: Exception) {
                _error.value = e.message ?: "Tag suggestion failed"
            }
        }
    }

    fun createRecipeWithTags(recipe: Recipe, tagIds: List<Long>, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val allIng = recipe.allIngredients
                val category = if (recipe.category == Category.UNKNOWN)
                    classifyRecipe(recipe.title, allIng.map { it.name }, recipe.steps.map { it.instruction })
                else recipe.category
                val summary = recipe.summary ?: ai.generateSummary(recipe.title, allIng, recipe.steps)
                val finalTagIds = if (tagIds.isNotEmpty()) {
                    tagIds
                } else {
                    try {
                        val suggestion = repo.suggestTagsForRecipe(recipe)
                        (suggestion.confirmed + suggestion.newlyCreated).map { it.id }
                    } catch (e: Exception) {
                        Timber.w(e, "Tag suggestion fallback in createRecipeWithTags")
                        emptyList()
                    }
                }
                val id = repo.saveRecipeWithTags(recipe.copy(category = category, summary = summary), finalTagIds)
                _error.value = null
                onComplete(id)
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun updateRecipeWithTags(recipe: Recipe, tagIds: List<Long>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val allIng = recipe.allIngredients
                val category = if (recipe.category == Category.UNKNOWN)
                    classifyRecipe(recipe.title, allIng.map { it.name }, recipe.steps.map { it.instruction })
                else recipe.category
                val summary = recipe.summary ?: ai.generateSummary(recipe.title, allIng, recipe.steps)
                val finalTagIds = if (tagIds.isNotEmpty()) {
                    tagIds
                } else {
                    try {
                        val suggestion = repo.suggestTagsForRecipe(recipe)
                        (suggestion.confirmed + suggestion.newlyCreated).map { it.id }
                    } catch (e: Exception) {
                        Timber.w(e, "Tag suggestion fallback in updateRecipeWithTags")
                        emptyList()
                    }
                }
                repo.saveRecipeWithTags(recipe.copy(category = category, summary = summary), finalTagIds)
                _error.value = null
                onComplete()
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun createUserTag(name: String, group: TagGroup) {
        viewModelScope.launch {
            try {
                repo.createUserTag(name, group)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create tag"
            }
        }
    }

    fun toggleFavorite(id: Long, value: Boolean) {
        viewModelScope.launch { repo.toggleFavorite(id, value) }
    }

    fun updateRecipe(recipe: Recipe, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val allIng = recipe.allIngredients
                val category = if (recipe.category == Category.UNKNOWN)
                    classifyRecipe(recipe.title, allIng.map { it.name }, recipe.steps.map { it.instruction })
                else recipe.category
                val summary = recipe.summary ?: ai.generateSummary(recipe.title, allIng, recipe.steps)
                repo.updateRecipe(recipe.copy(category = category, summary = summary))
                _error.value = null
                onComplete()
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun deleteRecipe(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.deleteRecipe(id)
                _error.value = null
                onComplete()
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun updatePhotoPath(id: Long, path: String?) {
        viewModelScope.launch {
            try {
                repo.updatePhotoPath(id, path)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update photo path"
            }
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch {
            try {
                repo.deleteTag(id)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete tag"
            }
        }
    }

    fun restoreDefaultTags() {
        viewModelScope.launch {
            try {
                val inserted = repo.ensureDefaultTags()
                repo.migrateEnglishTagsToCzech()
                _error.value = if (inserted > 0) "Restored $inserted default tags" else "Default tags already present"
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to restore default tags"
            }
        }
    }
}
