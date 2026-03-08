package net.ericclark.studiare.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * A dialog for selecting decks and a format for export.
 * Updated to use dimensions for shapes and spacing.
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
    val dimensions = LocalStudiareDimensions.current
    val context = LocalContext.current
    var includeSets by rememberSaveable { mutableStateOf(true) }
    val selectedDecks = remember { mutableStateListOf<net.ericclark.studiare.data.DeckWithCards>() }
    var format by remember { mutableStateOf("JSON") }
    val listState = rememberLazyListState()

    val decksAndTheirSets = remember(decks) {
        val setPrefix = getText(context, R.string.set_)
        val mainDecks = decks.filter { it.deck.parentDeckId == null }.sortedBy { it.deck.name }
        val setsByParent = decks.filter { it.deck.parentDeckId != null }.groupBy { it.deck.parentDeckId!! }
        val setComparator = compareBy<net.ericclark.studiare.data.DeckWithCards, Int?>(nullsLast()) {
            it.deck.name.removePrefix(setPrefix).toIntOrNull()
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
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge), // Dynamic corner radius
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    getText(R.string.export_decks_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(bottom = dimensions.paddingMedium)
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
                    Text(if (areAllSelected) getText(R.string.deselect_all_button) else getText(R.string.all_select))
                }
                Spacer(Modifier.height(dimensions.spacingSmall))

                Box {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(dimensions.cornerRadiusSmall)
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
                                                MaterialTheme.colorScheme.surfaceContainer,
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
                                                MaterialTheme.colorScheme.surfaceContainer
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = getText(R.string.more_decks_available), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(dimensions.spacingSmall))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                        .clickable { includeSets = !includeSets }
                        .padding(vertical = dimensions.paddingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = includeSets, onCheckedChange = { includeSets = it })
                    Spacer(Modifier.width(dimensions.spacingMedium))
                    Text(getText(R.string.sets_include))
                }
                Spacer(Modifier.height(dimensions.spacingSmall))

                Text(getText(R.string.export_format), style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = format == "JSON", onClick = { format = "JSON" })
                    Text(getText(R.string.format_json))
                    Spacer(Modifier.width(dimensions.spacingMedium))
                    RadioButton(selected = format == "CSV", onClick = { format = "CSV" })
                    Text(getText(R.string.format_csv))
                }
                Spacer(Modifier.height(dimensions.spacingMedium))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(
                        onClick = { onExport(selectedDecks.toList(), format) },
                        enabled = selectedDecks.isNotEmpty()
                    ) {
                        Text(getText(R.string.export))
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
    val dimensions = LocalStudiareDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(
                start = if (isSet) dimensions.paddingLarge else dimensions.paddingMedium,
                end = dimensions.paddingMedium,
                top = 2.dp, // slight touch target improvement
                bottom = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
        Spacer(Modifier.width(dimensions.spacingMedium))
        Text(deck.deck.name, style = MaterialTheme.typography.bodyLarge)
    }
}