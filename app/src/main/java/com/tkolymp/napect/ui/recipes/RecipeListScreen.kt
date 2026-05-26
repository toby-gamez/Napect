package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tkolymp.napect.R
import com.tkolymp.napect.domain.model.RecipeListItem
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.domain.model.TagGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    recipes: List<RecipeListItem>,
    modifier: Modifier = Modifier,
    onItemClick: (Long) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    availableTags: List<Tag> = emptyList(),
    selectedTagId: Long? = null,
    onTagSelected: (Long?) -> Unit = {},
    onDelete: ((Long) -> Unit)? = null,
    emptyMessage: String? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val tagScrollState = rememberScrollState()
    LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp), state = lazyListState, contentPadding = contentPadding) {
        if (!errorMessage.isNullOrBlank()) {
            item {
                androidx.compose.material3.Card(
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

        stickyHeader {
            Surface(modifier = Modifier.fillMaxWidth()) {
                val tagCandidates = availableTags.filter { it.group == TagGroup.CATEGORY || it.group == TagGroup.OTHER }
                Row(modifier = Modifier.horizontalScroll(tagScrollState).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    val allSelected = selectedTagId == null
                    FilterChip(selected = allSelected, onClick = { onTagSelected(null) }, label = { Text(stringResource(R.string.filter_all)) })
                    for (t in tagCandidates) {
                        val sel = selectedTagId == t.id
                        FilterChip(selected = sel, onClick = { onTagSelected(if (sel) null else t.id) }, label = { Text(t.name) }, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillParentMaxHeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (recipes.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxHeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(emptyMessage ?: stringResource(R.string.no_recipes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(recipes) { r ->
                RecipeCard(
                    recipe = r,
                    onClick = { onItemClick(r.id) },
                    onDelete = onDelete?.let { { it(r.id) } }
                )
            }
        }
    }
}
