package net.ericclark.studiare.screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

    // State for theme and data
    val context = LocalContext.current
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

    // Group main decks and their sets
    val deckGroups = remember(decks) {
        val mainDecks = decks.filter { it.deck.parentDeckId == null }
        val setsByParent = decks
            .filter { it.deck.parentDeckId != null }
            .groupBy { it.deck.parentDeckId!! }

        val setComparator = compareBy<DeckWithCards, Int?>(nullsLast()) {
            it.deck.name.removePrefix(getText(context,R.string.set_)).toIntOrNull()
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.deck.name }

        mainDecks.map { mainDeck ->
            val sets = (setsByParent[mainDeck.deck.id] ?: emptyList()).sortedWith(setComparator)
            mainDeck to sets
        }
    }

    // --- Dialogs ---
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
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.options_more))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                        ) {
                            DropdownMenuItem(text = { Text(getText(R.string.decks_import)) }, onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream"))
                                showMenu = false
                            })
                            DropdownMenuItem(text = { Text(getText(R.string.decks_export)) }, onClick = {
                                showExportDialog = true
                                showMenu = false
                            })
                            DropdownMenuItem(
                                text = { Text(getText(R.string.settings)) },
                                onClick = { navController.navigate("settings"); showMenu = false }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { navController.navigate("deckEditor") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium) // Use dimension
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = getText(R.string.deck_create),
                    modifier = Modifier.size(20.dp)
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
                        Text(getText(R.string.no_decks_yet), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(getText(R.string.create_or_import_to_start), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    // Apply Spacing Mode + Ensure bottom padding for FAB (100.dp)
                    contentPadding = PaddingValues(
                        start = dimensions.paddingLarge,
                        end = dimensions.paddingLarge,
                        top = dimensions.paddingLarge,
                        bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimensions.spacingLarge),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingLarge)
                ) {
                    items(deckGroups) { (mainDeck, sets) ->
                        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)) {
                            DeckListItem(
                                deck = mainDeck,
                                dimensions = dimensions,
                                setsCount = sets.size,
                                onStudy = { if (mainDeck.cards.isNotEmpty()) navController.navigate("studyModeSelection/${mainDeck.deck.id}") },
                                onEdit = { navController.navigate("deckEditor?deckId=${mainDeck.deck.id}") },
                                onDelete = { showDeleteDialog = mainDeck },
                                onManageSets = { navController.navigate("setManager/${mainDeck.deck.id}") }
                            )

                            // Only show sets here if preference is enabled
                            if (sets.isNotEmpty() && displaySetsUnderDecks) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = dimensions.paddingSmall) // Slight indent
                                ) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                                        contentPadding = PaddingValues(bottom = 8.dp)
                                    ) {
                                        items(sets) { set ->
                                            SetListItem(
                                                deck = set,
                                                dimensions = dimensions,
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
    onStudy: () -> Unit,
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
                        TextButton(
                            onClick = onManageSets,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.AccountTree, getText(R.string.manage_sets), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sets_count_simple, setsCount), color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (onToggleStar != null) {
                        IconButton(onClick = onToggleStar) {
                            Icon(
                                imageVector = if (deck.deck.isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = if (deck.deck.isStarred) getText(R.string.unstar_set) else getText(R.string.star_set),
                                tint = if (deck.deck.isStarred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant
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
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, getText(R.string.edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, getText(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(dimensions.spacingSmall))
                Button(
                    onClick = onStudy,
                    enabled = deck.cards.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(getText(R.string.study))
                }
            }
        }
    }
}

@Composable
fun SetListItem(
    deck: DeckWithCards,
    dimensions: StudiareDimensions,
    onStudy: () -> Unit
) {
    Card(
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            // CHANGE: Added fillMaxWidth() so the column spans the full 160dp
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.paddingMedium),
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
                    stringResource(R.string.cards_count_lowercase, deck.cards.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(dimensions.spacingMedium))

            Button(
                onClick = onStudy,
                enabled = deck.cards.isNotEmpty(),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(getText(R.string.study))
            }
        }
    }
}

// ... (Rest of the file/dialogs remain unchanged) ...
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
                CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
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
        val mainDecks = decksToOverwrite.filter { it.parentDeckId == null }.sortedBy { it.name }
        val setsByParentId = decksToOverwrite.filter { it.parentDeckId != null }.groupBy { it.parentDeckId!! }
        val setComparator = compareBy<Deck, Int?>(nullsLast()) { it.name.removePrefix("Set ").toIntOrNull() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
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

@Composable
fun TopSliderDialogSection(options: List<String>, selectedMode: String, onModeChange: (String) -> Unit) {
    val selectedIndex = options.indexOf(selectedMode).coerceAtLeast(0)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        val segmentWidth = this.maxWidth / options.size
        val indicatorOffset by animateDpAsState(targetValue = segmentWidth * selectedIndex, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing), label = "indicator")

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