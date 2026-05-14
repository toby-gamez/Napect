package com.tkolymp.napect.data.ai

import com.tkolymp.napect.domain.model.TagGroup
import java.text.Normalizer

/**
 * Simple keyword-based tag suggester. Operates offline.
 */
object TagSuggester {
    // Use regex-based matching for safer, word-boundary suggestions. Each pattern
    // can map to multiple tag suggestions (e.g. pasta -> Pasta, Italian).
    private val KEYWORD_MAP: List<Pair<Regex, List<Pair<String, TagGroup>>>> = listOf(
        // TIME
        Regex("\\b15\\s?min\\b") to listOf("15 min" to TagGroup.TIME),
        Regex("\\b30\\s?min\\b") to listOf("30 min" to TagGroup.TIME),
        Regex("\\b1\\s?h(our)?s?\\b") to listOf("1 h" to TagGroup.TIME),
        Regex("\\b2\\s?h\\+?\\b") to listOf("2 h+" to TagGroup.TIME),

        // DIET
        Regex("\\bvegan\\b") to listOf("Vegan" to TagGroup.DIET),
        Regex("\\bvegetarian\\b") to listOf("Vegetarian" to TagGroup.DIET),
        Regex("\\bgluten(-|\\s)?free\\b|\\bgluten\\b") to listOf("Gluten-Free" to TagGroup.DIET),
        Regex("\\bdairy(-|\\s)?free\\b|\\bdairy\\b") to listOf("Dairy-Free" to TagGroup.DIET),

        // METHOD
        Regex("\\bfry\\b|\\bfried\\b") to listOf("Fried" to TagGroup.METHOD),
        Regex("\\bbake\\b|\\bbaked\\b|\\boven\\b") to listOf("Baked" to TagGroup.METHOD),
        Regex("\\bgrill\\b|\\bgrilled\\b") to listOf("Grilled" to TagGroup.METHOD),
        Regex("\\bsteam\\b|\\bsteamed\\b") to listOf("Steamed" to TagGroup.METHOD),
        Regex("\\braw\\b") to listOf("Raw" to TagGroup.METHOD),

        // CUISINE
        Regex("\\bital(ian)?\\b") to listOf("Italian" to TagGroup.CUISINE),
        Regex("\\bpasta\\b|\\bpizza\\b") to listOf("Italian" to TagGroup.CUISINE),
        Regex("\\bsoy sauce\\b|\\bwok\\b|\\btofu\\b") to listOf("Chinese" to TagGroup.CUISINE),

        // Cheesecake / strawberry (English + Czech variants)
        Regex("\\bcheesecake\\b") to listOf("Dessert" to TagGroup.CATEGORY, "Cheesecake" to TagGroup.OTHER),
        Regex("\\bstrawberr(y|ies)\\b|\\bjahodov\\b|\\bjahod\\b|\\bjahody\\b") to listOf("Strawberry" to TagGroup.OTHER),
        // No-bake / nepečený hints
        Regex("\\bno[- ]?bake\\b|\\bnepecen\\b|\\bnepecen[y|a|e]\\b") to listOf("No-Bake" to TagGroup.METHOD, "Dessert" to TagGroup.CATEGORY),

        // CATEGORY / MEAL hints (map to CATEGORY when appropriate). We return multiple
        // complementary tags for richer suggestions (e.g. dessert -> Dessert, Sweet, Baked)
        Regex("\\bsoup\\b|\\bbroth\\b") to listOf("Soup" to TagGroup.CATEGORY),
        Regex("\\bdessert\\b|\\bcake\\b|\\bcookie\\b|\\bpudding\\b|\\bpie\\b") to listOf(
            "Dessert" to TagGroup.CATEGORY,
            "Sweet" to TagGroup.OTHER,
            "Baked" to TagGroup.METHOD,
            "Baking" to TagGroup.CATEGORY
        ),
        Regex("\\bbaking\\b|\\boven\\b") to listOf("Baking" to TagGroup.CATEGORY, "Baked" to TagGroup.METHOD),
        Regex("\\bbreakfast\\b") to listOf("Breakfast" to TagGroup.CATEGORY),
        Regex("\\bholiday\\b|\\bchristmas\\b|\\beaster\\b") to listOf("Holiday" to TagGroup.CATEGORY),
        Regex("\\bquick\\b|\\bfast\\b|\\b30\\s?min\\b|\\b15\\s?min\\b") to listOf("Quick" to TagGroup.CATEGORY, "Quick" to TagGroup.OTHER),
        // Difficulty
        Regex("\\beasy\\b") to listOf("Easy" to TagGroup.DIFFICULTY),
        Regex("\\bmedium\\b") to listOf("Medium" to TagGroup.DIFFICULTY),
        Regex("\\bhard\\b|\\bdifficult\\b") to listOf("Hard" to TagGroup.DIFFICULTY),

        // COMMON / INGREDIENTS / PROPERTIES
        Regex("\\bchicken\\b") to listOf("Chicken" to TagGroup.OTHER),
        Regex("\\bbeef\\b") to listOf("Beef" to TagGroup.OTHER),
        Regex("\\bpork\\b") to listOf("Pork" to TagGroup.OTHER),
        Regex("\\bpasta\\b") to listOf("Pasta" to TagGroup.OTHER, "Italian" to TagGroup.CUISINE),
        Regex("\\bczech\\b|\\bcesky\\b|\\bczechia\\b") to listOf("Czech" to TagGroup.CUISINE),
        Regex("\\brace\\b") to listOf("Rice" to TagGroup.OTHER),
        Regex("\\bspicy\\b|\\bchili\\b|\\bchilli\\b") to listOf("Spicy" to TagGroup.OTHER),
        Regex("\\bquick\\b|\\bfast\\b") to listOf("Quick" to TagGroup.OTHER),
        Regex("\\bcheap\\b|\\bbudget\\b") to listOf("Budget" to TagGroup.OTHER),
        Regex("\\bhealthy\\b") to listOf("Healthy" to TagGroup.OTHER),
        Regex("\\bsweet\\b") to listOf("Sweet" to TagGroup.OTHER),
        Regex("\\bsavory\\b") to listOf("Savory" to TagGroup.OTHER)
    )

    fun suggest(text: String): Set<Pair<String, TagGroup>> {
        // Lowercase and remove diacritics so patterns match both English and Czech variants
        val lower = text.lowercase()
        val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
        val results = mutableSetOf<Pair<String, TagGroup>>()
        for ((regex, vs) in KEYWORD_MAP) {
            if (regex.containsMatchIn(normalized)) {
                for (v in vs) results.add(v)
            }
        }
        return results
    }
}
