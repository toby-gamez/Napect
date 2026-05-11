package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.tkolymp.napect.domain.model.Recipe

@Composable
fun AddRecipeScreen(onSave: (Recipe) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }

    // Parent can pass system/gap padding via modifier (e.g. Modifier.padding(innerPadding))
    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it },
            label = { Text("Summary") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Button(onClick = {
            if (title.isNotBlank()) {
                onSave(Recipe(title = title, summary = if (summary.isBlank()) null else summary))
            }
        }, modifier = Modifier.padding(top = 12.dp)) {
            Text("Save")
        }
        Button(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
            Text("Cancel")
        }
    }
}
