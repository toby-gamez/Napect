package com.tkolymp.napect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tkolymp.napect.R
import androidx.compose.ui.Alignment
import androidx.compose.material3.Switch
import androidx.hilt.navigation.compose.hiltViewModel
import com.tkolymp.napect.data.local.ThemeMode
import com.tkolymp.napect.ui.settings.SettingsViewModel
import com.tkolymp.napect.domain.model.TagGroup
import androidx.compose.material3.MaterialTheme

@Composable
fun SettingsScreen(
    allTags: List<com.tkolymp.napect.domain.model.Tag> = emptyList(),
    onCreateTag: (String, com.tkolymp.napect.domain.model.TagGroup) -> Unit = { _, _ -> },
    onDeleteTag: (Long) -> Unit = {},
    onRestoreDefaults: () -> Unit = {},
    error: String? = null,
    modifier: Modifier = Modifier
) {
    val settingsVm: SettingsViewModel = hiltViewModel()
    val prefs by settingsVm.prefs.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text(stringResource(R.string.section_theme))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ThemeMode.values().forEach { mode ->
                val selected = prefs.themeMode == mode
                FilterChip(selected = selected, onClick = { settingsVm.setThemeMode(mode) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercaseChar() }) })
                Spacer(modifier = Modifier.size(4.dp))
            }
        }

        Spacer(modifier = Modifier.size(12.dp))
        Text(stringResource(R.string.default_servings_label, prefs.defaultServings))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { settingsVm.setDefaultServings((prefs.defaultServings - 1).coerceAtLeast(1)) }, modifier = Modifier.size(40.dp)) { Icon(imageVector = Icons.Filled.Remove, contentDescription = stringResource(R.string.default_servings_decrease)) }
            IconButton(onClick = { settingsVm.setDefaultServings(prefs.defaultServings + 1) }, modifier = Modifier.size(40.dp)) { Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.default_servings_increase)) }
        }

        Spacer(modifier = Modifier.size(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.setting_screenshot_protection))
            Switch(
                checked = prefs.screenshotProtectionEnabled,
                onCheckedChange = { settingsVm.setScreenshotProtectionEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.section_tags_settings), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        // group tags by group
        val grouped = allTags.groupBy { it.group }
        grouped.forEach { (group, tags) ->
            Text(group.displayName, modifier = Modifier.padding(top = 8.dp))
            Column {
                var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
                tags.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                        // show name and AI badge when applicable
                        if (t.isAiGenerated) {
                            androidx.compose.material3.AssistChip(onClick = {}, label = { Text(t.name) }, leadingIcon = { Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = "AI") })
                        } else {
                            androidx.compose.material3.AssistChip(onClick = {}, label = { Text(t.name) })
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        IconButton(onClick = { pendingDeleteId = t.id }) { Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_tag)) }
                    }
                }

                // Confirmation dialog for deleting a tag
                val deleteId = pendingDeleteId
                if (deleteId != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { pendingDeleteId = null },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                pendingDeleteId = null
                                onDeleteTag(deleteId)
                            }) { Text(stringResource(R.string.delete)) }
                        },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.cancel)) } },
                        title = { Text(stringResource(R.string.delete_tag_title)) },
                        text = { Text(stringResource(R.string.delete_tag_message)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(12.dp))
        // Create tag form
        var newTagName by remember { mutableStateOf("") }
        var expanded by remember { mutableStateOf(false) }
        var selectedGroup by remember { mutableStateOf(com.tkolymp.napect.domain.model.TagGroup.OTHER) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newTagName, onValueChange = { newTagName = it }, label = { Text(stringResource(R.string.tag_name_hint)) }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { expanded = true }) { Text(selectedGroup.displayName) }
            androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TagGroup.values().forEach { g ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(g.displayName) }, onClick = { selectedGroup = g; expanded = false })
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (newTagName.isNotBlank()) {
                    onCreateTag(newTagName.trim(), selectedGroup)
                    newTagName = ""
                }
            }) { Text(stringResource(R.string.create_tag)) }
        }

        Spacer(modifier = Modifier.size(12.dp))
        Button(onClick = { onRestoreDefaults() }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.restore_default_tags))
        }
    }
}
