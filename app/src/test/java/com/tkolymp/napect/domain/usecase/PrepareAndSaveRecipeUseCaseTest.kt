package com.tkolymp.napect.domain.usecase

import com.tkolymp.napect.domain.model.Category
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.IngredientGroup
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.Step
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.domain.model.TagGroup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrepareAndSaveRecipeUseCaseTest {
    private lateinit var classifyRecipe: ClassifyRecipeUseCase
    private lateinit var fakeRepo: FakeRecipeRepository
    private lateinit var fakeAi: FakeAiClient
    private lateinit var useCase: PrepareAndSaveRecipeUseCase

    @Before
    fun setUp() {
        classifyRecipe = ClassifyRecipeUseCase()
        fakeRepo = FakeRecipeRepository()
        fakeAi = FakeAiClient()
        useCase = PrepareAndSaveRecipeUseCase(classifyRecipe, fakeRepo, fakeAi)
    }

    @Test
    fun `invoke saves recipe with classified category when UNKNOWN`() = runTest {
        val id = useCase(sampleRecipe(category = Category.UNKNOWN))
        val saved = fakeRepo.savedRecipes.find { it.id == id }
        assertEquals(Category.DESSERT, saved?.category)
    }

    @Test
    fun `invoke preserves explicit category`() = runTest {
        val id = useCase(sampleRecipe(category = Category.SOUP))
        val saved = fakeRepo.savedRecipes.find { it.id == id }
        assertEquals(Category.SOUP, saved?.category)
    }

    @Test
    fun `invoke generates summary when missing`() = runTest {
        fakeAi.generateSummaryResult = "AI generated summary"
        val id = useCase(sampleRecipe(summary = null))
        val saved = fakeRepo.savedRecipes.find { it.id == id }
        assertEquals("AI generated summary", saved?.summary)
    }

    @Test
    fun `invoke preserves existing summary`() = runTest {
        fakeAi.generateSummaryResult = "AI summary (should not be used)"
        val id = useCase(sampleRecipe(summary = "Existing summary"))
        val saved = fakeRepo.savedRecipes.find { it.id == id }
        assertEquals("Existing summary", saved?.summary)
    }

    @Test
    fun `invoke uses provided tag ids when non empty`() = runTest {
        val tag = Tag(id = 42, name = "Test", group = TagGroup.OTHER)
        val id = useCase(sampleRecipe(), tagIds = listOf(42))
        val saved = fakeRepo.savedRecipes.find { it.id == id }
        assertTrue(saved?.tags?.any { it.id == 42L } ?: false)
    }

    @Test
    fun `invoke saves with no tags when tagIds empty`() = runTest {
        val id = useCase(sampleRecipe(), tagIds = emptyList())
        val saved = fakeRepo.savedRecipes.find { it.id == id }
        assertTrue(saved?.tags?.isEmpty() ?: false)
    }

    @Test
    fun `update modifies existing recipe`() = runTest {
        val tag = Tag(id = 7, name = "Orig", group = TagGroup.OTHER)
        val savedId = fakeRepo.saveRecipeWithTags(sampleRecipe(id = 0, category = Category.UNKNOWN), listOf(7))

        fakeAi.generateSummaryResult = "Updated summary"
        useCase.update(sampleRecipe(id = savedId, title = "Updated", category = Category.UNKNOWN))

        val updated = fakeRepo.savedRecipes.find { it.id == savedId }
        assertEquals("Updated", updated?.title)
        assertEquals("Updated summary", updated?.summary)
    }

    private fun sampleRecipe(
        id: Long = 0,
        title: String = "Čokoládový koláč",
        summary: String? = null,
        category: Category = Category.UNKNOWN,
    ) = Recipe(
        id = id,
        title = title,
        summary = summary,
        category = category,
        isFavorite = false,
        photoPath = null,
        createdAt = java.time.Instant.now(),
        ingredientGroups = listOf(
            IngredientGroup(name = "", ingredients = listOf(
                Ingredient(name = "čokoláda", amount = 200.0, unit = "g"),
                Ingredient(name = "cukr", amount = 100.0, unit = "g"),
            ))
        ),
        steps = listOf(Step(instruction = "upéct v troubě")),
        tags = emptyList(),
        sourceUrl = null,
    )
}
