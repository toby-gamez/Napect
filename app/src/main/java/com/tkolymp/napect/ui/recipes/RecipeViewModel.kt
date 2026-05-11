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

class RecipeViewModel(private val repo: RecipeRepository) : ViewModel() {
    val recipes: StateFlow<List<Recipe>> = repo.getAllRecipes()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createRecipe(recipe: Recipe, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.createRecipe(recipe)
            onComplete(id)
        }
    }
}
