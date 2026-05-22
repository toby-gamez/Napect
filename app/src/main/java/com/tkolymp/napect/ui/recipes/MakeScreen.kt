package com.tkolymp.napect.ui.recipes

import android.app.Activity
import android.view.WindowManager
// animations removed to avoid overlapping during transitions
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
// More-vert removed for Make screen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
// Dropdown menu removed from Make screen
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tkolymp.napect.R
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.data.local.PhotoManager
import com.tkolymp.napect.domain.model.Recipe
import kotlinx.coroutines.delay
import com.tkolymp.napect.data.local.SettingsRepository

@Composable
fun MakeScreen(
    recipe: Recipe,
    onFinish: (() -> Unit)? = null,
    onSave: ((Recipe) -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onDelete: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    // Load user prefs to prefill servings for the prepare (Make) screen
    val repo = remember { SettingsRepository(context) }
    val prefs by repo.prefsFlow.collectAsState(initial = com.tkolymp.napect.data.local.UserPreferences())
    var servings by remember(recipe, prefs) { mutableStateOf(prefs.defaultServings.coerceAtLeast(1)) }

    // Keep screen on while this composable is present
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Timer (start/stop controlled by user)
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    // reset timer when recipe changes
    LaunchedEffect(recipe.id) {
        elapsedSeconds = 0L
        isRunning = false
    }
    // tick while running
    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    fun formatElapsed(s: Long): String {
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    // Ingredient checklist state (id -> checked)
    val checked = remember { mutableStateMapOf<Long, Boolean>() }
    val allIngredients = recipe.ingredientGroups.flatMap { it.ingredients }
    allIngredients.forEach { ing -> if (!checked.containsKey(ing.id)) checked[ing.id] = false }

    // Steps / page state: page 0 == ingredients; pages 1..N are steps
    val steps = recipe.steps
    val stepCount = steps.size
    var pageIndex by remember { mutableStateOf(0) }
    val totalPages = 1 + stepCount

    // Start the timer automatically when the user navigates to the first step
    LaunchedEffect(pageIndex) {
        if (pageIndex == 1) {
            isRunning = true
        }
    }

    // Menu removed from Make screen (no overflow actions here)

    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            // add top padding to leave room for the floating timer so it doesn't overlap content
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 16.dp)) {
                // Photo (kept at top for context)
                if (recipe.photoPath != null) {
                    val bmp = PhotoManager.loadBitmap(recipe.photoPath!!)
                    if (bmp != null) {
                        val img = bmp.asImageBitmap()
                        Image(bitmap = img, contentDescription = stringResource(R.string.recipe_photo), modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
                     }
                 } else if (recipe.photo != null) {
                     val bmp = sampledBitmap(recipe.photo!!)
                     if (bmp != null) {
                         val img = bmp.asImageBitmap()
                         Image(bitmap = img, contentDescription = stringResource(R.string.recipe_photo), modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
                    }
                }

                // Title row
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // show favorite toggle using filled icon (outlined not available here)
                        if (onToggleFavorite != null) {
                            IconButton(onClick = { onToggleFavorite(recipe.id, !recipe.isFavorite) }) {
                                Icon(imageVector = Icons.Filled.Favorite, contentDescription = stringResource(R.string.nav_favorites))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.size(12.dp))

                // Pages: 0 == ingredient checklist, 1..N == step pages (no animation to avoid overlapping)
                if (pageIndex == 0) {
                    val doneCount = allIngredients.count { checked[it.id] == true }
                    Text(stringResource(R.string.ingredients_header, doneCount, allIngredients.size), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.size(6.dp))
                    LinearProgressIndicator(progress = if (allIngredients.isEmpty()) 0f else doneCount.toFloat() / allIngredients.size.toFloat(), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))

                    // Portion selector moved here: controls how ingredients are scaled while preparing
                    Spacer(modifier = Modifier.size(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (servings > 1) servings-- }, modifier = Modifier.size(40.dp)) { Icon(imageVector = Icons.Filled.Remove, contentDescription = stringResource(R.string.servings_decrease)) }
                         Text(stringResource(R.string.servings_label, servings), modifier = Modifier.padding(horizontal = 8.dp))
                         IconButton(onClick = { servings++ }, modifier = Modifier.size(40.dp)) { Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.servings_increase)) }
                    }

                    Column {
                        allIngredients.forEach { ing: Ingredient ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Checkbox(checked = checked[ing.id] == true, onCheckedChange = { v -> checked[ing.id] = v })
                                Spacer(modifier = Modifier.width(8.dp))
                                val textDecoration = if (checked[ing.id] == true) TextDecoration.LineThrough else TextDecoration.None
                                // scale ingredient amounts based on selected servings
                                val scaled = if (recipe.servingsBase > 0)
                                    ing.amount * servings.toDouble() / recipe.servingsBase.toDouble()
                                else ing.amount
                                val amountStr = scaled.takeIf { !it.isNaN() } ?: ing.amount
                                Text(text = buildString {
                                    val amtDouble = (amountStr as? Double) ?: ing.amount
                                    if (amtDouble != 0.0) {
                                        append(if (amtDouble == kotlin.math.floor(amtDouble)) amtDouble.toInt().toString() else "%.2f".format(amtDouble).trimEnd('0').trimEnd('.'))
                                        append(" ")
                                    }
                                    if (!ing.unit.isNullOrBlank()) { append(ing.unit); append(" ") }
                                    append(ing.name)
                                }, modifier = Modifier.weight(1f), textDecoration = textDecoration)
                            }
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Button(onClick = { if (totalPages > 1) pageIndex = 1 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.proceed_to_steps)) }
                    }
                } else {
                    val stepIndex = pageIndex - 1
                    val step = steps.getOrNull(stepIndex)
                    if (step == null) {
                        Text(stringResource(R.string.no_steps), modifier = Modifier.padding(top = 8.dp))
                    } else {
                        // use bodyLarge for the step header as requested
                        Text(stringResource(R.string.step_header, stepIndex + 1, stepCount), style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.size(6.dp))
                        LinearProgressIndicator(progress = (stepIndex + 1).toFloat() / stepCount.toFloat(), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.step_number, stepIndex + 1), style = MaterialTheme.typography.displaySmall)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(step.instruction, style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.size(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Button(onClick = { if (pageIndex > 0) pageIndex-- }, enabled = pageIndex > 0) { Text(stringResource(R.string.previous)) }
                                     Button(onClick = {
                                         if (pageIndex < totalPages - 1) pageIndex++ else onFinish?.invoke()
                                     }, enabled = true) { Text(if (pageIndex < totalPages - 1) stringResource(R.string.next) else stringResource(R.string.done)) }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.size(120.dp))
            }

            // Floating timer (top-end overlay). Click to start/stop.
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                ElevatedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.clickable { isRunning = !isRunning }) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isRunning) stringResource(R.string.timer_pause) else stringResource(R.string.timer_start))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(formatElapsed(elapsedSeconds))
                    }
                }
            }

            // Bottom floating split control (Start/Stop + overflow)
            Box(modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)) {
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(), shape = RoundedCornerShape(28.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Button(onClick = { isRunning = !isRunning }, modifier = Modifier.height(48.dp)) {
                                     Icon(if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isRunning) stringResource(R.string.timer_pause) else stringResource(R.string.timer_start))
                                     Spacer(modifier = Modifier.width(8.dp))
                                     Text(if (isRunning) stringResource(R.string.stop) else stringResource(R.string.start))
                                 }
                            }
                        }
            }
        }
    }
}
