package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tkolymp.napect.domain.model.Recipe

@Composable
fun RecipeListScreen(
    recipes: List<Recipe>,
    onItemClick: (Recipe) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    // Keep a small outer padding and apply scaffold contentPadding to the LazyColumn
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), contentPadding = contentPadding) {
        items(recipes) { r ->
            // animate each card's placement and visibility
            AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(200)), exit = fadeOut()) {
                Card(modifier = Modifier.padding(8.dp).clickable { onItemClick(r) }.animateContentSize()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = r.title)
                        r.summary?.let { Text(text = it, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        }
    }
}
