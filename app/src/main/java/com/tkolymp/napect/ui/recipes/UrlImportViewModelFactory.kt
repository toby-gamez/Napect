package com.tkolymp.napect.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tkolymp.napect.data.network.UrlImportService
import com.tkolymp.napect.data.ai.GeminiNanoService
import com.tkolymp.napect.data.ai.AiClient
import com.tkolymp.napect.domain.repository.RecipeRepository

// Minimal factory used during the migration; expects non-null GeminiNanoService and AiClient.
class UrlImportViewModelFactory(private val service: UrlImportService, private val repo: RecipeRepository, private val gemini: GeminiNanoService, private val ai: AiClient) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UrlImportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UrlImportViewModel(service, repo, gemini, ai) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
