package com.tkolymp.napect.data.ai.openai

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
data class ExtractedIngredientGroup(
    val name: String = "",
    val ingredients: List<String> = emptyList(),
)

@Serializable
data class ExtractedRecipe(
    val title: String,
    val description: String? = null,
    val ingredientGroups: List<ExtractedIngredientGroup> = emptyList(),
    val steps: List<String> = emptyList(),
    val difficulty: String? = null,
)

fun ExtractedRecipe.toImportedRecipeData(sourceUrl: String? = null) = ImportedRecipeData(
    title = title,
    description = description,
    ingredientGroups = ingredientGroups.map { ImportedIngredientGroup(name = it.name, ingredients = it.ingredients) },
    steps = steps,
    sourceUrl = sourceUrl,
    difficulty = difficulty,
)
