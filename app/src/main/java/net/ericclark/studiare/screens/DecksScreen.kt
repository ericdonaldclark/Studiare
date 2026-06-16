package net.ericclark.studiare.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.LocalIndication
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
    val displaySetsUnderDecks by viewModel.displaySetsUnderDecks.collectAsState()
    val deckSetCountsSnapshot by viewModel.deckSetCountsSnapshot.collectAsState()

    // Map spacing mode to Dimensions
    val dimensions = when (spacingMode) {
        SpacingMode.COMPACT -> CompactDimensions
        SpacingMode.NORMAL -> NormalDimensions
        else -> ComfortableDimensions
    }

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

    if (showDelayedLoading)
    {
        LoadingOverlay("Processing...")
    }
    else if (viewModel.isProcessing) {
        LoadingOverlay()
    }

    // --- Export Logic ---
    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            uri?.let {
                decksToExport?.let { decks ->
                    val content = viewModel.getDecksAsString(decks, "JSON", exportIncludeMetadata) // ADDED PARAM
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
                    val content = viewModel.getDecksAsString(decks, "CSV", exportIncludeMetadata) // ADDED PARAM
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
                    viewModel.exportToAnkiPackage(context, decks, it, exportIncludeMetadata) // ADDED PARAM
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
        Dialog(onDismissRequest = { showCollectionDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp), // M3 Expressive Dialog Shape
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Select Collection",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        item {
                            SelectableDialogItem(
                                text = getText(R.string.decks_all),
                                isSelected = selectedCollectionId == null,
                                onClick = {
                                    viewModel.selectCollection(null)
                                    showCollectionDialog = false
                                }
                            )
                        }
                        items(allCollections, key = { it.collection.id }) { collectionData ->
                            SelectableDialogItem(
                                text = collectionData.collection.name,
                                isSelected = selectedCollectionId == collectionData.collection.id,
                                onClick = {
                                    viewModel.selectCollection(collectionData.collection.id)
                                    showCollectionDialog = false
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCollectionDialog = false }) {
                            Text(getText(R.string.cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                showCollectionDialog = false
                                navController.navigate("collectionManager")
                            }
                        ) {
                            Text("Edit Collections")
                        }
                    }
                }
            }
        }
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
                        isLocalProcessing = true
                        pendingImportUri = it
                        val analysisList = viewModel.analyzeAnkiPackage(context, it)

                        val decksToMap = mutableListOf<Pair<String, List<Pair<String, net.ericclark.studiare.data.MediaType>>>>()
                        val autoMappedConfigs = mutableListOf<net.ericclark.studiare.screens.AnkiMappingConfig>()
                        var subDecks = 0

                        val groupedByRoot = analysisList.groupBy { it.first.split("::").first() }

                        for ((rootName, deckEntries) in groupedByRoot) {
                            val subdecksInRoot = deckEntries.count { it.first.contains("::") }
                            subDecks += subdecksInRoot

                            val combinedFields = deckEntries.flatMap { it.second }.distinctBy { it.first }

                            val hasStandardFields = combinedFields.size == 2 &&
                                    combinedFields.any { f -> f.first.equals("Front", true) || f.first.equals("Question", true) } &&
                                    combinedFields.any { f -> f.first.equals("Back", true) || f.first.equals("Answer", true) }

                            if (combinedFields.size > 2 || (!hasStandardFields && combinedFields.isNotEmpty())) {
                                decksToMap.add(Pair(rootName, combinedFields))
                            } else if (combinedFields.isNotEmpty()) {
                                val mapping = mutableMapOf<net.ericclark.studiare.screens.MapperDestination, List<net.ericclark.studiare.screens.MapperItem>>()
                                combinedFields.forEach { (text, type) ->
                                    val dest = if (text.equals("Front", true) || text.equals("Question", true)) net.ericclark.studiare.screens.MapperDestination.FRONT else net.ericclark.studiare.screens.MapperDestination.BACK
                                    val list = mapping.getOrPut(dest) { mutableListOf() } as MutableList<net.ericclark.studiare.screens.MapperItem>
                                    list.add(net.ericclark.studiare.screens.MapperItem(text = text, type = type, destination = dest))
                                }
                                autoMappedConfigs.add(net.ericclark.studiare.screens.AnkiMappingConfig(
                                    originalAnkiName = rootName,
                                    deckName = rootName,
                                    mapping = mapping
                                ))
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
    LaunchedEffect(viewModel.isLoading, deckGroups.size, deckSetCountsSnapshot?.size, selectedCollectionId) {
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
            stableScreenState = if (deckGroups.isNotEmpty() || !deckSetCountsSnapshot.isNullOrEmpty()) {
                if (deckGroups.isNotEmpty()) 2 else 0
            } else 1
        }
    }

    // --- UI Structure ---
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                navigationIcon = {
                    AnimatedHamburgerMenu(viewModel = viewModel, windowWidthSizeClass = windowWidthSizeClass)
                },
                title = {
                    val currentCollectionName = if (viewModel.isLoading || selectedCollectionId == "UNINITIALIZED") {
                        ""
                    } else if (selectedCollectionId == null) {
                        getText(R.string.decks_all) // Resolves to "All Decks"
                    } else {
                        allCollections.find { it.collection.id == selectedCollectionId }?.collection?.name ?: getText(R.string.decks_all)
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
                                contentDescription = "Switch Collection",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
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
            Box {
                AnimatedVisibility(
                    visible = stableScreenState != 1,
                    enter = fadeIn() + androidx.compose.animation.scaleIn(),
                    exit = fadeOut() + androidx.compose.animation.scaleOut()
                ) {
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
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {


            // ─────────────────────────────────────────────────────────────────────────

            // ── Three-layer overlay ───────────────────────────────────────────────────
            // Bottom → top stacking order:
            //   1. Deck grid  — always composed, even during loading (deckGroups is empty
            //                   then so it's free). Pre-measuring means cards are ready the
            //                   instant the skeleton clears — no gap.
            //   2. Empty state — fades independently of the other layers.
            //   3. Skeleton   — starts opaque, fades OUT with EnterTransition.None so it can
            //                   never accidentally flash back in on recomposition.
            Box(modifier = Modifier.fillMaxSize()) {

                // ── Layer 1: real deck grid ────────────────────────────────────────────
                if (stableScreenState != 1) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
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
                            Column(
                                // animateItem fires when items are inserted/removed in an already-
                                // visible list (e.g. after the user creates or deletes a deck).
                                // During initial load the skeleton covers the grid, so these
                                // animations play silently underneath with no visual artifact.
                                modifier = Modifier.animateItem(
                                    fadeInSpec  = tween(durationMillis = 300, easing = EaseInOut),
                                    fadeOutSpec = tween(durationMillis = 200, easing = EaseInOut),
                                    placementSpec = spring(
                                        stiffness    = Spring.StiffnessLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy
                                    )
                                ),
                                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                            ) {
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
                                    enter = slideInVertically(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        initialOffsetY = { it / 4 }
                                    ) + fadeIn() + expandVertically(),
                                    exit = slideOutVertically(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        targetOffsetY = { -it / 4 }
                                    ) + fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = dimensions.paddingSmall)
                                    ) {
                                        val listState = rememberLazyListState()

                                        LazyRow(
                                            state = listState,
                                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
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

                // ── Layer 2: empty state ───────────────────────────────────────────────
                // animateFloatAsState is scope-agnostic (works in BoxScope unlike the
                // ColumnScope-only AnimatedVisibility overload). Asymmetric durations:
                // 350 ms fade-in feels deliberate; 200 ms fade-out is snappy.
                val emptyAlpha by animateFloatAsState(
                    targetValue   = if (stableScreenState == 1) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (stableScreenState == 1) 350 else 200,
                        easing         = EaseInOut
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
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
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
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
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
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
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
                if (skeletonAlpha > 0f) {
                    DeckSkeletonLoader(
                        modifier = Modifier.graphicsLayer { alpha = skeletonAlpha },
                        dimensions = dimensions,
                        contentPadding = PaddingValues(
                            start  = dimensions.paddingLarge,
                            end    = dimensions.paddingLarge,
                            top    = dimensions.paddingLarge,
                            bottom = 120.dp
                        ),
                        snapshotCounts = deckSetCountsSnapshot,
                        displaySetsUnderDecks = displaySetsUnderDecks
                    )
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
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardSquish"
    )

    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation, pressedElevation = 8.dp),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
            .clickable(
                interactionSource = cardInteractionSource,
                indication = LocalIndication.current
            ) { onManageSets() }
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

// ---------------------------------------------------------------------------
// Skeleton loader — shown while viewModel.isLoading == true.
// Mirrors the real grid layout (same columns, padding, spacing) so the
// transition into real content is seamless. The pulse is driven by a single
// shared InfiniteTransition so every placeholder beats in unison.
// ---------------------------------------------------------------------------

// Replacement Code
@Composable
fun DeckSkeletonLoader(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    dimensions: StudiareDimensions = LocalStudiareDimensions.current,
    snapshotCounts: List<Int>? = null,
    displaySetsUnderDecks: Boolean = true
) {
    if (snapshotCounts == null) return // Wait until we know the snapshot counts to avoid flashing

    val infiniteTransition = rememberInfiniteTransition(label = "skeletonPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue  = 0.50f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    val itemCount = if (snapshotCounts.isNotEmpty()) snapshotCounts.size else 0

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        contentPadding = contentPadding,
        verticalArrangement   = Arrangement.spacedBy(dimensions.spacingLarge),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingLarge),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        items(itemCount) { index ->
            val setsCount = if (snapshotCounts.isNotEmpty()) snapshotCounts[index] else 0

            Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                DeckSkeletonItem(pulseAlpha = pulseAlpha, dimensions = dimensions, setsCount = setsCount)

                if (displaySetsUnderDecks && setsCount > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = dimensions.paddingSmall)
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                            userScrollEnabled = false
                        ) {
                            items(setsCount) {
                                SetSkeletonItem(pulseAlpha = pulseAlpha, dimensions = dimensions)
                            }
                        }

                        if (setsCount > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingSmall),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 0 until setsCount) {
                                    val isSelected = i == 0
                                    val width = if (isSelected) 24.dp else 8.dp
                                    val color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha * 0.3f)
                                    }
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

@Composable
private fun DeckSkeletonItem(
    pulseAlpha: Float,
    dimensions: StudiareDimensions,
    setsCount: Int
) {
    val fill    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha)
    val fillDim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha * 0.55f)

    ElevatedCard(
        shape  = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box {
                        Text(
                            text       = "Deck Name",
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.graphicsLayer { alpha = 0f }
                        )
                        Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(8.dp)).background(fill))
                    }
                    Spacer(Modifier.height(4.dp))
                    Box {
                        SuggestionChip(
                            onClick  = {},
                            label    = { Text("000 Cards") },
                            colors   = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            border   = null,
                            modifier = Modifier.graphicsLayer { alpha = 0f }
                        )
                        Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(50)).background(fillDim))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(
                            onClick        = {},
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier       = Modifier.graphicsLayer { alpha = 0f }
                        ) {
                            Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sets_count_simple, setsCount))
                        }
                        Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(8.dp)).background(fillDim))
                    }
                }
            }

            Spacer(Modifier.height(dimensions.paddingLarge))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(onClick = {}, modifier = Modifier.graphicsLayer { alpha = 0f }) {
                        Icon(Icons.Default.Edit, null)
                    }
                    Box(modifier = Modifier.matchParentSize().clip(CircleShape).background(fillDim))
                }
                Box {
                    IconButton(onClick = {}, modifier = Modifier.graphicsLayer { alpha = 0f }) {
                        Icon(Icons.Default.Delete, null)
                    }
                    Box(modifier = Modifier.matchParentSize().clip(CircleShape).background(fillDim))
                }
                Spacer(Modifier.width(dimensions.spacingSmall))
                Box {
                    StudySplitButton(
                        onStudyMain   = {},
                        onStudyOption = {},
                        modifier      = Modifier.graphicsLayer { alpha = 0f }
                    )
                    Row(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(
                                    topStart    = dimensions.cornerRadiusLarge,
                                    bottomStart = dimensions.cornerRadiusLarge,
                                    topEnd      = 0.dp,
                                    bottomEnd   = 0.dp
                                ))
                                .background(fill)
                        )
                        Spacer(Modifier.width(1.dp))
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(
                                    topStart    = 0.dp,
                                    bottomStart = 0.dp,
                                    topEnd      = dimensions.cornerRadiusLarge,
                                    bottomEnd   = dimensions.cornerRadiusLarge
                                ))
                                .background(fill)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetSkeletonItem(
    pulseAlpha: Float,
    dimensions: StudiareDimensions
) {
    val fill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha)
    val fillDim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha * 0.55f)

    Card(
        modifier = Modifier.width(190.dp).height(160.dp),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(dimensions.paddingMedium)
        ) {
            Column {
                Box {
                    Text(
                        "Set Name",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.graphicsLayer { alpha = 0f }
                    )
                    Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(4.dp)).background(fill))
                }
                Spacer(Modifier.height(4.dp))
                Box {
                    Text("0 cards", style = MaterialTheme.typography.labelMedium, modifier = Modifier.graphicsLayer { alpha = 0f })
                    Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(4.dp)).background(fillDim))
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box {
                    StudySplitButton(
                        onStudyMain = {},
                        onStudyOption = {},
                        modifier = Modifier.graphicsLayer { alpha = 0f }
                    )
                    Row(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = dimensions.cornerRadiusLarge, bottomStart = dimensions.cornerRadiusLarge, topEnd = 0.dp, bottomEnd = 0.dp))
                                .background(fill)
                        )
                        Spacer(Modifier.width(1.dp))
                        Box(
                            modifier = Modifier.width(40.dp).fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = dimensions.cornerRadiusLarge, bottomEnd = dimensions.cornerRadiusLarge))
                                .background(fill)
                        )
                    }
                }
            }
        }
    }
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp), // M3 Expressive Dialog Shape
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = getText(R.string.sort_decks),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(options) { mode ->
                        SelectableDialogItem(
                            text = mode.asString(),
                            isSelected = mode == currentSortMode,
                            onClick = {
                                onSortModeSelected(mode)
                                onDismiss()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(getText(R.string.cancel))
                    }
                }
            }
        }
    }
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
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
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

@Composable
fun SelectableDialogItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "selectableItemSquish"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "contentColor"
    )
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(vertical = 16.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = fontWeight),
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = contentColor)
        }
    }
}