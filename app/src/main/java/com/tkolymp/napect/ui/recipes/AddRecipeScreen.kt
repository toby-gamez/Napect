package com.tkolymp.napect.ui.recipes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
// Column already imported above
import androidx.compose.foundation.layout.Row
// fillMaxWidth already imported above
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
// Keyboard input types referenced fully-qualified to avoid import resolution issues in some build setups
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.Manifest
import android.content.Intent
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// keyboard input options removed for compatibility; rely on default keyboard
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import com.tkolymp.napect.domain.model.Ingredient
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon

// Stateful holder for UI ingredient inputs to avoid list-replacement issues
private class IngredientInputState(
    initialAmount: String = "",
    initialUnit: String = "",
    initialName: String = ""
) {
    val amount: MutableState<String> = mutableStateOf(initialAmount)
    val unit: MutableState<String> = mutableStateOf(initialUnit)
    val name: MutableState<String> = mutableStateOf(initialName)
}

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
    // Registerers allow the parent (NapectApp) to show global actions (topbar) that
    // trigger the photo pick / camera flows which are implemented here.
    onRegisterPickPhotoAction: (((() -> Unit) -> Unit)) = { _ -> },
    onRegisterOpenCameraAction: (((() -> Unit) -> Unit)) = { _ -> },
    onOpenCamera: () -> Unit = {},
    importVm: UrlImportViewModel? = null
) {
    val init = initialRecipe
    var title by remember(init) { mutableStateOf(init?.title ?: "") }
    var summary by remember(init) { mutableStateOf(init?.summary ?: "") }
    var servingsBase by remember(init) { mutableStateOf(init?.servingsBase ?: 4) }

    val ingredients = remember(init) { mutableStateListOf<IngredientInputState>().apply {
        init?.ingredients?.forEach { add(IngredientInputState(it.amount.toString(), it.unit ?: "", it.name)) }
        if (isEmpty()) add(IngredientInputState())
    } }
    val steps = remember(init) { mutableStateListOf<String>().apply {
        init?.steps?.forEach { add(it.instruction) }
        if (isEmpty()) add("")
    } }

    // image bytes picked from gallery (optional)
    val context = LocalContext.current
    var photoBytes by remember(init) { mutableStateOf<ByteArray?>(init?.photo) }

    val pickLauncher = rememberLauncherForActivityResult(GetContent()) { uri ->
        if (uri != null) {
            try {
                // set preview bytes
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    val baos = ByteArrayOutputStream()
                    ins.copyTo(baos)
                    photoBytes = baos.toByteArray()
                }
                // trigger OCR import if ViewModel provided
                importVm?.importImage(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read image: ${e.localizedMessage ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var currentCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Platform camera (TakePicture) launcher. Declared before permission launcher so
    // it can be safely invoked from within the permission callback.
    val takePictureLauncher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            try {
                currentCameraUri?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        val baos = ByteArrayOutputStream()
                        ins.copyTo(baos)
                        photoBytes = baos.toByteArray()
                    }
                    // trigger OCR import if ViewModel provided
                    importVm?.importImage(uri)
                }
            } catch (_: Exception) { }
        }
    }

    // Permission launcher for camera access. Defined here so it can be used by the
    // openCameraAction that is registered with the parent actions.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            try {
                // create file & launch camera
                val tmpFile = java.io.File.createTempFile("camera_capture_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
                tmpFile.deleteOnExit()
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)
                currentCameraUri = uri
                // Grant URI write permission to any camera activity that can handle the intent
                try {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri) }
                    val resList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    for (res in resList) {
                        context.grantUriPermission(res.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } catch (_: Exception) { }
                takePictureLauncher.launch(uri)
                Toast.makeText(context, "Opening camera…", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to open camera: ${e.localizedMessage ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Inline URL import UI state (merged into Add screen)
    var showUrlEntry by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    // final source URL for the recipe (populated from inline entry or import VM results)
    var sourceUrl by remember { mutableStateOf<String?>(init?.sourceUrl) }

    // (TakePicture launcher already declared above)

    // Helper that encapsulates the camera-launch logic for use when permission already granted
    val launchCameraInternal: () -> Unit = {
        try {
            val tmpFile = java.io.File.createTempFile("camera_capture_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
            tmpFile.deleteOnExit()
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)
            currentCameraUri = uri
            try {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri) }
                val resList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                for (res in resList) {
                    context.grantUriPermission(res.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (_: Exception) { }
            takePictureLauncher.launch(uri)
            Toast.makeText(context, "Opening camera…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to open camera: ${e.localizedMessage ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    // Action the parent can invoke to open the camera. It will request permission if needed.
    val openCameraAction: () -> Unit = {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCameraInternal()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to open camera: ${e.localizedMessage ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    // Register the local pick & camera actions with the parent so top-bar actions can invoke them.
    onRegisterPickPhotoAction({ pickLauncher.launch("image/*") })
    onRegisterOpenCameraAction(openCameraAction)

    // Observe import VM state (if provided) and populate fields when OCR completes
    if (importVm != null) {
        val importState by importVm.state.collectAsState()
        LaunchedEffect(importState) {
            when (importState) {
                is UrlImportState.Success -> {
                    val data = (importState as UrlImportState.Success).data
                    if (title.isBlank()) title = data.title
                    if (!data.description.isNullOrBlank() && summary.isBlank()) summary = data.description.orEmpty()
                    if (data.ingredients.isNotEmpty()) {
                        ingredients.clear()
                        data.ingredients.forEach { raw ->
                            try {
                                val parsed = com.tkolymp.napect.data.parse.IngredientParser.parse(raw)
                                val amtStr = parsed.amount?.let { a ->
                                    // show integer values without decimal when possible
                                    if (a == kotlin.math.floor(a)) a.toInt().toString() else a.toString()
                                } ?: ""
                                ingredients.add(IngredientInputState(initialAmount = amtStr, initialUnit = parsed.unit ?: "", initialName = parsed.name))
                            } catch (_: Exception) {
                                ingredients.add(IngredientInputState(initialName = raw))
                            }
                        }
                    }
                    if (data.steps.isNotEmpty()) {
                        steps.clear()
                        data.steps.forEach { steps.add(it) }
                    }
                    // populate the screen's sourceUrl if importer provided one
                    if (!data.sourceUrl.isNullOrBlank()) sourceUrl = data.sourceUrl
                    // Build a preview recipe and request suggestions automatically so tags
                    // appear without requiring the user to press a button.
                    try {
                        val ingDomain = ingredients.mapIndexedNotNull { idx, it ->
                            val amt = it.amount.value.toDoubleOrNull() ?: 0.0
                            val unit = it.unit.value.ifBlank { null }
                            val name = it.name.value.trim()
                            if (name.isBlank()) return@mapIndexedNotNull null
                            com.tkolymp.napect.domain.model.Ingredient(amount = amt, unit = unit, name = name, sortOrder = idx)
                        }
                        val stepsDomain = steps.mapIndexedNotNull { idx, s ->
                            val instr = s.trim()
                            if (instr.isBlank()) return@mapIndexedNotNull null
                            com.tkolymp.napect.domain.model.Step(stepNumber = idx + 1, instruction = instr)
                        }
                        val preview = com.tkolymp.napect.domain.model.Recipe(
                            id = init?.id ?: 0L,
                            title = data.title,
                            summary = data.description ?: null,
                            sourceUrl = data.sourceUrl,
                            ingredients = ingDomain,
                            steps = stepsDomain,
                            photo = null,
                            servingsBase = 4
                        )
                        onSuggest(preview)
                    } catch (_: Exception) {
                        // ignore any preview/suggestion failures; suggestions are best-effort
                    }
                }
                is UrlImportState.Error -> {
                    Toast.makeText(context, "Import failed: ${(importState as UrlImportState.Error).message}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    // Auto-suggest tags as the user edits the form. Debounced to avoid spamming the suggester
    LaunchedEffect(Unit) {
        snapshotFlow {
            // Build a preview Recipe from current UI values
            val ingDomain = ingredients.mapIndexedNotNull { idx, it ->
                val amt = it.amount.value.toDoubleOrNull() ?: 0.0
                val unit = it.unit.value.ifBlank { null }
                val name = it.name.value.trim()
                if (name.isBlank()) return@mapIndexedNotNull null
                com.tkolymp.napect.domain.model.Ingredient(amount = amt, unit = unit, name = name, sortOrder = idx)
            }
            val stepsDomain = steps.mapIndexedNotNull { idx, s ->
                val instr = s.trim()
                if (instr.isBlank()) return@mapIndexedNotNull null
                com.tkolymp.napect.domain.model.Step(stepNumber = idx + 1, instruction = instr)
            }
            com.tkolymp.napect.domain.model.Recipe(
                id = init?.id ?: 0L,
                title = title.trim(),
                summary = summary.ifBlank { null },
                sourceUrl = sourceUrl,
                ingredients = ingDomain,
                steps = stepsDomain,
                photo = null,
                servingsBase = servingsBase
            )
        }
            .debounce(700)
            .collectLatest { preview ->
                // Avoid suggesting for empty previews
                if (preview.title.isNotBlank() || preview.ingredients.isNotEmpty() || preview.steps.isNotEmpty()) {
                    try {
                        onSuggest(preview)
                    } catch (_: Exception) { }
                }
            }
    }

    // Debug: if suggestions exist, print them to log (helps during runtime debugging)
    // (logged below after suggestion lists are constructed)

    // lists initialized above

    Column(modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        // Photo picker
        if (photoBytes != null) {
            val bmp = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes!!.size)
            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Selected photo", modifier = Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
            TextButton(onClick = { photoBytes = null }, modifier = Modifier.padding(top = 8.dp)) { Text("Remove Photo") }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add a photo using the + menu in the top bar", modifier = Modifier.weight(1f))
                // Camera & pick actions moved to top bar menu; remove inline camera/pick buttons.
                // URL import button (only shown if a UrlImportViewModel was provided)
                if (importVm != null) {
                    Button(onClick = { showUrlEntry = !showUrlEntry }) { Text("Import URL") }
                }
            }
            // Inline URL entry shown when the Import URL button is toggled
            if (showUrlEntry) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = urlText, onValueChange = { urlText = it }, label = { Text("URL") }, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        if (urlText.isNotBlank()) {
                            try {
                                importVm?.fetchUrl(urlText)
                                showUrlEntry = false
                                urlText = ""
                                Toast.makeText(context, "Importing URL…", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to import URL: ${e.localizedMessage ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Please enter a URL", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Import") }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.weight(1f))
            // voice input: will append parsed text into fields via simple heuristics
            VoiceInputButton(onResult = { text ->
                // simple parse: split lines; first line -> title if title blank; lines with digits/units -> ingredients; others -> steps
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
                    ingredients.clear()
                    ingLines.forEach { ingredients.add(IngredientInputState(initialName = it)) }
                }
                if (stepLines.isNotEmpty()) {
                    steps.clear()
                    stepLines.forEach { steps.add(it) }
                }
            })
        }
        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it },
            label = { Text("Summary") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (servingsBase > 1) servingsBase-- }, modifier = Modifier.size(40.dp)) {
                    Icon(imageVector = Icons.Filled.Remove, contentDescription = "Decrease base servings")
                }
                Text("  Base servings: $servingsBase  ", modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { servingsBase++ }, modifier = Modifier.size(40.dp)) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Increase base servings")
                }
        }

        Spacer(modifier = Modifier.size(8.dp))
        Text("Ingredients")
        Column(modifier = Modifier.fillMaxWidth()) {
            ingredients.forEachIndexed { index, ing ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = ing.amount.value,
                        onValueChange = { ing.amount.value = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                    OutlinedTextField(
                        value = ing.unit.value,
                        onValueChange = { ing.unit.value = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                    OutlinedTextField(
                        value = ing.name.value,
                        onValueChange = { ing.name.value = it },
                        label = { Text("Ingredient") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { if (ingredients.size > 1) ingredients.removeAt(index) else { ingredients[index] = IngredientInputState() } },
                        modifier = Modifier.size(40.dp)
                    ) {
                        androidx.compose.material3.Icon(imageVector = Icons.Filled.Delete, contentDescription = "Remove ingredient")
                    }
                }
            }
        }

        Button(onClick = { ingredients.add(IngredientInputState()) }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Add Ingredient")
        }

        Spacer(modifier = Modifier.size(12.dp))
        // Tag picker
        Text("Tags", fontWeight = FontWeight.Bold)
        val selectedTagIds = remember { mutableStateListOf<Long>() }
        // preselect from initial recipe if present
        LaunchedEffect(init) {
            selectedTagIds.clear()
            init?.tags?.forEach { selectedTagIds.add(it.id) }
        }

        // Track whether the user interacted with tag chips. If they did, respect their
        // explicit selection. If they didn't touch tags at all, apply AI suggestions
        // automatically on save. When suggestions arrive and the user hasn't touched
        // tags yet, merge them into the selectedTagIds so they appear pre-selected.
        val userTouchedTags = remember { mutableStateOf(false) }

        // Build suggestion lists from the passed TagSuggestion object (if any)
        val suggestedTagsList = suggested?.let { it.confirmed + it.newlyCreated } ?: emptyList()
        val suggestedIdsLocal = suggestedTagsList.map { it.id }.toSet()
        val suggestedNamesLocal = suggestedTagsList.map { it.name }.toSet()

        try {
            Log.d("AddRecipeScreen", "suggested -> confirmed=${suggested?.confirmed?.map { it.name }} newly=${suggested?.newlyCreated?.map { it.name }} ids=${suggestedTagsList.map { it.id }}")
        } catch (_: Exception) { }

        // Use the whole suggested object as the effect key so we react to any change
        // and merge suggestions into selectedTagIds in a way that's tolerant to
        // ordering/race conditions between DB updates and UI composition.
        LaunchedEffect(suggested) {
            Log.d("AddRecipeScreen", "LaunchedEffect(suggested) triggered: userTouched=${userTouchedTags.value}, suggestedNames=${suggestedTagsList.map { it.name }}, selectedBefore=${selectedTagIds.toList()}")
            if (!userTouchedTags.value && suggestedTagsList.isNotEmpty()) {
                for (t in suggestedTagsList) {
                    val match = availableTags.find { it.id == t.id || it.name.equals(t.name, ignoreCase = true) }
                    if (match != null) {
                        if (!selectedTagIds.contains(match.id)) selectedTagIds.add(match.id)
                    } else {
                        if (t.id > 0L && !selectedTagIds.contains(t.id)) selectedTagIds.add(t.id)
                    }
                }
                Log.d("AddRecipeScreen", "LaunchedEffect(suggested) applied -> selectedAfter=${selectedTagIds.toList()}")
            }
        }

        // group tags by group for display
        val grouped = availableTags.groupBy { it.group }
        // Determine any suggested tags that are not yet present in availableTags
        val availableNamesLower = availableTags.map { it.name.lowercase() }.toSet()
        val extraSuggested = suggestedTagsList.filter { it.name.lowercase() !in availableNamesLower }

        // No separate name-only selection list — we toggle selectedTagIds directly for
        // suggested tags that are not yet part of availableTags.
        grouped.forEach { (group, tags) ->
            Text(group.name, modifier = Modifier.padding(top = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                for (t in tags) {
                    // If the user touched tags, show only their explicit selection.
                    // Otherwise, preselect AI-suggested tags for convenience.
                    val checked = if (userTouchedTags.value) selectedTagIds.contains(t.id) else (selectedTagIds.contains(t.id) || suggestedIdsLocal.contains(t.id) || suggestedNamesLocal.contains(t.name))
                    val onChipClick = {
                        if (selectedTagIds.contains(t.id)) selectedTagIds.remove(t.id) else selectedTagIds.add(t.id)
                        userTouchedTags.value = true
                    }

                    if (t.isAiGenerated) {
                        FilterChip(selected = checked, onClick = onChipClick, label = { Text(t.name) }, leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.AutoAwesome, contentDescription = "AI") }, modifier = Modifier.padding(end = 8.dp))
                    } else {
                        FilterChip(selected = checked, onClick = onChipClick, label = { Text(t.name) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }

        // Render extra suggested tags (those not present yet in availableTags)
        if (extraSuggested.isNotEmpty()) {
            Text("Suggested", modifier = Modifier.padding(top = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                for (st in extraSuggested) {
                    val checked = if (userTouchedTags.value) selectedTagIds.contains(st.id) else true
                    val onChipClick = {
                        if (selectedTagIds.contains(st.id)) selectedTagIds.remove(st.id) else selectedTagIds.add(st.id)
                        userTouchedTags.value = true
                    }
                    FilterChip(selected = checked, onClick = onChipClick, label = { Text(st.name) }, leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.AutoAwesome, contentDescription = "AI") }, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.size(8.dp))
        // Tag creation moved to Settings screen to keep Add/Edit focused on tagging selection

        Spacer(modifier = Modifier.size(12.dp))
        Text("Steps")
        Column(modifier = Modifier.fillMaxWidth()) {
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(value = step, onValueChange = { steps[index] = it }, label = { Text("Step ${index + 1}") }, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { if (steps.size > 1) steps.removeAt(index) else steps[index] = "" },
                        modifier = Modifier.size(40.dp)
                    ) {
                        androidx.compose.material3.Icon(imageVector = Icons.Filled.Delete, contentDescription = "Remove step")
                    }
                }
            }
        }

        Button(onClick = { steps.add("") }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Add Step")
        }

        Spacer(modifier = Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (title.isNotBlank()) {
                    val ingDomain = ingredients.mapIndexedNotNull { idx, it ->
                        val amt = it.amount.value.toDoubleOrNull() ?: 0.0
                        val unit = it.unit.value.ifBlank { null }
                        val name = it.name.value.trim()
                        if (name.isBlank()) return@mapIndexedNotNull null
                        Ingredient(amount = amt, unit = unit, name = name, sortOrder = idx)
                    }
                    val stepsDomain = steps.mapIndexedNotNull { idx, s ->
                        val instr = s.trim()
                        if (instr.isBlank()) return@mapIndexedNotNull null
                        Step(stepNumber = idx + 1, instruction = instr)
                    }

                    // If the user interacted with tag controls or explicitly selected tags,
                    // use their selection. Otherwise apply AI suggestions automatically.
                    val finalTagIds = if (userTouchedTags.value || selectedTagIds.isNotEmpty()) {
                        selectedTagIds.toList()
                    } else {
                        suggestedIdsLocal.toList()
                    }

                    onSave(
                        Recipe(
                            id = init?.id ?: 0L,
                            title = title.trim(),
                            summary = summary.ifBlank { null },
                            sourceUrl = sourceUrl,
                            ingredients = ingDomain,
                            steps = stepsDomain,
                            photo = photoBytes,
                            servingsBase = servingsBase
                        ),
                        finalTagIds
                    )
                }
            }) {
                Text("Save")
            }
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}
