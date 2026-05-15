package com.tkolymp.napect.ui.recipes

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.domain.model.TagGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    recipes: List<Recipe>,
    onItemClick: (Recipe) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    // availableTags should contain TagGroup.CATEGORY and TagGroup.OTHER tags
    availableTags: List<Tag> = emptyList(),
    // currently selected tag id used for filtering (null = all)
    selectedTagId: Long? = null,
    onTagSelected: (Long?) -> Unit = {},
    onDelete: ((Long) -> Unit)? = null
) {
    // Keep a small outer padding and apply scaffold contentPadding to the LazyColumn
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // tag filter row (replaces category chips). Show tags from Category and Other groups
        val scrollState = rememberScrollState()
        Row(modifier = Modifier.horizontalScroll(scrollState).padding(bottom = 8.dp)) {
            // show an "All" chip
            val allSelected = selectedTagId == null
            FilterChip(selected = allSelected, onClick = { onTagSelected(null) }, label = { Text("Vše") })

            // Only show CATEGORY and OTHER tags from the provided availableTags list
            val tagCandidates = availableTags.filter { it.group == TagGroup.CATEGORY || it.group == TagGroup.OTHER }
            for (t in tagCandidates) {
                val sel = selectedTagId == t.id
                FilterChip(selected = sel, onClick = { onTagSelected(if (sel) null else t.id) }, label = { Text(t.name) }, modifier = Modifier.padding(start = 8.dp))
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(recipes) { r ->
                // animate each card's placement and visibility
                AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(200)), exit = fadeOut()) {
                    Card(modifier = Modifier
                        .padding(8.dp)
                        .clickable { onItemClick(r) }
                        .animateContentSize()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // compact detail-like header: photo, title, summary, tags
                            r.photo?.let { bytes ->
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Foto receptu", modifier = Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Crop)
                            }

                            // Title row
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(r.title, style = MaterialTheme.typography.headlineSmall)
                                if (onDelete != null) {
                                    IconButton(onClick = { onDelete(r.id) }, modifier = Modifier.size(40.dp)) {
                                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Smazat recept")
                                    }
                                }
                            }

                            r.summary?.let { Text(text = it, modifier = Modifier.padding(top = 4.dp)) }

                            if (r.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.size(8.dp))
                                FlowRow(modifier = Modifier.fillMaxWidth()) {
                                    r.tags.forEach { t ->
                                        if (t.isAiGenerated) {
                                            AssistChip(onClick = {}, label = { Text(t.name) }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "AI") }, modifier = Modifier.padding(end = 8.dp))
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
        }
    }
}
