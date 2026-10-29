package net.ericclark.studiare.screens.Screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import net.ericclark.studiare.screens.UI_Components.ConnectedButtonGroup
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
@OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
    var showExportDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(fabMenuExpanded) { fabMenuExpanded = false }

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
                    viewModel.markDecksExported(decks)
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
                    viewModel.markDecksExported(decks)
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
                    viewModel.markDecksExported(decks)
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

    // Returning to this screen via NavHost's popEnterTransition re-disposes and freshly
    // recomposes this whole composable — and since deckGroups is already cached in the
    // long-lived ViewModel, stableScreenState below resolves straight to 2 (skeleton
    // skipped) on the very first frame, before the real grid has actually been
    // measured/laid out. That first real layout pass is expensive enough (same class of
    // issue diagnosed for SimpleEditorScreen's grid) to outlast NavHost's own transition
    // duration, producing a true zero-frame cut instead of a visible slide. Held back
    // until the transition visually settles so the skeleton (already built for exactly
    // this "not ready yet" gap) covers the real grid's layout cost too, not just data
    // loading.
    val navAnimatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    var transitionSettled by remember { mutableStateOf(navAnimatedVisibilityScope == null) }
    LaunchedEffect(navAnimatedVisibilityScope) {
        val transition = navAnimatedVisibilityScope?.transition ?: return@LaunchedEffect
        snapshotFlow { transition.currentState }.first { it == EnterExitState.Visible }
        transitionSettled = true
    }

    // transitionSettled itself is NOT gated to debug-only, unlike the other screens' versions of
    // this pattern — it's dual-purpose here: Layer 1's composition gate below (safe to bypass in
    // release, same reasoning as SimpleEditorScreen.kt's mainContentReady) AND the pane-stack
    // AnimatedContent's double-animation guard further down (`enteringDeckList && !transitionSettled`),
    // which must stay accurate in every build — it's not a performance workaround, it's what
    // stops the DeckList pane's own entrance animation from stacking on top of a NavHost
    // popEnterTransition that's still genuinely mid-flight. Bypassing transitionSettled itself in
    // release would silently reintroduce that double-animation bug. layer1CompositionReady is the
    // separate, composition-gate-only value release builds can skip.
    val layer1CompositionReady = transitionSettled || !BuildConfig.DEBUG

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
    //
    // Deliberately NOT gated on transitionSettled (unlike Layer 1's own composition below) —
    // the skeleton/loading indicator should only ever appear when we genuinely don't know yet
    // whether decks exist (a true cold start), never just because the NavHost transition into
    // this screen hasn't visually settled. Forcing state 0 whenever !transitionSettled used to
    // make the skeleton/spinner flash on every return to this screen even though deckGroups was
    // already cached — gating Layer 1's composition (below) is what actually protects the
    // transition from being swallowed; this state machine should reflect real data readiness.
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            val paneStackForChrome by viewModel.paneStack.collectAsState()
            // Was a bare `if`, swapping chrome instantly with no animation at all — invisible
            // regardless of how the pane body itself animated (confirmed via a real on-device
            // PerfDebug check: entering Study Hub by button press goes through this pane path,
            // and its app-bar swap was the one piece not sliding, while the keyboard-shortcut
            // path — the real NavHost route, bypassing this entirely — already slid correctly).
            // Mirrors the pane body's own proven AnimatedContent transitionSpec above exactly
            // (same durations/easing/direction), so the chrome now moves with the body instead
            // of cutting ahead of it.
            AnimatedContent(
                targetState = paneStackForChrome.size > 1,
                transitionSpec = {
                    if (!targetState && !transitionSettled) {
                        // NavHost itself is still mid-transition into this screen — that
                        // transition already owns this motion; don't double it with our own
                        // slide (same reasoning as the pane body's own AnimatedContent above).
                        androidx.compose.animation.EnterTransition.None togetherWith ExitTransition.None
                    } else if (targetState) {
                        // Forward: a deeper pane becomes active — slide + fade in from the right.
                        (fadeIn(animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                            slideInHorizontally(animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { fullWidth -> fullWidth / 4 }) togetherWith
                            (fadeOut(animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) +
                                slideOutHorizontally(animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) { fullWidth -> -fullWidth / 4 })
                    } else {
                        // Backward: returning to the deck-list chrome — slide + fade in from the left.
                        (fadeIn(animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                            slideInHorizontally(animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { fullWidth -> -fullWidth / 4 }) togetherWith
                            (fadeOut(animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) +
                                slideOutHorizontally(animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) { fullWidth -> fullWidth / 4 })
                    }
                },
                label = "decksTopBarChromeTransition"
            ) { deep ->
                if (deep) {
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
                } else {
                    CustomTopAppBar(
                        viewModel = viewModel,
                        screenId = ShortcutScreen.DECKS,
                        navigationIcon = {
                            // Grid/Tree view toggle — moved here from above the deck grid so it reads as
                            // a persistent app-bar control rather than screen content. Kept compact (a
                            // tight contentPadding override, not the group's normal full-size default)
                            // so it fits the app bar's existing height and stays narrow enough to leave
                            // the title room on any screen width.
                            if (!viewModel.isLoading && expectDecks) {
                                ConnectedButtonGroup(
                                    items = listOf(DeckViewMode.GRID, DeckViewMode.TREE),
                                    selected = currentViewMode,
                                    onSelected = { viewModel.setDeckViewMode(if (it == DeckViewMode.GRID) 0 else 1) },
                                    iconFor = { mode, _ ->
                                        Icon(
                                            if (mode == DeckViewMode.GRID) Icons.Default.GridView else Icons.Default.AccountTree,
                                            contentDescription = if (mode == DeckViewMode.GRID) getText(R.string.grid_view) else getText(R.string.tree_view)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    // CenterAlignedTopAppBar gives the navigationIcon slot a surprisingly
                                    // wide constraint (to keep the title symmetrically centered against
                                    // the actions on the other side) — ConnectedButtonGroup's ToggleButtons
                                    // use weight(1f) internally, so without this they stretched to fill
                                    // that whole slot instead of their own compact size. IntrinsicSize.Min
                                    // caps the width to what the content actually needs.
                                    modifier = Modifier
                                        .width(IntrinsicSize.Min)
                                        .padding(start = 4.dp)
                                )
                            }
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
                            // Import/Export moved into the Add Deck FAB's menu; Sort and Settings stay
                            // directly in the app bar on every layout (no more overflow menu).
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
                        }
                    )
                }
            }
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
                // Also gated on layer1CompositionReady (see its own declaration above, and the
                // comment above stableScreenState's declaration) — without this, the heavy
                // grid/tree composition still happens immediately regardless of the skeleton
                // drawn on top of it, since the skeleton is just a visual overlay, not a
                // composition gate; the actual CPU cost is what swallows the NavHost transition's
                // frame budget. DEBUG-only (via layer1CompositionReady) — a release build's
                // AOT-compiled hot paths (baseline profile) were confirmed on-device not to need
                // this safety net, while debug is always JIT-interpreted regardless.
                //
                // Wrapped in AnimatedVisibility (not a plain if) with fadeIn, same pattern as
                // RecentsScreen's mainContentReady gate — now that stableScreenState no longer
                // forces the skeleton to show on a returning visit (see above), this is what
                // covers that gap: once layer1CompositionReady flips true, the grid/tree fades in
                // instead of popping in instantly.
                androidx.compose.animation.AnimatedVisibility(
                    visible = stableScreenState != 1 && layer1CompositionReady,
                    enter = fadeIn(),
                    exit = ExitTransition.None,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val selectedDeckId by viewModel.currentDeckId.collectAsState()
                    val selectedSetId by viewModel.currentSetId.collectAsState()

                    val paneStack by viewModel.paneStack.collectAsState()

                    val visibleStack = paneStack.takeLast(2) // placeholder, replaced below

                    // One back-handler pops the deepest layer, regardless of what it is.
                    androidx.activity.compose.BackHandler(enabled = paneStack.size > 1) {
                        viewModel.popPane()
                    }

                    // Renders a single pane's content. Factored out so it can be called both from
                    // the wide-layout Row below (several panes shown side by side at once) and
                    // from the narrow-layout AnimatedContent further down (exactly one pane on
                    // screen, crossfading to the next) without duplicating this ~230-line block.
                    // isActivePane/visiblePaneCount/availableWidth replace what used to be direct
                    // references to the Row's own visibleStack/targetPaneWidth, since this no
                    // longer always runs inside that Row's scope.
                    @Composable
                    fun PaneContent(
                        dest: net.ericclark.studiare.PaneDestination,
                        isActivePane: Boolean,
                        visiblePaneCount: Int,
                        availableWidth: androidx.compose.ui.unit.Dp
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            when (dest) {
                                is net.ericclark.studiare.PaneDestination.DeckList -> {
                                    // Pane-1 content. The grid/tree toggle used to
                                    // live here, above the content; it's now in the
                                    // top app bar's navigationIcon slot instead (see
                                    // the Scaffold's topBar above), so this is just
                                    // the grid/tree content itself.
                                    Box(Modifier.fillMaxSize()) {
                                        // Was a bare if/else — an instant, unanimated cut between
                                        // two entirely different composables (confirmed via reading
                                        // the code directly; the "grid<->flow crossfade" mentioned
                                        // in DeckGridContent's own comment below is an unrelated
                                        // animation inside that composable, not this toggle). Same
                                        // 300ms/200ms pure-fade timing already used everywhere else
                                        // in the app for a content replace, not a navigation.
                                        AnimatedContent(
                                            targetState = currentViewMode,
                                            transitionSpec = {
                                                fadeIn(animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) togetherWith
                                                    fadeOut(animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            label = "deckViewModeTransition"
                                        ) { mode ->
                                            if (mode == DeckViewMode.GRID) {
                                                DeckGridContent(
                                                    deckGroups,
                                                    dimensions,
                                                    navController,
                                                    viewModel,
                                                    displaySetsUnderDecks,
                                                    onDeleteRequested = { showDeleteDialog = it },
                                                    useFlowLayout = shouldUseFlowLayout(gridLargeScreenLayout, windowWidthSizeClass, visiblePaneCount),
                                                    // The *settled* pane width, not the live `paneWidth` this content actually
                                                    // sits inside (that one is still mid-spring right when a pane opens/closes).
                                                    // Column/flow math keyed off the live width forced a full relayout on every
                                                    // single frame of that spring *in addition to* the grid<->flow crossfade
                                                    // already running — two animations fighting over the same layout at once,
                                                    // which is what actually read as dropped frames/items jumping. Sizing off
                                                    // the stable target instead means only the outer AnimatedVisibility's width
                                                    // animates; the content underneath doesn't reflow mid-transition.
                                                    availableWidth = availableWidth
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


                                        // Collapse the tree's expanded/selected state back to just the root
                                        // list. Only relevant in tree view, and only once there's actually
                                        // something expanded or selected to collapse.
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = stableScreenState != 1 && expectDecks &&
                                                    currentViewMode == DeckViewMode.TREE &&
                                                    (viewModel.treeSelectedPath.isNotEmpty() || viewModel.treeExpandedNodeIds.isNotEmpty()),
                                            // Axis-based expand/collapse from the bottom (where this FAB is
                                            // anchored), not scale — matches M3's Android enter/exit guidance.
                                            enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                                fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) +
                                                fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(dimensions.paddingMedium)
                                                .offset(y = (-72).dp)
                                        ) {
                                            val collapseInteractionSource = remember { MutableInteractionSource() }
                                            val collapseTooltipState = rememberTooltipState()
                                            TooltipBox(
                                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                                                tooltip = { PlainTooltip { Text(getText(R.string.collapse_to_top_level)) } },
                                                state = collapseTooltipState
                                            ) {
                                                FloatingActionButton(
                                                    onClick = { viewModel.collapseTreeToTopLevel() },
                                                    interactionSource = collapseInteractionSource,
                                                    modifier = Modifier.pressSquish(collapseInteractionSource, 0.85f),
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
                                            visible = (stableScreenState != 1 && expectDecks) || fabMenuExpanded,
                                            enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                                fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) +
                                                fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(dimensions.paddingMedium)
                                        ) {
                                            // Add Deck / Import / Export live together in one FAB menu —
                                            // Sort and Settings stay as their own app bar buttons instead.
                                            FloatingActionButtonMenu(
                                                expanded = fabMenuExpanded,
                                                button = {
                                                    TooltipBox(
                                                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                                            if (fabMenuExpanded) TooltipAnchorPosition.Start else TooltipAnchorPosition.Above
                                                        ),
                                                        tooltip = { PlainTooltip { Text(getText(R.string.deck_create)) } },
                                                        state = rememberTooltipState()
                                                    ) {
                                                        ToggleFloatingActionButton(
                                                            checked = fabMenuExpanded,
                                                            onCheckedChange = { fabMenuExpanded = it },
                                                            containerSize = ToggleFloatingActionButtonDefaults.containerSizeMedium(),
                                                            containerCornerRadius = ToggleFloatingActionButtonDefaults.containerCornerRadiusMedium(),
                                                            // Default TopEnd anchors the shrink-to-X animation at the top-right
                                                            // corner of the (larger) unchecked bounding box; BottomEnd keeps it
                                                            // anchored where the FAB visually sits, at the bottom-right.
                                                            contentAlignment = Alignment.BottomEnd,
                                                            modifier = Modifier.withShortcut(
                                                                Key.N,
                                                                "N"
                                                            ) { navController.navigate("deckEditor") }
                                                        ) {
                                                            val imageVector by remember {
                                                                derivedStateOf {
                                                                    if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Layers
                                                                }
                                                            }
                                                            Icon(
                                                                painter = rememberVectorPainter(imageVector),
                                                                contentDescription = getText(R.string.deck_actions),
                                                                modifier = Modifier.animateIcon(
                                                                    { checkedProgress },
                                                                    size = ToggleFloatingActionButtonDefaults.iconSizeMedium()
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            ) {
                                                // Each item is independently measured by the menu's own
                                                // staggered-animation layout (wrapping them in a shared
                                                // Column to intrinsics-match would collapse them into one
                                                // animated unit), so a shared minimum width is the
                                                // reliable way to make all three match the widest label.
                                                val fabMenuItemMinWidth = 200.dp
                                                // The menu reserves bottom space equal to the button's full
                                                // *unchecked* (Medium, 80dp) box, not its actual closed-X
                                                // size (56dp) — M3 doesn't expose that gap as a param, so
                                                // nudge the items down by the 24dp difference to close it.
                                                val fabMenuItemOffsetY = 20.dp
                                                FloatingActionButtonMenuItem(
                                                    onClick = {
                                                        fabMenuExpanded = false
                                                        showExportDialog = true
                                                    },
                                                    modifier = Modifier.offset(y = fabMenuItemOffsetY).widthIn(min = fabMenuItemMinWidth),
                                                    icon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                                    text = { Text(getText(R.string.decks_export)) }
                                                )
                                                Spacer(Modifier.height(5.dp))
                                                FloatingActionButtonMenuItem(
                                                    onClick = {
                                                        fabMenuExpanded = false
                                                        importLauncher.launch(arrayOf("*/*"))
                                                    },
                                                    modifier = Modifier.offset(y = fabMenuItemOffsetY).widthIn(min = fabMenuItemMinWidth),
                                                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                                                    text = { Text(getText(R.string.decks_import)) }
                                                )
                                                Spacer(Modifier.height(5.dp))
                                                FloatingActionButtonMenuItem(
                                                    onClick = {
                                                        fabMenuExpanded = false
                                                        navController.navigate("deckEditor")
                                                    },
                                                    modifier = Modifier.offset(y = fabMenuItemOffsetY).widthIn(min = fabMenuItemMinWidth),
                                                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                                    text = { Text(getText(R.string.deck_create)) }
                                                )
                                            }
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
                                                if (isActivePane) activePaneChrome =
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
                                                if (isActivePane) activePaneChrome =
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

                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        val minPaneWidth = 320.dp // matches DeckGridContent's own GridCells.Adaptive minSize
                        // Multiple panes only from the desktop width up; below it, one pane fills the screen.
                        // Shared with MainActivity's own nav-rail-vs-bottom-bar decision, rather than
                        // each recomputing the same DESKTOP_MIN_WIDTH_DP check independently.
                        val maxVisiblePanes = if (net.ericclark.studiare.LocalIsDesktopWidth.current) (maxWidth / minPaneWidth).toInt().coerceIn(1, 3) else 1
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

                        if (maxVisiblePanes == 1) {
                            // Narrow/phone layout: exactly one pane is ever on screen.
                            // AnimatedContent instead of the Row below — both the outgoing and
                            // incoming pane's content are composed in the SAME Box bounds during
                            // the crossfade (not divided side-by-side the way Row would split
                            // them), and the outgoing pane stays composed for its own exit
                            // animation's duration instead of vanishing the instant it's popped
                            // from paneStack. The Row + per-pane AnimatedVisibility approach below
                            // can't do that: a popped pane simply stops being emitted into the Row
                            // at all, so its "exit" transition never gets a chance to play — which
                            // is exactly why Study Hub's own fade+slide-out wasn't appearing.
                            val currentTop = paneStack.lastOrNull()
                            AnimatedContent(
                                targetState = currentTop,
                                transitionSpec = {
                                    val enteringDeckList = targetState is net.ericclark.studiare.PaneDestination.DeckList
                                    if (enteringDeckList && !transitionSettled) {
                                        // NavHost itself is still mid-transition into this screen
                                        // (e.g. popping back from Simple Editor) — that transition
                                        // already owns this motion; don't double it with our own
                                        // slide (same reasoning as isRootDeckListPane below).
                                        androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                                    } else if (enteringDeckList) {
                                        // Backward: closing a pane, returning to the deck list —
                                        // slide + fade in from the LEFT, mirroring the forward
                                        // direction's from-the-right.
                                        (fadeIn(animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                            slideInHorizontally(animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { fullWidth -> -fullWidth / 4 }) togetherWith
                                            (fadeOut(animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) +
                                                slideOutHorizontally(animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) { fullWidth -> fullWidth / 4 })
                                    } else {
                                        // Forward: opening a new pane — slide + fade in from the right.
                                        (fadeIn(animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                            slideInHorizontally(animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { fullWidth -> fullWidth / 4 }) togetherWith
                                            (fadeOut(animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) +
                                                slideOutHorizontally(animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) { fullWidth -> -fullWidth / 4 })
                                    }
                                },
                                label = "singlePaneTransition"
                            ) { dest ->
                                if (dest != null) {
                                    PaneContent(
                                        dest = dest,
                                        isActivePane = dest.paneKey == currentTop?.paneKey,
                                        visiblePaneCount = 1,
                                        availableWidth = maxWidth
                                    )
                                }
                            }
                        } else {
                        Row(modifier = Modifier.fillMaxSize()) {
                        visibleStack.forEachIndexed { index, dest ->
                            key(dest.paneKey) {
                                if (index > 0) {
                                    androidx.compose.material3.VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                val paneVisibleState = remember(dest.paneKey) {
                                    androidx.compose.animation.core.MutableTransitionState(false)
                                }.apply { targetState = true }

                                // Whether this is the root deckList pane reappearing — must be
                                // checked against the pane's own *type*, not its position in
                                // visibleStack (see the narrow-layout AnimatedContent branch above
                                // for the phone-width equivalent of this same concern).
                                val isRootDeckListPane = dest is net.ericclark.studiare.PaneDestination.DeckList
                                androidx.compose.animation.AnimatedVisibility(
                                    visibleState = paneVisibleState,
                                    modifier = Modifier.width(paneWidth),
                                    // Only the root deckList pane reappearing skips this — it
                                    // re-triggers this same remember/targetState=true dance every
                                    // time this whole composable is freshly recomposed, including
                                    // when NavHost pops back to the deckList route from elsewhere,
                                    // which was stacking a second entrance on top of (and visually
                                    // overriding) NavHost's own popEnterTransition. The root pane's
                                    // appearance is already fully owned by that outer page-level
                                    // transition, so it gets none here. This wide-layout path keeps
                                    // several simultaneous side-by-side panes, which is a genuinely
                                    // different pattern from the narrow-layout single-pane replace
                                    // above, so it isn't affected by that branch's exit-animation fix.
                                    enter = if (isRootDeckListPane) {
                                        androidx.compose.animation.EnterTransition.None
                                    } else {
                                        fadeIn(animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                            slideInHorizontally(
                                                animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                                initialOffsetX = { fullWidth -> fullWidth / 4 }
                                            )
                                    },
                                    exit = if (isRootDeckListPane) {
                                        androidx.compose.animation.ExitTransition.None
                                    } else {
                                        fadeOut(animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)) +
                                            slideOutHorizontally(
                                                animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                                                targetOffsetX = { fullWidth -> fullWidth / 4 }
                                            )
                                    }
                                ) {
                                    PaneContent(
                                        dest = dest,
                                        isActivePane = dest == visibleStack.last(),
                                        visiblePaneCount = visibleStack.size,
                                        availableWidth = targetPaneWidth
                                    )
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
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, emptyStateBadgeShape()),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Style,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(Modifier.height(16.dp))

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
                        DelayedLoadingIndicator(isLoading = stableScreenState == 0, modifier = Modifier.graphicsLayer { alpha = skeletonAlpha })
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
