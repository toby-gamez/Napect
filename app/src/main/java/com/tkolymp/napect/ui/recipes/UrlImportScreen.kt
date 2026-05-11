package com.tkolymp.napect.ui.recipes

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tkolymp.napect.data.network.ImportedRecipeData

@Composable
fun UrlImportScreen(
    importVm: UrlImportViewModel,
    initialUrl: String? = null,
    onSaved: (Long) -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val state by importVm.state.collectAsState()
    var url by remember { mutableStateOf(initialUrl ?: "") }

    // editable review fields
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf<String?>(null) }
    val ingredients = remember { mutableStateListOf<String>() }
    val steps = remember { mutableStateListOf<String>() }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) importVm.fetchUrl(initialUrl)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (url.isNotBlank()) importVm.fetchUrl(url) }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Import")
        }

        when (state) {
            is UrlImportState.Idle -> {}
            is UrlImportState.Loading -> Text("Loading...", modifier = Modifier.padding(top = 8.dp))
            is UrlImportState.Error -> Text("Error: ${(state as UrlImportState.Error).message}")
            is UrlImportState.Saved -> Text("Saved", modifier = Modifier.padding(top = 8.dp))
            is UrlImportState.Success -> {
                val data = (state as UrlImportState.Success).data
                // populate editable review fields when data arrives
                LaunchedEffect(data) {
                    title = data.title
                    description = data.description
                    ingredients.clear(); ingredients.addAll(data.ingredients)
                    steps.clear(); steps.addAll(data.steps)
                }

                ReviewImportedForm(title = title, onTitleChange = { title = it }, description = description, onDescriptionChange = { description = it }, ingredients = ingredients, steps = steps, onSave = {
                    val d = ImportedRecipeData(title = title, description = description, ingredients = ingredients.toList(), steps = steps.toList(), sourceUrl = url)
                    importVm.saveImported(d) { onSaved(it) }
                }, onCancel = onCancel)
            }
        }
    }
}

@Composable
private fun ReviewImportedForm(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String?,
    onDescriptionChange: (String?) -> Unit,
    ingredients: MutableList<String>,
    steps: MutableList<String>,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    OutlinedTextField(value = title, onValueChange = onTitleChange, label = { Text("Title") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    OutlinedTextField(value = description ?: "", onValueChange = { onDescriptionChange(it.ifBlank { null }) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

    Text("Ingredients", modifier = Modifier.padding(top = 8.dp))
    ingredients.forEachIndexed { idx, ing ->
        OutlinedTextField(value = ing, onValueChange = { ingredients[idx] = it }, label = { Text("Ingredient") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }

    Text("Steps", modifier = Modifier.padding(top = 8.dp))
    steps.forEachIndexed { idx, s ->
        OutlinedTextField(value = s, onValueChange = { steps[idx] = it }, label = { Text("Step ${idx + 1}") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }

    Button(onClick = onSave, modifier = Modifier.padding(top = 12.dp)) { Text("Save") }
    Button(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text("Cancel") }
}
