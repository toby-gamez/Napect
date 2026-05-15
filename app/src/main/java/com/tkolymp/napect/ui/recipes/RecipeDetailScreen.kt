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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip

@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    onClose: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onEdit: ((Long) -> Unit)? = null,
    onDelete: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = SettingsRepository(context)
    val prefs by repo.prefsFlow.collectAsState(initial = com.tkolymp.napect.data.local.UserPreferences())

    var servings by remember(recipe, prefs) { mutableStateOf(prefs.defaultServings.coerceAtLeast(1)) }
    var showConfirmDelete by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        recipe.photo?.let { bytes ->
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Foto receptu", modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
        }

        // Title row
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onToggleFavorite != null) {
                    IconButton(onClick = { onToggleFavorite(recipe.id, !recipe.isFavorite) }) {
                        Icon(imageVector = if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Oblíbené")
                    }
                } else {
                    Icon(imageVector = if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Oblíbené")
                }
                if (onEdit != null) {
                    IconButton(onClick = { onEdit(recipe.id) }) { Icon(imageVector = Icons.Filled.Edit, contentDescription = "Upravit recept") }
                }
                if (onDelete != null) {
                    IconButton(onClick = { showConfirmDelete = true }) { Icon(imageVector = Icons.Filled.Delete, contentDescription = "Smazat recept") }
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) { Text("Zavřít") }
                }
            }
        }

        recipe.summary?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }

        // Tags
        if (recipe.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.size(8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                recipe.tags.forEach { t ->
                    if (t.isAiGenerated) {
                        AssistChip(onClick = {}, label = { Text(t.name) }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "AI") }, modifier = Modifier.padding(end = 8.dp))
                    } else {
                        AssistChip(onClick = {}, label = { Text(t.name) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }

        // Servings scaler
        Spacer(modifier = Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (servings > 1) servings-- }, modifier = Modifier.size(40.dp)) { Icon(imageVector = Icons.Filled.Remove, contentDescription = "Snížit porce") }
            Text("  Porce: $servings  ", modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { servings++ }, modifier = Modifier.size(40.dp)) { Icon(imageVector = Icons.Filled.Add, contentDescription = "Zvýšit porce") }
        }

        // ── Ingredients ──────────────────────────────────────────────────────────
        Spacer(modifier = Modifier.size(12.dp))
        Text("Ingredience", style = MaterialTheme.typography.titleMedium)

        recipe.ingredientGroups.forEach { group ->
            // Only show a sub-heading when the group has a custom name (e.g. "Dough", "Topping")
            if (group.name.isNotBlank()) {
                Text(group.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            }
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                group.ingredients.forEach { ing ->
                    val scaled = if (recipe.servingsBase > 0)
                        ing.amount * servings.toDouble() / recipe.servingsBase.toDouble()
                    else ing.amount
                    val amountStr = scaled.takeIf { !it.isNaN() } ?: ing.amount
                    Text(
                        buildString {
                            val amtDouble = amountStr as Double
                            if (amtDouble != 0.0) {
                                append(if (amtDouble == kotlin.math.floor(amtDouble)) amtDouble.toInt().toString() else "%.2f".format(amtDouble).trimEnd('0').trimEnd('.'))
                                append(" ")
                            }
                            if (!ing.unit.isNullOrBlank()) { append(ing.unit); append(" ") }
                            append(ing.name)
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // ── Steps ────────────────────────────────────────────────────────────
        Spacer(modifier = Modifier.size(4.dp))
        Text("Postup", style = MaterialTheme.typography.titleMedium)
        Column {
            recipe.steps.forEach { step ->
                Text("${step.stepNumber}. ${step.instruction}", modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            confirmButton = {
                TextButton(onClick = { showConfirmDelete = false; onDelete?.invoke(recipe.id) }) { Text("Smazat") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) { Text("Zrušit") }
            },
            title = { Text("Smazat recept?") },
            text = { Text("Recept bude trvale smazán.") }
        )
    }
}
