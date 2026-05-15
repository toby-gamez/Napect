package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import android.app.DatePickerDialog
import java.util.Calendar
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import kotlinx.coroutines.launch

@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    onClose: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onEdit: ((Long) -> Unit)? = null,
    onDelete: ((Long) -> Unit)? = null,
    onMake: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onRegisterTopBarActions: (((@Composable () -> Unit) -> Unit))? = null
) {
    val context = LocalContext.current
    val repo = SettingsRepository(context)
    val prefs by repo.prefsFlow.collectAsState(initial = com.tkolymp.napect.data.local.UserPreferences())

    // On detail screen show amounts for a single portion (as requested)
    var servings by remember(recipe, prefs) { mutableStateOf(1) }
    var showConfirmDelete by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            recipe.photo?.let { bytes ->
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Foto receptu", modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
            }

            // Title row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.headlineMedium)
                // Empty placeholder: actions moved to the top app bar
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
            Text("Ingredience", style = MaterialTheme.typography.titleLarge)

            recipe.ingredientGroups.forEach { group ->
                // Only show a sub-heading when the group has a custom name (e.g. "Dough", "Topping")
                if (group.name.isNotBlank()) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    group.ingredients.forEach { ing ->
                        val scaled = if (recipe.servingsBase > 0)
                            ing.amount * servings.toDouble() / recipe.servingsBase.toDouble()
                        else ing.amount
                        val amountStr = scaled.takeIf { !it.isNaN() } ?: ing.amount
                        // Wrap each ingredient row in a small card for better visual grouping
                        androidx.compose.material3.ElevatedCard(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
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
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // ── Steps ────────────────────────────────────────────────────────────
            Spacer(modifier = Modifier.size(4.dp))
            Text("Postup", style = MaterialTheme.typography.titleLarge)
            Column {
                recipe.steps.forEach { step ->
                    // Each step displayed inside a card
                    androidx.compose.material3.ElevatedCard(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("${step.stepNumber}. ${step.instruction}", modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.size(120.dp))
        }

        // Floating split-like action at bottom-end
        var menuExpanded by remember { mutableStateOf(false) }
        var showDatePicker by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        if (showDatePicker) {
            val cal = Calendar.getInstance()
            DatePickerDialog(context, { _, y, m, d ->
                val picked = Calendar.getInstance().apply { set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d); set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                coroutineScope.launch {
                    try {
                        repo.setPlannedCookDate(recipe.id, picked.timeInMillis)
                        Toast.makeText(context, "Vaření naplánováno na ${d}.${m+1}.$y", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, "Chyba při plánování", Toast.LENGTH_SHORT).show()
                    }
                }
                showDatePicker = false
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        Box(modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)) {
            androidx.compose.material3.ElevatedCard(colors = androidx.compose.material3.CardDefaults.elevatedCardColors(), shape = RoundedCornerShape(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    androidx.compose.material3.Button(onClick = { onMake?.invoke(recipe.id) }, modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kuchtit")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box {
                        IconButton(onClick = { menuExpanded = !menuExpanded }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (recipe.isFavorite) "Odebrat z oblíbených" else "Přidat do oblíbených") },
                                leadingIcon = { Icon(imageVector = if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onToggleFavorite?.invoke(recipe.id, !recipe.isFavorite)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Smazat recept") },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showConfirmDelete = true
                                }
                            )
                        
                        }
                    }
                }
            }

            if (showConfirmDelete) {
                androidx.compose.material3.AlertDialog(onDismissRequest = { showConfirmDelete = false }, confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showConfirmDelete = false; onDelete?.invoke(recipe.id) }) { Text("Smazat") }
                }, dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showConfirmDelete = false }) { Text("Zrušit") }
                }, title = { Text("Smazat recept?") }, text = { Text("Recept bude trvale smazán.") })
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

    // Register top-bar actions for this detail screen (favorite, edit, delete)
    onRegisterTopBarActions?.invoke({
        Row {
            if (onToggleFavorite != null) {
                IconButton(onClick = { onToggleFavorite(recipe.id, !recipe.isFavorite) }) {
                    Icon(imageVector = if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Oblíbené")
                }
            }
            if (onEdit != null) {
                IconButton(onClick = { onEdit(recipe.id) }) { Icon(imageVector = Icons.Filled.Edit, contentDescription = "Upravit recept") }
            }
            if (onDelete != null) {
                IconButton(onClick = { showConfirmDelete = true }) { Icon(imageVector = Icons.Filled.Delete, contentDescription = "Smazat recept") }
            }
        }
    })
}
