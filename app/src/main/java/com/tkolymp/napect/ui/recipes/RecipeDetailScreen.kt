package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tkolymp.napect.domain.model.Recipe
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    onClose: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var servings by remember { mutableStateOf(recipe.servingsBase.coerceAtLeast(1)) }

    Column(modifier = modifier.padding(16.dp)) {
        recipe.photo?.let { bytes ->
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Recipe photo", modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onToggleFavorite != null) {
                    IconButton(onClick = { onToggleFavorite(recipe.id, !recipe.isFavorite) }) {
                        Icon(imageVector = if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Favorite")
                    }
                } else {
                    // show icon without click when callback not provided
                    Icon(imageVector = if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Favorite")
                }
                // show close only when the caller provided an onClose handler
                if (onClose != null) {
                    IconButton(onClick = onClose) { Text("Close") }
                }
            }
        }

        recipe.summary?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }

        Spacer(modifier = Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { if (servings > 1) servings-- }) { Text("-") }
            Text("  Servings: $servings  ", modifier = Modifier.padding(horizontal = 8.dp))
            Button(onClick = { servings++ }) { Text("+") }
        }

        Spacer(modifier = Modifier.size(12.dp))
        Text("Ingredients", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(recipe.ingredients) { ing ->
                // scale amount proportionally
                val scaled = if (recipe.servingsBase > 0) ing.amount * servings.toDouble() / recipe.servingsBase.toDouble() else ing.amount
                Text("${scaled.takeIf { !it.isNaN() } ?: ing.amount} ${ing.unit.orEmpty()} ${ing.name}")
            }
        }

        Spacer(modifier = Modifier.size(12.dp))
        Text("Steps", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(recipe.steps) { step ->
                Text("${step.stepNumber}. ${step.instruction}", modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
