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
}
