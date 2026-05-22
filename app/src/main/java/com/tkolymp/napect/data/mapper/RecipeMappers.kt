package com.tkolymp.napect.data.mapper

import com.tkolymp.napect.data.local.entity.IngredientEntity
import com.tkolymp.napect.data.local.entity.IngredientGroupEntity
import com.tkolymp.napect.data.local.entity.IngredientGroupWithIngredients
import com.tkolymp.napect.data.local.entity.RecipeEntity
import com.tkolymp.napect.data.local.entity.RecipeListItemEntity
import com.tkolymp.napect.data.local.entity.RecipeListItemWithTags
import com.tkolymp.napect.data.local.entity.RecipeWithDetails
import com.tkolymp.napect.data.local.entity.StepEntity
import com.tkolymp.napect.domain.model.Category
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.IngredientGroup
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.RecipeListItem
import com.tkolymp.napect.domain.model.Step
import com.tkolymp.napect.data.local.entity.TagEntity
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.domain.model.TagGroup
import java.time.Instant

fun RecipeWithDetails.toDomain(): Recipe = Recipe(
    id = recipe.id,
    title = recipe.title,
    summary = recipe.summary,
    sourceUrl = recipe.sourceUrl,
    sourceNote = recipe.sourceNote,
    isFavorite = recipe.isFavorite,
    category = recipe.category?.let { try { Category.valueOf(it) } catch (e: Exception) { Category.UNKNOWN } } ?: Category.UNKNOWN,
    photo = recipe.photo,
    photoPath = recipe.photoPath,
    servingsBase = recipe.servingsBase,
    ingredientGroups = ingredientGroups
        .sortedBy { it.group.sortOrder }
        .map { it.toDomain() },
    steps = steps.sortedBy { it.stepNumber }.map { it.toDomain() },
    tags = tags.map { it.toDomain() },
    createdAt = Instant.ofEpochMilli(recipe.createdAt),
    updatedAt = Instant.ofEpochMilli(recipe.updatedAt)
)

fun RecipeListItemWithTags.toDomainListItem(): RecipeListItem = RecipeListItem(
    id = recipe.id,
    title = recipe.title,
    summary = recipe.summary,
    photoPath = recipe.photoPath,
    isFavorite = recipe.isFavorite,
    category = recipe.category?.let { try { Category.valueOf(it) } catch (e: Exception) { Category.UNKNOWN } } ?: Category.UNKNOWN,
    tags = tags.map { it.toDomain() },
    createdAt = Instant.ofEpochMilli(recipe.createdAt)
)

fun RecipeListItem.toEntity(): RecipeListItemEntity = RecipeListItemEntity(
    id = id,
    title = title,
    summary = summary,
    photoPath = photoPath,
    isFavorite = isFavorite,
    category = category.name,
    createdAt = createdAt.toEpochMilli()
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
    photoPath = photoPath,
    servingsBase = servingsBase,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

// ─── Ingredient group ────────────────────────────────────────────────────────

fun IngredientGroupWithIngredients.toDomain(): IngredientGroup = IngredientGroup(
    id = group.id,
    recipeId = group.recipeId,
    name = group.name,
    sortOrder = group.sortOrder,
    ingredients = ingredients.sortedBy { it.sortOrder }.map { it.toDomain() },
)

fun IngredientGroup.toEntity(): IngredientGroupEntity = IngredientGroupEntity(
    id = id,
    recipeId = recipeId,
    name = name,
    sortOrder = sortOrder,
)

// ─── Ingredient ───────────────────────────────────────────────────────────────

fun Ingredient.toEntity(): IngredientEntity = IngredientEntity(
    id = id,
    groupId = groupId,
    amount = amount,
    unit = unit,
    name = name,
    sortOrder = sortOrder,
)

fun IngredientEntity.toDomain(): Ingredient = Ingredient(
    id = id,
    groupId = groupId,
    amount = amount,
    unit = unit,
    name = name,
    sortOrder = sortOrder,
)

// ─── Step ─────────────────────────────────────────────────────────────────────

fun Step.toEntity(): StepEntity = StepEntity(
    id = id,
    recipeId = recipeId,
    stepNumber = stepNumber,
    instruction = instruction,
)

fun StepEntity.toDomain(): Step = Step(
    id = id,
    recipeId = recipeId,
    stepNumber = stepNumber,
    instruction = instruction,
)

// ─── Tag ──────────────────────────────────────────────────────────────────────

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
