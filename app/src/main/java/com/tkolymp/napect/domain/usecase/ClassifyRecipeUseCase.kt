package com.tkolymp.napect.domain.usecase

import com.tkolymp.napect.domain.model.Category
import javax.inject.Inject

class ClassifyRecipeUseCase @Inject constructor() {

    operator fun invoke(title: String?, ingredients: List<String>, steps: List<String>): Category {
        val text = (listOfNotNull(title) + ingredients + steps).joinToString(" ").lowercase()
        for ((cat, keywords) in CATEGORY_KEYWORDS) {
            for (k in keywords) {
                if (text.contains(k)) return cat
            }
        }
        return Category.MAIN
    }

    companion object {
        private val CATEGORY_KEYWORDS = mapOf(
            Category.SOUP to listOf("soup", "broth", "bouillon", "polév"),
            Category.DESSERT to listOf("cake", "cookie", "dessert", "pudding", "sweet", "cukr", "koláč"),
            Category.BAKING to listOf("bake", "bread", "yeast", "oven", "pečení", "chléb"),
            Category.BREAKFAST to listOf("breakfast", "porridge", "muesli", "snídan"),
            Category.QUICK to listOf("quick", "30 min", "15 min", "fast", "rychl"),
            Category.DIET to listOf("gluten", "vegan", "vegetarian", "keto", "low carb", "bezlepk"),
        )
    }
}
