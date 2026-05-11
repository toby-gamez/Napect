package com.tkolymp.napect.data.ai

import com.tkolymp.napect.domain.model.Category

/**
 * Tiny rule-based classifier that uses keywords to guess a category.
 * This is a fallback for device-local non-ML classification until Gemini Nano is available.
 */
object RecipeClassifier {
    private val MAP: Map<Category, List<String>> = mapOf(
        Category.SOUP to listOf("soup", "broth", "bouillon", "polév"),
        Category.DESSERT to listOf("cake", "cookie", "dessert", "pudding", "sweet", "cukr", "koláč"),
        Category.BAKING to listOf("bake", "bread", "yeast", "oven", "bake", "pečení", "chléb"),
        Category.BREAKFAST to listOf("breakfast", "porridge", "muesli", "snídan"),
        Category.QUICK to listOf("quick", "30 min", "15 min", "fast", "rychl"),
        Category.DIET to listOf("gluten", "vegan", "vegetarian", "keto", "low carb", "bezlepk")
    )

    fun classify(title: String?, ingredients: List<String>, steps: List<String>): Category {
        val text = (listOfNotNull(title) + ingredients + steps).joinToString(" ").lowercase()
        for ((cat, keys) in MAP) {
            for (k in keys) if (text.contains(k)) return cat
        }
        return Category.MAIN
    }
}
