package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import com.tkolymp.napect.R
import com.tkolymp.napect.domain.model.RecipeListItem
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagedRecipeListScreen(
    pagedRecipes: Flow<PagingData<RecipeListItem>>,
    modifier: Modifier = Modifier,
    onItemClick: (Long) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onDelete: ((Long) -> Unit)? = null,
    emptyMessage: String? = null,
    errorMessage: String? = null,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val lazyItems = pagedRecipes.collectAsLazyPagingItems()

    val isLoading = lazyItems.itemCount == 0 && lazyItems.loadState.refresh is androidx.paging.LoadState.Loading
    val isEmpty = lazyItems.itemCount == 0 && lazyItems.loadState.refresh is androidx.paging.LoadState.NotLoading

    LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp), state = lazyListState, contentPadding = contentPadding) {
        if (!errorMessage.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillParentMaxHeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (isEmpty) {
            item {
                Box(modifier = Modifier.fillParentMaxHeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(emptyMessage ?: stringResource(R.string.no_recipes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(lazyItems.itemCount) { index ->
                val r = lazyItems[index]
                if (r != null) {
                    RecipeCard(
                        recipe = r,
                        onClick = { onItemClick(r.id) },
                        onDelete = onDelete?.let { { it(r.id) } }
                    )
                }
            }

            if (lazyItems.loadState.append is androidx.paging.LoadState.Loading) {
                item { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
        }
    }
}
