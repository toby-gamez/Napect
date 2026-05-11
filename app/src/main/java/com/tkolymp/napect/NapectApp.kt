package com.tkolymp.napect

// layout imports intentionally minimal
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tkolymp.napect.ui.recipes.AddRecipeScreen
import com.tkolymp.napect.ui.recipes.RecipeListScreen
import com.tkolymp.napect.ui.recipes.RecipeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NapectApp(vm: RecipeViewModel) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    AppNavBar(currentDestination = currentDestination, onDestinationChange = { currentDestination = it }) {
        Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (showAdd) "Add Recipe" else currentDestination.label) },
                navigationIcon = {
                    if (showAdd) {
                        IconButton(onClick = { showAdd = false }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }, floatingActionButton = {
            if (!showAdd) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add")
                }
            }
        }) { innerPadding ->
            val items = vm.recipes.collectAsState().value
            if (showAdd) {
                AddRecipeScreen(onSave = { r ->
                    vm.createRecipe(r) { showAdd = false }
                }, onCancel = { showAdd = false }, modifier = Modifier.padding(innerPadding))
            } else {
                RecipeListScreen(recipes = items, contentPadding = innerPadding)
            }
        }
    }
}
