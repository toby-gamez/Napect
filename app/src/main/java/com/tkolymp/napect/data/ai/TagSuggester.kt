package com.tkolymp.napect.data.ai

import com.tkolymp.napect.domain.model.TagGroup

/**
 * Simple keyword-based tag suggester. Operates offline.
 */
object TagSuggester {
    private val KEYWORD_MAP: Map<String, Pair<String, TagGroup>> = mapOf(
        // TIME
        "15 min" to ("15 min" to TagGroup.TIME),
        "15min" to ("15 min" to TagGroup.TIME),
        "30 min" to ("30 min" to TagGroup.TIME),
        "30min" to ("30 min" to TagGroup.TIME),
        "1 h" to ("1 h" to TagGroup.TIME),
        "1h" to ("1 h" to TagGroup.TIME),
        "hour" to ("1 h" to TagGroup.TIME),

        // DIET
        "vegan" to ("Vegan" to TagGroup.DIET),
        "vegetarian" to ("Vegetarian" to TagGroup.DIET),
        "gluten" to ("Gluten-Free" to TagGroup.DIET),
        "gluten-free" to ("Gluten-Free" to TagGroup.DIET),
        "dairy" to ("Dairy-Free" to TagGroup.DIET),

        // METHOD
        "fry" to ("Fried" to TagGroup.METHOD),
        "fried" to ("Fried" to TagGroup.METHOD),
        "bake" to ("Baked" to TagGroup.METHOD),
        "baked" to ("Baked" to TagGroup.METHOD),
        "grill" to ("Grilled" to TagGroup.METHOD),

        // CUISINE
        "ital" to ("Italian" to TagGroup.CUISINE),
        "pasta" to ("Italian" to TagGroup.CUISINE),
        "pizza" to ("Italian" to TagGroup.CUISINE),
        "soy sauce" to ("Chinese" to TagGroup.CUISINE),
        "wok" to ("Chinese" to TagGroup.CUISINE),
        "tofu" to ("Chinese" to TagGroup.CUISINE),

        // MEAL
        "breakfast" to ("Breakfast" to TagGroup.MEAL),
        "lunch" to ("Lunch" to TagGroup.MEAL),
        "dinner" to ("Dinner" to TagGroup.MEAL)
    )

    fun suggest(text: String): Set<Pair<String, TagGroup>> {
        val lower = text.lowercase()
        val results = mutableSetOf<Pair<String, TagGroup>>()
        for ((k, v) in KEYWORD_MAP) {
            if (lower.contains(k)) results.add(v)
        }
        return results
    }
}
