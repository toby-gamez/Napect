package com.tkolymp.napect.data.mapper

import com.tkolymp.napect.data.local.entity.IngredientEntity
import com.tkolymp.napect.data.local.entity.RecipeEntity
import com.tkolymp.napect.data.local.entity.RecipeWithDetails
import com.tkolymp.napect.data.local.entity.StepEntity
import com.tkolymp.napect.domain.model.Category
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.Step
import java.util.*

fun RecipeWithDetails.toDomain(): Recipe = Recipe(
    id = recipe.id,
    title = recipe.title,
    summary = recipe.summary,
    sourceUrl = recipe.sourceUrl,
    sourceNote = recipe.sourceNote,
    isFavorite = recipe.isFavorite,
    category = recipe.category?.let { try { Category.valueOf(it) } catch (e: Exception) { Category.UNKNOWN } } ?: Category.UNKNOWN,
    photo = recipe.photo,
    servingsBase = recipe.servingsBase,
    createdAt = Date(recipe.createdAt),
    updatedAt = Date(recipe.updatedAt)
)

fun Recipe.toEntity(): RecipeEntity = RecipeEntity(
    id = id,
    title = title,
    summary = summary,
    sourceUrl = sourceUrl,
    sourceNote = sourceNote,
    isFavorite = isFavorite,
    category = category.name,
    photo = photo,
    servingsBase = servingsBase,
    createdAt = createdAt.time,
    updatedAt = updatedAt.time,
)

fun Ingredient.toEntity(): IngredientEntity = IngredientEntity(
    id = id,
    recipeId = recipeId,
    amount = amount,
    unit = unit,
    name = name,
    sortOrder = sortOrder,
)

fun Step.toEntity(): StepEntity = StepEntity(
    id = id,
    recipeId = recipeId,
    stepNumber = stepNumber,
    instruction = instruction,
)

fun IngredientEntity.toDomain(): Ingredient = Ingredient(
    id = id,
    recipeId = recipeId,
    amount = amount,
    unit = unit,
    name = name,
    sortOrder = sortOrder,
)

fun StepEntity.toDomain(): Step = Step(
    id = id,
    recipeId = recipeId,
    stepNumber = stepNumber,
    instruction = instruction,
)
