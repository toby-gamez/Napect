package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import com.tkolymp.napect.R
import com.tkolymp.napect.data.local.PhotoManager
import com.tkolymp.napect.domain.model.RecipeListItem
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.domain.model.TagGroup
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagedRecipeListScreen(
    pagedRecipes: Flow<PagingData<RecipeListItem>>,
    modifier: Modifier = Modifier,
    onItemClick: (Long) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    availableTags: List<Tag> = emptyList(),
    selectedTagId: Long? = null,
    onTagSelected: (Long?) -> Unit = {},
    onDelete: ((Long) -> Unit)? = null,
    emptyMessage: String = "Zatím žádné recepty",
    errorMessage: String? = null,
) {
    val lazyItems = pagedRecipes.collectAsLazyPagingItems()

    LaunchedEffect(lazyItems.itemCount, lazyItems.loadState) {
        Timber.d("PagedRecipeListScreen: itemCount=%d refreshLoadState=%s appendLoadState=%s", lazyItems.itemCount, lazyItems.loadState.refresh::class.simpleName, lazyItems.loadState.append::class.simpleName)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        if (!errorMessage.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        val scrollState = rememberScrollState()
        Row(modifier = Modifier.horizontalScroll(scrollState).padding(bottom = 8.dp)) {
            val allSelected = selectedTagId == null
            FilterChip(selected = allSelected, onClick = { onTagSelected(null) }, label = { Text(stringResource(R.string.filter_all)) })

            val tagCandidates = availableTags.filter { it.group == TagGroup.CATEGORY || it.group == TagGroup.OTHER }
            for (t in tagCandidates) {
                val sel = selectedTagId == t.id
                FilterChip(selected = sel, onClick = { onTagSelected(if (sel) null else t.id) }, label = { Text(t.name) }, modifier = Modifier.padding(start = 8.dp))
            }
        }

        when {
            lazyItems.itemCount == 0 && lazyItems.loadState.refresh is androidx.paging.LoadState.Loading -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            lazyItems.itemCount == 0 && lazyItems.loadState.refresh is androidx.paging.LoadState.NotLoading -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(emptyMessage, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = contentPadding) {
            items(lazyItems.itemCount) { index ->
                val r = lazyItems[index]
                if (r != null) {
                    Card(modifier = Modifier
                        .padding(8.dp)
                        .clickable { onItemClick(r.id) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (r.photoPath != null) {
                                val bmp = PhotoManager.loadBitmap(r.photoPath!!)
                                if (bmp != null) {
                                    val img = bmp.asImageBitmap()
                                    Image(bitmap = img, contentDescription = stringResource(R.string.recipe_photo), modifier = Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Crop)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(r.title, style = MaterialTheme.typography.headlineSmall)
                                if (onDelete != null) {
                                    IconButton(onClick = { onDelete(r.id) }, modifier = Modifier.size(40.dp)) {
                                        Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_recipe))
                                    }
                                }
                            }

                            r.summary?.let { Text(text = it, modifier = Modifier.padding(top = 4.dp)) }

                            if (r.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.size(8.dp))
                                FlowRow(modifier = Modifier.fillMaxWidth()) {
                                    r.tags.forEach { t ->
                                        if (t.isAiGenerated) {
                                            AssistChip(onClick = {}, label = { Text(t.name) }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = stringResource(R.string.ai_badge)) }, modifier = Modifier.padding(end = 8.dp))
                                        } else {
                                            AssistChip(onClick = {}, label = { Text(t.name) }, modifier = Modifier.padding(end = 8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (lazyItems.loadState.append is androidx.paging.LoadState.Loading) {
                item { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
        }
        }
    }
}
