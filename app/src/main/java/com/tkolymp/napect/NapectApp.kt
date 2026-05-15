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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.filled.ContentPaste

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
    // tag selection (replaces the previous Category chips). Kept in-memory only.
    var selectedTagId by remember { mutableStateOf<Long?>(null) }
    // FAB menu state
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var fabUrlText by remember { mutableStateOf("") }

    // BackHandler integrates with the native OnBackPressedDispatcher so the system back
    // gesture (edge-swipe) and hardware back button are handled here.
    val navController = rememberNavController()

    // Close FAB menu on system back when it is open
    BackHandler(enabled = fabMenuExpanded) { fabMenuExpanded = false }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Holders for actions that child screens can register so the top app bar can
    // expose them in a global actions menu.
    val pickActionState = remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraActionState = remember { mutableStateOf<(() -> Unit)?>(null) }
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
        // collapse the FAB speed-dial on any navigation
        fabMenuExpanded = false
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
                        currentRoute == "add" -> "Přidat recept"
                        currentRoute?.startsWith("recipe/") == true || currentRoute == "recipe/{id}" -> "Recept"
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
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Zpět")
                        }
                    }
                }
                , actions = {
                    var menuExpanded by remember { mutableStateOf(false) }

                    if (currentRoute == "add") {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Akce")
                        }

                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Vybrat fotografii") }, onClick = {
                                menuExpanded = false
                                try { pickActionState.value?.invoke() } catch (_: Exception) { }
                            })
                            DropdownMenuItem(text = { Text("Otevřít fotoaparát") }, onClick = {
                                menuExpanded = false
                                try { cameraActionState.value?.invoke() } catch (_: Exception) { }
                            })
                        }
                    }
                }
            )
        }, floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = it },
                        modifier = Modifier.animateFloatingActionButton(
                            visible = currentRoute != "add" && currentRoute?.endsWith("/edit") != true,
                            alignment = Alignment.BottomEnd
                        ),
                    ) {
                        val imageVector by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                            }
                        }
                        Icon(
                            painter = rememberVectorPainter(imageVector),
                            contentDescription = if (fabMenuExpanded) "Zavřít nabídku" else "Přidat recept",
                            modifier = Modifier.animateIcon({ checkedProgress })
                        )
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        showUrlDialog = true
                    },
                    text = { Text("Zadat odkaz", style = MaterialTheme.typography.bodyLarge) },
                    icon = { Icon(Icons.Filled.Link, contentDescription = null) },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        navController.navigate("add")
                    },
                    text = { Text("Napsat ručně", style = MaterialTheme.typography.bodyLarge) },
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                )
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
                    // Apply tag filter if selectedTagId is set
                    val tagFiltered = selectedTagId?.let { tid -> searchFiltered.filter { r -> r.tags.any { t -> t.id == tid } } } ?: searchFiltered
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Debug helper: show list sizes for diagnosis (remove after debugging)
                        Text(text = "Debug: total=${baseList.size}, search=${searchResults.size}, searchFiltered=${searchFiltered.size}, selectedTag=${selectedTagId ?: "All"}", modifier = Modifier.padding(8.dp))
                        // show any ViewModel error messages
                        val vmError by vm.error.collectAsState()
                        if (!vmError.isNullOrBlank()) Text(text = "Error: $vmError", modifier = Modifier.padding(8.dp))
                        OutlinedTextField(value = vm.searchQuery.value, onValueChange = { vm.setSearchQuery(it) }, label = { androidx.compose.material3.Text("Hledat") }, modifier = Modifier.fillMaxWidth().padding(8.dp))
                         // compute available tags (Category + Other) that are used by at least one recipe
                        val allTags by vm.allTags.collectAsState()
                        val usedTags = allTags.filter { tg -> (tg.group == com.tkolymp.napect.domain.model.TagGroup.CATEGORY || tg.group == com.tkolymp.napect.domain.model.TagGroup.OTHER) && baseList.any { r -> r.tags.any { t -> t.id == tg.id } } }
                        RecipeListScreen(recipes = tagFiltered, onItemClick = { navController.navigate("recipe/${it.id}") }, contentPadding = PaddingValues(0.dp), availableTags = usedTags, selectedTagId = selectedTagId, onTagSelected = { selectedTagId = it }, onDelete = { id -> vm.deleteRecipe(id) })
                    }
                }
                navComposable(AppDestinations.FAVORITES.name,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(150)) }
                ) {
                    val baseList = items.filter { it.isFavorite }
                    val searchFiltered = if (vm.searchQuery.value.isBlank()) baseList else searchResults.filter { r -> baseList.any { it.id == r.id } }
                    val tagFiltered = selectedTagId?.let { tid -> searchFiltered.filter { r -> r.tags.any { t -> t.id == tid } } } ?: searchFiltered
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Debug helper: show list sizes for diagnosis (remove after debugging)
                        Text(text = "Debug: total=${baseList.size}, search=${searchResults.size}, searchFiltered=${searchFiltered.size}, selectedTag=${selectedTagId ?: "All"}", modifier = Modifier.padding(8.dp))
                        OutlinedTextField(value = vm.searchQuery.value, onValueChange = { vm.setSearchQuery(it) }, label = { androidx.compose.material3.Text("Hledat") }, modifier = Modifier.fillMaxWidth().padding(8.dp))
                        val allTags by vm.allTags.collectAsState()
                        val usedTags = allTags.filter { tg -> (tg.group == com.tkolymp.napect.domain.model.TagGroup.CATEGORY || tg.group == com.tkolymp.napect.domain.model.TagGroup.OTHER) && baseList.any { r -> r.tags.any { t -> t.id == tg.id } } }
                        RecipeListScreen(recipes = tagFiltered, onItemClick = { navController.navigate("recipe/${it.id}") }, contentPadding = PaddingValues(0.dp), availableTags = usedTags, selectedTagId = selectedTagId, onTagSelected = { selectedTagId = it }, onDelete = { id -> vm.deleteRecipe(id) })
                    }
                }
                navComposable(AppDestinations.SETTINGS.name,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(150)) }
                ) {
                    val allTags by vm.allTags.collectAsState()
                    val error by vm.error.collectAsState()
                    SettingsScreen(
                        allTags = allTags,
                        onCreateTag = { name, group -> vm.createUserTag(name, group) },
                        onDeleteTag = { id -> vm.deleteTag(id) },
                        onRestoreDefaults = { vm.restoreDefaultTags() },
                        error = error
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
                            onRegisterPickPhotoAction = { cb -> pickActionState.value = cb },
                            onRegisterOpenCameraAction = { cb -> cameraActionState.value = cb },
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
                            onRegisterPickPhotoAction = { cb -> pickActionState.value = cb },
                            onRegisterOpenCameraAction = { cb -> cameraActionState.value = cb },
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

            // URL import dialog — opened from the FAB "Enter URL" option
            if (showUrlDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showUrlDialog = false
                        fabUrlText = ""
                    },
                    title = { Text("Importovat z odkazu") },
                    text = {
                        val clipboardManager = LocalClipboardManager.current
                        OutlinedTextField(
                            value = fabUrlText,
                            onValueChange = { fabUrlText = it },
                            label = { Text("Odkaz na recept") },
                            placeholder = { Text("https://...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) fabUrlText = clip
                                }) {
                                    Icon(Icons.Filled.ContentPaste, contentDescription = "Vložit")
                                }
                            }
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val url = fabUrlText.trim()
                                if (url.isNotBlank() && importVm != null) {
                                    try { importVm.fetchUrl(url) } catch (_: Exception) { }
                                }
                                showUrlDialog = false
                                fabUrlText = ""
                                navController.navigate("add")
                            },
                            enabled = fabUrlText.isNotBlank()
                        ) { Text("Importovat") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showUrlDialog = false
                            fabUrlText = ""
                        }) { Text("Zrušit") }
                    }
                )
            }
        }
    }
}
