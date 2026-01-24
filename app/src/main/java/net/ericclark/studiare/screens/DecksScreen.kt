package net.ericclark.studiare.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures // ADDED
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import java.util.Locale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import kotlin.math.min
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.max
import net.ericclark.studiare.*
import net.ericclark.studiare.components.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.R

const val TAGS = "Tags"
const val ANY = "Any"
const val DIFFICULTY = "Difficulty"
const val ALPHABETICAL = "Alphabetical"
const val ALPHABET = "Alphabet"
const val CARD_ORDER = "Card Order"
const val REVIEW_DATE = "Review Date"
const val INCORRECT_DATE = "Incorrect Date"
const val REVIEW_COUNT = "Review Count"
const val SCORE = "Score"
const val RANDOM = "Random"

//region homepage deck screen
/**
 * The main screen of the app, displaying a list of all flashcard decks.
 * It includes a top app bar with options for theme switching and import/export,
 * a floating action button to create new decks, and a grid of existing decks.
 * @param navController The NavController for navigating between screens.
 * @param decks The list of decks to display.
 * @param viewModel The ViewModel providing data and business logic.
 */
@Composable
fun DeckListScreen(navController: NavController, decks: List<net.ericclark.studiare.data.DeckWithCards>, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    // State for managing dialogs and menus
    var showDeleteDialog by remember { mutableStateOf<net.ericclark.studiare.data.DeckWithCards?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    // REMOVED: showDeleteAllDecksDialog, showAboutDialog, showThemeDialog (Moved to Settings)
    var showExportDialog by remember { mutableStateOf(false) }

    // State for theme and data
    val context = LocalContext.current
    val importDuplicateQueue by viewModel.importDuplicateQueue.collectAsState()
    val overwriteConfirmation by viewModel.overwriteConfirmation.collectAsState()

    // A state variable to hold the list of decks selected for export.
    var decksToExport by remember { mutableStateOf<List<net.ericclark.studiare.data.DeckWithCards>?>(null) }

    // Group main decks and their sets for proper UI rendering.
    val deckGroups = remember(decks) {
        val mainDecks = decks.filter { it.deck.parentDeckId == null }
        val setsByParent = decks
            .filter { it.deck.parentDeckId != null }
            .groupBy { it.deck.parentDeckId!! }

        val setComparator = compareBy<net.ericclark.studiare.data.DeckWithCards, Int?>(nullsLast()) {
            it.deck.name.removePrefix("Set ").toIntOrNull()
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.deck.name }

        mainDecks.map { mainDeck ->
            val sets = (setsByParent[mainDeck.deck.id] ?: emptyList()).sortedWith(setComparator)
            mainDeck to sets
        }
    }

    // Show a dialog if there are decks with duplicates in the import queue
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
            onConfirm = { selectedIds ->
                viewModel.proceedWithImport(selectedIds)
            }
        )
    }

    // Show loading overlay if a bulk operation is in progress
    if (viewModel.isProcessing) {
        LoadingOverlay()
    }

    // Launcher for exporting decks to a JSON file
    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            uri?.let {
                decksToExport?.let { decks ->
                    val content = viewModel.getDecksAsString(decks, "JSON")
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(content.toByteArray())
                    }
                }
            }
            decksToExport = null // Reset state after use
        }
    )

    // Launcher for exporting decks to a CSV file
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri: Uri? ->
            uri?.let {
                decksToExport?.let { decks ->
                    val content = viewModel.getDecksAsString(decks, "CSV")
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(content.toByteArray())
                    }
                }
            }
            decksToExport = null // Reset state after use
        }
    )

    // Show the new export dialog when triggered
    if (showExportDialog) {
        ExportDecksDialog(
            decks = decks,
            onDismiss = { showExportDialog = false },
            onExport = { selectedDecks, format ->
                showExportDialog = false
                decksToExport = selectedDecks
                val dateFormat = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
                val date = Date()
                val dtFormat = dateFormat.format(date)
                if (format == "CSV") {
                    csvExportLauncher.launch("flashcard_decks_${dtFormat}.csv")
                } else {
                    jsonExportLauncher.launch("flashcard_decks_${dtFormat}.json")
                }
            }
        )
    }

    // Activity result launcher for importing decks from a file (JSON or CSV)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                val content = context.contentResolver.openInputStream(it)?.bufferedReader().use { reader -> reader?.readText() }
                val mimeType = context.contentResolver.getType(it)
                if (content != null) {
                    viewModel.importDecksFromString(content, mimeType)
                }
            }
        }
    )

    Scaffold(
        topBar = {
            CustomTopAppBar(
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.studiare_solid),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .padding(start = 8.dp)
                    )
                },
                title = { Text("Decks") },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            // Menu items moved to Settings
                            DropdownMenuItem(text = { Text("Import Decks") }, onClick = {
                                importLauncher.launch(
                                    arrayOf(
                                        "application/json",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "text/plain",
                                        "application/vnd.ms-excel",
                                        "application/octet-stream"
                                    )
                                )
                                showMenu = false
                            })
                            DropdownMenuItem(text = { Text("Export Decks") }, onClick = {
                                showExportDialog = true
                                showMenu = false
                            })
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    navController.navigate("settings")
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("deckEditor") }) {
                Icon(Icons.Default.Add, contentDescription = "Create Deck")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (decks.isEmpty() && !viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No decks yet. Create one or import.", fontSize = 18.sp, color = Color.Gray)
                }
            } else if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(deckGroups) { (mainDeck, sets) ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DeckListItem(
                                deck = mainDeck,
                                onStudy = {
                                    if (mainDeck.cards.isNotEmpty()) {
                                        navController.navigate("studyModeSelection/${mainDeck.deck.id}")
                                    }
                                },
                                onEdit = { navController.navigate("deckEditor?deckId=${mainDeck.deck.id}") },
                                onDelete = { showDeleteDialog = mainDeck },
                                onManageSets = { navController.navigate("setManager/${mainDeck.deck.id}") }
                            )

                            if (sets.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    items(sets) { set ->
                                        SetListItem(
                                            deck = set,
                                            onStudy = {
                                                if (set.cards.isNotEmpty()) {
                                                    navController.navigate("studyModeSelection/${set.deck.id}")
                                                }
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

    showDeleteDialog?.let { deckToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Confirm Deletion", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { Text("Are you sure you want to delete the deck \"${deckToDelete.deck.name}\"?") },
            confirmButton = {
                Button(onClick = { viewModel.deleteDeck(deckToDelete.deck.id); showDeleteDialog = null }) { Text("Delete") }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun LoadingOverlay(message: String = "Processing...") {
    Dialog(onDismissRequest = { }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
@Composable
fun ImportOverwriteDialog(
    decksToOverwrite: List<net.ericclark.studiare.data.Deck>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val selectedDeckIds = remember { mutableStateListOf(*decksToOverwrite.map { it.id }.toTypedArray()) }

    // Group and sort decks for hierarchical display
    val deckGroups = remember(decksToOverwrite) {
        val mainDecks = decksToOverwrite
            .filter { it.parentDeckId == null }
            .sortedBy { it.name }

        val setsByParentId = decksToOverwrite
            .filter { it.parentDeckId != null }
            .groupBy { it.parentDeckId!! }

        val setComparator = compareBy<net.ericclark.studiare.data.Deck, Int?>(nullsLast()) {
            it.name.removePrefix("Set ").toIntOrNull()
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

        mainDecks.map { mainDeck ->
            mainDeck to (setsByParentId[mainDeck.id]?.sortedWith(setComparator) ?: emptyList())
        }
    }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Overwrite Existing Decks?") },
        text = {
            Column {
                Text("The imported file contains decks that already exist. Select the decks you wish to overwrite.")
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier
                    .heightIn(max = 300.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                ) {
                    deckGroups.forEach { (mainDeck, sets) ->
                        item(key = mainDeck.id) {
                            OverwriteDeckItem(
                                deck = mainDeck,
                                isSelected = mainDeck.id in selectedDeckIds,
                                onToggle = {
                                    if (mainDeck.id in selectedDeckIds) selectedDeckIds.remove(mainDeck.id)
                                    else selectedDeckIds.add(mainDeck.id)
                                }
                            )
                        }
                        items(sets, key = { it.id }) { set ->
                            OverwriteDeckItem(
                                deck = set,
                                isSelected = set.id in selectedDeckIds,
                                onToggle = {
                                    if (set.id in selectedDeckIds) selectedDeckIds.remove(set.id)
                                    else selectedDeckIds.add(set.id)
                                },
                                isSet = true
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDeckIds.toList()) }) {
                Text("Overwrite Selected")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel Import")
            }
        }
    )
}

@Composable
private fun OverwriteDeckItem(
    deck: net.ericclark.studiare.data.Deck,
    isSelected: Boolean,
    onToggle: () -> Unit,
    isSet: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(
                vertical = 4.dp,
                horizontal = 8.dp
            )
            .padding(start = if (isSet) 24.dp else 0.dp), // Indent sets
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = null // Handled by Row click
        )
        Spacer(Modifier.width(16.dp))
        Text(deck.name)
    }
}

@Composable
fun DeckListItem(
    deck: net.ericclark.studiare.data.DeckWithCards,
    onStudy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageSets: () -> Unit,
    onToggleStar: (() -> Unit)? = null,
    showManageSetsButton: Boolean = true
) {
    Card(elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(
                        deck.deck.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 48.dp) // Space for the icon button
                    )
                    Text("${deck.cards.size} cards", fontSize = 14.sp, color = Color.Gray)
                }
                if (showManageSetsButton) {
                    IconButton(
                        onClick = onManageSets,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.AccountTree, "Manage Sets")
                    }
                } else if (onToggleStar != null) {
                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = if (deck.deck.isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = if (deck.deck.isStarred) "Unstar Set" else "Star Set",
                            tint = if (deck.deck.isStarred) Color(0xFFFFD700) else LocalContentColor.current
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStudy, modifier = Modifier.weight(1f), enabled = deck.cards.isNotEmpty()) { Text("Study") }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit Deck") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete Deck", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

//endregion Decks

//region UI components

// -----------------------------------------------------------------------------
// 1. TopSliderDialogSection
// -----------------------------------------------------------------------------
@Composable
fun TopSliderDialogSection(
    options: List<String>, // Now accepts a dynamic list
    selectedMode: String,
    onModeChange: (String) -> Unit
) {
    val selectedIndex = options.indexOf(selectedMode).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .padding(4.dp)
    ) {
        // Calculate width based on number of options (3 for presets, 3 for set modes)
        val segmentWidth = this.maxWidth / options.size

        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "indicator"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .fillMaxHeight()
                .width(segmentWidth)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEach { mode ->
                val isSelected = selectedMode == mode
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "text_color"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onModeChange(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = mode, color = textColor, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                }
            }
        }
    }
}



@OptIn(ExperimentalLayoutApi::class)
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
    result: net.ericclark.studiare.data.DuplicateCheckResult,
    onDismiss: () -> Unit,
    onConfirmRemove: () -> Unit,
    onConfirmSaveAnyway: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicates Found", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column {
                Text("Duplicates were found in '${result.deckName}'. Would you like to remove them before saving?")
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(result.duplicates) { duplicate ->
                        Text("- \"${duplicate.text}\" (${duplicate.count} times)")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirmRemove) {
                Text("Remove & Save")
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onConfirmSaveAnyway) {
                    Text("Save Anyway")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

//endregion

@Composable
fun SetListItem(deck: net.ericclark.studiare.data.DeckWithCards, onStudy: () -> Unit) {
    Card(
        modifier = Modifier.width(120.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                deck.deck.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${deck.cards.size} cards",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStudy,
                enabled = deck.cards.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Study")
            }
        }
    }
}



