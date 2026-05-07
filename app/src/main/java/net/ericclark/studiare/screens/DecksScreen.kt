package net.ericclark.studiare.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*
import net.ericclark.studiare.*
import net.ericclark.studiare.R // Ensure this matches your package R
import net.ericclark.studiare.components.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.data.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.livedata.observeAsState
import kotlinx.coroutines.launch

/**
 * The main screen of the app, redesigned with Material 3 Expressive principles.
 * Features bolder shapes (28dp corners), large FABs, and elevated card hierarchies.
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun DeckListScreen(
    navController: NavController,
    deckGroups: List<Pair<DeckSummary, List<DeckSummary>>>,
    viewModel: FlashcardViewModel
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current

    // State for managing dialogs and menus
    var showDeleteDialog by remember { mutableStateOf<DeckSummary?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    // State for Anki Mapping
    var showAnkiMapper by remember { mutableStateOf(false) }
    var pendingAnkiDecks by remember { mutableStateOf<List<Pair<String, List<Pair<String, net.ericclark.studiare.data.MediaType>>>>>(emptyList()) }
    var currentAnkiDeckIndex by remember { mutableStateOf(0) }
    var completedAnkiConfigs by remember { mutableStateOf<List<net.ericclark.studiare.screens.AnkiMappingConfig>>(emptyList()) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val deckSortMode by viewModel.deckSortMode.collectAsState()

    // State for theme and data
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val importDuplicateQueue by viewModel.importDuplicateQueue.collectAsState()
    val overwriteConfirmation by viewModel.overwriteConfirmation.collectAsState()

    // Customization States
    val spacingMode by viewModel.spacingMode.collectAsState()
    val displaySetsUnderDecks by viewModel.displaySetsUnderDecks.collectAsState()

    // Map spacing mode to Dimensions
    val dimensions = when (spacingMode) {
        SpacingMode.COMPACT -> CompactDimensions
        SpacingMode.NORMAL -> NormalDimensions
        else -> ComfortableDimensions
    }

    var decksToExport by remember { mutableStateOf<List<DeckWithCards>?>(null) }

    // --- Dialogs ---
    if (showSortDialog) {
        DeckSortDialog(
            currentSortMode = deckSortMode,
            onDismiss = { showSortDialog = false },
            onSortModeSelected = { viewModel.setDeckSortMode(it) }
        )
    }

    if (importDuplicateQueue.isNotEmpty()) {
        DuplicateWarningDialog(
            result = importDuplicateQueue.first(),
            onDismiss = { viewModel.dismissImportDuplicateWarning() },
            onConfirmRemove = { viewModel.saveImportWithDuplicatesRemoved() },
            onConfirmSaveAnyway = { viewModel.saveImportIgnoringDuplicates() }
        )
    }

    overwriteConfirmation?.let { data ->
        ImportOverwriteDialog(
            decksToOverwrite = data.decksToOverwrite,
            onDismiss = { viewModel.cancelImport() },
            onConfirm = { selectedIds -> viewModel.proceedWithImport(selectedIds) }
        )
    }

    if (showAnkiMapper && pendingImportUri != null && currentAnkiDeckIndex < pendingAnkiDecks.size) {
        val currentDeckData = pendingAnkiDecks[currentAnkiDeckIndex]
        val hasNext = currentAnkiDeckIndex < pendingAnkiDecks.size - 1

        AnkiFieldMappingDialog(
            ankiFields = currentDeckData.second,
            originalAnkiName = currentDeckData.first,
            hasNextDeck = hasNext,
            onDismiss = {
                showAnkiMapper = false
                pendingImportUri = null
            },
            onSaveMapping = { newConfigsForThisDeck -> // This is now a List again
                val updatedConfigs = completedAnkiConfigs + newConfigsForThisDeck

                if (hasNext) {
                    completedAnkiConfigs = updatedConfigs
                    currentAnkiDeckIndex++
                } else {
                    showAnkiMapper = false
                    val uriToImport = pendingImportUri
                    if (uriToImport != null) {
                        coroutineScope.launch {
                            viewModel.importFromAnkiPackage(context, uriToImport, updatedConfigs)
                        }
                        pendingImportUri = null
                    }
                }
            }
        )
    }

    if (viewModel.isProcessing) {
        LoadingOverlay()
    }

    // --- Export Logic ---
    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            uri?.let {
                decksToExport?.let { decks ->
                    val content = viewModel.getDecksAsString(decks, "JSON")
                    context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(content.toByteArray()) }
                }
            }
            decksToExport = null
        }
    )

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri: Uri? ->
            uri?.let {
                decksToExport?.let { decks ->
                    val content = viewModel.getDecksAsString(decks, "CSV")
                    context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(content.toByteArray()) }
                }
            }
            decksToExport = null
        }
    )

    // Anki Export Launcher
    val ankiExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri: Uri? ->
            uri?.let {
                decksToExport?.let { decks ->
                    viewModel.exportToAnkiPackage(context, decks, it)
                }
            }
            decksToExport = null
        }
    )

    val allDecksWithCards by viewModel.allDecks.observeAsState(emptyList())
    if (showExportDialog) {
        ExportDecksDialog(
            decks = allDecksWithCards,
            onDismiss = { showExportDialog = false },
            onExport = { selectedDecks, format ->
                showExportDialog = false
                decksToExport = selectedDecks
                val dateFormat = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
                val dtFormat = dateFormat.format(Date())

                // Route to the correct launcher based on selection
                when (format) {
                    "CSV" -> {
                        val fileName = context.getString(R.string.output_file_name, dtFormat, "csv")
                        csvExportLauncher.launch(fileName)
                    }
                    "ANKI_APKG" -> ankiExportLauncher.launch("Studiare_Export_${dtFormat}.apkg")
                    "ANKI_COLPKG" -> ankiExportLauncher.launch("Studiare_Export_${dtFormat}.colpkg")
                    else -> jsonExportLauncher.launch("flashcard_decks_${dtFormat}.json")
                }
            }
        )
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(it)

                // Securely extract the filename from the URI to check the extension
                var filename = ""
                contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        filename = cursor.getString(nameIndex)
                    }
                }

                // Route to Anki if it's .apkg, .colpkg, or a zip file
                if (filename.endsWith(".apkg", ignoreCase = true) ||
                    filename.endsWith(".colpkg", ignoreCase = true) ||
                    mimeType == "application/zip" ||
                    (mimeType == "application/octet-stream" && (filename.contains(".apkg") || filename.contains(".colpkg")))
                ) {
                    coroutineScope.launch {
                        pendingImportUri = it
                        val analysisList = viewModel.analyzeAnkiPackage(context, it)

                        val decksToMap = mutableListOf<Pair<String, List<Pair<String, net.ericclark.studiare.data.MediaType>>>>()
                        val autoMappedConfigs = mutableListOf<net.ericclark.studiare.screens.AnkiMappingConfig>()

                        for ((deckName, fields) in analysisList) {
                            val hasStandardFields = fields.size == 2 &&
                                    fields.any { f -> f.first.equals("Front", true) || f.first.equals("Question", true) } &&
                                    fields.any { f -> f.first.equals("Back", true) || f.first.equals("Answer", true) }

                            // If it's complex or weirdly named, add to the UI dialog queue
                            if (fields.size > 2 || (!hasStandardFields && fields.isNotEmpty())) {
                                decksToMap.add(Pair(deckName, fields))
                            } else if (fields.isNotEmpty()) {
                                // Auto-map perfectly standard 2-field decks silently
                                val mapping = mutableMapOf<net.ericclark.studiare.screens.MapperDestination, List<net.ericclark.studiare.screens.MapperItem>>()
                                fields.forEach { (text, type) ->
                                    val dest = if (text.equals("Front", true) || text.equals("Question", true)) net.ericclark.studiare.screens.MapperDestination.FRONT else net.ericclark.studiare.screens.MapperDestination.BACK
                                    val list = mapping.getOrPut(dest) { mutableListOf() } as MutableList<net.ericclark.studiare.screens.MapperItem>
                                    list.add(net.ericclark.studiare.screens.MapperItem(text = text, type = type, destination = dest))
                                }
                                autoMappedConfigs.add(net.ericclark.studiare.screens.AnkiMappingConfig(
                                    originalAnkiName = deckName,
                                    deckName = deckName.split("::").last().trim(),
                                    mapping = mapping
                                ))
                            }
                        }

                        if (decksToMap.isNotEmpty()) {
                            pendingAnkiDecks = decksToMap
                            currentAnkiDeckIndex = 0
                            completedAnkiConfigs = autoMappedConfigs
                            showAnkiMapper = true
                        } else {
                            // All decks were standard, import immediately
                            viewModel.importFromAnkiPackage(context, it, autoMappedConfigs.takeIf { c -> c.isNotEmpty() })
                            pendingImportUri = null
                        }
                    }
                } else {
                    // Standard JSON/CSV processing
                    try {
                        val content = contentResolver.openInputStream(it)?.bufferedReader().use { reader -> reader?.readText() }
                        if (!content.isNullOrBlank()) {
                            viewModel.importDecksFromString(content, mimeType)
                        }
                    } catch (e: Exception) {
                        AppLogger.e("DeckListScreen", "Failed to read import file", e)
                    }
                }
            }
        }
    )

    val tooltipState = rememberTooltipState()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    LaunchedEffect(viewModel.importError) {
        viewModel.importError?.let { error ->
            val result = snackbarHostState.showSnackbar(
                message = "Import failed",
                actionLabel = "Copy Error",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(error))
            }
            viewModel.clearImportError()
        }
    }

    // --- UI Structure ---
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.studiare_solid),
                        contentDescription = getText(R.string.app_logo),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .padding(start = 12.dp)
                    )
                },
                title = {
                    Text(
                        getText(R.string.decks_all),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                positioning = androidx.compose.material3.TooltipAnchorPosition.Below
                            ),
                            tooltip = {
                                PlainTooltip {
                                    Text(getText(R.string.sort_decks))
                                }
                            },
                            state = tooltipState
                        ) {
                            IconButton(
                                onClick = { showSortDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = getText(R.string.sort_decks)
                                )
                            }
                        }

                        IconButton(onClick = {
                            importLauncher.launch(arrayOf("*/*"))
                        }) {
                            Icon(Icons.Default.Download, contentDescription = getText(R.string.decks_import))
                        }
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.Upload, contentDescription = getText(R.string.decks_export))
                        }
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = getText(R.string.settings))
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.options_more))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.sort_decks)) },
                                    leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                                    onClick = {
                                        showSortDialog = true
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.decks_import)) },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = {
                                        importLauncher.launch(arrayOf("*/*"))
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.decks_export)) },
                                    leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                    onClick = {
                                        showExportDialog = true
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.settings)) },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = { navController.navigate("settings"); showMenu = false }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            val fabInteractionSource = remember { MutableInteractionSource() }
            val isFabPressed by fabInteractionSource.collectIsPressedAsState()
            val fabScale by animateFloatAsState(
                targetValue = if (isFabPressed) 0.85f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "fabSquish"
            )

            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { navController.navigate("deckEditor") },
                interactionSource = fabInteractionSource,
                modifier = Modifier.scale(fabScale),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // M3 Expressive prefers highly rounded pill shapes
                icon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = getText(R.string.deck_create), // Screen readers will read the text instead
                        modifier = Modifier.size(24.dp)
                    )
                },
                text = {
                    Text(
                        text = getText(R.string.deck_create),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            AnimatedContent(
                targetState = when {
                    viewModel.isLoading -> 0
                    deckGroups.isEmpty() -> 1
                    else -> 2
                },
                transitionSpec = {
                    (slideInVertically(animationSpec = androidx.compose.animation.core.tween(800)) +
                            fadeIn(animationSpec = androidx.compose.animation.core.tween(800)) +
                            expandVertically(animationSpec = androidx.compose.animation.core.tween(800))).togetherWith(
                        slideOutVertically(animationSpec = androidx.compose.animation.core.tween(800)) +
                                fadeOut(animationSpec = androidx.compose.animation.core.tween(800)) +
                                shrinkVertically(animationSpec = androidx.compose.animation.core.tween(800))
                    )
                },
                label = "mainScreenTransition"
            ) { targetState ->
                when (targetState) {
                    1 -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                                Spacer(Modifier.height(16.dp))
                                Text(getText(R.string.no_decks_yet), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
                                Text(getText(R.string.create_or_import_to_start), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    0 -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                    2 -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            // Apply Spacing Mode + Ensure bottom padding for FAB (100.dp)
                            contentPadding = PaddingValues(
                                start = dimensions.paddingLarge,
                                end = dimensions.paddingLarge,
                                top = dimensions.paddingLarge,
                                bottom = 120.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(dimensions.spacingLarge),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingLarge)
                        ) {
                            items(deckGroups) { (mainDeck, sets) ->
                                Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                                    DeckListItem(
                                        deck = mainDeck,
                                        dimensions = dimensions,
                                        setsCount = sets.size,
                                        onStudy = { autoOpen ->
                                            val route = if (autoOpen != null) "studyModeSelection/${mainDeck.deck.id}?autoOpen=$autoOpen" else "studyModeSelection/${mainDeck.deck.id}"
                                            if (mainDeck.totalCards > 0) navController.navigate(route)
                                        },
                                        onEdit = { navController.navigate("deckEditor?deckId=${mainDeck.deck.id}") },
                                        onDelete = { showDeleteDialog = mainDeck },
                                        onManageSets = { navController.navigate("setManager/${mainDeck.deck.id}") }
                                    )

                                    // Only show sets here if preference is enabled
                                    AnimatedVisibility(
                                        visible = sets.isNotEmpty() && displaySetsUnderDecks,
                                        enter = slideInVertically() + fadeIn() + expandVertically(),
                                        exit = slideOutVertically() + fadeOut() + shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = dimensions.paddingSmall) // Slight indent
                                        ) {
                                            val listState = rememberLazyListState()

                                            LazyRow(
                                                state = listState,
                                                horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)//,
                                                //contentPadding = PaddingValues(bottom = 8.dp)
                                            ) {
                                                items(sets) { set ->
                                                    SetListItem(
                                                        deck = set,
                                                        dimensions = dimensions,
                                                        onStudy = { autoOpen ->
                                                            val route = if (autoOpen != null) "studyModeSelection/${set.deck.id}?autoOpen=$autoOpen" else "studyModeSelection/${set.deck.id}"
                                                            if (set.totalCards > 0) navController.navigate(route)
                                                        }
                                                    )
                                                }
                                            }

                                            if (sets.size > 1) {
                                                val currentIndex by remember {
                                                    derivedStateOf {
                                                        val layoutInfo = listState.layoutInfo
                                                        val visibleItemsInfo = layoutInfo.visibleItemsInfo
                                                        if (visibleItemsInfo.isEmpty()) {
                                                            0
                                                        } else {
                                                            val viewportStart = layoutInfo.viewportStartOffset
                                                            val viewportEnd = layoutInfo.viewportEndOffset
                                                            val viewportCenter = viewportStart + (viewportEnd - viewportStart) / 2
                                                            visibleItemsInfo.minByOrNull {
                                                                kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
                                                            }?.index ?: 0
                                                        }
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingSmall),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    sets.indices.forEach { index ->
                                                        val isSelected = index == currentIndex
                                                        val width by androidx.compose.animation.core.animateDpAsState(
                                                            targetValue = if (isSelected) 24.dp else 8.dp,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            ),
                                                            label = "dotWidth"
                                                        )
                                                        val color by androidx.compose.animation.animateColorAsState(
                                                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                            label = "dotColor"
                                                        )

                                                        Box(
                                                            modifier = Modifier
                                                                .padding(horizontal = 4.dp)
                                                                .size(width = width, height = 8.dp)
                                                                .clip(CircleShape)
                                                                .background(color)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { deckToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
            title = { Text(getText(R.string.delete_deck_question)) },
            text = { Text(stringResource(R.string.delete_deck_confirm, deckToDelete.deck.name)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteDeck(deckToDelete.deck.id); showDeleteDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(getText(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(getText(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun DeckListItem(
    deck: DeckSummary,
    dimensions: StudiareDimensions,
    setsCount: Int,
    onStudy: (String?) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageSets: () -> Unit,
    onToggleStar: (() -> Unit)? = null,
    showManageSetsButton: Boolean = true
) {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation, pressedElevation = 8.dp),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.deck.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    SuggestionChip(
                        onClick = { },
                        label = { Text(stringResource(R.string.cards_count, deck.totalCards)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = null
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showManageSetsButton) {
                        // Changed to display number of sets
                        val manageInteractionSource = remember { MutableInteractionSource() }
                        val isManagePressed by manageInteractionSource.collectIsPressedAsState()
                        val manageScale by animateFloatAsState(
                            targetValue = if (isManagePressed) 0.95f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "manageSquish"
                        )
                        TextButton(
                            onClick = onManageSets,
                            interactionSource = manageInteractionSource,
                            modifier = Modifier.scale(manageScale),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.AccountTree, getText(R.string.manage_sets), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sets_count_simple, setsCount), color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (onToggleStar != null) {
                        val starInteractionSource = remember { MutableInteractionSource() }
                        val isStarPressed by starInteractionSource.collectIsPressedAsState()
                        val starScale by animateFloatAsState(
                            targetValue = if (isStarPressed) 0.85f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "starSquish"
                        )
                        val starTint by animateColorAsState(
                            targetValue = if (deck.deck.isStarred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "starColor"
                        )
                        IconButton(
                            onClick = onToggleStar,
                            interactionSource = starInteractionSource,
                            modifier = Modifier.scale(starScale)
                        ) {
                            Icon(
                                imageVector = if (deck.deck.isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = if (deck.deck.isStarred) getText(R.string.unstar_set) else getText(R.string.star_set),
                                tint = starTint
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(dimensions.paddingLarge))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val editInteractionSource = remember { MutableInteractionSource() }
                val isEditPressed by editInteractionSource.collectIsPressedAsState()
                val editScale by animateFloatAsState(
                    targetValue = if (isEditPressed) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "editSquish"
                )
                IconButton(onClick = onEdit, interactionSource = editInteractionSource, modifier = Modifier.scale(editScale)) {
                    Icon(Icons.Default.Edit, getText(R.string.edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val deleteInteractionSource = remember { MutableInteractionSource() }
                val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()
                val deleteScale by animateFloatAsState(
                    targetValue = if (isDeletePressed) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "deleteSquish"
                )
                IconButton(onClick = onDelete, interactionSource = deleteInteractionSource, modifier = Modifier.scale(deleteScale)) {
                    Icon(Icons.Default.Delete, getText(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(dimensions.spacingSmall))
                StudySplitButton(
                    onStudyMain = { onStudy(null) },
                    onStudyOption = { onStudy(it) },
                    enabled = deck.totalCards > 0
                )
                /*
                val studyInteractionSource = remember { MutableInteractionSource() }
                val isStudyPressed by studyInteractionSource.collectIsPressedAsState()
                val studyScale by animateFloatAsState(
                    targetValue = if (isStudyPressed) 0.95f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "studySquish"
                )
                Button(
                    onClick = onStudy,
                    interactionSource = studyInteractionSource,
                    modifier = Modifier.scale(studyScale),
                    enabled = deck.cards.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(getText(R.string.study))
                }
                */
            }
        }
    }
}

@Composable
fun SetListItem(
    deck: DeckSummary,
    dimensions: StudiareDimensions,
    onStudy: (String?) -> Unit
) {
    Card(
        modifier = Modifier.width(190.dp).height(160.dp), // Give it a fixed height to make it a square tile
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensions.paddingMedium)
        ) {
            Column {
                Text(
                    deck.deck.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.cards_count_lowercase, deck.totalCards),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // REPLACED: Normal Button with the new Split Button
                StudySplitButton(
                    onStudyMain = { onStudy(null) },
                    onStudyOption = { onStudy(it) },
                    enabled = deck.totalCards > 0
                )
            }

            // This Box fills the rest of the height, floating the button perfectly in the middle
            /*
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val studyInteractionSource = remember { MutableInteractionSource() }
                val isStudyPressed by studyInteractionSource.collectIsPressedAsState()
                val studyScale by animateFloatAsState(
                    targetValue = if (isStudyPressed) 0.95f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "studySquish"
                )

                Button(
                    onClick = onStudy,
                    interactionSource = studyInteractionSource,
                    enabled = deck.cards.isNotEmpty(),
                    modifier = Modifier.scale(studyScale),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(getText(R.string.study))
                }
            }
            */
        }
    }
}

@Composable
fun LoadingOverlay(message: String? = null) {
    val displayMessage = message ?: getText(R.string.processing)
    Dialog(onDismissRequest = { }) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = displayMessage, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun ImportOverwriteDialog(
    decksToOverwrite: List<Deck>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val selectedDeckIds = remember { mutableStateListOf(*decksToOverwrite.map { it.id }.toTypedArray()) }
    val deckGroups = remember(decksToOverwrite) {
        val naturalOrderComparator = Comparator<String> { s1, s2 ->
            val regex = Regex("\\d+|\\D+")
            val matches1 = regex.findAll(s1).map { it.value }.toList()
            val matches2 = regex.findAll(s2).map { it.value }.toList()

            for (i in 0 until minOf(matches1.size, matches2.size)) {
                val m1 = matches1[i]
                val m2 = matches2[i]
                if (m1 != m2) {
                    val n1 = m1.toLongOrNull()
                    val n2 = m2.toLongOrNull()
                    if (n1 != null && n2 != null) {
                        return@Comparator n1.compareTo(n2)
                    }
                    return@Comparator m1.compareTo(m2, ignoreCase = true)
                }
            }
            matches1.size.compareTo(matches2.size)
        }
        val mainDecks = decksToOverwrite.filter { it.parentDeckId == null }.sortedBy { it.name }
        val setsByParentId = decksToOverwrite.filter { it.parentDeckId != null }.groupBy { it.parentDeckId!! }
        val setComparator = Comparator<Deck> { d1, d2 -> naturalOrderComparator.compare(d1.name, d2.name) }
        mainDecks.map { mainDeck -> mainDeck to (setsByParentId[mainDeck.id]?.sortedWith(setComparator) ?: emptyList()) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getText(R.string.overwrite_existing)) },
        text = {
            Column {
                Text(getText(R.string.select_decks_to_overwrite), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    deckGroups.forEach { (mainDeck, sets) ->
                        item(key = mainDeck.id) {
                            OverwriteDeckItem(
                                deck = mainDeck,
                                isSelected = mainDeck.id in selectedDeckIds,
                                onToggle = { if (mainDeck.id in selectedDeckIds) selectedDeckIds.remove(mainDeck.id) else selectedDeckIds.add(mainDeck.id) }
                            )
                        }
                        items(sets, key = { it.id }) { set ->
                            OverwriteDeckItem(
                                deck = set,
                                isSelected = set.id in selectedDeckIds,
                                onToggle = { if (set.id in selectedDeckIds) selectedDeckIds.remove(set.id) else selectedDeckIds.add(set.id) },
                                isSet = true
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(selectedDeckIds.toList()) }) { Text(getText(R.string.overwrite_selected)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) } }
    )
}

@Composable
private fun OverwriteDeckItem(
    deck: Deck,
    isSelected: Boolean,
    onToggle: () -> Unit,
    isSet: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tileSquish"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onToggle() }
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .padding(start = if (isSet) 24.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = null)
        Spacer(Modifier.width(16.dp))
        Text(deck.name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun TopSliderDialogSection(options: List<String>, selectedMode: String, onModeChange: (String) -> Unit) {
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(4.dp)
    ) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = { onModeChange(mode) },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(text = mode, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}


@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}

@Composable
fun DuplicateWarningDialog(
    result: DuplicateCheckResult,
    onDismiss: () -> Unit,
    onConfirmRemove: () -> Unit,
    onConfirmSaveAnyway: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getText(R.string.duplicates_found)) },
        text = {
            Column {
                Text(stringResource(R.string.duplicates_found_message, result.deckName))
                Spacer(Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(result.duplicates) { duplicate ->
                        Text(stringResource(R.string.duplicate_item_format, duplicate.text, duplicate.count), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirmRemove) { Text(getText(R.string.remove_and_save)) } },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onConfirmSaveAnyway) { Text(getText(R.string.save_anyway)) }
                TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
            }
        }
    )
}

@Composable
fun DeckSortDialog(
    currentSortMode: DeckSortMode,
    onDismiss: () -> Unit,
    onSortModeSelected: (DeckSortMode) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current

    // Filter out the difficulty sort options for Decks
    val options = listOf(
        DeckSortMode.A_TO_Z,
        DeckSortMode.Z_TO_A,
        DeckSortMode.DATE_ADDED_NEW_TO_OLD,
        DeckSortMode.DATE_ADDED_OLD_TO_NEW,
        DeckSortMode.DATE_MODIFIED_NEW_TO_OLD,
        DeckSortMode.DATE_MODIFIED_OLD_TO_NEW
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getText(R.string.sort_decks)) },
        text = {
            Column {
                options.forEach { mode ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "sortRowSquish"
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .scale(scale)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current
                            ) {
                                onSortModeSelected(mode)
                                onDismiss()
                            }
                            .padding(vertical = dimensions.spacingSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentSortMode,
                            onClick = {
                                onSortModeSelected(mode)
                                onDismiss()
                            }
                        )
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Text(mode.asString())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(getText(R.string.cancel))
            }
        }
    )
}

@Composable
fun StudySplitButton(
    onStudyMain: () -> Unit,
    onStudyOption: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    includeText: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val dimensions = LocalStudiareDimensions.current

    // 1. Create the asymmetric shape for the Left (Leading) button
    val leadingShape = RoundedCornerShape(
        topStart = dimensions.cornerRadiusLarge,
        bottomStart = dimensions.cornerRadiusLarge,
        topEnd = 0.dp,
        bottomEnd = 0.dp
    )

    // 2. Create the asymmetric shape for the Right (Trailing) button
    val trailingShape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = dimensions.cornerRadiusLarge,
        bottomEnd = dimensions.cornerRadiusLarge
    )

    Box(modifier = modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onStudyMain,
                    // THE FIX: Wrap the shape in the required SplitButtonShapes object
                    shapes = androidx.compose.material3.SplitButtonShapes(
                        shape = leadingShape,
                        pressedShape = leadingShape,
                        checkedShape = leadingShape
                    ),
                    modifier = if (!includeText) Modifier.width(64.dp) else Modifier
                ) {
                    if (includeText) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(getText(R.string.study), fontWeight = FontWeight.Bold)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    // THE FIX: Wrap the shape in the required SplitButtonShapes object
                    shapes = androidx.compose.material3.SplitButtonShapes(
                        shape = trailingShape,
                        pressedShape = trailingShape,
                        checkedShape = trailingShape
                    )
                ) {
                    val rotation by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        label = "Trailing Icon Rotation"
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = getText(R.string.options_more),
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
        ) {
            DropdownMenuItem(
                text = { Text(getText(R.string.preset_practice)) },
                leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                onClick = { expanded = false; onStudyOption("study") }
            )
            DropdownMenuItem(
                text = { Text(getText(R.string.preset_quiz)) },
                leadingIcon = { Icon(Icons.Default.Quiz, contentDescription = null) },
                onClick = { expanded = false; onStudyOption("quiz") }
            )
            DropdownMenuItem(
                text = { Text(getText(R.string.preset_game)) },
                leadingIcon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
                onClick = { expanded = false; onStudyOption("game") }
            )
            DropdownMenuItem(
                text = { Text(getText(R.string.spaced_repetition_label)) },
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                onClick = { expanded = false; onStudyOption("fsrs") }
            )
        }
    }
}