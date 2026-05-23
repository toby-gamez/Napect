package com.tkolymp.napect.ui.recipes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.tkolymp.napect.domain.model.RecipeListItem
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.domain.model.TagGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class RecipeListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun empty_state_shows_message() {
        composeTestRule.setContent {
            RecipeListScreen(recipes = emptyList())
        }
        composeTestRule.onNodeWithText("Zatím žádné recepty").assertIsDisplayed()
    }

    @Test
    fun list_displays_recipe_titles() {
        composeTestRule.setContent {
            RecipeListScreen(
                recipes = listOf(
                    RecipeListItem(id = 1, title = "Test Recipe 1"),
                    RecipeListItem(id = 2, title = "Test Recipe 2"),
                )
            )
        }
        composeTestRule.onNodeWithText("Test Recipe 1").assertExists()
        composeTestRule.onNodeWithText("Test Recipe 2").assertExists()
    }

    @Test
    fun filter_all_chip_is_displayed() {
        composeTestRule.setContent {
            RecipeListScreen(
                recipes = listOf(
                    RecipeListItem(id = 1, title = "Test")
                ),
                availableTags = listOf(
                    Tag(id = 1, name = "Czech", group = TagGroup.CUISINE),
                ),
            )
        }
        composeTestRule.onNodeWithText("Vše").assertIsDisplayed()
    }
}
