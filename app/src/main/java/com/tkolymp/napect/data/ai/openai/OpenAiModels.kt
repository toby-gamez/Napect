package com.tkolymp.napect.data.ai.openai

import com.tkolymp.napect.data.network.ImportedIngredient
import com.tkolymp.napect.data.network.ImportedIngredientGroup
import com.tkolymp.napect.data.network.ImportedRecipeData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    @SerialName("response_format") val responseFormat: JsonElement? = null,
)

@Serializable
data class ChatChoice(val message: ChatMessage, @SerialName("finish_reason") val finishReason: String? = null)

@Serializable
data class ChatResponse(
    val id: String = "",
    val choices: List<ChatChoice> = emptyList(),
    val usage: Usage? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
)

@Serializable
data class ExtractedIngredient(
    val amount: Double? = null,
    val unit: String? = null,
    val name: String = "",
)

private fun ExtractedIngredient.toDisplayString(): String = buildString {
    amount?.let { a ->
        append(if (a == kotlin.math.floor(a)) a.toInt().toString() else a.toString())
        append(' ')
    }
    unit?.let { append(it); append(' ') }
    append(name)
}.trim()

@Serializable
data class ExtractedIngredientGroup(
    val name: String = "",
    val ingredients: List<ExtractedIngredient> = emptyList(),
)

@Serializable
data class ExtractedRecipe(
    val title: String,
    val description: String? = null,
    val ingredientGroups: List<ExtractedIngredientGroup> = emptyList(),
    val steps: List<String> = emptyList(),
    val difficulty: String? = null,
    @SerialName("caloriesKcal") val caloriesKcal: Double? = null,
    @SerialName("fatG") val fatG: Double? = null,
    @SerialName("carbsG") val carbsG: Double? = null,
    @SerialName("proteinsG") val proteinsG: Double? = null,
    @SerialName("nutriScore") val nutriScore: String? = null,
)

fun ExtractedRecipe.toImportedRecipeData(sourceUrl: String? = null) = ImportedRecipeData(
    title = title,
    description = description,
    ingredientGroups = ingredientGroups.map { grp ->
        ImportedIngredientGroup(
            name = grp.name,
            ingredients = grp.ingredients.map { it.toDisplayString() },
            structuredIngredients = grp.ingredients.map { ImportedIngredient(it.amount, it.unit, it.name) },
        )
    },
    steps = steps,
    sourceUrl = sourceUrl,
    difficulty = difficulty,
    caloriesKcal = caloriesKcal,
    fatG = fatG,
    carbsG = carbsG,
    proteinsG = proteinsG,
    nutriScore = nutriScore,
)
