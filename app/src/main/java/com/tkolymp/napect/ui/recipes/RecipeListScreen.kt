package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    recipes: List<Recipe>,
    onItemClick: (Recipe) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    selectedCategory: Category? = null,
    onCategorySelected: (Category?) -> Unit = {},
    onDelete: ((Long) -> Unit)? = null
) {
    // Keep a small outer padding and apply scaffold contentPadding to the LazyColumn
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // category filter row
        val scrollState = rememberScrollState()
        Row(modifier = Modifier.horizontalScroll(scrollState).padding(bottom = 8.dp)) {
            // show an "All" chip
            val allSelected = selectedCategory == null
            FilterChip(selected = allSelected, onClick = { onCategorySelected(null) }, label = { Text("All") })
            Category.values().forEach { c ->
                val sel = selectedCategory == c
                FilterChip(selected = sel, onClick = { onCategorySelected(if (sel) null else c) }, label = { Text(c.name.lowercase().replaceFirstChar { it.uppercaseChar() }) })
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(recipes) { r ->
            // animate each card's placement and visibility
            AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(200)), exit = fadeOut()) {
                Card(modifier = Modifier.padding(8.dp).clickable { onItemClick(r) }.animateContentSize()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                            // show category name for debugging
                            Text(text = "${r.title} — ${r.category.name}", modifier = Modifier.align(androidx.compose.ui.Alignment.CenterStart))
                            if (onDelete != null) {
                                Button(onClick = { onDelete(r.id) }, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)) { Text("Delete") }
                            }
                        }
                        r.summary?.let { Text(text = it, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        }
        }
    }
}
