package com.tkolymp.napect

import timber.log.Timber
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.ui.Modifier
import com.tkolymp.napect.ui.recipes.AddRecipeScreen
import com.tkolymp.napect.ui.recipes.RecipeViewModel
import com.tkolymp.napect.ui.recipes.PagedRecipeListScreen
import com.tkolymp.napect.domain.model.RecipeListItem
import com.tkolymp.napect.ui.recipes.RecipeListScreen
import com.tkolymp.napect.ui.recipes.RecipeDetailScreen
import com.tkolymp.napect.ui.recipes.MakeScreen
import com.tkolymp.napect.ui.recipes.UrlImportViewModel
import com.tkolymp.napect.data.local.PhotoManager
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBar
import androidx.compose.material3.ListItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Card
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
import androidx.hilt.navigation.compose.hiltViewModel
// URL import screen removed: import handled inline in AddRecipeScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color

val LocalSnackbarHostState = compositionLocalOf { SnackbarHostState() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun NapectApp(
    initialSharedUrl: String? = null,
    initialSharedImageUri: android.net.Uri? = null
) {
    val vm: RecipeViewModel = hiltViewModel()
    val importVm: com.tkolymp.napect.ui.recipes.UrlImportViewModel = hiltViewModel()
    // selected bottom nav destination (keeps the bottom bar highlighted)
    var selectedDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    // FAB menu state
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var fabUrlText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

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
    // Optional composable slot for additional top-bar actions (e.g. recipe favorite)
    val detailActionsState = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
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
        // clear any detail actions when route changes
        detailActionsState.value = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(modifier = Modifier.fillMaxSize(), snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val titleText = when {
                        currentRoute == "add" -> stringResource(R.string.title_add_recipe)
                        currentRoute?.startsWith("recipe/") == true || currentRoute == "recipe/{id}" -> stringResource(R.string.title_recipe)
                        currentRoute != null && AppDestinations.values().any { it.name == currentRoute } -> stringResource(AppDestinations.valueOf(currentRoute).labelRes)
                        else -> stringResource(selectedDestination.labelRes)
                    }
                    // animate title changes
                    AnimatedContent(targetState = titleText, transitionSpec = {
                        fadeIn(tween(150)).togetherWith(fadeOut(tween(150)))
                    }) { Text(it) }
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }

                    if (currentRoute == "add") {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_menu))
                        }

                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pick_photo)) },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Image, contentDescription = stringResource(R.string.pick_photo)) },
                                onClick = {
                                    menuExpanded = false
                                    try { pickActionState.value?.invoke() } catch (e: Exception) { Timber.w(e) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.open_camera)) },
                                leadingIcon = { Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.open_camera)) },
                                onClick = {
                                    menuExpanded = false
                                    try { cameraActionState.value?.invoke() } catch (e: Exception) { Timber.w(e) }
                                }
                            )
                        }
                    }
                    // Render any registered detail actions (e.g. recipe favorite)
                    detailActionsState.value?.invoke()
                }
            )
        }) { innerPadding ->
            val searchQuery by vm.searchQuery.collectAsState()
            val filteredItems: List<RecipeListItem> by vm.filteredRecipeListItems.collectAsState()
            val searchSuggestions by vm.searchListItems.collectAsState()

            // If the app was opened via share, fetch/import the shared content and navigate to add screen
            LaunchedEffect(initialSharedUrl, initialSharedImageUri) {
                if (!initialSharedUrl.isNullOrBlank()) {
                    try {
                        importVm.fetchUrl(initialSharedUrl)
                    } catch (e: java.lang.Exception) {
                        Timber.w(e, "Failed to fetch initial shared URL")
                    }
                    try { navController.navigate("add") } catch (e: Exception) { Timber.w(e, "add navigation failed") }
                } else if (initialSharedImageUri != null) {
                    try {
                        importVm.importImage(initialSharedImageUri)
                    } catch (e: java.lang.Exception) {
                        Timber.w(e, "Failed to import initial shared image")
                    }
                    try { navController.navigate("add") } catch (e: Exception) { Timber.w(e, "add navigation failed") }
                }
            }

            NavHost(navController = navController, startDestination = AppDestinations.HOME.name, modifier = Modifier.padding(innerPadding)) {
                // Tab screens: use a short fade when switching, do not slide
                navComposable(AppDestinations.HOME.name,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(150)) }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val vmErrorState by vm.error.collectAsState()
                        val vmError = vmErrorState
                        if (!vmError.isNullOrBlank()) {
                            androidx.compose.material3.Card(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Text(
                                    text = vmError,
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        // Material3 SearchBar: follows the Material Search pattern with suggestions
                        val homeSearchActive = rememberSaveable { mutableStateOf(false) }
                        var homeLocalQuery by remember { mutableStateOf(searchQuery) }
                        LaunchedEffect(searchQuery) { if (searchQuery != homeLocalQuery) homeLocalQuery = searchQuery }
                        SearchBar(
                            query = homeLocalQuery,
                            onQueryChange = { homeLocalQuery = it; vm.setSearchQuery(it) },
                            onSearch = { q -> vm.setSearchQuery(q); homeSearchActive.value = false },
                            active = homeSearchActive.value,
                            onActiveChange = { homeSearchActive.value = it },
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                             leadingIcon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_hint)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { vm.setSearchQuery("") }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear)) }
                                 }
                             }
                         ) {
                             val suggestions = if (searchQuery.isBlank()) filteredItems.take(6) else searchSuggestions
                             val suggestionModifier = Modifier.fillMaxWidth()
                             LazyColumn(modifier = Modifier.heightIn(max = 600.dp)) {
                                 items(suggestions) { r ->
                                     Card(modifier = suggestionModifier
                                         .clickable {
                                             // navigate directly to the recipe detail
                                             try { navController.navigate("recipe/${r.id}") } catch (e: Exception) { Timber.w(e, "Search suggestion navigation failed") }
                                             vm.setSearchQuery(r.title)
                                             homeSearchActive.value = false
                                         }
                                         .padding(8.dp)) {
                                         Column(modifier = Modifier.padding(12.dp)) {
                                             if (r.photoPath != null) {
                                                 val bmp = PhotoManager.loadBitmap(r.photoPath!!)
                                                 if (bmp != null) {
                                                     val img = bmp.asImageBitmap()
                                                     androidx.compose.foundation.Image(bitmap = img, contentDescription = "Fotografie receptu", modifier = Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Crop)
                                                 }
                                             }
                                             Text(r.title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                                             r.summary?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                                         }
                                     }
                                 }
                             }
                          }
                          // compute available tags (Category + Other); for paged view show all without filtering by items
                        val allTags by vm.allTags.collectAsState()
                        val currentTagId by vm.selectedTagId.collectAsState()
                        val usedTags = allTags.filter { tg -> tg.group == com.tkolymp.napect.domain.model.TagGroup.CATEGORY || tg.group == com.tkolymp.napect.domain.model.TagGroup.OTHER }
                        PagedRecipeListScreen(pagedRecipes = vm.pagedRecipes, onItemClick = { id -> navController.navigate("recipe/$id") }, contentPadding = PaddingValues(0.dp), availableTags = usedTags, selectedTagId = currentTagId, onTagSelected = { vm.setSelectedTagId(it) }, onDelete = { id -> vm.deleteRecipe(id) })
                    }
                }
                navComposable(AppDestinations.FAVORITES.name,
                    enterTransition = { fadeIn(tween(150)) },
                    exitTransition = { fadeOut(tween(150)) }
                ) {
                    val favItems: List<RecipeListItem> = filteredItems.filter { it.isFavorite }
                    val favSuggestions = searchSuggestions.filter { it.isFavorite }
                    Column(modifier = Modifier.fillMaxSize()) {
                        val favSearchActive = rememberSaveable { mutableStateOf(false) }
                        var favLocalQuery by remember { mutableStateOf(searchQuery) }
                        LaunchedEffect(searchQuery) { if (searchQuery != favLocalQuery) favLocalQuery = searchQuery }
                        SearchBar(
                            query = favLocalQuery,
                            onQueryChange = { favLocalQuery = it; vm.setSearchQuery(it) },
                            onSearch = { q -> vm.setSearchQuery(q); favSearchActive.value = false },
                            active = favSearchActive.value,
                            onActiveChange = { favSearchActive.value = it },
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                             leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { vm.setSearchQuery("") }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear)) }
                                 }
                             }
                         ) {
                             val suggestions = if (searchQuery.isBlank()) favItems.take(6) else favSuggestions
                             val suggestionModifier = Modifier.fillMaxWidth()
                             LazyColumn(modifier = Modifier.heightIn(max = 600.dp)) {
                                 items(suggestions) { r ->
                                     Card(modifier = suggestionModifier
                                         .clickable {
                                             try { navController.navigate("recipe/${r.id}") } catch (e: Exception) { Timber.w(e, "Search suggestion navigation failed") }
                                             vm.setSearchQuery(r.title)
                                             favSearchActive.value = false
                                         }
                                         .padding(8.dp)) {
                                         Column(modifier = Modifier.padding(12.dp)) {
                                             if (r.photoPath != null) {
                                                 val bmp = PhotoManager.loadBitmap(r.photoPath!!)
                                                 if (bmp != null) {
                                                     val img = bmp.asImageBitmap()
                                                     androidx.compose.foundation.Image(bitmap = img, contentDescription = "Fotografie receptu", modifier = Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Crop)
                                                 }
                                             }
                                             Text(r.title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                                             r.summary?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                                         }
                                     }
                                 }
                             }
                         }
                         val allTags by vm.allTags.collectAsState()
                        val currentTagId by vm.selectedTagId.collectAsState()
                        val usedTags = allTags.filter { tg -> (tg.group == com.tkolymp.napect.domain.model.TagGroup.CATEGORY || tg.group == com.tkolymp.napect.domain.model.TagGroup.OTHER) && favItems.any { r -> r.tags.any { t -> t.id == tg.id } } }
                        RecipeListScreen(recipes = favItems, onItemClick = { id -> navController.navigate("recipe/$id") }, contentPadding = PaddingValues(0.dp), availableTags = usedTags, selectedTagId = currentTagId, onTagSelected = { vm.setSelectedTagId(it) }, onDelete = { id -> vm.deleteRecipe(id) })
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
                    LaunchedEffect(suggested) {
                        Timber.d("VM suggested tags: confirmed=%s newly=%s ids=%s", suggested?.confirmed?.map { it.name }, suggested?.newlyCreated?.map { it.name }, suggestedIds)
                    }
                        AddRecipeScreen(
                            onSave = { r, tagIds ->
                                vm.createRecipeWithTags(r, tagIds) { id ->
                                    r.photo?.let { bytes ->
                                        PhotoManager.savePhoto(context, id, bytes)
                                        vm.updatePhotoPath(id, PhotoManager.getPhotoPath(context, id))
                                    }
                                    navController.popBackStack()
                                }
                            },
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
                            onSave = { updated, tagIds ->
                                vm.updateRecipeWithTags(updated, tagIds) {
                                    if (updated.id != 0L) {
                                        updated.photo?.let { bytes ->
                                            PhotoManager.savePhoto(context, updated.id, bytes)
                                            vm.updatePhotoPath(updated.id, PhotoManager.getPhotoPath(context, updated.id))
                                        }
                                    }
                                    navController.popBackStack()
                                }
                            },
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
                        RecipeDetailScreen(
                            recipe = it,
                            onClose = { navController.popBackStack() },
                            onToggleFavorite = { idArg, fav -> vm.toggleFavorite(idArg, fav) },
                            onEdit = { rid -> navController.navigate("recipe/$rid/edit") },
                            onDelete = { rid -> vm.deleteRecipe(rid) { navController.popBackStack() } },
                            onMake = { rid -> navController.navigate("recipe/$rid/make") },
                            onRegisterTopBarActions = { cb -> detailActionsState.value = cb }
                        )
                    }
                }

                // Make / cooking mode
                navComposable("recipe/{id}/make",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut(tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(tween(300)) }
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: 0L
                    val recipe by vm.getRecipeById(id).collectAsState(initial = null)
                    recipe?.let {
                        MakeScreen(recipe = it, onFinish = { navController.popBackStack() }, onSave = { r -> vm.updateRecipe(r) }, onToggleFavorite = { rid, fav -> vm.toggleFavorite(rid, fav) }, onDelete = { rid -> vm.deleteRecipe(rid) { navController.popBackStack() } })
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
                    title = { Text(stringResource(R.string.import_url_title)) },
                     text = {
                         val clipboardManager = LocalClipboardManager.current
                         OutlinedTextField(
                             value = fabUrlText,
                             onValueChange = { fabUrlText = it },
                             label = { Text(stringResource(R.string.import_url_hint)) },
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
                                    Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.paste))
                                }
                            }
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val url = fabUrlText.trim()
                                if (url.isNotBlank()) {
                                    try { importVm.fetchUrl(url) } catch (e: Exception) { Timber.w(e) }
                                }
                                showUrlDialog = false
                                fabUrlText = ""
                                navController.navigate("add")
                            },
                            enabled = fabUrlText.isNotBlank()
                        ) { Text(stringResource(R.string.import_action)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showUrlDialog = false
                            fabUrlText = ""
                        }) { Text(stringResource(R.string.cancel)) }
                    }
                )
            }
        }
    }
    }

        // Scrim overlay — covers the full screen (app bar + nav bar + content)
        // when the FAB menu is open; uses surface colour so it adapts to light/dark
        val scrimColor = MaterialTheme.colorScheme.surface
        AnimatedVisibility(
            visible = fabMenuExpanded,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = 0.7f))
                    .clickable { fabMenuExpanded = false }
            )
        }

        // FAB — rendered after the scrim so it always floats above it
        val showFab = currentRoute != "add" && currentRoute?.endsWith("/edit") != true && currentRoute?.endsWith("/make") != true && currentRoute?.startsWith("recipe/") != true
        AnimatedVisibility(
            visible = showFab,
            enter = fadeIn(tween(200)) + scaleIn(tween(200)),
            exit = fadeOut(tween(200)) + scaleOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp + 16.dp, end = 16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedVisibility(
                        visible = fabMenuExpanded,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150))
                    ) {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                 Text(
                                     stringResource(R.string.fab_enter_url),
                                     style = MaterialTheme.typography.bodyLarge,
                                     color = MaterialTheme.colorScheme.onSurface,
                                     modifier = Modifier.padding(end = 12.dp)
                                 )
                                 SmallFloatingActionButton(
                                     onClick = {
                                         fabMenuExpanded = false
                                         showUrlDialog = true
                                     }
                                 ) {
                                     Icon(Icons.Filled.Link, contentDescription = stringResource(R.string.fab_enter_url))
                                 }
                             }
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                 Text(
                                     stringResource(R.string.fab_write_manually),
                                     style = MaterialTheme.typography.bodyLarge,
                                     color = MaterialTheme.colorScheme.onSurface,
                                     modifier = Modifier.padding(end = 12.dp)
                                 )
                                 SmallFloatingActionButton(
                                     onClick = {
                                         fabMenuExpanded = false
                                         navController.navigate("add")
                                     }
                                 ) {
                                     Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.fab_write_manually))
                                 }
                            }
                        }
                    }
                    FloatingActionButton(
                        onClick = { fabMenuExpanded = !fabMenuExpanded }
                    ) {
                        Icon(
                            imageVector = if (fabMenuExpanded) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = if (fabMenuExpanded) stringResource(R.string.fab_close_menu) else stringResource(R.string.fab_add_recipe),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    } // end outer Box
}
