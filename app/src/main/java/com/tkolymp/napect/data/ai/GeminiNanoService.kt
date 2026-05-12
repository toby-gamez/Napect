package com.tkolymp.napect.data.ai

import android.content.Context
import com.tkolymp.napect.data.network.ImportedRecipeData
import com.tkolymp.napect.data.network.UrlImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wrapper for on-device Gemini Nano extraction. If Gemini/AICore is available, this class
 * would call into the appropriate APIs. For now it uses a heuristic to detect availability
 * and falls back to UrlImportService + a small summarizer.
 *
 * The implementation is intentionally defensive so the app runs on devices without AICore.
 */
class GeminiNanoService(private val context: Context, private val fallback: UrlImportService = UrlImportService()) {
    suspend fun extractRecipeFromUrl(url: String): Result<ImportedRecipeData> = withContext(Dispatchers.IO) {
        try {
            // If real Gemini/AICore integration is available, call it here (future extension).
            // For now, use the fallback importer and add a short generated summary.
            val base = fallback.importFromUrl(url)
            if (base.isFailure) return@withContext base
            val data = base.getOrThrow()
            // generate a short summary (2 sentences) if missing
            val summary = if (!data.description.isNullOrBlank()) generateShortSummary(data.description) else generateSummaryFromContent(data)
            val enriched = data.copy(description = summary)
            return@withContext Result.success(enriched)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateShortSummary(text: String): String {
        // naive: break into sentences and take first two
        val sentences = text.trim().split(Regex("(?<=[.!?])\\s+"))
        return when {
            sentences.isEmpty() -> ""
            sentences.size == 1 -> sentences[0]
            else -> sentences.take(2).joinToString(" ")
        }
    }

    private fun generateSummaryFromContent(data: ImportedRecipeData): String {
        val topIngredients = data.ingredients.take(4).joinToString(", ")
        return if (topIngredients.isBlank()) {
            "${data.title}."
        } else {
            "${data.title}. Hlavní ingredience: $topIngredients."
        }
    }

    fun isGeminiAvailable(): Boolean {
        // Try to detect AICore / on-device Gemini availability without linking the SDK.
        // We prefer a reflection-based check so the app doesn't require the AICore
        // compile-time dependency and can run on all devices.
        return try {
            // Check for a common AICore/GenerativeModel class. Only report available when
            // the AICore classes are actually present — avoids attempting to use Gemini
            // on devices that do not provide the SDK (e.g., Samsung devices without AICore).
            Class.forName("com.google.ai.generativelanguage.GenerativeModel")
            true
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Attempt to ask an on-device model to summarize the recipe. This method uses reflection
     * to avoid a hard dependency on AICore. If reflection fails or model is unavailable,
     * null is returned so callers can fallback to local summarizer.
     */
    suspend fun summarizeRecipe(title: String, ingredients: List<String>, steps: List<String>): String? = withContext(Dispatchers.IO) {
        try {
            // Try to load a hypothetical AICore entrypoint via reflection. The exact API
            // may differ between devices / SDK versions; this is a best-effort attempt.
            val aiClass = try { Class.forName("com.google.ai.core.AiClient") } catch (_: Throwable) { null }
            if (aiClass != null) {
                // If present, attempt to call a static convenience method `summarize(...)`.
                // This is intentionally permissive; if signatures differ we'll catch and fallback.
                try {
                    val method = aiClass.getMethod("summarize", String::class.java, List::class.java, List::class.java)
                    val result = method.invoke(null, title, ingredients, steps)
                    return@withContext result?.toString()
                } catch (_: Throwable) {
                    // reflection call failed — fall through to local summarizer
                }
            }

            // No on-device API available — fallback to a light summarizer
            val top = ingredients.take(3).joinToString(", ")
            return@withContext if (top.isBlank()) {
                if (title.isBlank()) null else title
            } else {
                "${title.trim()}. Hlavní ingredience: $top."
            }
        } catch (e: Exception) {
            null
        }
    }
}
