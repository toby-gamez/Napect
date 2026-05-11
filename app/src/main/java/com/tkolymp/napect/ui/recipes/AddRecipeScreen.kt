package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

// Stateful holder for UI ingredient inputs to avoid list-replacement issues
private class IngredientInputState(
    val amount: MutableState<String> = mutableStateOf("")
    , val unit: MutableState<String> = mutableStateOf("")
    , val name: MutableState<String> = mutableStateOf("")
)

@Composable
fun AddRecipeScreen(onSave: (Recipe) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }

    val ingredients = remember { mutableStateListOf<IngredientInputState>() }
    val steps = remember { mutableStateListOf<String>() }

    // image bytes picked from gallery (optional)
    val context = LocalContext.current
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }

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

    // start with one empty row for convenience
    if (ingredients.isEmpty()) ingredients.add(IngredientInputState())
    if (steps.isEmpty()) steps.add("")

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
                            title = title.trim(),
                            summary = summary.ifBlank { null },
                            ingredients = ingDomain,
                            steps = stepsDomain,
                            photo = photoBytes
                        )
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
