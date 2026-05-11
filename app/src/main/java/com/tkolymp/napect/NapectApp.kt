package com.tkolymp.napect

// layout imports intentionally minimal
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Link
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.ui.Modifier
import com.tkolymp.napect.ui.recipes.AddRecipeScreen
import com.tkolymp.napect.ui.recipes.RecipeListScreen
import com.tkolymp.napect.ui.recipes.RecipeViewModel
import com.tkolymp.napect.ui.recipes.RecipeDetailScreen
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.tkolymp.napect.ui.recipes.UrlImportScreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun NapectApp(vm: RecipeViewModel, importVm: com.tkolymp.napect.ui.recipes.UrlImportViewModel? = null, initialSharedUrl: String? = null) {
    // selected bottom nav destination (keeps the bottom bar highlighted)
    var selectedDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    // BackHandler integrates with the native OnBackPressedDispatcher so the system back
    // gesture (edge-swipe) and hardware back button are handled here.
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // ensure bottom bar highlights match when navigating via navController
    LaunchedEffect(currentRoute) {
        currentRoute?.let { r ->
            AppDestinations.values().find { it.name == r }?.let { selectedDestination = it }
        }
    }

    AppNavBar(currentDestination = selectedDestination, onDestinationChange = { dest ->
        if (dest != selectedDestination) {
            selectedDestination = dest
            // navigate; use name as route
            navController.navigate(dest.name) {
                // pop up to start to avoid building a large back stack when reselecting
                launchSingleTop = true
            }
        }
    }) {
        Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val titleText = when {
                        currentRoute == "add" -> "Add Recipe"
                        currentRoute?.startsWith("recipe/") == true || currentRoute == "recipe/{id}" -> "Recipe"
                        currentRoute != null && AppDestinations.values().any { it.name == currentRoute } -> AppDestinations.valueOf(currentRoute).label
                        else -> selectedDestination.label
                    }
                    // animate title changes
                    AnimatedContent(targetState = titleText, transitionSpec = {
                        fadeIn(tween(150)).togetherWith(fadeOut(tween(150)))
                    }) { Text(it) }
                },
                navigationIcon = {
                    if (navController.previousBackStackEntry != null) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
                , actions = {
                    // link icon -> open import screen if importVm available
                    if (importVm != null) {
                        IconButton(onClick = { navController.navigate("url_import") }) {
                            Icon(Icons.Filled.Link, contentDescription = "Import URL")
                        }
                    }
                }
            )
        }, floatingActionButton = {
            // navigate to add screen
            AnimatedVisibility(visible = currentRoute != "add", enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                FloatingActionButton(onClick = { navController.navigate("add") }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add")
                }
            }
        }) { innerPadding ->
            val items by vm.recipes.collectAsState()
            val searchResults by vm.searchResults.collectAsState()

            // If the app was opened via share and importVm + initialSharedUrl are provided, navigate to the import screen
            LaunchedEffect(initialSharedUrl) {
                if (!initialSharedUrl.isNullOrBlank() && importVm != null) {
                    navController.navigate("url_import")
                }
            }

            NavHost(navController = navController, startDestination = AppDestinations.HOME.name, modifier = Modifier.padding(innerPadding)) {
                composable(AppDestinations.HOME.name) {
                    val baseList = items
                    val filtered = if (vm.searchQuery.value.isBlank()) baseList else searchResults.filter { r -> baseList.any { it.id == r.id } }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = vm.searchQuery.value, onValueChange = { vm.setSearchQuery(it) }, label = { androidx.compose.material3.Text("Search") }, modifier = Modifier.fillMaxWidth().padding(8.dp))
                        RecipeListScreen(recipes = filtered, onItemClick = { navController.navigate("recipe/${it.id}") }, contentPadding = PaddingValues(0.dp))
                    }
                }
                composable(AppDestinations.FAVORITES.name) {
                    val baseList = items.filter { it.isFavorite }
                    val filtered = if (vm.searchQuery.value.isBlank()) baseList else searchResults.filter { r -> baseList.any { it.id == r.id } }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = vm.searchQuery.value, onValueChange = { vm.setSearchQuery(it) }, label = { androidx.compose.material3.Text("Search") }, modifier = Modifier.fillMaxWidth().padding(8.dp))
                        RecipeListScreen(recipes = filtered, onItemClick = { navController.navigate("recipe/${it.id}") }, contentPadding = PaddingValues(0.dp))
                    }
                }
                composable(AppDestinations.SETTINGS.name) {
                    SettingsScreen()
                }
                composable("url_import") {
                    importVm?.let { UrlImportScreen(importVm = it, initialUrl = initialSharedUrl, onSaved = { id -> navController.popBackStack() }, onCancel = { navController.popBackStack() }) }
                }
                composable("add") {
                    AddRecipeScreen(onSave = { r -> vm.createRecipe(r) { navController.popBackStack() } }, onCancel = { navController.popBackStack() })
                }
                composable("recipe/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: 0L
                    val recipe by vm.getRecipeById(id).collectAsState(initial = null)
                    recipe?.let {
                        RecipeDetailScreen(recipe = it, onClose = { navController.popBackStack() }, onToggleFavorite = { idArg, fav -> vm.toggleFavorite(idArg, fav) })
                    }
                }
            }
        }
    }
}
