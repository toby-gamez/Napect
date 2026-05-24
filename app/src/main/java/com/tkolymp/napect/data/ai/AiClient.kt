package com.tkolymp.napect.data.ai

import com.tkolymp.napect.data.ai.openai.OpenAiKeyProvider
import com.tkolymp.napect.data.ai.openai.OpenAiService
import com.tkolymp.napect.data.network.ImportedRecipeData
import com.tkolymp.napect.data.network.UrlImportCache
import com.tkolymp.napect.data.network.UrlImportService
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.Step
import timber.log.Timber

interface AiClient {
    suspend fun extractRecipeFromUrl(url: String): Result<ImportedRecipeData>
    suspend fun generateSummary(title: String, ingredients: List<Ingredient>, steps: List<Step>, imported: ImportedRecipeData? = null): String?
    suspend fun inferDifficulty(title: String, ingredients: List<Ingredient>, steps: List<Step>, imported: ImportedRecipeData? = null): String?
}

class DefaultAiClient(
    private val openAi: OpenAiService,
    private val keyProvider: OpenAiKeyProvider,
    private val urlImportService: UrlImportService,
) : AiClient {
    private val cache = UrlImportCache()

    override suspend fun extractRecipeFromUrl(url: String): Result<ImportedRecipeData> {
        cache.get(url)?.let { return Result.success(it) }

        val result = if (!keyProvider.getKey().isNullOrBlank()) {
            runCatching {
                val htmlResult = urlImportService.fetchHtml(url)
                if (htmlResult.isFailure) {
                    urlImportService.importFromUrl(url)
                } else {
                    val html = htmlResult.getOrThrow()
                    val aiResult = openAi.extractRecipeFromHtml(html, url)
                    if (aiResult.isFailure) {
                        Timber.w(aiResult.exceptionOrNull(), "OpenAI extraction failed, falling back to JSON-LD parser")
                        urlImportService.importFromUrl(url)
                    } else {
                        val data = aiResult.getOrThrow()
                        if (data.caloriesKcal == null && data.fatG == null && data.carbsG == null && data.proteinsG == null) {
                            val jsonLdNutrition = urlImportService.parseNutritionFromHtml(html)
                            if (jsonLdNutrition != null) {
                                Result.success(data.copy(
                                    caloriesKcal = jsonLdNutrition.caloriesKcal,
                                    fatG = jsonLdNutrition.fatG,
                                    carbsG = jsonLdNutrition.carbsG,
                                    proteinsG = jsonLdNutrition.proteinsG,
                                ))
                            } else aiResult
                        } else aiResult
                    }
                }
            }.getOrElse { e ->
                Timber.w(e, "OpenAI path failed, falling back to JSON-LD parser")
                urlImportService.importFromUrl(url)
            }
        } else {
            urlImportService.importFromUrl(url)
        }

        result.getOrNull()?.let { cache.put(url, it) }
        return result
    }

    override suspend fun generateSummary(title: String, ingredients: List<Ingredient>, steps: List<Step>, imported: ImportedRecipeData?): String? {
        if (!keyProvider.getKey().isNullOrBlank()) {
            try {
                val summary = openAi.summarize(title, ingredients.map { it.name }, steps.map { it.instruction })
                if (!summary.isNullOrBlank()) return summary
            } catch (e: Exception) {
                Timber.w(e, "OpenAI summarization failed, using fallback")
            }
        }
        return localSummary(title, ingredients)
    }

    override suspend fun inferDifficulty(title: String, ingredients: List<Ingredient>, steps: List<Step>, imported: ImportedRecipeData?): String? {
        if (!keyProvider.getKey().isNullOrBlank()) {
            try {
                val difficulty = openAi.inferDifficulty(title, ingredients.map { it.name }, steps.map { it.instruction })
                if (!difficulty.isNullOrBlank()) return difficulty
            } catch (e: Exception) {
                Timber.w(e, "OpenAI difficulty inference failed, using fallback")
            }
        }
        return localDifficultyInference(title, ingredients, steps)
    }

    private fun localSummary(title: String, ingredients: List<Ingredient>): String? {
        val top = ingredients.take(3).map { it.name }.filter { it.isNotBlank() }
        return if (top.isEmpty()) {
            if (title.isBlank()) null else title
        } else {
            "${title.trim()}. Hlavní ingredience: ${top.joinToString(", ")}."
        }
    }

    private fun localDifficultyInference(title: String, ingredients: List<Ingredient>, steps: List<Step>): String? {
        val text = (listOf(title) + ingredients.map { it.name } + steps.map { it.instruction })
            .joinToString(" ")
        return when {
            Regex("\\b(no[- ]?bake|nepecen|nepečen)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Jednoduché"
            Regex("\\b(easy|simple|quick|beginner|jednoduch|prost[ae])\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Jednoduché"
            Regex("\\b(medium|stredni|střední)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Střední"
            Regex("\\b(hard|difficult|challenging|advanced|narocn|náročn)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Náročné"
            else -> null
        }
    }
}
