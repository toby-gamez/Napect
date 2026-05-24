package com.tkolymp.napect.domain.usecase

import com.tkolymp.napect.data.ai.AiClient
import com.tkolymp.napect.data.network.ImportedRecipeData
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.Step

class FakeAiClient : AiClient {
    var extractRecipeFromUrlResult: Result<ImportedRecipeData> = Result.success(ImportedRecipeData(title = "Test Recipe"))
    var generateSummaryResult: String? = null
    var inferDifficultyResult: String? = null

    override suspend fun extractRecipeFromUrl(url: String): Result<ImportedRecipeData> = extractRecipeFromUrlResult

    override suspend fun generateSummary(
        title: String,
        ingredients: List<Ingredient>,
        steps: List<Step>,
        imported: ImportedRecipeData?,
    ): String? = generateSummaryResult

    override suspend fun inferDifficulty(
        title: String,
        ingredients: List<Ingredient>,
        steps: List<Step>,
        imported: ImportedRecipeData?,
    ): String? = inferDifficultyResult
}
