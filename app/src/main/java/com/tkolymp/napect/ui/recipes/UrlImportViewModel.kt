package com.tkolymp.napect.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.tkolymp.napect.data.network.ImportedRecipeData
import com.tkolymp.napect.data.network.groupIngredients
import com.tkolymp.napect.data.network.UrlImportService
import com.tkolymp.napect.data.ai.AiClient
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.IngredientGroup
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.Step
import com.tkolymp.napect.domain.repository.RecipeRepository
import com.tkolymp.napect.domain.usecase.ClassifyRecipeUseCase
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tkolymp.napect.data.network.ImportedIngredientGroup
import com.tkolymp.napect.data.work.UrlImportWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import timber.log.Timber
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
    @ApplicationContext private val context: Context,
    private val service: UrlImportService,
    private val repo: RecipeRepository,
    private val ai: AiClient,
    private val workManager: WorkManager,
    private val classifyRecipe: ClassifyRecipeUseCase,
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
                val unitPattern = Regex("\\b(g|kg|ml|dl|l|tsp|tbsp|cup|oz|lb|ks|lžíce|lžička|šálek)\\b")
                for (ln in lines) {
                    val lower = ln.lowercase()
                    val looksLikeIngredient = lower.matches(Regex("^[0-9⅛⅜¼½¾⅓⅔].*")) ||
                            unitPattern.containsMatchIn(lower)
                    if (looksLikeIngredient) ingredients.add(ln) else steps.add(ln)
                }

                val title = lines.firstOrNull() ?: context.getString(com.tkolymp.napect.R.string.scanned_recipe)
                val data = ImportedRecipeData(title = title, description = null, ingredientGroups = groupIngredients(ingredients), steps = steps, sourceUrl = null)
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

    private fun isValidUrl(url: String) =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    fun fetchUrl(url: String) {
        if (!isValidUrl(url)) {
            _state.value = UrlImportState.Error("Neplatná URL adresa. Zadejte odkaz začínající http:// nebo https://")
            return
        }
        viewModelScope.launch {
            _state.value = UrlImportState.Loading
            val res = ai.extractRecipeFromUrl(url)
            if (res.isSuccess) {
                _state.value = UrlImportState.Success(res.getOrThrow())
            } else {
                _state.value = UrlImportState.Error(res.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun resetState() {
        _state.value = UrlImportState.Idle
    }

    fun saveImported(data: ImportedRecipeData, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            // Convert each imported ingredient group into a domain IngredientGroup
            val domainGroups = data.ingredientGroups.mapIndexed { gIdx, importedGroup ->
                val ings = importedGroup.ingredients.mapIndexed { idx, s ->
                    try {
                        val parsed = com.tkolymp.napect.data.parse.IngredientParser.parse(s)
                        val amt = parsed.amount ?: 0.0
                        Ingredient(amount = amt, unit = parsed.unit, name = if (parsed.name.isBlank()) s else parsed.name, sortOrder = idx)
                    } catch (e: Exception) {
                        Ingredient(amount = 0.0, unit = null, name = s, sortOrder = idx)
                    }
                }
                IngredientGroup(name = importedGroup.name, sortOrder = gIdx, ingredients = ings)
            }.ifEmpty { listOf(IngredientGroup(name = "", ingredients = emptyList())) }

            val allIngredients = domainGroups.flatMap { it.ingredients }
            val steps = data.steps.mapIndexed { idx, s -> Step(stepNumber = idx + 1, instruction = s) }
            val category = classifyRecipe(data.title ?: "", data.ingredients, data.steps)
            val recipe = Recipe(
                title = data.title,
                summary = data.description ?: ai.generateSummary(data.title, allIngredients, steps, data),
                ingredientGroups = domainGroups,
                steps = steps,
                category = category,
                sourceUrl = data.sourceUrl,
                timeMinutes = data.timeMinutes,
            )
            val suggestion = try { repo.suggestTagsForRecipe(recipe) } catch (e: Exception) { null }
            val tagIds = suggestion?.let { (it.confirmed + it.newlyCreated).map { t -> t.id } } ?: emptyList()
            val id = repo.saveRecipeWithTags(recipe, tagIds)
            _state.value = UrlImportState.Saved
            onComplete(id)
        }
    }

    /**
     * Background-resilient URL import via WorkManager. Survives process death and retries
     * on network failure (up to 2 times with exponential backoff).
     * On success, reads the JSON result file the worker persisted and emits [UrlImportState.Success].
     */
    fun fetchUrlWithWorker(url: String) {
        if (!isValidUrl(url)) {
            _state.value = UrlImportState.Error("Neplatná URL adresa. Zadejte odkaz začínající http:// nebo https://")
            return
        }
        _state.value = UrlImportState.Loading
        val request = UrlImportWorker.buildRequest(url)
        workManager.enqueueUniqueWork(UrlImportWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, request)

        viewModelScope.launch {
            try {
                // Collect only until the first terminal state — WorkInfo.State emits multiple times
                // for SUCCEEDED which would cause readResultFile to fail on the second read
                // (file already deleted by the first successful read).
                val terminalInfos = workManager.getWorkInfosForUniqueWorkFlow(UrlImportWorker.WORK_NAME)
                    .first { infos ->
                        infos.firstOrNull()?.state?.let { s ->
                            s == WorkInfo.State.SUCCEEDED || s == WorkInfo.State.FAILED || s == WorkInfo.State.CANCELLED
                        } ?: false
                    }
                val info = terminalInfos.firstOrNull() ?: return@launch
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val filePath = info.outputData.getString(UrlImportWorker.KEY_RESULT_FILE)
                        val data = filePath?.let { readResultFile(it) }
                        _state.value = if (data != null) {
                            UrlImportState.Success(data)
                        } else {
                            UrlImportState.Error("Failed to read import result")
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val error = info.outputData.getString(UrlImportWorker.KEY_ERROR) ?: "Import failed"
                        Timber.w("UrlImportWorker failed: %s", error)
                        _state.value = UrlImportState.Error(error)
                    }
                    WorkInfo.State.CANCELLED -> _state.value = UrlImportState.Idle
                    else -> {}
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _state.value = UrlImportState.Error(e.message ?: "Import failed")
                }
            }
        }
    }

    private fun readResultFile(path: String): ImportedRecipeData? {
        return try {
            val json = JSONObject(java.io.File(path).readText())
            java.io.File(path).delete()
            val groups = (0 until json.getJSONArray("ingredientGroups").length()).map { i ->
                val g = json.getJSONArray("ingredientGroups").getJSONObject(i)
                val ings = (0 until g.getJSONArray("ingredients").length()).map { j ->
                    g.getJSONArray("ingredients").getString(j)
                }
                val structuredIngs = mutableListOf<com.tkolymp.napect.data.network.ImportedIngredient>()
                g.optJSONArray("structuredIngredients")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val o = arr.getJSONObject(j)
                        structuredIngs.add(com.tkolymp.napect.data.network.ImportedIngredient(
                            amount = if (o.isNull("amount")) null else o.optDouble("amount").takeIf { !it.isNaN() },
                            unit = if (o.isNull("unit")) null else o.optString("unit").ifBlank { null },
                            name = o.optString("name"),
                        ))
                    }
                }
                ImportedIngredientGroup(name = g.getString("name"), ingredients = ings, structuredIngredients = structuredIngs)
            }
            val steps = (0 until json.getJSONArray("steps").length()).map { i ->
                json.getJSONArray("steps").getString(i)
            }
            ImportedRecipeData(
                title = json.getString("title"),
                description = json.optString("description").ifBlank { null },
                ingredientGroups = groups,
                steps = steps,
                sourceUrl = json.optString("sourceUrl").ifBlank { null },
                caloriesKcal = if (json.isNull("caloriesKcal")) null else json.optDouble("caloriesKcal").takeIf { !it.isNaN() },
                fatG = if (json.isNull("fatG")) null else json.optDouble("fatG").takeIf { !it.isNaN() },
                carbsG = if (json.isNull("carbsG")) null else json.optDouble("carbsG").takeIf { !it.isNaN() },
                proteinsG = if (json.isNull("proteinsG")) null else json.optDouble("proteinsG").takeIf { !it.isNaN() },
                nutriScore = json.optString("nutriScore").ifBlank { null },
                timeMinutes = if (json.isNull("timeMinutes")) null else json.optInt("timeMinutes").takeIf { it > 0 },
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to deserialize import result from %s", path)
            null
        }
    }
}
