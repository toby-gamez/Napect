package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.tkolymp.napect.data.local.SettingsRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.tkolymp.napect.domain.model.Recipe
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
// single dp import above
@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    onClose: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onEdit: ((Long) -> Unit)? = null,
    onDelete: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Use the user's default servings preference (from Settings) as the initial value
    // so the app default is respected when opening a recipe detail.
    val context = LocalContext.current
    val repo = SettingsRepository(context)
    val prefs by repo.prefsFlow.collectAsState(initial = com.tkolymp.napect.data.local.UserPreferences())

    var servings by remember(recipe, prefs) { mutableStateOf(prefs.defaultServings.coerceAtLeast(1)) }

    var showConfirmDelete by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
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

                if (onEdit != null) {
                    Button(onClick = { onEdit(recipe.id) }) { Text("Edit") }
                }

                if (onDelete != null) {
                    Button(onClick = { showConfirmDelete = true }) { Text("Delete") }
                }

                // show close only when the caller provided an onClose handler
                if (onClose != null) {
                    IconButton(onClick = onClose) { Text("Close") }
                }
            }
        }

        recipe.summary?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }

        // Tags
        if (recipe.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.size(8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                recipe.tags.forEach { t ->
                    AssistChip(onClick = {}, label = { Text(t.name) }, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { if (servings > 1) servings-- }) { Text("-") }
            Text("  Servings: $servings  ", modifier = Modifier.padding(horizontal = 8.dp))
            Button(onClick = { servings++ }) { Text("+") }
        }

        Spacer(modifier = Modifier.size(12.dp))
        Text("Ingredients", style = MaterialTheme.typography.titleMedium)
        Column {
            recipe.ingredients.forEach { ing ->
                val scaled = if (recipe.servingsBase > 0) ing.amount * servings.toDouble() / recipe.servingsBase.toDouble() else ing.amount
                Text("${scaled.takeIf { !it.isNaN() } ?: ing.amount} ${ing.unit.orEmpty()} ${ing.name}")
            }
        }

        Spacer(modifier = Modifier.size(12.dp))
        Text("Steps", style = MaterialTheme.typography.titleMedium)
        Column {
            recipe.steps.forEach { step ->
                Text("${step.stepNumber}. ${step.instruction}", modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(onDismissRequest = { showConfirmDelete = false }, confirmButton = {
            TextButton(onClick = {
                showConfirmDelete = false
                onDelete?.invoke(recipe.id)
            }) { Text("Delete") }
        }, dismissButton = {
            TextButton(onClick = { showConfirmDelete = false }) { Text("Cancel") }
        }, title = { Text("Delete recipe?") }, text = { Text("This will permanently delete the recipe.") })
    }
}
