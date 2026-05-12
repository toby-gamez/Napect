package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
// Column already imported above
import androidx.compose.foundation.layout.Row
// fillMaxWidth already imported above
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
// Keyboard input types referenced fully-qualified to avoid import resolution issues in some build setups
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// keyboard input options removed for compatibility; rely on default keyboard
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.Step
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.domain.model.TagGroup
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

// Stateful holder for UI ingredient inputs to avoid list-replacement issues
private class IngredientInputState(
    initialAmount: String = "",
    initialUnit: String = "",
    initialName: String = ""
) {
    val amount: MutableState<String> = mutableStateOf(initialAmount)
    val unit: MutableState<String> = mutableStateOf(initialUnit)
    val name: MutableState<String> = mutableStateOf(initialName)
}

@Composable
fun AddRecipeScreen(
    onSave: (Recipe, List<Long>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialRecipe: Recipe? = null,
    availableTags: List<Tag> = emptyList(),
    suggestedTagIds: Set<Long> = emptySet(),
    onSuggest: (Recipe) -> Unit = {},
    onCreateUserTag: (String, TagGroup) -> Unit = { _, _ -> }
) {
    val init = initialRecipe
    var title by remember(init) { mutableStateOf(init?.title ?: "") }
    var summary by remember(init) { mutableStateOf(init?.summary ?: "") }
    var servingsBase by remember(init) { mutableStateOf(init?.servingsBase ?: 4) }

    val ingredients = remember(init) { mutableStateListOf<IngredientInputState>().apply {
        init?.ingredients?.forEach { add(IngredientInputState(it.amount.toString(), it.unit ?: "", it.name)) }
        if (isEmpty()) add(IngredientInputState())
    } }
    val steps = remember(init) { mutableStateListOf<String>().apply {
        init?.steps?.forEach { add(it.instruction) }
        if (isEmpty()) add("")
    } }

    // image bytes picked from gallery (optional)
    val context = LocalContext.current
    var photoBytes by remember(init) { mutableStateOf<ByteArray?>(init?.photo) }

    val pickLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    val baos = ByteArrayOutputStream()
                    ins.copyTo(baos)
                    photoBytes = baos.toByteArray()
                }
            } catch (_: Exception) {
            }
        }
    }

    // lists initialized above

    Column(modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        // Photo picker
        if (photoBytes != null) {
            val bmp = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes!!.size)
            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Selected photo", modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
            Button(onClick = { photoBytes = null }, modifier = Modifier.padding(top = 8.dp)) { Text("Remove Photo") }
        } else {
            Button(onClick = { pickLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Pick Photo") }
        }

        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it },
            label = { Text("Summary") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { if (servingsBase > 1) servingsBase-- }) { Text("-") }
            Text("  Base servings: $servingsBase  ", modifier = Modifier.padding(horizontal = 8.dp))
            Button(onClick = { servingsBase++ }) { Text("+") }
        }

        Spacer(modifier = Modifier.size(8.dp))
        Text("Ingredients")
        Column(modifier = Modifier.fillMaxWidth()) {
            ingredients.forEachIndexed { index, ing ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = ing.amount.value,
                        onValueChange = { ing.amount.value = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                    OutlinedTextField(
                        value = ing.unit.value,
                        onValueChange = { ing.unit.value = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                    OutlinedTextField(
                        value = ing.name.value,
                        onValueChange = { ing.name.value = it },
                        label = { Text("Ingredient") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { if (ingredients.size > 1) ingredients.removeAt(index) else { ingredients[index] = IngredientInputState() } }) {
                        Text("Remove")
                    }
                }
            }
        }

        Button(onClick = { ingredients.add(IngredientInputState()) }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Add Ingredient")
        }

        Spacer(modifier = Modifier.size(12.dp))
        // Tag picker
        Text("Tags", fontWeight = FontWeight.Bold)
        val selectedTagIds = remember { mutableStateListOf<Long>() }
        // preselect from initial recipe if present
        LaunchedEffect(init) {
            selectedTagIds.clear()
            init?.tags?.forEach { selectedTagIds.add(it.id) }
        }

        // group tags by group for display
        val grouped = availableTags.groupBy { it.group }
        grouped.forEach { (group, tags) ->
            Text(group.name, modifier = Modifier.padding(top = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                for (t in tags) {
                    val checked = selectedTagIds.contains(t.id) || suggestedTagIds.contains(t.id)
                    FilterChip(selected = checked, onClick = {
                        if (selectedTagIds.contains(t.id)) selectedTagIds.remove(t.id) else selectedTagIds.add(t.id)
                    }, label = { Text(t.name) }, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.size(8.dp))
        // Quick suggestion button
        Button(onClick = {
            // build a Recipe preview from current fields and request suggestions
            val ingDomain = ingredients.mapIndexedNotNull { idx, it ->
                val amt = it.amount.value.toDoubleOrNull() ?: 0.0
                val unit = it.unit.value.ifBlank { null }
                val name = it.name.value.trim()
                if (name.isBlank()) return@mapIndexedNotNull null
                Ingredient(amount = amt, unit = unit, name = name, sortOrder = idx)
            }
            val stepsDomain = steps.mapIndexedNotNull { idx, s ->
                val instr = s.trim()
                if (instr.isBlank()) return@mapIndexedNotNull null
                Step(stepNumber = idx + 1, instruction = instr)
            }
            val preview = Recipe(
                id = init?.id ?: 0L,
                title = title.trim(),
                summary = summary.ifBlank { null },
                ingredients = ingDomain,
                steps = stepsDomain,
                photo = photoBytes,
                servingsBase = servingsBase
            )
            onSuggest(preview)
        }) { Text("Suggest tags") }

        // Create custom tag UI
        Spacer(modifier = Modifier.size(8.dp))
        var newTagName by remember { mutableStateOf("") }
        var expanded by remember { mutableStateOf(false) }
        var selectedGroup by remember { mutableStateOf(TagGroup.OTHER) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newTagName, onValueChange = { newTagName = it }, label = { Text("New tag name") }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { expanded = true }) { Text(selectedGroup.name) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TagGroup.values().forEach { g ->
                    DropdownMenuItem(text = { Text(g.name) }, onClick = { selectedGroup = g; expanded = false })
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (newTagName.isNotBlank()) {
                    onCreateUserTag(newTagName.trim(), selectedGroup)
                    newTagName = ""
                }
            }) { Text("Create tag") }
        }

        Spacer(modifier = Modifier.size(12.dp))
        Text("Steps")
        Column(modifier = Modifier.fillMaxWidth()) {
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(value = step, onValueChange = { steps[index] = it }, label = { Text("Step ${index + 1}") }, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { if (steps.size > 1) steps.removeAt(index) else steps[index] = "" }) {
                        Text("Remove")
                    }
                }
            }
        }

        Button(onClick = { steps.add("") }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Add Step")
        }

        Spacer(modifier = Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (title.isNotBlank()) {
                    val ingDomain = ingredients.mapIndexedNotNull { idx, it ->
                        val amt = it.amount.value.toDoubleOrNull() ?: 0.0
                        val unit = it.unit.value.ifBlank { null }
                        val name = it.name.value.trim()
                        if (name.isBlank()) return@mapIndexedNotNull null
                        Ingredient(amount = amt, unit = unit, name = name, sortOrder = idx)
                    }
                    val stepsDomain = steps.mapIndexedNotNull { idx, s ->
                        val instr = s.trim()
                        if (instr.isBlank()) return@mapIndexedNotNull null
                        Step(stepNumber = idx + 1, instruction = instr)
                    }

                    onSave(
                        Recipe(
                            id = init?.id ?: 0L,
                            title = title.trim(),
                            summary = summary.ifBlank { null },
                            ingredients = ingDomain,
                            steps = stepsDomain,
                            photo = photoBytes,
                            servingsBase = servingsBase
                        ),
                        selectedTagIds.toList()
                    )
                }
            }) {
                Text("Save")
            }
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}
