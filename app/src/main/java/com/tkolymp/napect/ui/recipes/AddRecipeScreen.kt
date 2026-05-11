package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// keyboard input options removed for compatibility; rely on default keyboard
import androidx.compose.ui.unit.dp
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.Step

// Simple holder for UI ingredient inputs
private data class IngredientInput(val amountText: String = "", val unit: String = "", val name: String = "")

@Composable
fun AddRecipeScreen(onSave: (Recipe) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }

    val ingredients = remember { mutableStateListOf<IngredientInput>() }
    val steps = remember { mutableStateListOf<String>() }

    // start with one empty row for convenience
    if (ingredients.isEmpty()) ingredients.add(IngredientInput())
    if (steps.isEmpty()) steps.add("")

    Column(modifier = modifier.padding(16.dp)) {
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
                        value = ing.amountText,
                        onValueChange = { ingredients[index] = ing.copy(amountText = it) },
                        label = { Text("Amount") },
                        modifier = Modifier.width(90.dp)
                    )
                    OutlinedTextField(value = ing.unit, onValueChange = { ingredients[index] = ing.copy(unit = it) }, label = { Text("Unit") }, modifier = Modifier.width(100.dp))
                    OutlinedTextField(value = ing.name, onValueChange = { ingredients[index] = ing.copy(name = it) }, label = { Text("Ingredient") }, modifier = Modifier.weight(1f))
                    Button(onClick = { if (ingredients.size > 1) ingredients.removeAt(index) else { ingredients[index] = IngredientInput() } }) {
                        Text("Remove")
                    }
                }
            }
        }

        Button(onClick = { ingredients.add(IngredientInput()) }, modifier = Modifier.padding(top = 8.dp)) {
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
                        val amt = it.amountText.toDoubleOrNull() ?: 0.0
                        val unit = it.unit.ifBlank { null }
                        val name = it.name.trim()
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
                            steps = stepsDomain
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
