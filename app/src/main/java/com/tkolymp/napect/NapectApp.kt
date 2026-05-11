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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun NapectApp(vm: RecipeViewModel) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    AppNavBar(currentDestination = currentDestination, onDestinationChange = { currentDestination = it }) {
        Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // animate title changes
                    AnimatedContent(targetState = if (showAdd) "Add Recipe" else currentDestination.label, transitionSpec = {
                        fadeIn(tween(150)).togetherWith(fadeOut(tween(150)))
                    }) { Text(it) }
                },
                navigationIcon = {
                    if (showAdd) {
                        IconButton(onClick = { showAdd = false }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }, floatingActionButton = {
            // animate FAB appearance/disappearance
            AnimatedVisibility(visible = !showAdd, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add")
                }
            }
        }) { innerPadding ->
            val items by vm.recipes.collectAsState()

            // Animate switching between list and add screen
            AnimatedContent(targetState = showAdd, transitionSpec = {
                val duration = 300
                if (targetState) {
                    val enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }, animationSpec = tween(duration)) +
                        fadeIn(animationSpec = tween(duration))
                    val exit = slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth }, animationSpec = tween(duration)) +
                        fadeOut(animationSpec = tween(duration))
                    enter.togetherWith(exit).using(SizeTransform(clip = false))
                } else {
                    val enter = slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth }, animationSpec = tween(duration)) +
                        fadeIn(animationSpec = tween(duration))
                    val exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }, animationSpec = tween(duration)) +
                        fadeOut(animationSpec = tween(duration))
                    enter.togetherWith(exit).using(SizeTransform(clip = false))
                }
            }, contentKey = { it }) { isAdd ->
                if (isAdd) {
                    AddRecipeScreen(onSave = { r ->
                        vm.createRecipe(r) { showAdd = false }
                    }, onCancel = { showAdd = false }, modifier = Modifier.padding(innerPadding))
                } else {
                    RecipeListScreen(recipes = items, contentPadding = innerPadding)
                }
            }
        }
    }
}
