package com.tkolymp.napect.data.ai.openai

import com.tkolymp.napect.data.local.OpenAiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: OpenAiService

    private val keyProvider = object : OpenAiKeyProvider {
        var apiKey: String? = "sk-test"
        override fun getKey(): String? = apiKey
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val config = object : OpenAiConfig {
            override val openAiModel: Flow<String> = flowOf("gpt-4o-mini")
            override val openAiBaseUrl: Flow<String> = flowOf(server.url("/v1").toString())
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

        service = OpenAiService(client, keyProvider, config)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `extractRecipeFromHtml success returns ImportedRecipeData`() = runTest {
        server.enqueue(MockResponse().setBody(chatResponseJson(VALID_RECIPE_JSON)))
        val result = service.extractRecipeFromHtml("<html>test</html>", "https://example.com")
        assertTrue(result.isSuccess)
        assertEquals("Svíčková", result.getOrThrow().title)
        assertEquals("Střední", result.getOrThrow().difficulty)
    }

    @Test
    fun `extractRecipeFromHtml malformed JSON in content returns failure`() = runTest {
        server.enqueue(MockResponse().setBody(chatResponseJson("not json at all")))
        val result = service.extractRecipeFromHtml("<html>test</html>", null)
        assertTrue(result.isFailure)
    }

    @Test
    fun `extractRecipeFromHtml 401 returns failure without retry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = service.extractRecipeFromHtml("<html>test</html>", null)
        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `extractRecipeFromHtml 429 retries once then fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
        val result = service.extractRecipeFromHtml("<html>test</html>", null)
        assertTrue(result.isFailure)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `extractRecipeFromHtml 429 then success retries and returns result`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setBody(chatResponseJson(VALID_RECIPE_JSON)))
        val result = service.extractRecipeFromHtml("<html>test</html>", "https://example.com")
        assertTrue(result.isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `extractRecipeFromHtml 500 retries once and returns result on second attempt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(chatResponseJson(VALID_RECIPE_JSON)))
        val result = service.extractRecipeFromHtml("<html>test</html>", null)
        assertTrue(result.isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `extractRecipeFromHtml no key returns failure without hitting server`() = runTest {
        keyProvider.apiKey = null
        val result = service.extractRecipeFromHtml("<html>test</html>", null)
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    private fun chatResponseJson(content: String): String {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"id":"chatcmpl-test","choices":[{"message":{"role":"assistant","content":"$escaped"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":20}}"""
    }

    private companion object {
        val VALID_RECIPE_JSON = """{"title":"Svíčková","description":"Tradiční česká svíčková.","ingredientGroups":[{"name":"","ingredients":["500g hovězí","200ml smetana"]}],"steps":["Osmažte maso.","Přidejte zeleninu.","Podávejte."],"difficulty":"Střední"}"""
    }
}
