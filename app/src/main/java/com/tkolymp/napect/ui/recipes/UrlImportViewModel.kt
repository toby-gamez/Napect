package com.tkolymp.napect.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface UrlImportState {
    object Idle : UrlImportState
    object Loading : UrlImportState
    data class Success(val data: ImportedRecipeData) : UrlImportState
    data class Error(val message: String) : UrlImportState
    object Saved : UrlImportState
}

@HiltViewModel
class UrlImportViewModel @Inject constructor(
    private val service: UrlImportService,
    private val repo: RecipeRepository,
    private val gemini: GeminiNanoService,
    private val ai: AiClient
) : ViewModel() {
    private val _state = MutableStateFlow<UrlImportState>(UrlImportState.Idle)
    val state: StateFlow<UrlImportState> = _state.asStateFlow()
    // Incoming shared data flows (used for initial share and onNewIntent runtime shares)
    private val _incomingSharedImageUri = MutableStateFlow<android.net.Uri?>(null)
    val incomingSharedImageUri: StateFlow<android.net.Uri?> = _incomingSharedImageUri.asStateFlow()

    private val _incomingSharedUrl = MutableStateFlow<String?>(null)
    val incomingSharedUrl: StateFlow<String?> = _incomingSharedUrl.asStateFlow()

    // Called by the Activity to deliver a shared image Uri (initial or via onNewIntent)
    fun receiveSharedImageUri(uri: android.net.Uri?) {
        _incomingSharedImageUri.value = uri
    }

    // Called by the Activity to deliver a shared URL/text (initial or via onNewIntent)
    fun receiveSharedUrl(url: String?) {
        _incomingSharedUrl.value = url
    }

    /**
     * Import an image Uri: run ML Kit OCR to extract text, then attempt to split into ingredients/steps.
     * This keeps the logic simple: use IngredientParser heuristics to find lines that look like ingredients.
     */
    fun importImage(uri: android.net.Uri) {
        viewModelScope.launch {
            _state.value = UrlImportState.Loading
            try {
                // run ML Kit text recognition off the main thread to avoid blocking the UI
                val context = com.tkolymp.napect.AppContextHolder.context ?: throw IllegalStateException("No context")
                val (inputImage, recognizer) = withContext(Dispatchers.IO) {
                    val img = com.google.mlkit.vision.common.InputImage.fromFilePath(context, uri)
                    val r = com.google.mlkit.vision.text.TextRecognition.getClient(
                        com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                    )
                    Pair(img, r)
                }
                val task = recognizer.process(inputImage)
                // Await the Task result without blocking the main thread by suspending the coroutine
                val result = withContext(Dispatchers.IO) { awaitTask(task) }
                val rawText = result.text ?: ""

                // simple heuristics: split by lines, categorize lines containing numbers or fractions as ingredients
                val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
                val ingredients = mutableListOf<String>()
                val steps = mutableListOf<String>()
                for (ln in lines) {
                    // ingredient-like heuristic: starts with digit or fraction or contains 'g'/'kg'/'tsp'/'cup'
                    val lower = ln.lowercase()
                    val looksLikeIngredient = lower.matches(Regex("^[0-9⅛⅜¼½¾⅓⅔].*")) ||
                            lower.contains("tsp") || lower.contains("tbsp") || lower.contains("cup") ||
                            lower.contains("g") || lower.contains("kg") || lower.contains("ml") || lower.contains("l")

                    if (looksLikeIngredient) ingredients.add(ln) else steps.add(ln)
                }

                val title = lines.firstOrNull() ?: "Scanned Recipe"
                val data = ImportedRecipeData(title = title, description = null, ingredients = ingredients, steps = steps, sourceUrl = null)
                _state.value = UrlImportState.Success(data)
            } catch (e: Exception) {
                _state.value = UrlImportState.Error(e.message ?: "OCR import failed")
            }
        }
    }

    // Helper to suspend until a com.google.android.gms.tasks.Task completes
    private suspend fun <T> awaitTask(task: com.google.android.gms.tasks.Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { res ->
            if (!cont.isCompleted) cont.resume(res)
        }
        task.addOnFailureListener { ex ->
            if (!cont.isCompleted) cont.resumeWithException(ex)
        }
        task.addOnCanceledListener {
            if (!cont.isCompleted) cont.resumeWithException(java.util.concurrent.CancellationException("Task was cancelled"))
        }
        cont.invokeOnCancellation {
            // best effort cancel - Task doesn't support cancellation uniformly
        }
    }

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
