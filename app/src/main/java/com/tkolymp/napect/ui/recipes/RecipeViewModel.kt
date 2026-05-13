package com.tkolymp.napect.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.repository.RecipeRepository
import com.tkolymp.napect.data.ai.RecipeClassifier
import com.tkolymp.napect.domain.model.Category
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.domain.model.TagGroup

class RecipeViewModel(private val repo: RecipeRepository, private val ai: com.tkolymp.napect.data.ai.AiClient? = null) : ViewModel() {
    val recipes: StateFlow<List<Recipe>> = repo.getAllRecipes()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    // debounced search results: empty query returns full list from repo
    val searchResults: StateFlow<List<Recipe>> = _searchQuery
        .debounce(300)
        .flatMapLatest { q -> if (q.isBlank()) repo.getAllRecipes() else repo.search(q) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    // expose search query as read-only flow for UI
    val searchQuery = _searchQuery.asStateFlow()

    // expose an optional error message for UI reporting
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun createRecipe(recipe: Recipe, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val category = if (recipe.category == Category.UNKNOWN) RecipeClassifier.classify(recipe.title, recipe.ingredients.map { it.name }, recipe.steps.map { it.instruction }) else recipe.category
                val summary = recipe.summary ?: ai?.generateSummary(recipe.title, recipe.ingredients, recipe.steps)
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
            } catch (e: Exception) {
                _error.value = e.message ?: "Tag suggestion failed"
            }
        }
    }

    fun createRecipeWithTags(recipe: Recipe, tagIds: List<Long>, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val category = if (recipe.category == Category.UNKNOWN) RecipeClassifier.classify(recipe.title, recipe.ingredients.map { it.name }, recipe.steps.map { it.instruction }) else recipe.category
                val summary = recipe.summary ?: ai?.generateSummary(recipe.title, recipe.ingredients, recipe.steps)
                // If no tag ids provided, attempt to auto-suggest tags using the repository AI suggester.
                val finalTagIds = if (tagIds.isNotEmpty()) {
                    tagIds
                } else {
                    try {
                        val suggestion = repo.suggestTagsForRecipe(recipe)
                        (suggestion.confirmed + suggestion.newlyCreated).map { it.id }
                    } catch (_: Exception) {
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
                val category = if (recipe.category == Category.UNKNOWN) RecipeClassifier.classify(recipe.title, recipe.ingredients.map { it.name }, recipe.steps.map { it.instruction }) else recipe.category
                val summary = recipe.summary ?: ai?.generateSummary(recipe.title, recipe.ingredients, recipe.steps)
                // update recipe core
                repo.updateRecipe(recipe.copy(category = category, summary = summary))
                // If no tag ids provided, attempt to auto-suggest tags and apply them
                val finalTagIds = if (tagIds.isNotEmpty()) {
                    tagIds
                } else {
                    try {
                        val suggestion = repo.suggestTagsForRecipe(recipe)
                        (suggestion.confirmed + suggestion.newlyCreated).map { it.id }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                repo.saveRecipeWithTags(recipe, finalTagIds)
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
                val category = if (recipe.category == Category.UNKNOWN) RecipeClassifier.classify(recipe.title, recipe.ingredients.map { it.name }, recipe.steps.map { it.instruction }) else recipe.category
                val summary = recipe.summary ?: ai?.generateSummary(recipe.title, recipe.ingredients, recipe.steps)
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
                _error.value = if (inserted > 0) "Restored $inserted default tags" else "Default tags already present"
                // optional: could expose a success state if needed
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to restore default tags"
            }
        }
    }
}
