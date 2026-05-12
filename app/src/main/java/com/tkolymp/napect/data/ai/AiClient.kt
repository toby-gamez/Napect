package com.tkolymp.napect.data.ai

import com.tkolymp.napect.data.network.ImportedRecipeData
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.Step

/**
 * Simple AI client abstraction. Implementations may call on-device Gemini/AICore or fall
 * back to lightweight local summarizers. All operations are suspendable.
 */
interface AiClient {
    suspend fun generateSummary(title: String, ingredients: List<Ingredient>, steps: List<Step>, imported: ImportedRecipeData? = null): String?
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
        } catch (_: Exception) {
            // ignore and fallback
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
}
