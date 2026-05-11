package com.tkolymp.napect.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tkolymp.napect.domain.repository.RecipeRepository

class RecipeViewModelFactory(private val repo: RecipeRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecipeViewModel::class.java)) {
            return RecipeViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
