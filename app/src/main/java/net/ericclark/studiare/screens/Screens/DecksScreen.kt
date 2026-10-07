package net.ericclark.studiare.screens.Screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.data.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.MutableTransitionState
import kotlinx.coroutines.delay
import net.ericclark.studiare.screens.Dialogs.AnkiFieldMappingDialog
import net.ericclark.studiare.screens.Dialogs.AnkiMappingConfig
import net.ericclark.studiare.screens.Dialogs.DeckSortDialog
import net.ericclark.studiare.screens.Dialogs.DuplicateWarningDialog
import net.ericclark.studiare.screens.Dialogs.ExportDecksDialog
import net.ericclark.studiare.screens.Dialogs.ImportOverwriteDialog
import net.ericclark.studiare.screens.Dialogs.MapperDestination
import net.ericclark.studiare.screens.Dialogs.MapperItem
import net.ericclark.studiare.screens.UI_Components.DeckGridContent
import net.ericclark.studiare.screens.UI_Components.DeckHierarchyTree
import net.ericclark.studiare.screens.UI_Components.DeckSkeletonLoader
import net.ericclark.studiare.screens.UI_Components.LoadingOverlay
import net.ericclark.studiare.screens.UI_Components.shouldUseFlowLayout

enum class DeckViewMode { GRID, TREE }

/**
 * The main screen of the app, redesigned with Material 3 Expressive principles.
 * Features bolder shapes (28dp corners), large FABs, and elevated card hierarchies.
 */
@OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun DeckListScreen(
    navController: NavController,
    deckGroups: List<Pair<DeckSummary, List<DeckSummary>>>,
    viewModel: FlashcardViewModel
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val windowHeightSizeClass = LocalWindowHeightSizeClass.current

    // State for managing dialogs and menus
    var activePaneChrome by remember { mutableStateOf(PaneChrome()) }
    var showDeleteDialog by remember { mutableStateOf<DeckSummary?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    val currentViewModeInt by viewModel.deckViewMode.collectAsState()
    val currentViewMode = if (currentViewModeInt == 1) DeckViewMode.TREE else DeckViewMode.GRID

    // State for Anki Mapping
    var showAnkiMapper by remember { mutableStateOf(false) }
    var pendingAnkiDecks by remember {
        mutableStateOf<List<Pair<String, List<Pair<String, net.ericclark.studiare.data.MediaType>>>>>(
            emptyList()
        )
    }
    var currentAnkiDeckIndex by remember { mutableStateOf(0) }
    var completedAnkiConfigs by remember {
        mutableStateOf<List<net.ericclark.studiare.screens.Dialogs.AnkiMappingConfig>>(
            emptyList()
        )
    }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var totalDecksNeedingMapping by remember { mutableIntStateOf(0) }
    var subDecksDetectedCount by remember { mutableIntStateOf(0) }
    var decksSkippedMappingCount by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    var isLocalProcessing by remember { mutableStateOf(false) }
    var showDelayedLoading by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.isProcessing, isLocalProcessing) {
        if (viewModel.isProcessing || isLocalProcessing) {
            kotlinx.coroutines.delay(1000)
            showDelayedLoading = true
        } else {
            showDelayedLoading = false
        }
    }

    // Collection States
    var showCollectionDialog by remember { mutableStateOf(false) }
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsState()
    val allCollections by viewModel.allCollectionsWithDecks.collectAsState()

    val deckSortMode by viewModel.deckSortMode.collectAsState()

    // State for theme and data
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val importDuplicateQueue by viewModel.importDuplicateQueue.collectAsState()
    val overwriteConfirmation by viewModel.overwriteConfirmation.collectAsState()

    // Customization States
    val spacingMode by viewModel.spacingMode.collectAsState()
    val storedDeckSetsDisplayMode by viewModel.deckSetsDisplayMode.collectAsState()
    val deckSetsDisplayMode = DeckSetsDisplayMode.resolve(storedDeckSetsDisplayMode, windowWidthSizeClass, windowHeightSizeClass)
    val displaySetsUnderDecks = deckSetsDisplayMode != DeckSetsDisplayMode.OFF
    val gridLargeScreenLayout = deckSetsDisplayMode == DeckSetsDisplayMode.BESIDE_DECKS
    val storedTreeLargeScreenLayout by viewModel.treeLargeScreenLayout.collectAsState()
    val treeLargeScreenLayout = DeckSetsDisplayMode.resolveTreeDirection(storedTreeLargeScreenLayout, windowWidthSizeClass, windowHeightSizeClass)
    val gridLoadingIndicator by viewModel.gridLoadingIndicator.collectAsState()
    val treeLoadingIndicator by viewModel.treeLoadingIndicator.collectAsState()
    val deckSetCountsSnapshot by viewModel.deckSetCountsSnapshot.collectAsState()

    // Map spacing mode to Dimensions
    val dimensions = LocalStudiareDimensions.current

    var decksToExport by remember { mutableStateOf<List<DeckWithCards>?>(null) }
    var exportIncludeMetadata by remember { mutableStateOf(true) }

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
            currentDeckMappingIndex = currentAnkiDeckIndex + 1,
            totalDecksToMap = totalDecksNeedingMapping,
            subDecksDetected = subDecksDetectedCount,
            decksSkippedMapping = decksSkippedMappingCount,
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

    if (showDelayedLoading) {
        LoadingOverlay("Processing...")
    } else if (viewModel.isProcessing) {
        LoadingOverlay()
    }

    // --- Export Logic ---
    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            uri?.let {
                decksToExport?.let { decks ->
                    val content = viewModel.getDecksAsString(
                        decks,
                        "JSON",
                        exportIncludeMetadata
                    ) // ADDED PARAM
                    context.contentResolver.openOutputStream(it)
                        ?.use { stream -> stream.write(content.toByteArray()) }
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
                    val content = viewModel.getDecksAsString(
                        decks,
                        "CSV",
                        exportIncludeMetadata
                    ) // ADDED PARAM
                    context.contentResolver.openOutputStream(it)
                        ?.use { stream -> stream.write(content.toByteArray()) }
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
                    viewModel.exportToAnkiPackage(
                        context,
                        decks,
                        it,
                        exportIncludeMetadata
                    ) // ADDED PARAM
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
            onExport = { selectedDecks, format, includeMetadata -> // NEW PARAM
                showExportDialog = false
                decksToExport = selectedDecks
                exportIncludeMetadata = includeMetadata // SAVE STATE
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

    if (showCollectionDialog) {
        CollectionPickerDialog(
            selectedCollectionId = selectedCollectionId,
            allCollections = allCollections,
            onSelectCollection = { collectionId ->
                viewModel.selectCollection(collectionId)
                showCollectionDialog = false
            },
            onEditCollections = {
                showCollectionDialog = false
                navController.navigate("collectionManager")
            },
            onDismiss = { showCollectionDialog = false }
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
                    val nameIndex =
                        cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        filename = cursor.getString(nameIndex)
                    }
                }

                // Route to Anki if it's .apkg, .colpkg, or a zip file
                if (filename.endsWith(".apkg", ignoreCase = true) ||
                    filename.endsWith(".colpkg", ignoreCase = true) ||
                    mimeType == "application/zip" ||
                    (mimeType == "application/octet-stream" && (filename.contains(".apkg") || filename.contains(
                        ".colpkg"
                    )))
                ) {
                    coroutineScope.launch {
                        isLocalProcessing = true
                        pendingImportUri = it
                        val analysisList = viewModel.analyzeAnkiPackage(context, it)

                        val decksToMap =
                            mutableListOf<Pair<String, List<Pair<String, net.ericclark.studiare.data.MediaType>>>>()
                        val autoMappedConfigs =
                            mutableListOf<net.ericclark.studiare.screens.Dialogs.AnkiMappingConfig>()
                        var subDecks = 0

                        val groupedByRoot = analysisList.groupBy { it.first.split("::").first() }

                        for ((rootName, deckEntries) in groupedByRoot) {
                            val subdecksInRoot = deckEntries.count { it.first.contains("::") }
                            subDecks += subdecksInRoot

                            val combinedFields =
                                deckEntries.flatMap { it.second }.distinctBy { it.first }

                            val hasStandardFields = combinedFields.size == 2 &&
                                    combinedFields.any { f ->
                                        f.first.equals(
                                            "Front",
                                            true
                                        ) || f.first.equals("Question", true)
                                    } &&
                                    combinedFields.any { f ->
                                        f.first.equals(
                                            "Back",
                                            true
                                        ) || f.first.equals("Answer", true)
                                    }

                            if (combinedFields.size > 2 || (!hasStandardFields && combinedFields.isNotEmpty())) {
                                decksToMap.add(Pair(rootName, combinedFields))
                            } else if (combinedFields.isNotEmpty()) {
                                val mapping =
                                    mutableMapOf<net.ericclark.studiare.screens.Dialogs.MapperDestination, List<net.ericclark.studiare.screens.Dialogs.MapperItem>>()
                                combinedFields.forEach { (text, type) ->
                                    val dest = if (text.equals("Front", true) || text.equals(
                                            "Question",
                                            true
                                        )
                                    ) net.ericclark.studiare.screens.Dialogs.MapperDestination.FRONT else net.ericclark.studiare.screens.Dialogs.MapperDestination.BACK
                                    val list =
                                        mapping.getOrPut(dest) { mutableListOf() } as MutableList<net.ericclark.studiare.screens.Dialogs.MapperItem>
                                    list.add(
                                        net.ericclark.studiare.screens.Dialogs.MapperItem(
                                            text = text,
                                            type = type,
                                            destination = dest
                                        )
                                    )
                                }
                                autoMappedConfigs.add(
                                    net.ericclark.studiare.screens.Dialogs.AnkiMappingConfig(
                                        originalAnkiName = rootName,
                                        deckName = rootName,
                                        mapping = mapping
                                    )
                                )
                            }
                        }

                        if (decksToMap.isNotEmpty()) {
                            pendingAnkiDecks = decksToMap
                            totalDecksNeedingMapping = decksToMap.size
                            subDecksDetectedCount = subDecks
                            decksSkippedMappingCount = autoMappedConfigs.size
                            currentAnkiDeckIndex = 0
                            completedAnkiConfigs = autoMappedConfigs
                            isLocalProcessing = false
                            showAnkiMapper = true
                        } else {
                            // All decks were standard, import immediately
                            isLocalProcessing = false
                            viewModel.importFromAnkiPackage(
                                context,
                                it,
                                autoMappedConfigs.takeIf { c -> c.isNotEmpty() })
                            pendingImportUri = null
                        }
                    }
                } else {
                    // Standard JSON/CSV processing
                    try {
                        val content = contentResolver.openInputStream(it)?.bufferedReader()
                            .use { reader -> reader?.readText() }
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

    // ── Stable-state guard ────────────────────────────────────────────────────
    // Problem: when the app first opens, viewModel.isLoading flips to false
    // before deckGroups has received its first DB emission.  That one-frame gap
    // causes a visible flash of the "no decks" empty state even when the user
    // has many decks.
    //
    // Fix: We now use the snapshot to guarantee we never flash the empty state
    // if we already know decks exist for this collection.
    // By intelligently initializing this state instead of blindly starting at 0,
    // we prevent the skeleton from flashing when navigating back to this screen
    // (since the cached data from the ViewModel is already present).
    var stableScreenState by remember {
        mutableStateOf(
            if (viewModel.isLoading || selectedCollectionId == "UNINITIALIZED" || deckSetCountsSnapshot == null) {
                0
            } else if (deckGroups.isNotEmpty()) {
                2
            } else if (!deckSetCountsSnapshot.isNullOrEmpty()) {
                0
            } else {
                0 // Start at 0 to allow the LaunchedEffect's 200ms grace period to verify true emptiness
            }
        )
    }
    LaunchedEffect(
        viewModel.isLoading,
        deckGroups.size,
        deckSetCountsSnapshot?.size,
        selectedCollectionId
    ) {
        if (viewModel.isLoading || selectedCollectionId == "UNINITIALIZED" || deckSetCountsSnapshot == null) {
            stableScreenState = 0
        } else if (deckGroups.isNotEmpty()) {
            stableScreenState = 2          // conclusive — switch immediately
        } else if (!deckSetCountsSnapshot.isNullOrEmpty()) {
            // Snapshot says there are decks, but Room hasn't emitted deckGroups yet
            stableScreenState = 0
        } else {
            // Possibly a transient empty before first DB emit; wait and re-check.
            kotlinx.coroutines.delay(200)
            stableScreenState =
                if (deckGroups.isNotEmpty() || !deckSetCountsSnapshot.isNullOrEmpty()) {
                    if (deckGroups.isNotEmpty()) 2 else 0
                } else 1
        }
    }

    // While stableScreenState == 0 we don't yet know for sure whether decks exist —
    // it covers both "loading, but the snapshot says we have some" and "loading, snapshot
    // says empty, quietly confirming before showing the empty state". Only the former
    // should show deck-list chrome (grid/tree toggle, "Create Deck" FAB); otherwise that
    // chrome flashes on screen right before the empty state replaces it.
    val expectDecks = stableScreenState == 2 || !deckSetCountsSnapshot.isNullOrEmpty()

    val focusRequester = remember { FocusRequester() }

    // --- UI Structure ---
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            val paneStackForChrome by viewModel.paneStack.collectAsState()
            if (paneStackForChrome.size > 1) {
                // A deeper pane is active — show its chrome instead of the deck-list chrome.
                CustomTopAppBar(
                    viewModel = viewModel,
                    screenId = activePaneChrome.screenId,
                    title = activePaneChrome.title,
                    navigationIcon = {
                        TooltipIconButton(description = getText(R.string.back), onClick = { viewModel.popPane() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
                        }
                    },
                    actions = activePaneChrome.actions
                )
                return@Scaffold
            }
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.DECKS,
                navigationIcon = {
                    // Hamburger menu removed since the global drawer is gone
                },
                title = {
                    // Tappable collection switcher, used at every width class (the
                    // per-pane collection header was removed).
                    run {
                        val currentCollectionName =
                            if (viewModel.isLoading || selectedCollectionId == "UNINITIALIZED") {
                                ""
                            } else if (selectedCollectionId == null) {
                                getText(R.string.decks_all)
                            } else {
                                allCollections.find { it.collection.id == selectedCollectionId }?.collection?.name
                                    ?: getText(R.string.decks_all)
                            }
                        if (currentCollectionName.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showCollectionDialog = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = currentCollectionName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = getText(R.string.switch_collection),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                        TooltipIconButton(
                            description = getText(R.string.sort_decks),
                            onClick = { showSortDialog = true },
                            modifier = Modifier.withShortcut(Key.A, "A", id = "decks.sort") { showSortDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = getText(R.string.sort_decks)
                            )
                        }
                        TooltipIconButton(
                            description = getText(R.string.decks_import),
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.withShortcut(Key.I, "I", id = "decks.import") { importLauncher.launch(arrayOf("*/*")) }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = getText(R.string.decks_import))
                        }
                        TooltipIconButton(
                            description = getText(R.string.decks_export),
                            onClick = { showExportDialog = true },
                            modifier = Modifier.withShortcut(Key.E, "E", id = "decks.export") { showExportDialog = true }
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = getText(R.string.decks_export))
                        }
                        TooltipIconButton(
                            description = getText(R.string.settings),
                            onClick = { navController.navigate("settings") },
                            modifier = Modifier.withShortcut(Key.S, "S", id = "decks.settings") { navController.navigate("settings") }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = getText(R.string.settings))
                        }
                    } else {
                        Box {
                            TooltipIconButton(description = getText(R.string.options_more), onClick = { showMenu = !showMenu }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = getText(R.string.options_more)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(16.dp)
                                )
                            ) {
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.sort_decks)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Sort,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showSortDialog = true
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.decks_import)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        importLauncher.launch(arrayOf("*/*"))
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.decks_export)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Upload,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showExportDialog = true
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.settings)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        navController.navigate("settings"); showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        val createDeckKey = resolveShortcutKey(LocalShortcutRemaps.current, "decks.create_new", Key.N)
        Column(
            modifier = Modifier
                .padding(padding)
                .autoFocusable(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp) {
                        when {
                            event.key == createDeckKey -> {
                                navController.navigate("deckEditor")
                                return@onPreviewKeyEvent true
                            }

                            (event.isCtrlPressed && event.key == Key.F) || event.key == Key.Slash -> {
                                // Focus search bar when implemented in the future
                                return@onPreviewKeyEvent true
                            }

                            event.isAltPressed -> {
                                val num = when (event.key) {
                                    Key.One, Key.NumPad1 -> 0
                                    Key.Two, Key.NumPad2 -> 1
                                    Key.Three, Key.NumPad3 -> 2
                                    Key.Four, Key.NumPad4 -> 3
                                    Key.Five, Key.NumPad5 -> 4
                                    Key.Six, Key.NumPad6 -> 5
                                    Key.Seven, Key.NumPad7 -> 6
                                    Key.Eight, Key.NumPad8 -> 7
                                    Key.Nine, Key.NumPad9 -> 8
                                    else -> -1
                                }
                                if (num in deckGroups.indices) {
                                    val deckId = deckGroups[num].first.deck.id
                                    navController.navigate("studyModeSelection/$deckId")
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                    }
                    false
                }
        ) {

            // ─────────────────────────────────────────────────────────────────────────

            // ── Three-layer overlay ───────────────────────────────────────────────────
            // Bottom → top stacking order:
            //   1. Deck grid/tree  — always composed, even during loading (deckGroups is empty
            //                   then so it's free). Pre-measuring means cards are ready the
            //                   instant the skeleton clears — no gap.
            //   2. Empty state — fades independently of the other layers.
            //   3. Skeleton   — starts opaque, fades OUT with EnterTransition.None so it can
            //                   never accidentally flash back in on recomposition.
            // Collected here (in addition to Layer 1's own copy below) so Layer 3's skeleton can
            // also tell whether a second pane is open, without needing Layer 1's scope.
            val skeletonPaneStack by viewModel.paneStack.collectAsState()
            Box(modifier = Modifier.fillMaxSize()) {

                // ── Layer 1: real deck layout ─────────────────────────────────────────
                if (stableScreenState != 1) {
                    val selectedDeckId by viewModel.currentDeckId.collectAsState()
                    val selectedSetId by viewModel.currentSetId.collectAsState()

                    val paneStack by viewModel.paneStack.collectAsState()

                    val visibleStack = paneStack.takeLast(2) // placeholder, replaced below

                    // One back-handler pops the deepest layer, regardless of what it is.
                    androidx.activity.compose.BackHandler(enabled = paneStack.size > 1) {
                        viewModel.popPane()
                    }

                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        val minPaneWidth = 320.dp // matches DeckGridContent's own GridCells.Adaptive minSize
                        // Multiple panes only from the desktop width up; below it, one pane fills the screen
                        val windowWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                        val maxVisiblePanes = if (windowWidthDp >= DESKTOP_MIN_WIDTH_DP) (maxWidth / minPaneWidth).toInt().coerceIn(1, 3) else 1
                        val visibleStack = paneStack.takeLast(maxVisiblePanes)

                        // Explicit, animated widths instead of Modifier.weight(1f): weight changes
                        // snap instantly, so opening/closing a pane used to make every *other*,
                        // already-visible pane jump to its new width in one frame while only the
                        // pane actually entering/exiting got a smooth transition. Same fix pattern
                        // as the tree's Miller-column widths in NavigationDrawer.kt.
                        val motionScheme = MaterialTheme.motionScheme
                        val dividerCount = (visibleStack.size - 1).coerceAtLeast(0)
                        val targetPaneWidth = (maxWidth - androidx.compose.material3.DividerDefaults.Thickness * dividerCount) / visibleStack.size.coerceAtLeast(1)
                        val paneWidth by androidx.compose.animation.core.animateDpAsState(
                            targetValue = targetPaneWidth,
                            animationSpec = motionScheme.defaultSpatialSpec(),
                            label = "paneWidth"
                        )

                        Row(modifier = Modifier.fillMaxSize()) {
                        visibleStack.forEachIndexed { index, dest ->
                            key(dest.paneKey) {
                                if (index > 0) {
                                    androidx.compose.material3.VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                val paneVisibleState = remember(dest.paneKey) {
                                    androidx.compose.animation.core.MutableTransitionState(false)
                                }.apply { targetState = true }

                                androidx.compose.animation.AnimatedVisibility(
                                    visibleState = paneVisibleState,
                                    modifier = Modifier.width(paneWidth),
                                    enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                            slideInHorizontally(
                                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                                initialOffsetX = { fullWidth -> fullWidth / 4 }
                                            ),
                                    exit = fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                            slideOutHorizontally(
                                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                                targetOffsetX = { fullWidth -> -fullWidth / 4 }
                                            )
                                ) {
                                    Box(Modifier.fillMaxSize()) {
                                        when (dest) {
                                            is net.ericclark.studiare.PaneDestination.DeckList -> {
                                                // Pane-1 content: the grid/tree toggle now lives
                                                // *inside* this Box, so it's scoped to this pane
                                                // only instead of stretching across the Row.
                                                Box(Modifier.fillMaxSize()) {
                                                    Column(Modifier.fillMaxSize()) {
                                                        if (!viewModel.isLoading && expectDecks) {
                                                            SingleChoiceSegmentedButtonRow(
                                                                modifier = Modifier
                                                                    .widthIn(max = 480.dp)
                                                                    .fillMaxWidth()
                                                                    .align(Alignment.CenterHorizontally)
                                                                    .padding(
                                                                        horizontal = dimensions.paddingLarge,
                                                                        vertical = 8.dp
                                                                    )
                                                            ) {
                                                                SegmentedButton(
                                                                    selected = currentViewMode == DeckViewMode.GRID,
                                                                    onClick = {
                                                                        viewModel.setDeckViewMode(
                                                                            0
                                                                        )
                                                                    },
                                                                    shape = SegmentedButtonDefaults.itemShape(
                                                                        index = 0,
                                                                        count = 2
                                                                    )
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.GridView,
                                                                        contentDescription = getText(R.string.grid_view)
                                                                    )
                                                                }
                                                                SegmentedButton(
                                                                    selected = currentViewMode == DeckViewMode.TREE,
                                                                    onClick = {
                                                                        viewModel.setDeckViewMode(
                                                                            1
                                                                        )
                                                                    },
                                                                    shape = SegmentedButtonDefaults.itemShape(
                                                                        index = 1,
                                                                        count = 2
                                                                    )
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.AccountTree,
                                                                        contentDescription = getText(R.string.tree_view)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Box(Modifier.weight(1f)) {
                                                            if (currentViewMode == DeckViewMode.GRID) {
                                                                DeckGridContent(
                                                                    deckGroups,
                                                                    dimensions,
                                                                    navController,
                                                                    viewModel,
                                                                    displaySetsUnderDecks,
                                                                    onDeleteRequested = { showDeleteDialog = it },
                                                                    useFlowLayout = shouldUseFlowLayout(gridLargeScreenLayout, windowWidthSizeClass, visibleStack.size),
                                                                    // The *settled* pane width, not the live `paneWidth` this content actually
                                                                    // sits inside (that one is still mid-spring right when a pane opens/closes).
                                                                    // Column/flow math keyed off the live width forced a full relayout on every
                                                                    // single frame of that spring *in addition to* the grid<->flow crossfade
                                                                    // already running — two animations fighting over the same layout at once,
                                                                    // which is what actually read as dropped frames/items jumping. Sizing off
                                                                    // the stable target instead means only the outer AnimatedVisibility's width
                                                                    // animates; the content underneath doesn't reflow mid-transition.
                                                                    availableWidth = targetPaneWidth
                                                                )
                                                            } else {
                                                                val activeSessions by viewModel.allActiveSessions.collectAsState()
                                                                DeckHierarchyTree(
                                                                    decks = allDecksWithCards,
                                                                    sessions = activeSessions,
                                                                    isLoading = viewModel.isLoading,
                                                                    navController = navController,
                                                                    viewModel = viewModel,
                                                                    onNavigateAction = { },
                                                                    orderedRootIds = deckGroups.map { it.first.deck.id },
                                                                    orderedSetIds = deckGroups.associate { (main, sets) -> main.deck.id to sets.map { it.deck.id } },
                                                                    useLargeScreenLayout = treeLargeScreenLayout,
                                                                    useLoadingIndicator = treeLoadingIndicator
                                                                )
                                                            }
                                                        }
                                                    }


                                                    // Collapse the tree's expanded/selected state back to just the root
                                                    // list. Only relevant in tree view, and only once there's actually
                                                    // something expanded or selected to collapse.
                                                    androidx.compose.animation.AnimatedVisibility(
                                                        visible = stableScreenState != 1 && expectDecks &&
                                                                currentViewMode == DeckViewMode.TREE &&
                                                                (viewModel.treeSelectedPath.isNotEmpty() || viewModel.treeExpandedNodeIds.isNotEmpty()),
                                                        enter = fadeIn() + androidx.compose.animation.scaleIn(),
                                                        exit = fadeOut() + androidx.compose.animation.scaleOut(),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(dimensions.paddingMedium)
                                                            .offset(y = (-72).dp)
                                                    ) {
                                                        val collapseInteractionSource = remember { MutableInteractionSource() }
                                                        val isCollapsePressed by collapseInteractionSource.collectIsPressedAsState()
                                                        val collapseScale by animateFloatAsState(
                                                            targetValue = if (isCollapsePressed) 0.85f else 1f,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                stiffness = Spring.StiffnessMedium
                                                            ),
                                                            label = "collapseFabSquish"
                                                        )
                                                        val collapseTooltipState = rememberTooltipState()
                                                        TooltipBox(
                                                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                                                            tooltip = { PlainTooltip { Text(getText(R.string.collapse_to_top_level)) } },
                                                            state = collapseTooltipState
                                                        ) {
                                                            FloatingActionButton(
                                                                onClick = { viewModel.collapseTreeToTopLevel() },
                                                                interactionSource = collapseInteractionSource,
                                                                modifier = Modifier.scale(collapseScale),
                                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.UnfoldLess,
                                                                    contentDescription = getText(R.string.collapse_to_top_level)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    androidx.compose.animation.AnimatedVisibility(
                                                        visible = stableScreenState != 1 && expectDecks,
                                                        enter = fadeIn() + androidx.compose.animation.scaleIn(),
                                                        exit = fadeOut() + androidx.compose.animation.scaleOut(),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(dimensions.paddingMedium)
                                                    ) {
                                                        val fabInteractionSource =
                                                            remember { MutableInteractionSource() }
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
                                                            modifier = Modifier
                                                                .scale(fabScale)
                                                                .withShortcut(
                                                                    Key.N,
                                                                    "N"
                                                                ) { navController.navigate("deckEditor") },
                                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                                            icon = {
                                                                Icon(
                                                                    Icons.Default.Add,
                                                                    contentDescription = getText(R.string.deck_create),
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
                                                } // closes the new outer Box(Modifier.fillMaxSize())
                                            }

                                            is net.ericclark.studiare.PaneDestination.SetManager -> {
                                                val parentDeck =
                                                    allDecksWithCards.find { it.deck.id == dest.deckId }
                                                if (parentDeck != null) {
                                                    val setsForDeck = allDecksWithCards
                                                        .filter { it.deck.parentDeckId == dest.deckId }
                                                        .map { DeckSummary(it.deck, it.cards.size) }
                                                    SetManagerScreen(
                                                        navController = navController,
                                                        parentDeck = parentDeck,
                                                        sets = setsForDeck,
                                                        viewModel = viewModel,
                                                        isPane = true,
                                                        onChromeChanged = { chrome ->
                                                            if (dest == visibleStack.last()) activePaneChrome =
                                                                chrome
                                                        }
                                                    )
                                                }
                                            }

                                            is net.ericclark.studiare.PaneDestination.StudyModeSelection -> {
                                                val studyDeck =
                                                    allDecksWithCards.find { it.deck.id == dest.deckId }
                                                if (studyDeck != null) {
                                                    StudyModeSelectionScreen(
                                                        navController = navController,
                                                        deck = studyDeck,
                                                        viewModel = viewModel,
                                                        autoOpen = dest.autoOpen,
                                                        isPane = true,
                                                        onChromeChanged = { chrome ->
                                                            if (dest == visibleStack.last()) activePaneChrome =
                                                                chrome
                                                        }
                                                    )
                                                }
                                            }

                                            is net.ericclark.studiare.PaneDestination.SavedSessions -> {
                                                // Wire up your saved-sessions screen here the same way,
                                                // once it exists — pushPane(PaneDestination.SavedSessions(deckId))
                                                // from wherever the user taps "Saved Sessions".
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                // ── Layer 2: empty state ───────────────────────────────────────────────
                // animateFloatAsState is scope-agnostic (works in BoxScope unlike the
                // ColumnScope-only AnimatedVisibility overload). Asymmetric durations:
                // 350 ms fade-in feels deliberate; 200 ms fade-out is snappy.
                val emptyAlpha by animateFloatAsState(
                    targetValue = if (stableScreenState == 1) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (stableScreenState == 1) 350 else 200,
                        easing = EaseInOut
                    ),
                    label = "emptyStateFade"
                )
                if (emptyAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp)
                            .graphicsLayer { alpha = emptyAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = getText(R.string.no_decks_yet),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(32.dp))

                            FilledTonalButton(
                                onClick = { importLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                                contentPadding = PaddingValues(horizontal = 24.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.align(Alignment.CenterStart)
                                    )
                                    Text(
                                        text = getText(R.string.decks_import),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            FilledTonalButton(
                                onClick = { navController.navigate("settings") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                                contentPadding = PaddingValues(horizontal = 24.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.align(Alignment.CenterStart)
                                    )
                                    Text(
                                        text = "Backup & Sync",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = { navController.navigate("deckEditor") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                                contentPadding = PaddingValues(horizontal = 24.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.align(Alignment.CenterStart)
                                    )
                                    Text(
                                        text = getText(R.string.deck_create),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Layer 3: skeleton ──────────────────────────────────────────────────
                // targetValue = 1f while loading, 0f once done. On first composition the
                // state is already 0 (loading) so the skeleton is immediately fully opaque
                // — equivalent to EnterTransition.None. The 500 ms exit gives the grid
                // below time to fully measure before it's uncovered.
                // Layer 3: skeleton — NOW passes matching padding
                val skeletonAlpha by animateFloatAsState(
                    targetValue = if (stableScreenState == 0) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (stableScreenState == 0) 0 else 500,
                        easing = EaseInOut
                    ),
                    label = "skeletonFade"
                )
                if (skeletonAlpha > 0f && gridLoadingIndicator) {
                    // Loading indicator (held back 400ms) instead of the skeleton, per settings.
                    if (currentViewMode == DeckViewMode.GRID) {
                        DelayedLoadingIndicator(modifier = Modifier.graphicsLayer { alpha = skeletonAlpha })
                    }
                } else if (skeletonAlpha > 0f) {
                    DeckSkeletonLoader(
                        modifier = Modifier.graphicsLayer { alpha = skeletonAlpha },
                        dimensions = dimensions,
                        contentPadding = PaddingValues(
                            start  = dimensions.paddingLarge,
                            end    = dimensions.paddingLarge,
                            top    = 0.dp,
                            bottom = dimensions.paddingLarge
                        ),
                        // Tree view draws its own loader, so the card skeleton is grid-only.
                        snapshotCounts = if (currentViewMode == DeckViewMode.GRID) deckSetCountsSnapshot else null,
                        displaySetsUnderDecks = displaySetsUnderDecks,
                        useFlowLayout = shouldUseFlowLayout(gridLargeScreenLayout, windowWidthSizeClass, skeletonPaneStack.size)
                    )
                }
            }
        }
    }

    showDeleteDialog?.let { deckToDelete ->
        AnimatedDialog(onDismissRequest = { showDeleteDialog = null }) {
            Surface(
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Text(getText(R.string.delete_deck_question), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Text(stringResource(R.string.delete_deck_confirm, deckToDelete.deck.name), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(dimensions.spacingLarge))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDeleteDialog = null }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Button(
                            onClick = {
                                viewModel.deleteDeck(deckToDelete.deck.id); showDeleteDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) { Text(getText(R.string.delete)) }
                    }
                }
            }
        }
    }
}
