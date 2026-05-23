package com.tkolymp.napect.data.local

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tkolymp.napect.data.local.dao.RecipeDao
import com.tkolymp.napect.data.mapper.toDomainListItem
import com.tkolymp.napect.domain.model.RecipeListItem
import timber.log.Timber

class RecipePagingSource(
    private val dao: RecipeDao,
    private val tagId: Long? = null,
    private val searchQuery: String = "",
) : PagingSource<Int, RecipeListItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RecipeListItem> {
        val position = params.key ?: 0
        val pageSize = params.loadSize
        Timber.d("RecipePagingSource.load: tagId=%s searchQuery='%s' position=%d pageSize=%d", tagId, searchQuery, position, pageSize)
        return try {
            val items = when {
                tagId != null && searchQuery.isNotBlank() ->
                    dao.searchRecipeListItemsByTagPaged(tagId, searchQuery, pageSize, position)
                tagId != null ->
                    dao.getRecipeListItemsByTagPaged(tagId, pageSize, position)
                searchQuery.isNotBlank() ->
                    dao.searchRecipeListItemsPaged(searchQuery, pageSize, position)
                else ->
                    dao.getAllRecipeListItemsPaged(pageSize, position)
            }
            Timber.d("RecipePagingSource.load: result count=%d", items.size)
            LoadResult.Page(
                data = items.map { it.toDomainListItem() },
                prevKey = if (position == 0) null else position - pageSize,
                nextKey = if (items.size < pageSize) null else position + items.size,
            )
        } catch (e: Exception) {
            Timber.w(e, "RecipePagingSource.load: error")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, RecipeListItem>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}
