package com.tkolymp.napect.data.local.dao

import androidx.room.*
import com.tkolymp.napect.data.local.entity.IngredientEntity
import com.tkolymp.napect.data.local.entity.IngredientGroupEntity
import com.tkolymp.napect.data.local.entity.RecipeEntity
import com.tkolymp.napect.data.local.entity.RecipeWithDetails
import com.tkolymp.napect.data.local.entity.StepEntity
import kotlinx.coroutines.flow.Flow

/** Lightweight container used when inserting a group together with its ingredients. */
data class IngredientGroupInsert(
    val group: IngredientGroupEntity,
    val ingredients: List<IngredientEntity>,
)

@Dao
interface RecipeDao {
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY created_at DESC")
    fun getAllRecipesWithDetails(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun getRecipeWithDetails(id: Long): Flow<RecipeWithDetails?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredientGroup(group: IngredientGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<StepEntity>)

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: Long)

    @Query("DELETE FROM ingredient_groups WHERE recipe_id = :recipeId")
    suspend fun deleteIngredientGroupsByRecipeId(recipeId: Long)

    @Query("DELETE FROM steps WHERE recipe_id = :recipeId")
    suspend fun deleteStepsByRecipeId(recipeId: Long)

    @Query("UPDATE recipes SET is_favorite = :fav WHERE id = :id")
    suspend fun updateFavorite(id: Long, fav: Boolean)

    /**
     * Insert a complete recipe (entity + ingredient groups + steps) in a single transaction.
     * The recipeId and groupId fields in the supplied entities are ignored — correct IDs are
     * assigned after each insert returns its generated key.
     */
    @Transaction
    suspend fun insertRecipeWithDetails(
        recipe: RecipeEntity,
        ingredientGroups: List<IngredientGroupInsert>,
        steps: List<StepEntity>,
    ): Long {
        val recipeId = insertRecipe(recipe)
        for (groupInsert in ingredientGroups) {
            val groupId = insertIngredientGroup(groupInsert.group.copy(recipeId = recipeId))
            if (groupInsert.ingredients.isNotEmpty()) {
                insertIngredients(groupInsert.ingredients.map { it.copy(groupId = groupId) })
            }
        }
        if (steps.isNotEmpty()) {
            insertSteps(steps.map { it.copy(recipeId = recipeId) })
        }
        return recipeId
    }

    /**
     * Replace ingredient groups, ingredients, and steps for an existing recipe in a single
     * transaction. The recipe row itself is updated separately via [updateRecipe].
     */
    @Transaction
    suspend fun replaceRecipeDetails(
        recipeId: Long,
        ingredientGroups: List<IngredientGroupInsert>,
        steps: List<StepEntity>,
    ) {
        deleteIngredientGroupsByRecipeId(recipeId)
        deleteStepsByRecipeId(recipeId)
        for (groupInsert in ingredientGroups) {
            val groupId = insertIngredientGroup(groupInsert.group.copy(recipeId = recipeId))
            if (groupInsert.ingredients.isNotEmpty()) {
                insertIngredients(groupInsert.ingredients.map { it.copy(groupId = groupId) })
            }
        }
        if (steps.isNotEmpty()) {
            insertSteps(steps.map { it.copy(recipeId = recipeId) })
        }
    }

    @Transaction
    @Query("SELECT * FROM recipes WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' ORDER BY created_at DESC")
    fun search(query: String): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT r.* FROM recipes r INNER JOIN recipe_tags rt ON r.id = rt.recipe_id WHERE rt.tag_id = :tagId ORDER BY r.created_at DESC")
    fun getRecipesByTagId(tagId: Long): Flow<List<RecipeWithDetails>>

    @Query("DELETE FROM recipe_tags WHERE recipe_id = :recipeId")
    suspend fun deleteRecipeTags(recipeId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeTagCrossRefs(refs: List<com.tkolymp.napect.data.local.entity.RecipeTagCrossRef>)

    @Transaction
    suspend fun setRecipeTags(recipeId: Long, tagIds: List<Long>) {
        deleteRecipeTags(recipeId)
        if (tagIds.isNotEmpty()) {
            val refs = tagIds.map { com.tkolymp.napect.data.local.entity.RecipeTagCrossRef(recipeId = recipeId, tagId = it) }
            insertRecipeTagCrossRefs(refs)
        }
    }
}
