package net.ericclark.studiare.screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SplitButtonDefaults

/**
 * The main screen of the app, redesigned with Material 3 Expressive principles.
 * Features bolder shapes (28dp corners), large FABs, and elevated card hierarchies.
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun DeckListScreen(navController: NavController, decks: List<DeckWithCards>, viewModel: FlashcardViewModel) {
    // State for managing dialogs and menus
    var showDeleteDialog by remember { mutableStateOf<DeckWithCards?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    val deckSortMode by viewModel.deckSortMode.collectAsState()

    // State for theme and data
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp > 600
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

    // Group main decks and their sets, and apply sorting
    val deckGroups = remember(decks, deckSortMode) {
        val mainDecksUnsorted = decks.filter { it.deck.parentDeckId == null }
        val setsByParent = decks
            .filter { it.deck.parentDeckId != null }
            .groupBy { it.deck.parentDeckId!! }

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

        val deckComparator = Comparator<DeckWithCards> { d1, d2 ->
            when (deckSortMode) {
                DeckSortMode.A_TO_Z -> naturalOrderComparator.compare(d1.deck.name, d2.deck.name)
                DeckSortMode.Z_TO_A -> naturalOrderComparator.compare(d2.deck.name, d1.deck.name)
                DeckSortMode.DATE_ADDED_NEW_TO_OLD -> d2.deck.createdAt.compareTo(d1.deck.createdAt)
                DeckSortMode.DATE_ADDED_OLD_TO_NEW -> d1.deck.createdAt.compareTo(d2.deck.createdAt)
                DeckSortMode.DATE_MODIFIED_NEW_TO_OLD -> d2.deck.updatedAt.compareTo(d1.deck.updatedAt)
                DeckSortMode.DATE_MODIFIED_OLD_TO_NEW -> d1.deck.updatedAt.compareTo(d2.deck.updatedAt)
                else -> naturalOrderComparator.compare(d1.deck.name, d2.deck.name)
            }
        }

        val mainDecks = mainDecksUnsorted.sortedWith(deckComparator)

        mainDecks.map { mainDeck ->
            val sets = (setsByParent[mainDeck.deck.id] ?: emptyList()).sortedWith(deckComparator)
            mainDeck to sets
        }
    }

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

    if (showExportDialog) {
        ExportDecksDialog(
            decks = decks,
            onDismiss = { showExportDialog = false },
            onExport = { selectedDecks, format ->
                showExportDialog = false
                decksToExport = selectedDecks
                val dateFormat = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
                val dtFormat = dateFormat.format(Date())
                val fileName = context.getString(R.string.output_file_name, dtFormat, "csv")
                if (format == "CSV") csvExportLauncher.launch(fileName)
                else jsonExportLauncher.launch("flashcard_decks_${dtFormat}.json")
            }
        )
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                val content = context.contentResolver.openInputStream(it)?.bufferedReader().use { reader -> reader?.readText() }
                val mimeType = context.contentResolver.getType(it)
                if (content != null) viewModel.importDecksFromString(content, mimeType)
            }
        }
    )

    // --- UI Structure ---
    Scaffold(
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
                    if (isLargeScreen) {
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.Default.Sort, contentDescription = getText(R.string.sort_decks))
                        }
                        IconButton(onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream"))
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
                                        importLauncher.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream"))
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
                shape = RoundedCornerShape(dimensions.cornerRadiusLarge), // M3 Expressive prefers highly rounded pill shapes
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
                    decks.isEmpty() -> 1
                    else -> 2
                },
                transitionSpec = {
                    (slideInVertically() + fadeIn() + expandVertically()).togetherWith(
                        slideOutVertically() + fadeOut() + shrinkVertically()
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
                            CircularWavyProgressIndicator()
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
                                            if (mainDeck.cards.isNotEmpty()) navController.navigate(route)
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
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)//,
                                                //contentPadding = PaddingValues(bottom = 8.dp)
                                            ) {
                                                items(sets) { set ->
                                                    SetListItem(
                                                        deck = set,
                                                        dimensions = dimensions,
                                                        onStudy = { autoOpen ->
                                                            val route = if (autoOpen != null) "studyModeSelection/${set.deck.id}?autoOpen=$autoOpen" else "studyModeSelection/${set.deck.id}"
                                                            if (set.cards.isNotEmpty()) navController.navigate(route)
                                                        }
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
    deck: DeckWithCards,
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
        shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                        label = { Text(stringResource(R.string.cards_count, deck.cards.size)) },
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
                    enabled = deck.cards.isNotEmpty()
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
    deck: DeckWithCards,
    dimensions: StudiareDimensions,
    onStudy: (String?) -> Unit
) {
    Card(
        modifier = Modifier.width(160.dp).height(160.dp), // Give it a fixed height to make it a square tile
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
                    stringResource(R.string.cards_count_lowercase, deck.cards.size),
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
                    enabled = deck.cards.isNotEmpty(),
                    includeText = false
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

    Box(modifier = modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onStudyMain,
                    // Force the leading button to be 88dp (which is 2/3 of the total width)
                    // when the text is hidden, mimicking your custom layout ratio!
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
                        // Using a Box with fillMaxWidth ensures the icon perfectly centers
                        // within the 88dp space instead of hugging the left edge.
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp) // Slightly larger for visual balance
                            )
                        }
                    }
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it }
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
                text = { Text("Study") },
                leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                onClick = { expanded = false; onStudyOption("study") }
            )
            DropdownMenuItem(
                text = { Text("Quiz") },
                leadingIcon = { Icon(Icons.Default.Quiz, contentDescription = null) },
                onClick = { expanded = false; onStudyOption("quiz") }
            )
            DropdownMenuItem(
                text = { Text("Game") },
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

/*
@Composable
fun StudySplitButton(
    onStudyMain: () -> Unit,
    onStudyOption: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    includeText: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "splitButtonSquish"
    )

    Surface(
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.38f),
        modifier = modifier.scale(scale)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            // Main Button Action
            Row(
                modifier = Modifier
                    .clickable(
                        enabled = enabled,
                        onClick = onStudyMain,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current
                    )
                    // Apply exact 88.dp width when no text (double the 44dp right button)
                    .then(
                        if (includeText) Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        else Modifier.width(66.dp).padding(vertical = 10.dp)
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center // This perfectly centers the icon in the 88dp space!
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))

                if (includeText) {
                    Spacer(Modifier.width(8.dp))
                    Text(getText(R.string.study), fontWeight = FontWeight.Bold)
                }
            }

            // Divider
            VerticalDivider(
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp).width(1.dp)
            )

            // Dropdown Action
            Box {
                IconButton(
                    onClick = { expanded = true },
                    enabled = enabled,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = getText(R.string.options_more))
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Study") },
                        leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                        onClick = { expanded = false; onStudyOption("study") }
                    )
                    DropdownMenuItem(
                        text = { Text("Quiz") },
                        leadingIcon = { Icon(Icons.Default.Quiz, contentDescription = null) },
                        onClick = { expanded = false; onStudyOption("quiz") }
                    )
                    DropdownMenuItem(
                        text = { Text("Game") },
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
    }
}

 */