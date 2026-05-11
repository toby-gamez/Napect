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
        // Heuristic: Gemini Nano requires AICore on supported devices. We don't crash if not present.
        // This is a placeholder; a real implementation would check the presence of the AICore APIs.
        return android.os.Build.VERSION.SDK_INT >= 34
    }
}
