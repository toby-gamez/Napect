package com.tkolymp.napect.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tkolymp.napect.domain.repository.RecipeRepository
import com.tkolymp.napect.data.ai.AiClient

class RecipeViewModelFactory(private val repo: RecipeRepository, private val ai: AiClient? = null) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecipeViewModel::class.java)) {
            return RecipeViewModel(repo, ai) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
