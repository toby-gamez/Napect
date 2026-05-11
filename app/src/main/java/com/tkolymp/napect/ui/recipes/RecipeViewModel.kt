package com.tkolymp.napect.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.repository.RecipeRepository
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

class RecipeViewModel(private val repo: RecipeRepository) : ViewModel() {
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

    fun createRecipe(recipe: Recipe, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.createRecipe(recipe)
            onComplete(id)
        }
    }

    fun getRecipeById(id: Long) = repo.getRecipeById(id)

    fun toggleFavorite(id: Long, value: Boolean) {
        viewModelScope.launch { repo.toggleFavorite(id, value) }
    }
}
