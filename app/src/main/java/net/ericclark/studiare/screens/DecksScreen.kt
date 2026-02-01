package net.ericclark.studiare.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*
import net.ericclark.studiare.*
import net.ericclark.studiare.R // Ensure this matches your package R
import net.ericclark.studiare.components.*
import net.ericclark.studiare.data.*

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

/**
 * The main screen of the app, redesigned with Material 3 Expressive principles.
 * Features bolder shapes (28dp corners), large FABs, and elevated card hierarchies.
 */
@Composable
fun DeckListScreen(navController: NavController, decks: List<net.ericclark.studiare.data.DeckWithCards>, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    // State for managing dialogs and menus
    var showDeleteDialog by remember { mutableStateOf<net.ericclark.studiare.data.DeckWithCards?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // State for theme and data
    val context = LocalContext.current
    val importDuplicateQueue by viewModel.importDuplicateQueue.collectAsState()
    val overwriteConfirmation by viewModel.overwriteConfirmation.collectAsState()

    // A state variable to hold the list of decks selected for export.
    var decksToExport by remember { mutableStateOf<List<net.ericclark.studiare.data.DeckWithCards>?>(null) }

    // Group main decks and their sets
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

    // --- Dialogs (Unchanged logic, just UI handling) ---
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
                if (format == "CSV") csvExportLauncher.launch("flashcard_decks_${dtFormat}.csv")
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
            // Preserving your CustomTopAppBar, but consider putting a LargeTopAppBar here for full Expressive effect
            CustomTopAppBar(
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.studiare_solid),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(44.dp) // Slightly larger for Expressive
                            .clip(CircleShape)
                            .padding(start = 12.dp)
                    )
                },
                title = {
                    Text(
                        "All Decks",
                        style = MaterialTheme.typography.headlineSmall, // Bolder title
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)) // Rounder menu
                        ) {
                            DropdownMenuItem(text = { Text("Import Decks") }, onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream"))
                                showMenu = false
                            })
                            DropdownMenuItem(text = { Text("Export Decks") }, onClick = {
                                showExportDialog = true
                                showMenu = false
                            })
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { navController.navigate("settings"); showMenu = false }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // EXPRESSIVE CHANGE: Use LargeFloatingActionButton with a distinct shape
            LargeFloatingActionButton(
                onClick = { navController.navigate("deckEditor") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(20.dp) // Expressive "Squircle" feel
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create Deck",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (decks.isEmpty() && !viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text("No decks yet.", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
                        Text("Create one or import to start.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp), // Slightly wider minimum for expressive cards
                    // EXPRESSIVE CHANGE: Increased spacing to 24.dp for a cleaner, airier layout
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(deckGroups) { (mainDeck, sets) ->
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            DeckListItem(
                                deck = mainDeck,
                                onStudy = { if (mainDeck.cards.isNotEmpty()) navController.navigate("studyModeSelection/${mainDeck.deck.id}") },
                                onEdit = { navController.navigate("deckEditor?deckId=${mainDeck.deck.id}") },
                                onDelete = { showDeleteDialog = mainDeck },
                                onManageSets = { navController.navigate("setManager/${mainDeck.deck.id}") }
                            )

                            if (sets.isNotEmpty()) {
                                // EXPRESSIVE CHANGE: Container for sets to visually group them
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp) // Indent sets slightly
                                ) {

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        //contentPadding = PaddingValues(bottom = 20.dp)
                                    ) {
                                        items(sets) { set ->
                                            SetListItem(
                                                deck = set,
                                                onStudy = { if (set.cards.isNotEmpty()) navController.navigate("studyModeSelection/${set.deck.id}") }
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

    showDeleteDialog?.let { deckToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
            title = { Text("Delete Deck?") },
            text = { Text("Are you sure you want to delete \"${deckToDelete.deck.name}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteDeck(deckToDelete.deck.id); showDeleteDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * EXPRESSIVE OVERHAUL: DeckListItem
 * - Uses ElevatedCard for depth.
 * - Corner radius increased to 28.dp.
 * - Typography updated to headline/title styles.
 */
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
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp, pressedElevation = 8.dp),
        shape = RoundedCornerShape(28.dp), // Expressive Large Shape
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.deck.name,
                        style = MaterialTheme.typography.headlineSmall, // Bolder
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    SuggestionChip(
                        onClick = { /* No-op info chip */ },
                        label = { Text("${deck.cards.size} Cards") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = null
                    )
                }

                Row {
                    if (showManageSetsButton) {
                        IconButton(onClick = onManageSets) {
                            Icon(Icons.Default.AccountTree, "Manage Sets", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else if (onToggleStar != null) {
                        IconButton(onClick = onToggleStar) {
                            Icon(
                                imageVector = if (deck.deck.isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = if (deck.deck.isStarred) "Unstar Set" else "Star Set",
                                tint = if (deck.deck.isStarred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary Actions (Edit/Delete)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(8.dp))
                // Primary Action (Study)
                Button(
                    onClick = onStudy,
                    enabled = deck.cards.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Study")
                }
            }
        }
    }
}

/**
 * EXPRESSIVE OVERHAUL: SetListItem
 * - Uses Card for distinct visual hierarchy vs main decks.
 * - Corner radius 20.dp (Medium Shape).
 */
@Composable
fun SetListItem(deck: net.ericclark.studiare.data.DeckWithCards, onStudy: () -> Unit) {
    Card(
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(20.dp), // Expressive Medium Shape
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
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
                    "${deck.cards.size} cards",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onStudy,
                enabled = deck.cards.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Study")
            }
        }
    }
}

//region Reusable Components (Dialogs, Overlays - mostly structural updates)

@Composable
fun LoadingOverlay(message: String = "Processing...") {
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
                CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = message, style = MaterialTheme.typography.titleMedium)
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

    // Grouping logic remains the same
    val deckGroups = remember(decksToOverwrite) {
        val mainDecks = decksToOverwrite.filter { it.parentDeckId == null }.sortedBy { it.name }
        val setsByParentId = decksToOverwrite.filter { it.parentDeckId != null }.groupBy { it.parentDeckId!! }
        val setComparator = compareBy<net.ericclark.studiare.data.Deck, Int?>(nullsLast()) { it.name.removePrefix("Set ").toIntOrNull() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        mainDecks.map { mainDeck -> mainDeck to (setsByParentId[mainDeck.id]?.sortedWith(setComparator) ?: emptyList()) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Overwrite Existing?") },
        text = {
            Column {
                Text("Select decks to overwrite:", style = MaterialTheme.typography.bodyMedium)
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
        confirmButton = { Button(onClick = { onConfirm(selectedDeckIds.toList()) }) { Text("Overwrite Selected") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .padding(start = if (isSet) 24.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = null)
        Spacer(Modifier.width(16.dp))
        Text(deck.name, style = MaterialTheme.typography.bodyLarge)
    }
}

// TopSliderDialogSection remains mostly same but using M3 tokens
@Composable
fun TopSliderDialogSection(options: List<String>, selectedMode: String, onModeChange: (String) -> Unit) {
    val selectedIndex = options.indexOf(selectedMode).coerceAtLeast(0)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp) // Taller for expressive touch target
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        val segmentWidth = this.maxWidth / options.size
        val indicatorOffset by animateDpAsState(targetValue = segmentWidth * selectedIndex, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing), label = "indicator")

        // Indicator
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .fillMaxHeight()
                .width(segmentWidth)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEach { mode ->
                val isSelected = selectedMode == mode
                val textColor by animateColorAsState(targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "text_color")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onModeChange(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = mode, color = textColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// FlowRow wrapper
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
        title = { Text("Duplicates Found") },
        text = {
            Column {
                Text("Duplicates were found in '${result.deckName}'. Remove them before saving?")
                Spacer(Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(result.duplicates) { duplicate ->
                        Text("• \"${duplicate.text}\" (${duplicate.count})", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirmRemove) { Text("Remove & Save") } },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onConfirmSaveAnyway) { Text("Save Anyway") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}