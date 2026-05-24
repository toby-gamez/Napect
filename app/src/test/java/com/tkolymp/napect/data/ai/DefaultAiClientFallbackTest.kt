package com.tkolymp.napect.data.ai

import com.tkolymp.napect.data.ai.openai.OpenAiKeyProvider
import com.tkolymp.napect.data.ai.openai.OpenAiService
import com.tkolymp.napect.data.local.OpenAiConfig
import com.tkolymp.napect.data.network.ImportedRecipeData
import com.tkolymp.napect.data.network.UrlImportService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAiClientFallbackTest {
    private val fallbackData = ImportedRecipeData(title = "Fallback Recipe")
    private val openAiData = ImportedRecipeData(title = "OpenAI Recipe")

    private val fakeUrlImportService = FakeUrlImportService(fallbackData)
    private var storedKey: String? = null

    private val keyProvider = object : OpenAiKeyProvider {
        override fun getKey(): String? = storedKey
    }

    private val config = object : OpenAiConfig {
        override val openAiModel: Flow<String> = flowOf("gpt-4o-mini")
        override val openAiBaseUrl: Flow<String> = flowOf("http://fake.invalid/v1")
    }

    private val failingOpenAiService = OpenAiService(
        OkHttpClient(),
        keyProvider,
        config,
    )

    @Test
    fun `key absent uses fallback and ignores openai`() = runTest {
        storedKey = null
        val client = DefaultAiClient(failingOpenAiService, keyProvider, fakeUrlImportService)
        val result = client.extractRecipeFromUrl("https://example.com/recipe")
        assertTrue(result.isSuccess)
        assertEquals("Fallback Recipe", result.getOrThrow().title)
        assertEquals(1, fakeUrlImportService.importCallCount)
    }

    @Test
    fun `key absent caches and does not call fallback twice for same url`() = runTest {
        storedKey = null
        val client = DefaultAiClient(failingOpenAiService, keyProvider, fakeUrlImportService)
        client.extractRecipeFromUrl("https://example.com/recipe")
        client.extractRecipeFromUrl("https://example.com/recipe")
        assertEquals(1, fakeUrlImportService.importCallCount)
    }

    @Test
    fun `key present but openai fails uses fallback`() = runTest {
        storedKey = "sk-test"
        val client = DefaultAiClient(failingOpenAiService, keyProvider, fakeUrlImportService)
        val result = client.extractRecipeFromUrl("https://example.com/recipe")
        assertTrue(result.isSuccess)
        assertEquals("Fallback Recipe", result.getOrThrow().title)
    }
}

private class FakeUrlImportService(private val data: ImportedRecipeData) : UrlImportService() {
    var importCallCount = 0

    override suspend fun fetchHtml(url: String): Result<String> = Result.success("<html>fake</html>")

    override suspend fun importFromUrl(url: String): Result<ImportedRecipeData> {
        importCallCount++
        return Result.success(data)
    }
}
