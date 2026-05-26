package com.tkolymp.napect.domain.usecase

import com.tkolymp.napect.domain.model.Recipe
import java.text.Normalizer

object DifficultyEstimator {

    fun estimate(recipe: Recipe): String {
        val text = listOfNotNull(recipe.title, recipe.summary).joinToString(" ") +
            " " + recipe.allIngredients.joinToString(" ") { it.name } +
            " " + recipe.steps.joinToString(" ") { it.instruction }

        val ingCount = recipe.allIngredients.size
        val stepCount = recipe.steps.size
        val estimatedMins = extractMinutes(text)

        var score = 0
        if (ingCount <= 6) score-- else if (ingCount > 12) score++
        if (stepCount <= 4) score-- else if (stepCount > 8) score++
        if (estimatedMins != null) {
            if (estimatedMins <= 20) score-- else if (estimatedMins >= 90) score++
        }

        val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "").replace('-', ' ')
        if (normalized.contains("no bake") || normalized.contains("nobake") || normalized.contains("nepecen")) {
            return "Jednoduché"
        }

        return when {
            score <= -1 -> "Jednoduché"
            score >= 1  -> "Náročné"
            else        -> "Střední"
        }
    }

    private fun extractMinutes(s: String): Int? {
        val lower = s.lowercase()
        val hMin = Regex("(\\d{1,2})\\s*h(?:ours?)?\\s*(\\d{1,2})\\s*min").find(lower)
        if (hMin != null) {
            val h = hMin.groupValues[1].toIntOrNull() ?: 0
            val m = hMin.groupValues[2].toIntOrNull() ?: 0
            return h * 60 + m
        }
        val hour = Regex("(\\d{1,2}(?:\\.\\d+)?)\\s*h(?:ours?)?").find(lower)
        if (hour != null) {
            val h = hour.groupValues[1].toDoubleOrNull() ?: return null
            return (h * 60).toInt()
        }
        return Regex("(\\d{1,3})\\s*min").find(lower)?.groupValues?.get(1)?.toIntOrNull()
    }
}
