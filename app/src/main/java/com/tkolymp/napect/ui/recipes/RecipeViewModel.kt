package com.tkolymp.napect.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.RecipeListItem
import com.tkolymp.napect.domain.repository.RecipeRepository
import com.tkolymp.napect.domain.usecase.ClassifyRecipeUseCase
import com.tkolymp.napect.domain.usecase.PrepareAndSaveRecipeUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.asStateFlow
import com.tkolymp.napect.domain.model.Tag
import timber.log.Timber
import com.tkolymp.napect.domain.model.TagGroup

@HiltViewModel
class RecipeViewModel @Inject constructor(
    private val repo: RecipeRepository,
    private val classifyRecipe: ClassifyRecipeUseCase,
    private val prepareAndSave: PrepareAndSaveRecipeUseCase,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchListItems: StateFlow<List<RecipeListItem>> = _searchQuery
        .filter { it.length >= 2 || it.isBlank() }
        .debounce(300)
        .flatMapLatest { q -> if (q.isBlank()) repo.getAllRecipeListItems() else repo.searchRecipeListItems(q) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedTagId = MutableStateFlow<Long?>(null)
    val filteredRecipeListItems: StateFlow<List<RecipeListItem>> = combine(_searchQuery, _selectedTagId) { q, tagId ->
        q to tagId
    }.flatMapLatest { (q, tagId) ->
        val query = if (q.length < 2) "" else q
        val listFlow = when {
            tagId != null && query.isNotBlank() -> repo.searchRecipeListItemsByTag(tagId, query)
            tagId != null -> repo.getRecipeListItemsByTag(tagId)
            query.isNotBlank() -> repo.searchRecipeListItems(query)
            else -> repo.getAllRecipeListItems()
        }
        listFlow
    }.catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pagedRecipes: Flow<PagingData<RecipeListItem>> = combine(_searchQuery, _selectedTagId) { q, tagId ->
        q to tagId
    }.flatMapLatest { (q, tagId) ->
        val query = if (q.length < 2) "" else q
        repo.getPagedRecipeListItems(tagId, query).cachedIn(viewModelScope)
    }

    val selectedTagId: StateFlow<Long?> = _selectedTagId.asStateFlow()
    fun setSelectedTagId(tagId: Long?) { _selectedTagId.value = tagId }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _tagOperationLoading = MutableStateFlow(false)
    val tagOperationLoading: StateFlow<Boolean> = _tagOperationLoading.asStateFlow()

    fun createRecipe(recipe: Recipe, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val id = prepareAndSave(recipe)
                _error.value = null

                onComplete(id)
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun getRecipeById(id: Long) = repo.getRecipeById(id)

    val allTags = repo.getAllTags()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _suggestedTags = MutableStateFlow<com.tkolymp.napect.data.ai.TagSuggestion?>(null)
    val suggestedTags = _suggestedTags.asStateFlow()

    fun suggestTagsForRecipe(recipe: Recipe) {
        viewModelScope.launch {
            try {
                val suggestion = repo.suggestTagsForRecipe(recipe)
                _suggestedTags.value = suggestion
                Timber.d("suggestTagsForRecipe -> confirmed=%s newly=%s", suggestion.confirmed.map { it.name }, suggestion.newlyCreated.map { it.name })
            } catch (e: Exception) {
                _error.value = e.message ?: "Tag suggestion failed"
            }
        }
    }

    fun createRecipeWithTags(recipe: Recipe, tagIds: List<Long>, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            Timber.d("createRecipeWithTags: title='%s' tagIds=%s selectedTagId=%s searchQuery='%s'",
                recipe.title, tagIds, _selectedTagId.value, _searchQuery.value)
            try {
                val id = prepareAndSave(recipe, tagIds)
                _error.value = null
                onComplete(id)
            } catch (e: Exception) {
                Timber.w(e, "createRecipeWithTags: failed")
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun updateRecipeWithTags(recipe: Recipe, tagIds: List<Long>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                prepareAndSave.update(recipe, tagIds)
                _error.value = null

                onComplete()
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun createUserTag(name: String, group: TagGroup) {
        viewModelScope.launch {
            _tagOperationLoading.value = true
            try {
                repo.createUserTag(name, group)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create tag"
            } finally {
                _tagOperationLoading.value = false
            }
        }
    }

    fun toggleFavorite(id: Long, value: Boolean) {
        viewModelScope.launch { repo.toggleFavorite(id, value) }
    }

    fun updateRecipe(recipe: Recipe, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                prepareAndSave.update(recipe)
                _error.value = null

                onComplete()
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun deleteRecipe(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.deleteRecipe(id)
                _error.value = null

                onComplete()
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }

    fun updatePhotoPath(id: Long, path: String?) {
        viewModelScope.launch {
            try {
                repo.updatePhotoPath(id, path)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update photo path"
            }
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch {
            _tagOperationLoading.value = true
            try {
                repo.deleteTag(id)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete tag"
            } finally {
                _tagOperationLoading.value = false
            }
        }
    }

    fun restoreDefaultTags() {
        viewModelScope.launch {
            _tagOperationLoading.value = true
            try {
                val inserted = repo.ensureDefaultTags()
                repo.migrateEnglishTagsToCzech()
                _error.value = if (inserted > 0) "Restored $inserted default tags" else "Default tags already present"
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to restore default tags"
            } finally {
                _tagOperationLoading.value = false
            }
        }
    }
}
