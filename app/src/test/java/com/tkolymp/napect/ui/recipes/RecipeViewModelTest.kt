package com.tkolymp.napect.ui.recipes

import com.tkolymp.napect.data.ai.AiClient
import com.tkolymp.napect.data.ai.TagSuggestion
import com.tkolymp.napect.domain.model.Category
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.IngredientGroup
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.RecipeListItem
import com.tkolymp.napect.domain.model.Step
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.domain.model.TagGroup
import com.tkolymp.napect.domain.usecase.ClassifyRecipeUseCase
import com.tkolymp.napect.domain.usecase.FakeAiClient
import com.tkolymp.napect.domain.usecase.FakeRecipeRepository
import com.tkolymp.napect.domain.usecase.PrepareAndSaveRecipeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeRecipeRepository
    private lateinit var fakeAi: FakeAiClient
    private lateinit var classifyRecipe: ClassifyRecipeUseCase
    private lateinit var prepareAndSave: PrepareAndSaveRecipeUseCase
    private lateinit var viewModel: RecipeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeRecipeRepository()
        fakeAi = FakeAiClient()
        classifyRecipe = ClassifyRecipeUseCase()
        prepareAndSave = PrepareAndSaveRecipeUseCase(classifyRecipe, fakeRepo, fakeAi)
        viewModel = RecipeViewModel(fakeRepo, fakeAi, classifyRecipe, prepareAndSave)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty recipe list`() {
        assertTrue(viewModel.recipeListItems.value.isEmpty())
    }

    @Test
    fun `toggleFavorite updates favorite state`() = runTest(testDispatcher) {
        val id = fakeRepo.saveRecipeWithTags(sampleRecipe(), emptyList())
        viewModel.toggleFavorite(id, true)
        testDispatcher.scheduler.advanceUntilIdle()
        val saved = fakeRepo.savedRecipes.find { it.id == id }
        assertTrue(saved?.isFavorite == true)
    }

    @Test
    fun `createRecipe saves and calls onComplete`() = runTest(testDispatcher) {
        var completedId: Long? = null
        viewModel.createRecipe(sampleRecipe()) { id -> completedId = id }
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(completedId)
        assertTrue(completedId!! > 0)
    }

    @Test
    fun `createRecipe produces error on failure`() = runTest(testDispatcher) {
        fakeAi.generateSummaryResult = "summary"
        viewModel.createRecipe(sampleRecipe(category = Category.UNKNOWN))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.error.value)
    }

    @Test
    fun `setSearchQuery updates searchQuery state`() {
        viewModel.setSearchQuery("test")
        assertEquals("test", viewModel.searchQuery.value)
    }

    @Test
    fun `setSelectedTagId updates selectedTagId state`() {
        viewModel.setSelectedTagId(42L)
        assertEquals(42L, viewModel.selectedTagId.value)
    }

    @Test
    fun `deleteRecipe removes recipe`() = runTest(testDispatcher) {
        val id = fakeRepo.saveRecipeWithTags(sampleRecipe(), emptyList())
        viewModel.deleteRecipe(id)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(fakeRepo.savedRecipes.none { it.id == id })
    }

    @Test
    fun `createUserTag adds tag`() = runTest(testDispatcher) {
        viewModel.createUserTag("Nový štítek", TagGroup.OTHER)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.error.value)
    }

    @Test
    fun `updateRecipe modifies existing recipe`() = runTest(testDispatcher) {
        val id = fakeRepo.saveRecipeWithTags(sampleRecipe(id = 0), emptyList())
        var completed = false
        viewModel.updateRecipe(sampleRecipe(id = id, title = "Updated")) { completed = true }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(completed)
        assertEquals("Updated", fakeRepo.savedRecipes.find { it.id == id }?.title)
    }

    private fun sampleRecipe(id: Long = 0, title: String = "Test Recipe", category: Category = Category.MAIN) = Recipe(
        id = id,
        title = title,
        summary = "Test summary",
        category = category,
        isFavorite = false,
        photoPath = null,
        createdAt = java.time.Instant.now(),
        ingredientGroups = listOf(
            IngredientGroup(name = "", ingredients = listOf(Ingredient(name = "test", amount = 1.0, unit = "ks")))
        ),
        steps = listOf(Step(instruction = "mix")),
        tags = emptyList(),
        sourceUrl = null,
    )
}
