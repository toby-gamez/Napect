package com.tkolymp.napect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.tkolymp.napect.data.local.SettingsRepository
import com.tkolymp.napect.data.local.ThemeMode

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = SettingsRepository(context)
    val prefs by repo.prefsFlow.collectAsState(initial = com.tkolymp.napect.data.local.UserPreferences())
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ThemeMode.values().forEach { mode ->
                val selected = prefs.themeMode == mode
                FilterChip(selected = selected, onClick = { scope.launch { repo.setThemeMode(mode) } }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercaseChar() }) })
                Spacer(modifier = Modifier.size(4.dp))
            }
        }

        Spacer(modifier = Modifier.size(12.dp))
        Text("Default servings: ${prefs.defaultServings}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { scope.launch { repo.setDefaultServings((prefs.defaultServings - 1).coerceAtLeast(1)) } }) { Text("-") }
            Button(onClick = { scope.launch { repo.setDefaultServings(prefs.defaultServings + 1) } }) { Text("+") }
        }
    }
}
