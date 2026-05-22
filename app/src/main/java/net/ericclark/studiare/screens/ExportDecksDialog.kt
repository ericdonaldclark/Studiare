package net.ericclark.studiare.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.ericclark.studiare.LocalWindowWidthSizeClass
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
    onExport: (selectedDecks: List<net.ericclark.studiare.data.DeckWithCards>, format: String, includeMetadata: Boolean) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val context = LocalContext.current
    var includeSets by rememberSaveable { mutableStateOf(true) }
    var includeMetadata by rememberSaveable { mutableStateOf(true) }
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
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // Dynamic corner radius
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

                val selectAllInteractionSource = remember { MutableInteractionSource() }
                val isSelectAllPressed by selectAllInteractionSource.collectIsPressedAsState()
                val selectAllScale by animateFloatAsState(
                    targetValue = if (isSelectAllPressed) 0.95f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "selectAllSquish"
                )
                TextButton(
                    onClick = {
                        if (areAllSelected) {
                            selectedDecks.clear()
                        } else {
                            selectedDecks.clear()
                            selectedDecks.addAll(availableItemsToSelect)
                        }
                    },
                    interactionSource = selectAllInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .scale(selectAllScale)
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
                        AnimatedVisibility(
                            visible = canScrollUp,
                            enter = slideInVertically() + fadeIn() + expandVertically(),
                            exit = slideOutVertically() + fadeOut() + shrinkVertically()
                        ) {
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
                        AnimatedVisibility(
                            visible = canScrollDown,
                            enter = slideInVertically() + fadeIn() + expandVertically(),
                            exit = slideOutVertically() + fadeOut() + shrinkVertically()
                        ) {
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

                val includeSetsInteractionSource = remember { MutableInteractionSource() }
                val isIncludeSetsPressed by includeSetsInteractionSource.collectIsPressedAsState()
                val includeSetsScale by animateFloatAsState(
                    targetValue = if (isIncludeSetsPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "includeSetsSquish"
                )
                ListItem(
                    headlineContent = { Text(text = getText(R.string.sets_include), color = MaterialTheme.colorScheme.onSurface) },
                    trailingContent = { Checkbox(checked = includeSets, onCheckedChange = { includeSets = it }, enabled = true) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(includeSetsScale)
                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                        .clickable(interactionSource = includeSetsInteractionSource, indication = LocalIndication.current) { includeSets = !includeSets }
                )

                // NEW: Include Metadata Checkbox
                val includeMetadataInteractionSource = remember { MutableInteractionSource() }
                val isIncludeMetadataPressed by includeMetadataInteractionSource.collectIsPressedAsState()
                val includeMetadataScale by animateFloatAsState(
                    targetValue = if (isIncludeMetadataPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "includeMetadataSquish"
                )
                ListItem(
                    headlineContent = { Text(text = "Include Metadata", color = MaterialTheme.colorScheme.onSurface) },
                    supportingContent = { Text(text = "Export review history, stats, and dates", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { Checkbox(checked = includeMetadata, onCheckedChange = { includeMetadata = it }, enabled = true) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(includeMetadataScale)
                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                        .clickable(interactionSource = includeMetadataInteractionSource, indication = LocalIndication.current) { includeMetadata = !includeMetadata }
                )
                Spacer(Modifier.height(dimensions.spacingSmall))

                Text(getText(R.string.export_format), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(dimensions.spacingSmall))

                val windowWidthSizeClass = LocalWindowWidthSizeClass.current
                if (windowWidthSizeClass == WindowWidthSizeClass.Compact) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = when (format) {
                                "JSON" -> "JSON"
                                "CSV" -> "CSV"
                                "ANKI_APKG" -> ".apkg"
                                "ANKI_COLPKG" -> ".colpkg"
                                else -> format
                            },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(text = { Text("JSON") }, onClick = { format = "JSON"; expanded = false })
                            DropdownMenuItem(text = { Text("CSV") }, onClick = { format = "CSV"; expanded = false })
                            DropdownMenuItem(text = { Text(".apkg") }, onClick = { format = "ANKI_APKG"; expanded = false })
                            DropdownMenuItem(text = { Text(".colpkg") }, onClick = { format = "ANKI_COLPKG"; expanded = false })
                        }
                    }
                } else {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = format == "JSON",
                            onClick = { format = "JSON" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
                        ) { Text("JSON") }
                        SegmentedButton(
                            selected = format == "CSV",
                            onClick = { format = "CSV" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
                        ) { Text("CSV") }
                        SegmentedButton(
                            selected = format == "ANKI_APKG",
                            onClick = { format = "ANKI_APKG" },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
                        ) { Text(".apkg") }
                        SegmentedButton(
                            selected = format == "ANKI_COLPKG",
                            onClick = { format = "ANKI_COLPKG" },
                            shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
                        ) { Text(".colpkg") }
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }

                    val exportInteractionSource = remember { MutableInteractionSource() }
                    val isExportPressed by exportInteractionSource.collectIsPressedAsState()
                    val exportScale by animateFloatAsState(
                        targetValue = if (isExportPressed) 0.95f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "exportSquish"
                    )
                    Button(
                        onClick = { onExport(selectedDecks.toList(), format, includeMetadata) },
                        interactionSource = exportInteractionSource,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 56.dp)
                            .scale(exportScale),
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "itemSquish"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onToggle() }
            .padding(
                start = if (isSet) dimensions.paddingLarge else dimensions.paddingSmall,
                end = dimensions.paddingSmall,
                top = 2.dp, // Tight vertical spacing
                bottom = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
        // Tightened horizontal spacing between the checkbox and the text
        Spacer(Modifier.width(8.dp))
        Text(deck.deck.name, style = MaterialTheme.typography.bodyLarge)
    }
}