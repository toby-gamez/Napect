package com.tkolymp.napect.data.local.dao

import androidx.room.*
import com.tkolymp.napect.data.local.entity.IngredientEntity
import com.tkolymp.napect.data.local.entity.IngredientGroupEntity
import com.tkolymp.napect.data.local.entity.RecipeEntity
import com.tkolymp.napect.data.local.entity.RecipeListItemWithTags
import com.tkolymp.napect.data.local.entity.RecipeWithDetails
import com.tkolymp.napect.data.local.entity.StepEntity
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

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

    // ─── Lightweight list queries (no BLOB, no ingredient/step JOINs) ───────

    @Transaction
    @Query("SELECT id, title, summary, photo_path, is_favorite, category, created_at, time_minutes FROM recipes ORDER BY created_at DESC")
    fun getAllRecipeListItems(): Flow<List<RecipeListItemWithTags>>

    @Transaction
    @Query("SELECT id, title, summary, photo_path, is_favorite, category, created_at, time_minutes FROM recipes WHERE id IN (SELECT recipe_id FROM recipe_tags WHERE tag_id = :tagId) ORDER BY created_at DESC")
    fun getRecipeListItemsByTag(tagId: Long): Flow<List<RecipeListItemWithTags>>

    @Transaction
    @Query("SELECT id, title, summary, photo_path, is_favorite, category, created_at, time_minutes FROM recipes WHERE id IN (SELECT docid FROM recipe_fts WHERE recipe_fts MATCH :query) ORDER BY created_at DESC")
    fun searchRecipeListItems(query: String): Flow<List<RecipeListItemWithTags>>

    @Transaction
    @Query("""
        SELECT id, title, summary, photo_path, is_favorite, category, created_at, time_minutes FROM recipes
        WHERE id IN (SELECT recipe_id FROM recipe_tags WHERE tag_id = :tagId)
        AND id IN (SELECT docid FROM recipe_fts WHERE recipe_fts MATCH :query)
        ORDER BY created_at DESC
    """)
    fun searchRecipeListItemsByTag(tagId: Long, query: String): Flow<List<RecipeListItemWithTags>>

    // ─── Paging queries ────────────────────────────────────────────────────

    @Transaction
    @Query("SELECT id, title, summary, photo_path, is_favorite, category, created_at, time_minutes FROM recipes ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllRecipeListItemsPaged(limit: Int, offset: Int): List<RecipeListItemWithTags>

    @Transaction
    @Query("SELECT id, title, summary, photo_path, is_favorite, category, created_at, time_minutes FROM recipes WHERE id IN (SELECT recipe_id FROM recipe_tags WHERE tag_id = :tagId) ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecipeListItemsByTagPaged(tagId: Long, limit: Int, offset: Int): List<RecipeListItemWithTags>

    @Transaction
    @Query("SELECT id, title, summary, photo_path, is_favorite, category, created_at, time_minutes FROM recipes WHERE id IN (SELECT docid FROM recipe_fts WHERE recipe_fts MATCH :query) ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun searchRecipeListItemsPaged(query: String, limit: Int, offset: Int): List<RecipeListItemWithTags>

    @Transaction
    @Query("""
        SELECT id, title, summary, photo_path, is_favorite, category, created_at, time_minutes FROM recipes
        WHERE id IN (SELECT recipe_id FROM recipe_tags WHERE tag_id = :tagId)
        AND id IN (SELECT docid FROM recipe_fts WHERE recipe_fts MATCH :query)
        ORDER BY created_at DESC LIMIT :limit OFFSET :offset
    """)
    suspend fun searchRecipeListItemsByTagPaged(tagId: Long, query: String, limit: Int, offset: Int): List<RecipeListItemWithTags>

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

    @Query("UPDATE recipes SET photo_path = :path, photo = NULL WHERE id = :id")
    suspend fun updatePhotoPath(id: Long, path: String?)

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
        Timber.d("insertRecipeWithDetails: recipeId=%d title='%s' groups=%d steps=%d",
            recipeId, recipe.title, ingredientGroups.size, steps.size)
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
    @Query("SELECT * FROM recipes WHERE id IN (SELECT docid FROM recipe_fts WHERE recipe_fts MATCH :query) ORDER BY created_at DESC")
    fun search(query: String): Flow<List<RecipeWithDetails>>

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
