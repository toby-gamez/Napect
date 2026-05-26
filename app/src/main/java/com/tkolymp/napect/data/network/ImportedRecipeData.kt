package com.tkolymp.napect.data.network

/** A single ingredient as parsed/extracted (amount and unit may be null). */
data class ImportedIngredient(
    val amount: Double? = null,
    val unit: String? = null,
    val name: String,
)

/** A named ingredient sub-section within an imported recipe (e.g. "Těsto", "Na povrch"). */
data class ImportedIngredientGroup(
    val name: String = "",                                          // blank = default / ungrouped
    val ingredients: List<String>,                                  // raw strings (JSON-LD / OCR path)
    val structuredIngredients: List<ImportedIngredient> = emptyList(), // structured (AI path)
)

data class ImportedRecipeData(
    val title: String,
    val description: String? = null,
    val ingredientGroups: List<ImportedIngredientGroup> = emptyList(),
    val steps: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val difficulty: String? = null,
    val caloriesKcal: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
    val proteinsG: Double? = null,
    val nutriScore: String? = null,
    val timeMinutes: Int? = null,
    val imageUrl: String? = null,
) {
    /** Flat list of all ingredients across every group (backward-compat helper). */
    val ingredients: List<String> get() = ingredientGroups.flatMap { it.ingredients }
}

/**
 * Splits a flat ingredient string list into named sub-sections by detecting header lines.
 *
 * A line is treated as a section header when it:
 *  - ends with ":" (e.g. "Těsto:", "Na povrch:", "Topping:")
 *  - is wrapped in markdown bold markers (**Dough**)
 *
 * The header's trailing colon and bold markers are stripped to produce the section name.
 * If no headers are found the result is a single group with a blank name.
 */
fun groupIngredients(lines: List<String>): List<ImportedIngredientGroup> {
    if (lines.isEmpty()) return emptyList()

    val groups = mutableListOf<ImportedIngredientGroup>()
    var currentName = ""
    val currentIngredients = mutableListOf<String>()

    for (raw in lines) {
        val line = raw.trim()
        if (line.isBlank()) continue
        if (isIngredientSectionHeader(line)) {
            if (currentIngredients.isNotEmpty()) {
                groups.add(ImportedIngredientGroup(name = currentName, ingredients = currentIngredients.toList()))
                currentIngredients.clear()
            }
            currentName = line.trimEnd(':').trim().removePrefix("**").removeSuffix("**").trim()
        } else {
            currentIngredients.add(line)
        }
    }

    if (currentIngredients.isNotEmpty()) {
        groups.add(ImportedIngredientGroup(name = currentName, ingredients = currentIngredients.toList()))
    }

    // If nothing was grouped, return a single unnamed group
    return groups.ifEmpty { listOf(ImportedIngredientGroup(name = "", ingredients = lines.filter { it.isNotBlank() })) }
}

private fun isIngredientSectionHeader(line: String): Boolean {
    val t = line.trim()
    if (t.isBlank()) return false
    if (t.endsWith(":")) return true
    if (t.startsWith("**") && t.endsWith("**")) return true
    return false
}
