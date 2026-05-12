package com.tkolymp.napect.data.mapper

import com.tkolymp.napect.data.local.entity.IngredientEntity
import com.tkolymp.napect.data.local.entity.RecipeEntity
import com.tkolymp.napect.data.local.entity.RecipeWithDetails
import com.tkolymp.napect.data.local.entity.StepEntity
import com.tkolymp.napect.domain.model.Category
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.Step
import com.tkolymp.napect.data.local.entity.TagEntity
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.domain.model.TagGroup
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
    ingredients = ingredients.map { it.toDomain() },
    steps = steps.map { it.toDomain() },
    tags = tags.map { it.toDomain() },
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

fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    group = try { TagGroup.valueOf(group) } catch (e: Exception) { TagGroup.OTHER },
    isAiGenerated = isAiGenerated != 0,
    isUserCreated = isUserCreated != 0
)

fun Tag.toEntity(): TagEntity = TagEntity(
    id = id,
    name = name,
    group = group.name,
    isAiGenerated = if (isAiGenerated) 1 else 0,
    isUserCreated = if (isUserCreated) 1 else 0
)
