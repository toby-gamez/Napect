package com.tkolymp.napect.data.ai

import timber.log.Timber
import com.tkolymp.napect.data.network.ImportedRecipeData
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.Step

/**
 * Simple AI client abstraction. Implementations may call on-device Gemini/AICore or fall
 * back to lightweight local summarizers. All operations are suspendable.
 */
interface AiClient {
    suspend fun generateSummary(title: String, ingredients: List<Ingredient>, steps: List<Step>, imported: ImportedRecipeData? = null): String?
    // Try to infer difficulty level ("Jednoduché", "Střední", "Náročné") when available.
    // Returns a canonical Czech difficulty string or null when the client can't decide.
    suspend fun inferDifficulty(title: String, ingredients: List<Ingredient>, steps: List<Step>, imported: ImportedRecipeData? = null): String?
}

/**
 * Default implementation: uses GeminiNanoService when available, otherwise a lightweight summarizer.
 */
class DefaultAiClient(private val gemini: GeminiNanoService?) : AiClient {
    override suspend fun generateSummary(title: String, ingredients: List<Ingredient>, steps: List<Step>, imported: ImportedRecipeData?): String? {
        // Prefer gemini when available
        try {
            if (gemini != null && gemini.isGeminiAvailable()) {
                // Try to use a direct on-device summarization if possible
                val summary = gemini.summarizeRecipe(title, ingredients.map { it.name }, steps.map { it.instruction })
                if (!summary.isNullOrBlank()) return summary
                // gemini currently also exposes URL extraction; try to use it if we have an imported payload
                if (imported != null) {
                    val res = gemini.extractRecipeFromUrl(imported.sourceUrl ?: "")
                    if (res.isSuccess) return res.getOrThrow().description
                }
                // else fall through to local summarizer (no URL)
            }
        } catch (e: Exception) {
            Timber.w(e, "Gemini summarization failed, using fallback")
        }

        // Lightweight summarizer: title + top 3 ingredients summary
        val top = ingredients.take(3).map { it.name }.filter { it.isNotBlank() }
        return if (top.isEmpty()) {
            if (title.isBlank()) null else title
        } else {
            val ingr = top.joinToString(", ")
            "${title.trim()}. Hlavní ingredience: $ingr."
        }
    }

    override suspend fun inferDifficulty(title: String, ingredients: List<Ingredient>, steps: List<Step>, imported: ImportedRecipeData?): String? {
        // Best-effort inference using on-device Gemini when available. We keep this
        // light-weight and defensive: if the on-device API isn't present or doesn't
        // yield a clear result, return null so callers can fall back to heuristics.
        try {
            if (gemini != null && gemini.isGeminiAvailable()) {
                val summary = try { gemini.summarizeRecipe(title, ingredients.map { it.name }, steps.map { it.instruction }) } catch (_: Exception) { null }
                if (!summary.isNullOrBlank()) {
                    val lower = summary.lowercase()
                    // prefer easy/hard/medium keywords (English and Czech)
                    when {
                        Regex("\\b(no[- ]?bake|nepecen|nepečen|no[- ]?baking)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> return "Jednoduché"
                        Regex("\\b(easy|simple|quick|beginner|jednoduch|prost[ae])\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> return "Jednoduché"
                        Regex("\\b(medium|stredni|strednich)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> return "Střední"
                        Regex("\\b(hard|difficult|challenging|advanced|narocn)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> return "Náročné"
                        else -> return null
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Gemini difficulty inference failed")
        }
        return null
    }
}
