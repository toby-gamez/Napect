package com.tkolymp.napect.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.napect.data.ai.RecipeClassifier
import com.tkolymp.napect.data.network.ImportedRecipeData
import com.tkolymp.napect.data.network.UrlImportService
import com.tkolymp.napect.data.ai.GeminiNanoService
import com.tkolymp.napect.data.ai.AiClient
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.Step
import com.tkolymp.napect.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UrlImportState {
    object Idle : UrlImportState
    object Loading : UrlImportState
    data class Success(val data: ImportedRecipeData) : UrlImportState
    data class Error(val message: String) : UrlImportState
    object Saved : UrlImportState
}

class UrlImportViewModel(
    private val service: UrlImportService,
    private val repo: RecipeRepository,
    private val gemini: GeminiNanoService? = null,
    private val ai: AiClient? = null
) : ViewModel() {
    private val _state = MutableStateFlow<UrlImportState>(UrlImportState.Idle)
    val state: StateFlow<UrlImportState> = _state.asStateFlow()

    fun fetchUrl(url: String) {
        viewModelScope.launch {
            _state.value = UrlImportState.Loading
            // Prefer Gemini if provided and available
            val res = try {
                if (gemini != null && gemini.isGeminiAvailable()) gemini.extractRecipeFromUrl(url) else service.importFromUrl(url)
            } catch (e: Exception) {
                service.importFromUrl(url)
            }

            if (res.isSuccess) {
                _state.value = UrlImportState.Success(res.getOrThrow())
            } else {
                _state.value = UrlImportState.Error(res.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun saveImported(data: ImportedRecipeData, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            // parse ingredient strings into structured ingredients when possible
            val ing = data.ingredients.mapIndexed { idx, s ->
                try {
                    val parsed = com.tkolymp.napect.data.parse.IngredientParser.parse(s)
                    val amt = parsed.amount ?: 0.0
                    val unit = parsed.unit
                    val name = if (parsed.name.isBlank()) s else parsed.name
                    Ingredient(amount = amt, unit = unit, name = name, sortOrder = idx)
                } catch (e: Exception) {
                    Ingredient(amount = 0.0, unit = null, name = s, sortOrder = idx)
                }
            }
            val steps = data.steps.mapIndexed { idx, s -> Step(stepNumber = idx + 1, instruction = s) }
            val category = RecipeClassifier.classify(data.title, data.ingredients, data.steps)
            val recipe = Recipe(
                title = data.title,
                summary = data.description ?: ai?.generateSummary(data.title, ing, steps, data),
                ingredients = ing,
                steps = steps,
                category = category,
                sourceUrl = data.sourceUrl
            )
            // Suggest tags (keyword-only suggester) and create any missing tags; then save recipe with tag ids
            val suggestion = try { repo.suggestTagsForRecipe(recipe) } catch (e: Exception) { null }
            val tagIds = suggestion?.let { (it.confirmed + it.newlyCreated).map { t -> t.id } } ?: emptyList()
            val id = repo.saveRecipeWithTags(recipe, tagIds)
            _state.value = UrlImportState.Saved
            onComplete(id)
        }
    }
}
