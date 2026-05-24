package com.tkolymp.napect.ui.recipes

import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager

import android.net.Uri
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import android.os.Build
import android.provider.MediaStore
import timber.log.Timber
import kotlinx.coroutines.launch
import com.tkolymp.napect.LocalSnackbarHostState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tkolymp.napect.R
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import com.tkolymp.napect.domain.model.Ingredient
import com.tkolymp.napect.domain.model.IngredientGroup
import com.tkolymp.napect.domain.model.Recipe
import com.tkolymp.napect.domain.model.Step
import com.tkolymp.napect.domain.model.Tag
import com.tkolymp.napect.data.ai.TagSuggestion
import com.tkolymp.napect.domain.model.TagGroup
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontWeight

// ─── State holders ────────────────────────────────────────────────────────────

/** Holds mutable state for a single ingredient row. */
private class IngredientInputState(
    initialAmount: String = "",
    initialUnit: String = "",
    initialName: String = ""
) {
    val amount: MutableState<String> = mutableStateOf(initialAmount)
    val unit: MutableState<String> = mutableStateOf(initialUnit)
    val name: MutableState<String> = mutableStateOf(initialName)
}

/** Holds mutable state for an ingredient section (name + its ingredient rows). */
private class IngredientGroupState(initialName: String = "") {
    val name: MutableState<String> = mutableStateOf(initialName)
    val ingredients: SnapshotStateList<IngredientInputState> = mutableStateListOf()
}

private fun defaultGroupState(): IngredientGroupState =
    IngredientGroupState("").also { it.ingredients.add(IngredientInputState()) }

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun AddRecipeScreen(
    onSave: (Recipe, List<Long>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialRecipe: Recipe? = null,
    availableTags: List<Tag> = emptyList(),
    suggested: TagSuggestion? = null,
    onSuggest: (Recipe) -> Unit = {},
    onCreateUserTag: (String, TagGroup) -> Unit = { _, _ -> },
    onRegisterPickPhotoAction: (((() -> Unit) -> Unit)) = { _ -> },
    onRegisterOpenCameraAction: (((() -> Unit) -> Unit)) = { _ -> },
    onOpenCamera: () -> Unit = {},
    importVm: UrlImportViewModel? = null
) {
    val init = initialRecipe
    var title by remember(init) { mutableStateOf(init?.title ?: "") }
    var summary by remember(init) { mutableStateOf(init?.summary ?: "") }
    var servingsBase by remember(init) { mutableStateOf(init?.servingsBase ?: 4) }

    // Nutrition state (values for the whole recipe)
    var caloriesKcal by remember(init) { mutableStateOf(init?.caloriesKcal?.let { formatNutritionValue(it) } ?: "") }
    var fatG by remember(init) { mutableStateOf(init?.fatG?.let { formatNutritionValue(it) } ?: "") }
    var carbsG by remember(init) { mutableStateOf(init?.carbsG?.let { formatNutritionValue(it) } ?: "") }
    var proteinsG by remember(init) { mutableStateOf(init?.proteinsG?.let { formatNutritionValue(it) } ?: "") }
    var nutriScore by remember(init) { mutableStateOf(init?.nutriScore) }

    // Ingredient sections: always at least the default "Ingredients" group
    val ingredientGroups = remember(init) {
        mutableStateListOf<IngredientGroupState>().apply {
            if (init?.ingredientGroups?.isNotEmpty() == true) {
                init.ingredientGroups.sortedBy { it.sortOrder }.forEach { group ->
                    add(IngredientGroupState(group.name).also { state ->
                        group.ingredients.sortedBy { it.sortOrder }.forEach { ing ->
                            val amtStr = if (ing.amount == 0.0) ""
                            else if (ing.amount == kotlin.math.floor(ing.amount)) ing.amount.toInt().toString()
                            else ing.amount.toString()
                            state.ingredients.add(IngredientInputState(amtStr, ing.unit ?: "", ing.name))
                        }
                        if (state.ingredients.isEmpty()) state.ingredients.add(IngredientInputState())
                    })
                }
            }
            if (isEmpty()) add(defaultGroupState())
        }
    }

    val steps = remember(init) {
        mutableStateListOf<String>().apply {
            init?.steps?.sortedBy { it.stepNumber }?.forEach { add(it.instruction) }
            if (isEmpty()) add("")
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHostState.current
    var photoBytes by remember(init) { mutableStateOf<ByteArray?>(init?.photo) }
    var photoPath by remember(init) { mutableStateOf<String?>(init?.photoPath) }
    var sourceUrl by remember { mutableStateOf<String?>(init?.sourceUrl) }

    // ─── Camera & gallery launchers ───────────────────────────────────────────

    val pickLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    val baos = ByteArrayOutputStream()
                    ins.copyTo(baos)
                    photoBytes = baos.toByteArray()
                }
                importVm?.importImage(uri)
            } catch (e: Exception) {
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.error_load_image, e.localizedMessage ?: e.javaClass.simpleName)) }
            }
        }
    }

    var currentCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            try {
                currentCameraUri?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        val baos = ByteArrayOutputStream()
                        ins.copyTo(baos)
                        photoBytes = baos.toByteArray()
                    }
                    importVm?.importImage(uri)
                }
            } catch (e: Exception) { Timber.w(e) }
        }
    }

    val launchCameraInternal: () -> Unit = {
        try {
            val cameraDir = java.io.File(context.cacheDir, "camera_captures").also { it.mkdirs() }
            val tmpFile = java.io.File.createTempFile("camera_capture_${System.currentTimeMillis()}", ".jpg", cameraDir)
            tmpFile.deleteOnExit()
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)
            currentCameraUri = uri
            try {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri) }
                val resList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                for (res in resList) {
                    context.grantUriPermission(res.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) { Timber.w(e) }
            takePictureLauncher.launch(uri)
            scope.launch { snackbar.showSnackbar(context.getString(R.string.info_opening_camera)) }
        } catch (e: Exception) {
            Timber.w(e)
            scope.launch { snackbar.showSnackbar(context.getString(R.string.error_open_camera, e.localizedMessage ?: e.javaClass.simpleName)) }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) launchCameraInternal()
        else scope.launch { snackbar.showSnackbar(context.getString(R.string.error_camera_permission)) }
    }

    val openCameraAction: () -> Unit = {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCameraInternal()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: Exception) {
            Timber.w(e)
            scope.launch { snackbar.showSnackbar(context.getString(R.string.error_open_camera, e.localizedMessage ?: e.javaClass.simpleName)) }
        }
    }

    onRegisterPickPhotoAction({ pickLauncher.launch("image/*") })
    onRegisterOpenCameraAction(openCameraAction)

    // ─── URL import handler ───────────────────────────────────────────────────

    var showUrlEntry by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }

    val importState = if (importVm != null) {
        importVm.state.collectAsState()
    } else {
        remember { mutableStateOf(UrlImportState.Idle) }
    }
    val currentImportState by importState
    LaunchedEffect(currentImportState) {
        when (currentImportState) {
            is UrlImportState.Success -> {
                if (importVm != null) {
                    val data = (currentImportState as UrlImportState.Success).data
                    if (title.isBlank()) title = data.title
                    if (!data.description.isNullOrBlank() && summary.isBlank()) summary = data.description.orEmpty()
                    if (data.ingredientGroups.isNotEmpty()) {
                        val defaultGroup = ingredientGroups.firstOrNull() ?: defaultGroupState().also { ingredientGroups.add(it) }
                        if (data.ingredientGroups.size == 1) {
                            defaultGroup.name.value = data.ingredientGroups[0].name
                            defaultGroup.ingredients.clear()
                            fillIngredients(data.ingredientGroups[0], defaultGroup)
                        } else {
                            ingredientGroups.clear()
                            data.ingredientGroups.forEach { importedGroup ->
                                val gs = IngredientGroupState(importedGroup.name)
                                fillIngredients(importedGroup, gs)
                                if (gs.ingredients.isEmpty()) gs.ingredients.add(IngredientInputState())
                                ingredientGroups.add(gs)
                            }
                            if (ingredientGroups.isEmpty()) ingredientGroups.add(defaultGroupState())
                        }
                    }
                    if (data.steps.isNotEmpty()) {
                        steps.clear()
                        data.steps.forEach { steps.add(it) }
                    }
                    if (!data.sourceUrl.isNullOrBlank()) sourceUrl = data.sourceUrl
                    if (data.caloriesKcal != null && caloriesKcal.isBlank()) caloriesKcal = formatNutritionValue(data.caloriesKcal)
                    if (data.fatG != null && fatG.isBlank()) fatG = formatNutritionValue(data.fatG)
                    if (data.carbsG != null && carbsG.isBlank()) carbsG = formatNutritionValue(data.carbsG)
                    if (data.proteinsG != null && proteinsG.isBlank()) proteinsG = formatNutritionValue(data.proteinsG)
                    if (data.nutriScore != null && nutriScore == null) nutriScore = data.nutriScore
                    try {
                        val preview = buildPreviewRecipe(init, title = data.title, summary = data.description, ingredientGroups = ingredientGroups, steps = steps, sourceUrl = data.sourceUrl, servingsBase = servingsBase)
                        onSuggest(preview)
                    } catch (e: Exception) { Timber.w(e) }
                    importVm.resetState()
                }
            }
            is UrlImportState.Error -> {
                scope.launch { snackbar.showSnackbar(context.getString(R.string.error_import_failed, (currentImportState as UrlImportState.Error).message)) }
            }
            else -> {}
        }
    }

    // ─── Auto-suggest debounce ────────────────────────────────────────────────

    LaunchedEffect(Unit) {
        snapshotFlow {
            buildPreviewRecipe(init, title, summary, ingredientGroups, steps, sourceUrl, servingsBase)
        }
            .debounce(700)
            .collectLatest { preview ->
                if (preview.title.isNotBlank() || preview.allIngredients.isNotEmpty() || preview.steps.isNotEmpty()) {
                    try { onSuggest(preview) } catch (e: Exception) { Timber.w(e) }
                }
            }
    }

    // ─── Tag state ────────────────────────────────────────────────────────────

    val selectedTagIds = remember { mutableStateListOf<Long>() }
    LaunchedEffect(init) {
        selectedTagIds.clear()
        init?.tags?.forEach { selectedTagIds.add(it.id) }
    }

    val userTouchedTags = remember { mutableStateOf(false) }
    val suggestedTagsList = suggested?.let { it.confirmed + it.newlyCreated } ?: emptyList()
    val suggestedIdsLocal = suggestedTagsList.map { it.id }.toSet()
    val suggestedNamesLocal = suggestedTagsList.map { it.name }.toSet()

    Timber.d("suggested -> confirmed=%s newly=%s", suggested?.confirmed?.map { it.name }, suggested?.newlyCreated?.map { it.name })

    LaunchedEffect(suggested) {
        if (!userTouchedTags.value && suggestedTagsList.isNotEmpty()) {
            for (t in suggestedTagsList) {
                val match = availableTags.find { it.id == t.id || it.name.equals(t.name, ignoreCase = true) }
                if (match != null) {
                    if (!selectedTagIds.contains(match.id)) selectedTagIds.add(match.id)
                } else {
                    if (t.id > 0L && !selectedTagIds.contains(t.id)) selectedTagIds.add(t.id)
                }
            }
        }
    }

    // ─── UI ───────────────────────────────────────────────────────────────────

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {

        // Photo: prefer freshly picked bytes, fall back to existing file path
        val photoDisplayModel: Any? = photoBytes ?: photoPath?.let { java.io.File(it) }
        if (photoDisplayModel != null) {
            AsyncImage(
                model = photoDisplayModel,
                contentDescription = stringResource(R.string.selected_photo),
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentScale = ContentScale.Crop
            )
            TextButton(onClick = { photoBytes = null; photoPath = null }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.remove_photo)) }
        }

        // Title + voice
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.label_title)) }, modifier = Modifier.weight(1f))
            VoiceInputButton(onResult = { text ->
                val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                if (title.isBlank() && lines.isNotEmpty()) title = lines.first()
                val ingLines = mutableListOf<String>()
                val stepLines = mutableListOf<String>()
                for (ln in lines.drop(1)) {
                    val lower = ln.lowercase()
                    val looksLikeIngredient = lower.matches(Regex("^[0-9⅛⅜¼½¾⅓⅔].*")) || lower.contains("tsp") || lower.contains("tbsp") || lower.contains("cup") || lower.contains("g") || lower.contains("kg") || lower.contains("ml") || lower.contains("l")
                    if (looksLikeIngredient) ingLines.add(ln) else stepLines.add(ln)
                }
                if (ingLines.isNotEmpty()) {
                    // Place voice ingredients in the default (first) section
                    val defaultGroup = ingredientGroups.firstOrNull() ?: defaultGroupState().also { ingredientGroups.add(it) }
                    defaultGroup.ingredients.clear()
                    ingLines.forEach { defaultGroup.ingredients.add(IngredientInputState(initialName = it)) }
                }
                if (stepLines.isNotEmpty()) {
                    steps.clear()
                    stepLines.forEach { steps.add(it) }
                }
            })
        }

        OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text(stringResource(R.string.label_summary)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        OutlinedTextField(
            value = sourceUrl ?: "",
            onValueChange = { sourceUrl = it.ifBlank { null } },
            label = { Text(stringResource(R.string.label_source_url)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
        )

        // Servings
        Spacer(modifier = Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (servingsBase > 1) servingsBase-- }, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = stringResource(R.string.base_servings_decrease))
             }
             Text(stringResource(R.string.base_servings_label, servingsBase), modifier = Modifier.padding(horizontal = 8.dp))
             IconButton(onClick = { servingsBase++ }, modifier = Modifier.size(40.dp)) {
                 Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.base_servings_increase))
            }
        }

        // ── Ingredient sections ──────────────────────────────────────────────

        Spacer(modifier = Modifier.size(8.dp))
        Text(stringResource(R.string.section_ingredients), style = MaterialTheme.typography.titleMedium)

        ingredientGroups.forEachIndexed { groupIndex, group ->
            // Named sub-section header (only for groups added beyond the default)
            if (groupIndex > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    OutlinedTextField(
                        value = group.name.value,
                        onValueChange = { group.name.value = it },
                        label = { Text(stringResource(R.string.section_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { ingredientGroups.removeAt(groupIndex) }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.remove_section))
                    }
                }
            }

            // Ingredient rows within this section
            group.ingredients.forEachIndexed { ingIndex, ing ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    OutlinedTextField(value = ing.amount.value, onValueChange = { ing.amount.value = it }, label = { Text("0") }, singleLine = true, modifier = Modifier.width(80.dp))
                    OutlinedTextField(value = ing.unit.value, onValueChange = { ing.unit.value = it }, label = { Text("g") }, singleLine = true, modifier = Modifier.width(72.dp))
                    OutlinedTextField(value = ing.name.value, onValueChange = { ing.name.value = it }, label = { Text(stringResource(R.string.ingredient_name_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            if (group.ingredients.size > 1) group.ingredients.removeAt(ingIndex)
                            else group.ingredients[ingIndex] = IngredientInputState()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.remove_ingredient))
                    }
                }
            }

            TextButton(onClick = { group.ingredients.add(IngredientInputState()) }, modifier = Modifier.padding(top = 4.dp)) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.add), modifier = Modifier.size(16.dp))
                 Text(stringResource(R.string.add_ingredient))
            }
        }

        // Add section button
        Button(onClick = { ingredientGroups.add(IngredientGroupState("").also { it.ingredients.add(IngredientInputState()) }) }, modifier = Modifier.padding(top = 8.dp)) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.add), modifier = Modifier.size(18.dp))
             Text(stringResource(R.string.add_section))
        }

        // ── Tags ─────────────────────────────────────────────────────────────

        Spacer(modifier = Modifier.size(12.dp))
        Text(stringResource(R.string.section_tags), fontWeight = FontWeight.Bold)

        val grouped = availableTags.groupBy { it.group }
        val availableNamesLower = availableTags.map { it.name.lowercase() }.toSet()
        val extraSuggested = suggestedTagsList.filter { it.name.lowercase() !in availableNamesLower }

        grouped.forEach { (group, tags) ->
            Text(group.displayName, modifier = Modifier.padding(top = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                for (t in tags) {
                    val checked = if (userTouchedTags.value) selectedTagIds.contains(t.id)
                    else (selectedTagIds.contains(t.id) || suggestedIdsLocal.contains(t.id) || suggestedNamesLocal.contains(t.name))
                    val onChipClick = {
                        if (selectedTagIds.contains(t.id)) selectedTagIds.remove(t.id) else selectedTagIds.add(t.id)
                        userTouchedTags.value = true
                    }
                    if (t.isAiGenerated) {
                        FilterChip(selected = checked, onClick = onChipClick, label = { Text(t.name) }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "AI") }, modifier = Modifier.padding(end = 8.dp))
                    } else {
                        FilterChip(selected = checked, onClick = onChipClick, label = { Text(t.name) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }

        if (extraSuggested.isNotEmpty()) {
            Text(stringResource(R.string.section_suggested), modifier = Modifier.padding(top = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                for (st in extraSuggested) {
                    val checked = if (userTouchedTags.value) selectedTagIds.contains(st.id) else true
                    val onChipClick = {
                        if (selectedTagIds.contains(st.id)) selectedTagIds.remove(st.id) else selectedTagIds.add(st.id)
                        userTouchedTags.value = true
                    }
                    FilterChip(selected = checked, onClick = onChipClick, label = { Text(st.name) }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "AI") }, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }

        // ── Steps ────────────────────────────────────────────────────────────

        Spacer(modifier = Modifier.size(12.dp))
        Text(stringResource(R.string.section_steps))
        Column(modifier = Modifier.fillMaxWidth()) {
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(value = step, onValueChange = { steps[index] = it }, label = { Text("Krok ${index + 1}") }, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { if (steps.size > 1) steps.removeAt(index) else steps[index] = "" }, modifier = Modifier.size(40.dp)) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Odebrat krok")
                    }
                }
            }
        }
        Button(onClick = { steps.add("") }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.add_step)) }

        // ── Nutritional values ───────────────────────────────────────────────

        Spacer(modifier = Modifier.size(12.dp))
        Text(stringResource(R.string.section_nutrition), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.nutrition_per_serving_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(4.dp))

        NutritionField(
            value = caloriesKcal,
            onValueChange = { caloriesKcal = it },
            label = stringResource(R.string.nutrition_calories),
            unit = stringResource(R.string.nutrition_unit_kcal),
            servingsBase = servingsBase
        )
        NutritionField(
            value = fatG,
            onValueChange = { fatG = it },
            label = stringResource(R.string.nutrition_fat),
            unit = stringResource(R.string.nutrition_unit_g),
            servingsBase = servingsBase
        )
        NutritionField(
            value = carbsG,
            onValueChange = { carbsG = it },
            label = stringResource(R.string.nutrition_carbs),
            unit = stringResource(R.string.nutrition_unit_g),
            servingsBase = servingsBase
        )
        NutritionField(
            value = proteinsG,
            onValueChange = { proteinsG = it },
            label = stringResource(R.string.nutrition_proteins),
            unit = stringResource(R.string.nutrition_unit_g),
            servingsBase = servingsBase
        )

        // Nutri-Score chip picker
        Text(
            stringResource(R.string.nutrition_nutriscore),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            listOf("A", "B", "C", "D", "E").forEach { grade ->
                FilterChip(
                    selected = nutriScore == grade,
                    onClick = { nutriScore = if (nutriScore == grade) null else grade },
                    label = { Text(grade) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // ── Save / Cancel ────────────────────────────────────────────────────

        Spacer(modifier = Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (title.isNotBlank()) {
                    val groupsDomain = ingredientGroups.mapIndexed { gIdx, group ->
                        val ingDomain = group.ingredients.mapIndexedNotNull { iIdx, ing ->
                            val name = ing.name.value.trim()
                            if (name.isBlank()) return@mapIndexedNotNull null
                            Ingredient(amount = ing.amount.value.toDoubleOrNull() ?: 0.0, unit = ing.unit.value.ifBlank { null }, name = name, sortOrder = iIdx)
                        }
                        IngredientGroup(name = group.name.value.trim(), sortOrder = gIdx, ingredients = ingDomain)
                    }
                    val stepsDomain = steps.mapIndexedNotNull { idx, s ->
                        val instr = s.trim()
                        if (instr.isBlank()) return@mapIndexedNotNull null
                        Step(stepNumber = idx + 1, instruction = instr)
                    }
                    val finalTagIds = if (userTouchedTags.value || selectedTagIds.isNotEmpty()) selectedTagIds.toList()
                    else suggestedIdsLocal.toList()

                    onSave(
                        Recipe(
                            id = init?.id ?: 0L,
                            title = title.trim(),
                            summary = summary.ifBlank { null },
                            sourceUrl = sourceUrl,
                            ingredientGroups = groupsDomain,
                            steps = stepsDomain,
                            photo = photoBytes,
                            photoPath = photoPath,
                            servingsBase = servingsBase,
                            caloriesKcal = caloriesKcal.toDoubleOrNull(),
                            fatG = fatG.toDoubleOrNull(),
                            carbsG = carbsG.toDoubleOrNull(),
                            proteinsG = proteinsG.toDoubleOrNull(),
                            nutriScore = nutriScore,
                        ),
                        finalTagIds
                    )
                }
            }) { Text(stringResource(R.string.save)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }

}
}

// ─── Ingredient import helper ─────────────────────────────────────────────────

private fun fillIngredients(
    importedGroup: com.tkolymp.napect.data.network.ImportedIngredientGroup,
    target: IngredientGroupState,
) {
    if (importedGroup.structuredIngredients.isNotEmpty()) {
        importedGroup.structuredIngredients.forEach { ing ->
            val amtStr = ing.amount?.let { a ->
                if (a == kotlin.math.floor(a)) a.toInt().toString() else a.toString()
            } ?: ""
            target.ingredients.add(IngredientInputState(amtStr, ing.unit ?: "", ing.name))
        }
    } else {
        importedGroup.ingredients.forEach { raw ->
            try {
                val parsed = com.tkolymp.napect.data.parse.IngredientParser.parse(raw)
                val amtStr = parsed.amount?.let { a ->
                    if (a == kotlin.math.floor(a)) a.toInt().toString() else a.toString()
                } ?: ""
                target.ingredients.add(IngredientInputState(amtStr, parsed.unit ?: "", parsed.name))
            } catch (e: Exception) {
                Timber.w(e, "Ingredient parse failed, using raw text")
                target.ingredients.add(IngredientInputState(initialName = raw))
            }
        }
    }
}

// ─── Nutrition helpers ────────────────────────────────────────────────────────

private fun formatNutritionValue(d: Double): String =
    if (d == kotlin.math.floor(d)) d.toInt().toString() else d.toString()

@Composable
private fun NutritionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    unit: String,
    servingsBase: Int,
) {
    val total = if (servingsBase > 1 && value.isNotBlank()) {
        value.toDoubleOrNull()?.let { v -> formatNutritionValue(v * servingsBase) }
    } else null

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("$label / porci ($unit)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f)
            )
            if (total != null) {
                Text(
                    text = "celkem: $total $unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

// ─── Helper to build a preview Recipe from current UI state ──────────────────

private fun buildPreviewRecipe(
    init: Recipe?,
    title: String,
    summary: String?,
    ingredientGroups: List<IngredientGroupState>,
    steps: List<String>,
    sourceUrl: String?,
    servingsBase: Int,
): Recipe {
    val groupsDomain = ingredientGroups.mapIndexed { gIdx, group ->
        val ingDomain = group.ingredients.mapIndexedNotNull { iIdx, ing ->
            val name = ing.name.value.trim()
            if (name.isBlank()) return@mapIndexedNotNull null
            Ingredient(amount = ing.amount.value.toDoubleOrNull() ?: 0.0, unit = ing.unit.value.ifBlank { null }, name = name, sortOrder = iIdx)
        }
        IngredientGroup(name = group.name.value.trim(), sortOrder = gIdx, ingredients = ingDomain)
    }
    val stepsDomain = steps.mapIndexedNotNull { idx, s ->
        val instr = s.trim()
        if (instr.isBlank()) return@mapIndexedNotNull null
        Step(stepNumber = idx + 1, instruction = instr)
    }
    return Recipe(
        id = init?.id ?: 0L,
        title = title.trim(),
        summary = summary?.ifBlank { null },
        sourceUrl = sourceUrl,
        ingredientGroups = groupsDomain,
        steps = stepsDomain,
        photo = null,
        servingsBase = servingsBase
    )
}
