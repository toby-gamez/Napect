package com.tkolymp.napect.data.ai.openai

import com.tkolymp.napect.data.local.OpenAiConfig
import com.tkolymp.napect.data.network.ImportedRecipeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

class OpenAiService(
    private val okHttpClient: OkHttpClient,
    private val keyProvider: OpenAiKeyProvider,
    private val config: OpenAiConfig,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mediaType = "application/json".toMediaType()

    suspend fun extractRecipeFromHtml(html: String, sourceUrl: String?): Result<ImportedRecipeData> {
        val key = keyProvider.getKey() ?: return Result.failure(IllegalStateException("No OpenAI API key configured"))
        return try {
            val (model, baseUrl) = readSettings()
            val request = buildRequest(
                url = "$baseUrl/chat/completions",
                key = key,
                body = json.encodeToString(
                    ChatRequest(
                        model = model,
                        messages = RecipePrompt.buildExtractionMessages(html.take(30_000), sourceUrl),
                        maxTokens = 1500,
                        responseFormat = RecipePrompt.responseFormat,
                    )
                ),
            )
            executeWithRetry(request).use { r ->
                if (!r.isSuccessful) return Result.failure(Exception("OpenAI ${r.code}: ${r.message}"))
                val body = r.body?.string() ?: return Result.failure(Exception("Empty response"))
                val content = json.decodeFromString<ChatResponse>(body).choices.firstOrNull()?.message?.content
                    ?: return Result.failure(Exception("No content in response"))
                val extracted = json.decodeFromString<ExtractedRecipe>(content)
                Result.success(extracted.toImportedRecipeData(sourceUrl))
            }
        } catch (e: SerializationException) {
            Timber.w(e, "Failed to parse OpenAI response")
            Result.failure(Exception("Failed to parse OpenAI response: ${e.message}"))
        } catch (e: Exception) {
            Timber.w(e, "OpenAI extraction failed")
            Result.failure(e)
        }
    }

    suspend fun summarize(title: String, ingredients: List<String>, steps: List<String>): String? {
        val key = keyProvider.getKey() ?: return null
        return try {
            val (model, baseUrl) = readSettings()
            val request = buildRequest(
                url = "$baseUrl/chat/completions",
                key = key,
                body = json.encodeToString(
                    ChatRequest(
                        model = model,
                        messages = RecipePrompt.buildSummaryMessages(title, ingredients, steps),
                        maxTokens = 256,
                    )
                ),
            )
            executeWithRetry(request).use { r ->
                if (!r.isSuccessful) return null
                val body = r.body?.string() ?: return null
                json.decodeFromString<ChatResponse>(body).choices.firstOrNull()?.message?.content?.trim()
            }
        } catch (e: Exception) {
            Timber.w(e, "OpenAI summarization failed")
            null
        }
    }

    suspend fun inferDifficulty(title: String, ingredients: List<String>, steps: List<String>): String? {
        val key = keyProvider.getKey() ?: return null
        return try {
            val (model, baseUrl) = readSettings()
            val request = buildRequest(
                url = "$baseUrl/chat/completions",
                key = key,
                body = json.encodeToString(
                    ChatRequest(
                        model = model,
                        messages = RecipePrompt.buildDifficultyMessages(title, ingredients.size, steps.size),
                        maxTokens = 32,
                    )
                ),
            )
            executeWithRetry(request).use { r ->
                if (!r.isSuccessful) return null
                val body = r.body?.string() ?: return null
                val content = json.decodeFromString<ChatResponse>(body).choices.firstOrNull()?.message?.content?.trim()
                when {
                    content == null -> null
                    content.contains("jednoduché", ignoreCase = true) -> "Jednoduché"
                    content.contains("střední", ignoreCase = true) -> "Střední"
                    content.contains("náročné", ignoreCase = true) -> "Náročné"
                    else -> null
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "OpenAI difficulty inference failed")
            null
        }
    }

    suspend fun testConnection(): Result<Unit> {
        val key = keyProvider.getKey() ?: return Result.failure(IllegalStateException("No API key configured"))
        return try {
            val (model, baseUrl) = readSettings()
            val request = buildRequest(
                url = "$baseUrl/chat/completions",
                key = key,
                body = json.encodeToString(
                    ChatRequest(model = model, messages = listOf(ChatMessage("user", "ok")), maxTokens = 5)
                ),
            )
            withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }.use { r ->
                if (r.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("HTTP ${r.code}: ${r.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readSettings(): Pair<String, String> {
        val model = config.openAiModel.first()
        val baseUrl = config.openAiBaseUrl.first().trimEnd('/')
        return model to baseUrl
    }

    private fun buildRequest(url: String, key: String, body: String): Request =
        Request.Builder()
            .url(url)
            .post(body.toRequestBody(mediaType))
            .header("Authorization", "Bearer $key")
            .build()

    private suspend fun executeWithRetry(request: Request): okhttp3.Response {
        val r1 = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
        return when {
            r1.code == 429 -> {
                val waitMs = r1.header("Retry-After")?.toLongOrNull()?.times(1000L) ?: 2_000L
                r1.close()
                delay(waitMs)
                withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
            }
            r1.code in 500..599 -> {
                r1.close()
                delay(1_000L)
                withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
            }
            else -> r1
        }
    }
}
