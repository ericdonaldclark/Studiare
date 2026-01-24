package net.ericclark.studiare

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import kotlin.math.roundToInt
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.luminance
// ADD: Import for parsing Hex colors
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import net.ericclark.studiare.components.*
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.screens.TopSliderDialogSection
import net.ericclark.studiare.data.*

/**
 * A stable, custom implementation of a TopAppBar to avoid using experimental Material3 APIs.
 * @param title The title composable to be displayed in the app bar.
 * @param modifier The modifier to be applied to the app bar.
 * @param navigationIcon The composable for the navigation icon.
 * @param actions The composable for the actions on the trailing side.
 */
@Composable
fun CustomTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), contentAlignment = Alignment.Center) {
                    navigationIcon()
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    ProvideTextStyle(value = MaterialTheme.typography.titleLarge) {
                        title()
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    actions()
                }
            }
        }
    }
}

// --- NEW: Loading Overlay Composable ---


/**
 * A reusable button for navigating between cards in a study session.
 * @param onClick The action to perform when the button is clicked.
 * @param icon The icon to display on the button.
 * @param modifier The modifier to apply to the button.
 */
@Composable
fun StudyCardNavButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        icon()
    }
}

/**
 * A circular button with a checkmark to mark a card as "known".
 * @param isKnown The current known status of the card.
 * @param onClick The action to perform when the button is clicked.
 */
@Composable
fun MarkKnownButton(
    isKnown: Boolean,
    onClick: () -> Unit
) {
    val icon = if (isKnown) Icons.Filled.Check else Icons.Default.Check
    val colors = if (isKnown) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        ButtonDefaults.outlinedButtonColors()
    }
    val border = if (isKnown) null else BorderStroke(1.dp, MaterialTheme.colorScheme.primary)

    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(36.dp),
        contentPadding = PaddingValues(0.dp),
        colors = colors,
        border = border
    ) {
        Icon(icon, contentDescription = if (isKnown) "Mark as not known" else "Mark as known")
    }
}


/**
 * A slider for rating the difficulty of a card.
 * @param label The label to display above the slider.
 * @param difficulty The current difficulty value.
 * @param onDifficultyChange Callback for when the difficulty value changes.
 */
@Composable
fun DifficultySlider(label: String, difficulty: Int, onDifficultyChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = "$label: $difficulty", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = difficulty.toFloat(),
            onValueChange = { onDifficultyChange(it.roundToInt()) },
            valueRange = 1f..5f,
            steps = 3
        )
    }
}

/**
 * A dialog for selecting decks and a format for export.
 * @param decks The list of all available decks.
 * @param onDismiss Callback for when the dialog is dismissed.
 * @param onExport Callback that provides the list of selected decks and the chosen format.
 */
@Composable
fun ExportDecksDialog(
    decks: List<net.ericclark.studiare.data.DeckWithCards>,
    onDismiss: () -> Unit,
    onExport: (selectedDecks: List<net.ericclark.studiare.data.DeckWithCards>, format: String) -> Unit
) {
    var includeSets by rememberSaveable { mutableStateOf(true) }
    val selectedDecks = remember { mutableStateListOf<net.ericclark.studiare.data.DeckWithCards>() }
    var format by remember { mutableStateOf("JSON") }
    val listState = rememberLazyListState()

    val decksAndTheirSets = remember(decks) {
        val mainDecks = decks.filter { it.deck.parentDeckId == null }.sortedBy { it.deck.name }
        val setsByParent = decks.filter { it.deck.parentDeckId != null }.groupBy { it.deck.parentDeckId!! }
        val setComparator = compareBy<net.ericclark.studiare.data.DeckWithCards, Int?>(nullsLast()) {
            it.deck.name.removePrefix("Set ").toIntOrNull()
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.deck.name }

        mainDecks.map { mainDeck ->
            mainDeck to (setsByParent[mainDeck.deck.id] ?: emptyList()).sortedWith(setComparator)
        }
    }

    val availableItemsToSelect = remember(includeSets, decksAndTheirSets) {
        if (includeSets) {
            decksAndTheirSets.flatMap { (mainDeck, sets) -> listOf(mainDeck) + sets }
        } else {
            decksAndTheirSets.map { it.first }
        }
    }

    LaunchedEffect(availableItemsToSelect) {
        selectedDecks.clear()
        selectedDecks.addAll(availableItemsToSelect)
    }

    val areAllSelected = selectedDecks.size == availableItemsToSelect.size && availableItemsToSelect.isNotEmpty()

    val canScrollUp by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val canScrollDown by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index < listState.layoutInfo.totalItemsCount - 1
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Export Decks",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                OutlinedButton(
                    onClick = {
                        if (areAllSelected) {
                            selectedDecks.clear()
                        } else {
                            selectedDecks.clear()
                            selectedDecks.addAll(availableItemsToSelect)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (areAllSelected) "Deselect All" else "Select All")
                }
                Spacer(Modifier.height(8.dp))

                Box {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        decksAndTheirSets.forEach { (mainDeck, sets) ->
                            item(key = "deck-${mainDeck.deck.id}") {
                                DeckExportItem(
                                    deck = mainDeck,
                                    isSelected = mainDeck in selectedDecks,
                                    onToggle = {
                                        if (mainDeck in selectedDecks) selectedDecks.remove(mainDeck) else selectedDecks.add(mainDeck)
                                    }
                                )
                            }
                            if (includeSets) {
                                items(sets, key = { "set-${it.deck.id}" }) { set ->
                                    DeckExportItem(
                                        deck = set,
                                        isSelected = set in selectedDecks,
                                        onToggle = {
                                            if (set in selectedDecks) selectedDecks.remove(set) else selectedDecks.add(set)
                                        },
                                        isSet = true
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.align(Alignment.TopCenter)) {
                        AnimatedVisibility(visible = canScrollUp, enter = fadeIn(), exit = fadeOut()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                Color.Transparent
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                        AnimatedVisibility(visible = canScrollDown, enter = fadeIn(), exit = fadeOut()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "More decks available", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeSets = !includeSets }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = includeSets, onCheckedChange = { includeSets = it })
                    Spacer(Modifier.width(16.dp))
                    Text("Include Sets")
                }
                Spacer(Modifier.height(8.dp))

                Text("Export Format", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = format == "JSON", onClick = { format = "JSON" })
                    Text("JSON")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = format == "CSV", onClick = { format = "CSV" })
                    Text("CSV")
                }
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onExport(selectedDecks.toList(), format) },
                        enabled = selectedDecks.isNotEmpty()
                    ) {
                        Text("Export")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckExportItem(
    deck: net.ericclark.studiare.data.DeckWithCards,
    isSelected: Boolean,
    onToggle: () -> Unit,
    isSet: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(
                start = if (isSet) 32.dp else 16.dp,
                end = 16.dp,
                top = 1.dp,
                bottom = 1.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
        Spacer(Modifier.width(16.dp))
        Text(deck.deck.name)
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmButtonText: String = "Confirm"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(confirmButtonText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}



@Composable
fun SortModeDialogSection(
    sortMode: String, onSortModeChange: (String) -> Unit,
    sortDirection: String, onSortDirectionChange: (String) -> Unit,
    sortSide: String, onSortSideChange: (String) -> Unit,
    sortExpanded: Boolean, onToggleExpand: () -> Unit
) {
    DialogSection(
        title = "Sort & Priority",
        subtitle = if (sortMode == net.ericclark.studiare.screens.RANDOM) net.ericclark.studiare.screens.RANDOM else "$sortMode ($sortDirection)",
        isExpanded = sortExpanded,
        onToggle = onToggleExpand
    ) {
        Column {
            ToggleButton(
                text = net.ericclark.studiare.screens.RANDOM,
                isSelected = sortMode == net.ericclark.studiare.screens.RANDOM,
                onClick = { onSortModeChange(net.ericclark.studiare.screens.RANDOM) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            val sortOptions = listOf(
                net.ericclark.studiare.screens.ALPHABETICAL,
                net.ericclark.studiare.screens.REVIEW_DATE,
                net.ericclark.studiare.screens.INCORRECT_DATE,
                net.ericclark.studiare.screens.REVIEW_COUNT,
                net.ericclark.studiare.screens.CARD_ORDER,
                net.ericclark.studiare.screens.SCORE
            )
            val chunkedOptions = sortOptions.chunked(2)
            chunkedOptions.forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowOptions.forEach { option ->
                        ToggleButton(
                            text = option,
                            isSelected = sortMode == option,
                            onClick = { onSortModeChange(option) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowOptions.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (sortMode != net.ericclark.studiare.screens.RANDOM) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ascending", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = sortDirection == "DESC",
                        onCheckedChange = { onSortDirectionChange(if (it) "DESC" else "ASC") },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Text("Descending", style = MaterialTheme.typography.bodySmall)
                }
                if (sortMode == net.ericclark.studiare.screens.ALPHABETICAL) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Front Side", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = sortSide == "Back",
                            onCheckedChange = { onSortSideChange(if (it) "Back" else "Front") },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Text("Back Side", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}





@Composable
fun CardCountSection(
    numberOfCards: Int,
    availableCardsCount: Int,
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onValueChange: (Int) -> Unit,
    label: String = "Number of Cards"
) {
    DialogSection(
        title = label,
        subtitle = "$numberOfCards of $availableCardsCount",
        isExpanded = isExpanded,
        onToggle = { onToggle(!isExpanded) }
    ) {
        Text("Count: $numberOfCards", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { if (numberOfCards > 1) onValueChange(numberOfCards - 1) }, enabled = numberOfCards > 1) { Icon(Icons.Default.Remove, "Decrease") }
            Box(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).padding(horizontal = 24.dp, vertical = 12.dp)) { Text(if (availableCardsCount == 0) "0" else numberOfCards.toString(), fontSize = 20.sp) }
            IconButton(onClick = { if (numberOfCards < availableCardsCount) onValueChange(numberOfCards + 1) }, enabled = numberOfCards < availableCardsCount) { Icon(Icons.Default.Add, "Increase") }
        }
        Slider(
            value = numberOfCards.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 1f..availableCardsCount.toFloat().coerceAtLeast(1f),
            steps = (availableCardsCount - 2).coerceAtLeast(0)
        )
    }
}



@Composable
fun DialogSection(
    title: String,
    subtitle: String? = null,
    isExpanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isCollapsible = isExpanded != null && subtitle != null && onToggle != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isCollapsible, onClick = { onToggle?.invoke() }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (isCollapsible && !isExpanded!!) {
                    Text(
                        text = subtitle ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (isCollapsible) {
                Icon(
                    imageVector = if (isExpanded!!) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
        }

        AnimatedVisibility(visible = !isCollapsible || isExpanded!!) {
            Column {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}



@Composable
fun TextFieldWithNotes(
    mainText: String,
    onMainTextChange: (String) -> Unit,
    mainLabel: String,
    notesText: String?,
    onNotesTextChange: (String?) -> Unit,
    notesLabel: String
) {
    val showNotes = notesText != null

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = mainText,
                onValueChange = onMainTextChange,
                label = { Text(mainLabel) },
                modifier = Modifier.weight(1f)
            )
            if (!showNotes) {
                IconButton(onClick = { onNotesTextChange("") }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note")
                }
            }
        }
        AnimatedVisibility(visible = showNotes) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = notesText ?: "",
                    onValueChange = { onNotesTextChange(it) },
                    label = { Text(notesLabel) },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onNotesTextChange(null) }) {
                    Icon(Icons.Default.Clear, contentDescription = "Remove Note")
                }
            }
        }
    }
}

@Composable
fun SelectionModeDialogSection(
    state: net.ericclark.studiare.data.SelectionSectionState,
    actions: net.ericclark.studiare.data.SelectionSectionActions,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    // Subtitle Logic
    val subtitle = when (state.selectionMode) {
        net.ericclark.studiare.screens.ANY -> "All available cards"
        net.ericclark.studiare.screens.TAGS -> "${state.selectedTags.size} tags selected"
        net.ericclark.studiare.screens.DIFFICULTY -> "Diff: ${state.selectedDifficulties.sorted().joinToString()}"
        net.ericclark.studiare.screens.ALPHABET -> "${state.filterSide}: ${state.alphabetStart} - ${state.alphabetEnd}"
        net.ericclark.studiare.screens.CARD_ORDER -> "Cards ${state.cardOrderStart} - ${state.cardOrderEnd}"
        net.ericclark.studiare.screens.REVIEW_DATE, net.ericclark.studiare.screens.INCORRECT_DATE -> "${state.filterType} within ${state.timeValue} ${state.timeUnit}"
        net.ericclark.studiare.screens.REVIEW_COUNT -> "${state.reviewDirection}: ${state.reviewThreshold} reviews"
        net.ericclark.studiare.screens.SCORE -> "${state.scoreDirection}: ${state.scoreThreshold}%"
        else -> ""
    }

    DialogSection(
        title = "Selection Mode",
        subtitle = "$subtitle ${if (state.excludeKnown) "(No Known)" else ""}",
        isExpanded = isExpanded,
        onToggle = onToggleExpand
    ) {
        Column {
            val selectionOptions = listOf(
                net.ericclark.studiare.screens.ANY,
                net.ericclark.studiare.screens.DIFFICULTY,
                net.ericclark.studiare.screens.TAGS,
                net.ericclark.studiare.screens.ALPHABET,
                net.ericclark.studiare.screens.CARD_ORDER,
                net.ericclark.studiare.screens.REVIEW_DATE,
                net.ericclark.studiare.screens.INCORRECT_DATE,
                net.ericclark.studiare.screens.REVIEW_COUNT,
                net.ericclark.studiare.screens.SCORE
            )
            val chunkedSelection = selectionOptions.chunked(2)

            chunkedSelection.forEach { rowOptions ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowOptions.forEach { option ->
                        val isEnabled = if (option == net.ericclark.studiare.screens.REVIEW_COUNT) state.maxDeckReviews > 0 else true
                        ToggleButton(
                            text = option,
                            isSelected = state.selectionMode == option,
                            enabled = isEnabled,
                            onClick = {
                                actions.onModeChange(option)
                                // Defaults logic
                                if (option == net.ericclark.studiare.screens.REVIEW_DATE) actions.onFilterTypeChange("Exclude")
                                if (option == net.ericclark.studiare.screens.INCORRECT_DATE) actions.onFilterTypeChange("Include")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowOptions.size < 2) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))

            when (state.selectionMode) {
                net.ericclark.studiare.screens.ANY -> Text("Selects from all cards in the deck.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                net.ericclark.studiare.screens.ALPHABET -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.alphabetStart,
                            onValueChange = { if (it.length <= 1) actions.onAlphabetStartChange(it.uppercase()) },
                            label = { Text("From") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                        )
                        Text("-", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = state.alphabetEnd,
                            onValueChange = { if (it.length <= 1) actions.onAlphabetEndChange(it.uppercase()) },
                            label = { Text("To") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Front Side", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = state.filterSide == "Back",
                            onCheckedChange = { actions.onFilterSideChange(if (it) "Back" else "Front") },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Text("Back Side", style = MaterialTheme.typography.bodySmall)
                    }
                }

                net.ericclark.studiare.screens.CARD_ORDER -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start Card: ${state.cardOrderStart}", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = state.cardOrderStart.toFloat(),
                                onValueChange = { actions.onCardOrderStartChange(it.roundToInt()) },
                                valueRange = 1f..state.totalCards.toFloat(),
                                steps = 0
                            )
                        }
                        OutlinedTextField(
                            value = state.cardOrderStart.toString(),
                            onValueChange = { actions.onCardOrderStartChange(it.toIntOrNull() ?: 1) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("End Card: ${state.cardOrderEnd}", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = state.cardOrderEnd.toFloat(),
                                onValueChange = { actions.onCardOrderEndChange(it.roundToInt()) },
                                valueRange = 1f..state.totalCards.toFloat(),
                                steps = 0
                            )
                        }
                        OutlinedTextField(
                            value = state.cardOrderEnd.toString(),
                            onValueChange = { actions.onCardOrderEndChange(it.toIntOrNull() ?: state.totalCards) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                net.ericclark.studiare.screens.REVIEW_DATE, net.ericclark.studiare.screens.INCORRECT_DATE -> {
                    Column {
                        var isUnitDropdownExpanded by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledIconButton(
                                onClick = { if (state.timeValue > 1) actions.onTimeValueChange(state.timeValue - 1) },
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) { Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

                            OutlinedTextField(
                                value = state.timeValue.toString(),
                                onValueChange = { actions.onTimeValueChange(it.toIntOrNull() ?: 1) },
                                modifier = Modifier.width(60.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center)
                            )

                            FilledIconButton(
                                onClick = { actions.onTimeValueChange(state.timeValue + 1) },
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) { Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { isUnitDropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(state.timeUnit)
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(
                                    expanded = isUnitDropdownExpanded,
                                    onDismissRequest = { isUnitDropdownExpanded = false }
                                ) {
                                    listOf("Days", "Weeks", "Months", "Years").forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit) },
                                            onClick = { actions.onTimeUnitChange(unit); isUnitDropdownExpanded = false }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Include", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = state.filterType == "Exclude",
                                onCheckedChange = { actions.onFilterTypeChange(if (it) "Exclude" else "Include") },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Text("Exclude", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (state.selectionMode == net.ericclark.studiare.screens.REVIEW_DATE) {
                                if (state.filterType == "Exclude") "Selects cards NOT reviewed in the last ${state.timeValue} ${state.timeUnit}."
                                else "Selects cards reviewed within the last ${state.timeValue} ${state.timeUnit}."
                            } else {
                                if (state.filterType == "Exclude") "Selects cards NOT incorrect in the last ${state.timeValue} ${state.timeUnit}."
                                else "Selects cards incorrect within the last ${state.timeValue} ${state.timeUnit}."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                net.ericclark.studiare.screens.REVIEW_COUNT -> {
                    Column {
                        val sliderColors = if (state.reviewDirection == "Minimum") {
                            SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary
                            )
                        } else SliderDefaults.colors()

                        Text("Reviews: ${state.reviewThreshold}", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.reviewThreshold.toFloat(),
                            onValueChange = { actions.onReviewThresholdChange(it.roundToInt()) },
                            valueRange = 0f..state.maxDeckReviews.toFloat(),
                            steps = 0,
                            colors = sliderColors
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Minimum", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = state.reviewDirection == "Maximum",
                                onCheckedChange = { actions.onReviewDirectionChange(if (it) "Maximum" else "Minimum") },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Text("Maximum", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                net.ericclark.studiare.screens.SCORE -> {
                    Column {
                        val sliderColors = if (state.scoreDirection == "Minimum") {
                            SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary
                            )
                        } else SliderDefaults.colors()

                        Text("Score: ${state.scoreThreshold}%", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.scoreThreshold.toFloat(),
                            onValueChange = { actions.onScoreThresholdChange(it.roundToInt()) },
                            valueRange = 0f..100f,
                            steps = 0,
                            colors = sliderColors
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Minimum", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = state.scoreDirection == "Maximum",
                                onCheckedChange = { actions.onScoreDirectionChange(if (it) "Maximum" else "Minimum") },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Text("Maximum", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                net.ericclark.studiare.screens.TAGS -> {
                    if (state.availableTags.isEmpty()) {
                        Text("No tags found in this deck.", color = MaterialTheme.colorScheme.error)
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            state.availableTags.sortedBy { it.lowercase() }.forEach { tagName ->
                                val tagDef = state.allTagDefinitions.find { it.name == tagName }
                                val colorHex = tagDef?.color ?: "#0D47A1"
                                val isSelected = tagName in state.selectedTags

                                net.ericclark.studiare.components.TagChip(
                                    text = tagName,
                                    colorHex = colorHex,
                                    onDelete = if (isSelected) {
                                        { actions.onTagsChange(state.selectedTags - tagName) }
                                    } else null,
                                    onClick = if (!isSelected) {
                                        { actions.onTagsChange(state.selectedTags + tagName) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                net.ericclark.studiare.screens.DIFFICULTY -> {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        (1..5).forEach { diff ->
                            val isSelected = diff in state.selectedDifficulties
                            OutlinedButton(
                                onClick = {
                                    val newDiffs = state.selectedDifficulties.toMutableList()
                                    if (isSelected) {
                                        if (newDiffs.size > 1) newDiffs.remove(diff)
                                    } else newDiffs.add(diff)
                                    actions.onDifficultiesChange(newDiffs)
                                },
                                colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                                border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder,
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(40.dp)
                            ) { Text(diff.toString()) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Global Exclude
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Exclude Known Cards", modifier = Modifier.weight(1f))
                Switch(checked = state.excludeKnown, onCheckedChange = actions.onExcludeKnownChange)
            }
            Text("Available Pool: ${state.availableCardsCount} cards", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ToggleButton(text: String, isSelected: Boolean, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
    val border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder
    val containerColor = if (!enabled) Color.Black.copy(alpha = 0.5f) else colors.containerColor
    val contentColor = if (!enabled) Color.White else colors.contentColor

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        border = border,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, maxLines = 1)
    }
}

@Composable
fun ToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
        border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder,
        // Reduce horizontal padding to 4.dp (or whatever size you prefer)
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
    }
}



