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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable as navComposable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
// (single getValue import above)
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
import androidx.compose.runtime.remember
// URL import screen removed: import handled inline in AddRecipeScreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun NapectApp(
    vm: RecipeViewModel,
    importVm: com.tkolymp.napect.ui.recipes.UrlImportViewModel? = null,
    initialSharedUrl: String? = null,
    initialSharedImageUri: android.net.Uri? = null
) {
    // selected bottom nav destination (keeps the bottom bar highlighted)
    var selectedDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    // category selection is kept in-memory only (no need to save across process death here)
    var selectedCategory by remember { mutableStateOf<com.tkolymp.napect.domain.model.Category?>(null) }

    // BackHandler integrates with the native OnBackPressedDispatcher so the system back
    // gesture (edge-swipe) and hardware back button are handled here.
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Top level destinations: these should never show the back arrow
    val topLevelRoutes = setOf(
        AppDestinations.HOME.name,
        AppDestinations.FAVORITES.name,
        AppDestinations.SETTINGS.name
    )

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
                    // never show back arrow on top-level (bottom-nav) destinations
                    val isTopLevel = currentRoute in topLevelRoutes
                    if (!isTopLevel && navController.previousBackStackEntry != null) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
                , actions = {
                    // link icon -> open import screen if importVm available
                    // Import icon removed — URL import is handled inline in the Add screen
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
            LaunchedEffect(initialSharedUrl, initialSharedImageUri) {
                // If the app was opened via share, directly perform the import using the ViewModel
                // and navigate to the Add screen where the imported data will be populated.
                if (!initialSharedUrl.isNullOrBlank() && importVm != null) {
                    try {
                        importVm.fetchUrl(initialSharedUrl)
                    } catch (_: Exception) { }
                    navController.navigate("add")
                } else if (initialSharedImageUri != null && importVm != null) {
                    try {
                        importVm.importImage(initialSharedImageUri)
                    } catch (_: Exception) { }
                    navController.navigate("add")
                }
            }

            NavHost(navController = navController, startDestination = AppDestinations.HOME.name, modifier = Modifier.padding(innerPadding)) {
                // Tab screens: use a short fade when switching, do not slide
                navComposable(AppDestinations.HOME.name,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(150)) }
                ) {
                    val baseList = items
                    val searchFiltered = if (vm.searchQuery.value.isBlank()) baseList else searchResults.filter { r -> baseList.any { it.id == r.id } }
                    val categoryFiltered = selectedCategory?.let { c -> searchFiltered.filter { it.category == c } } ?: searchFiltered
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Debug helper: show list sizes for diagnosis (remove after debugging)
                        Text(text = "Debug: total=${baseList.size}, search=${searchResults.size}, searchFiltered=${searchFiltered.size}, categoryFiltered=${categoryFiltered.size}, selected=${selectedCategory?.name ?: "All"}", modifier = Modifier.padding(8.dp))
                        // show any ViewModel error messages
                        val vmError by vm.error.collectAsState()
                        if (!vmError.isNullOrBlank()) Text(text = "Error: $vmError", modifier = Modifier.padding(8.dp))
                        OutlinedTextField(value = vm.searchQuery.value, onValueChange = { vm.setSearchQuery(it) }, label = { androidx.compose.material3.Text("Search") }, modifier = Modifier.fillMaxWidth().padding(8.dp))
                        RecipeListScreen(recipes = categoryFiltered, onItemClick = { navController.navigate("recipe/${it.id}") }, contentPadding = PaddingValues(0.dp), selectedCategory = selectedCategory, onCategorySelected = { selectedCategory = it }, onDelete = { id -> vm.deleteRecipe(id) })
                    }
                }
                navComposable(AppDestinations.FAVORITES.name,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(150)) }
                ) {
                    val baseList = items.filter { it.isFavorite }
                    val searchFiltered = if (vm.searchQuery.value.isBlank()) baseList else searchResults.filter { r -> baseList.any { it.id == r.id } }
                    val categoryFiltered = selectedCategory?.let { c -> searchFiltered.filter { it.category == c } } ?: searchFiltered
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Debug helper: show list sizes for diagnosis (remove after debugging)
                        Text(text = "Debug: total=${baseList.size}, search=${searchResults.size}, searchFiltered=${searchFiltered.size}, categoryFiltered=${categoryFiltered.size}, selected=${selectedCategory?.name ?: "All"}", modifier = Modifier.padding(8.dp))
                        OutlinedTextField(value = vm.searchQuery.value, onValueChange = { vm.setSearchQuery(it) }, label = { androidx.compose.material3.Text("Search") }, modifier = Modifier.fillMaxWidth().padding(8.dp))
                        RecipeListScreen(recipes = categoryFiltered, onItemClick = { navController.navigate("recipe/${it.id}") }, contentPadding = PaddingValues(0.dp), selectedCategory = selectedCategory, onCategorySelected = { selectedCategory = it }, onDelete = { id -> vm.deleteRecipe(id) })
                    }
                }
                navComposable(AppDestinations.SETTINGS.name,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(150)) }
                ) {
                    val allTags by vm.allTags.collectAsState()
                    SettingsScreen(
                        allTags = allTags,
                        onCreateTag = { name, group -> vm.createUserTag(name, group) },
                        onDeleteTag = { id -> vm.deleteTag(id) },
                        onRestoreDefaults = { vm.restoreDefaultTags() }
                    )
                }

                // Non-tab screens: slide in from the right on navigation, slide out to the right on pop
                // URL import handled inline in Add screen; dedicated import screen removed.

                navComposable("add",
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut(tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(tween(300)) }
                ) {
                    val allTags by vm.allTags.collectAsState()
                    val suggested by vm.suggestedTags.collectAsState()
                    val suggestedIds = suggested?.let { (it.confirmed + it.newlyCreated).map { t -> t.id }.toSet() } ?: emptySet()
                    val suggestedNames = suggested?.let { (it.confirmed + it.newlyCreated).map { t -> t.name }.toSet() } ?: emptySet()
                    // Debug logging to ensure suggestions are visible at app composition time
                    LaunchedEffect(suggested) {
                        try {
                            android.util.Log.d("NapectApp", "VM suggested tags: confirmed=${suggested?.confirmed?.map { it.name }} newly=${suggested?.newlyCreated?.map { it.name }} ids=$suggestedIds")
                        } catch (_: Exception) { }
                    }
                        AddRecipeScreen(
                            onSave = { r, tagIds -> vm.createRecipeWithTags(r, tagIds) { navController.popBackStack() } },
                            onCancel = { navController.popBackStack() },
                            availableTags = allTags,
                            suggested = suggested,
                            onSuggest = { preview -> vm.suggestTagsForRecipe(preview) },
                            onCreateUserTag = { name, group -> vm.createUserTag(name, group) },
                            importVm = importVm
                        )
                }

                // Camera route replaced by launching the platform camera from AddRecipeScreen using
                // ActivityResultContracts.TakePicture. The AddRecipeScreen will navigate to an intermediate
                // temporary-host composable if needed. We remove the composable here since the camera flow
                // is handled via ActivityResult from the AddRecipeScreen.

                navComposable("recipe/{id}/edit",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut(tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(tween(300)) }
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: 0L
                    val recipe by vm.getRecipeById(id).collectAsState(initial = null)
                    recipe?.let {
                        val allTags by vm.allTags.collectAsState()
                        val suggested by vm.suggestedTags.collectAsState()
                        val suggestedIds = suggested?.let { (it.confirmed + it.newlyCreated).map { t -> t.id }.toSet() } ?: emptySet()
                        AddRecipeScreen(
                            initialRecipe = it,
                            onSave = { updated, tagIds -> vm.updateRecipeWithTags(updated, tagIds) { navController.popBackStack() } },
                            onCancel = { navController.popBackStack() },
                            availableTags = allTags,
                            suggested = suggested,
                            onSuggest = { preview -> vm.suggestTagsForRecipe(preview) },
                            onCreateUserTag = { name, group -> vm.createUserTag(name, group) },
                            importVm = importVm
                        )
                    }
                }
                navComposable("recipe/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut(tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(tween(300)) }
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: 0L
                    val recipe by vm.getRecipeById(id).collectAsState(initial = null)
                    recipe?.let {
                        RecipeDetailScreen(recipe = it, onClose = { navController.popBackStack() }, onToggleFavorite = { idArg, fav -> vm.toggleFavorite(idArg, fav) }, onEdit = { rid -> navController.navigate("recipe/$rid/edit") }, onDelete = { rid -> vm.deleteRecipe(rid) { navController.popBackStack() } })
                    }
                }
            }
        }
    }
}
