package com.tkolymp.napect.domain.usecase

import com.tkolymp.napect.data.ai.AiClient
import com.tkolymp.napect.domain.model.Category
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.repository.RecipeRepository
import javax.inject.Inject

class PrepareAndSaveRecipeUseCase @Inject constructor(
    private val classifyRecipe: ClassifyRecipeUseCase,
    private val repo: RecipeRepository,
    private val ai: AiClient,
) {
    /**
     * Classifies, generates summary (if missing), optionally suggests tags, and saves.
     * Returns the new recipe id.
     */
    suspend operator fun invoke(
        recipe: Recipe,
        tagIds: List<Long> = emptyList(),
    ): Long {
        val allIng = recipe.allIngredients
        val category = if (recipe.category == Category.UNKNOWN) {
            classifyRecipe(recipe.title, allIng.map { it.name }, recipe.steps.map { it.instruction })
        } else recipe.category

        val summary = recipe.summary ?: ai.generateSummary(recipe.title, allIng, recipe.steps)

        val prepared = recipe.copy(category = category, summary = summary)
        return repo.saveRecipeWithTags(prepared, tagIds)
    }

    /**
     * Classifies, generates summary (if missing), optionally suggests tags, and updates.
     */
    suspend fun update(
        recipe: Recipe,
        tagIds: List<Long> = emptyList(),
    ) {
        val allIng = recipe.allIngredients
        val category = if (recipe.category == Category.UNKNOWN) {
            classifyRecipe(recipe.title, allIng.map { it.name }, recipe.steps.map { it.instruction })
        } else recipe.category

        val summary = recipe.summary ?: ai.generateSummary(recipe.title, allIng, recipe.steps)

        val prepared = recipe.copy(category = category, summary = summary)
        repo.saveRecipeWithTags(prepared, tagIds)
    }
}
