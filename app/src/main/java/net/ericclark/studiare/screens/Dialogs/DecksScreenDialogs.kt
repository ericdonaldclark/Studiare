package net.ericclark.studiare.screens.Dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.*
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.data.*
import androidx.compose.foundation.LocalIndication

@Composable
fun ImportSelectionDialog(
    data: ImportSelectionData,
    onDismiss: () -> Unit,
    onConfirm: (selectedToImport: Set<String>, selectedToOverwrite: Set<String>) -> Unit
) {
    val allDecks = remember(data) { data.parsedDecks.map { it.deck } }
    val selectedImportIds =
        remember(data) { mutableStateListOf(*allDecks.map { it.id }.toTypedArray()) }
    val selectedOverwriteIds =
        remember(data) { mutableStateListOf(*data.conflictingDeckIds.toTypedArray()) }
    // Sets start collapsed under their main deck — only a summary shows until expanded.
    val expandedDeckIds = remember(data) { mutableStateSetOf<String>() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val areAllSelected = selectedImportIds.size == allDecks.size && allDecks.isNotEmpty()
    val canScrollUp by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val canScrollDown by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            // An item merely peeking a sliver into the viewport still counts as "visible" to
            // LazyListState, so comparing indices alone misses a barely-clipped last item (it's
            // the final index, so the index check says "nothing more to scroll to") — check that
            // it's also fully visible (its bottom doesn't extend past the viewport) before
            // concluding there's nothing left to scroll to.
            val viewportEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
            lastVisibleItem.index < layoutInfo.totalItemsCount - 1 || lastVisibleItem.offset + lastVisibleItem.size > viewportEnd
        }
    }
    val deckGroups = remember(data) {
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
        val mainDecks = allDecks.filter { it.parentDeckId == null }.sortedBy { it.name }
        val setsByParentId =
            allDecks.filter { it.parentDeckId != null }.groupBy { it.parentDeckId!! }
        val setComparator =
            Comparator<Deck> { d1, d2 -> naturalOrderComparator.compare(d1.name, d2.name) }
        mainDecks.map { mainDeck ->
            mainDeck to (setsByParentId[mainDeck.id]?.sortedWith(
                setComparator
            ) ?: emptyList())
        }
    }

    val dimensions = net.ericclark.studiare.ui.theme.LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        val maxDialogHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).heightIn(min = 280.dp, max = maxDialogHeight)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(dimensions.paddingLarge)) {
                Text(
                    getText(R.string.select_decks_to_import),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingSmall))
                val selectAllInteractionSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = {
                        if (areAllSelected) selectedImportIds.clear()
                        else {
                            selectedImportIds.clear()
                            selectedImportIds.addAll(allDecks.map { it.id })
                        }
                    },
                    interactionSource = selectAllInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressSquish(selectAllInteractionSource, 0.95f),
                    contentPadding = PaddingValues(horizontal = dimensions.paddingSmall, vertical = 4.dp),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(if (areAllSelected) getText(R.string.deselect_all_button) else getText(R.string.all_select))
                }
                Text(
                    "${selectedImportIds.size} / ${allDecks.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.heightIn(min = 150.dp, max = maxDialogHeight * 0.6f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusSmall))
                    ) {
                    deckGroups.forEach { (mainDeck, sets) ->
                        item(key = mainDeck.id) {
                            val isExpanded = mainDeck.id in expandedDeckIds
                            // Only counts sets that will actually be overwritten: still checked
                            // for import AND still set to Overwrite — unchecking a set (or
                            // flipping it to New Copy) means it's skipped/copied instead, so it
                            // drops out of this count as soon as the user changes either toggle.
                            val willOverwriteCount = sets.count {
                                it.id in data.conflictingDeckIds && it.id in selectedImportIds && it.id in selectedOverwriteIds
                            }
                            val collapsedSummary = when {
                                sets.isEmpty() || isExpanded -> null
                                willOverwriteCount > 0 -> pluralStringResource(R.plurals.import_sets_overwrite_summary, sets.size, willOverwriteCount, sets.size)
                                else -> pluralStringResource(R.plurals.import_sets_summary, sets.size, sets.size)
                            }
                            ImportDeckItem(
                                deck = mainDeck,
                                isConflicting = mainDeck.id in data.conflictingDeckIds,
                                isSelected = mainDeck.id in selectedImportIds,
                                isOverwrite = mainDeck.id in selectedOverwriteIds,
                                onToggleSelected = {
                                    if (mainDeck.id in selectedImportIds) selectedImportIds.remove(mainDeck.id) else selectedImportIds.add(mainDeck.id)
                                },
                                onToggleOverwrite = {
                                    if (mainDeck.id in selectedOverwriteIds) selectedOverwriteIds.remove(mainDeck.id) else selectedOverwriteIds.add(mainDeck.id)
                                },
                                hasChildren = sets.isNotEmpty(),
                                isExpanded = isExpanded,
                                onToggleExpanded = {
                                    if (isExpanded) expandedDeckIds.remove(mainDeck.id) else expandedDeckIds.add(mainDeck.id)
                                },
                                collapsedSummary = collapsedSummary,
                                collapsedSummaryIsWarning = willOverwriteCount > 0
                            )
                        }
                        if (mainDeck.id in expandedDeckIds) {
                            items(sets, key = { it.id }) { set ->
                                ImportDeckItem(
                                    deck = set,
                                    isConflicting = set.id in data.conflictingDeckIds,
                                    isSelected = set.id in selectedImportIds,
                                    isOverwrite = set.id in selectedOverwriteIds,
                                    onToggleSelected = {
                                        if (set.id in selectedImportIds) selectedImportIds.remove(set.id) else selectedImportIds.add(set.id)
                                    },
                                    onToggleOverwrite = {
                                        if (set.id in selectedOverwriteIds) selectedOverwriteIds.remove(set.id) else selectedOverwriteIds.add(set.id)
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
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                            MaterialTheme.colorScheme.surfaceContainerHigh
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
                Spacer(Modifier.height(dimensions.spacingLarge))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Button(
                        onClick = { onConfirm(selectedImportIds.toSet(), selectedOverwriteIds.toSet()) },
                        enabled = selectedImportIds.isNotEmpty(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) { Text(getText(R.string.import_selected)) }
                }
            }
        }
    }
}

@Composable
internal fun ImportDeckItem(
    deck: Deck,
    isConflicting: Boolean,
    isSelected: Boolean,
    isOverwrite: Boolean,
    onToggleSelected: () -> Unit,
    onToggleOverwrite: () -> Unit,
    isSet: Boolean = false,
    hasChildren: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpanded: () -> Unit = {},
    collapsedSummary: String? = null,
    collapsedSummaryIsWarning: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "importDeckChevron")
    Column(
        Modifier
            .fillMaxWidth()
            .pressSquish(interactionSource, 0.95f)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onToggleSelected() }
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .padding(start = if (isSet) 24.dp else 0.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = isSelected, onCheckedChange = null)
            Spacer(Modifier.width(16.dp))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(deck.name, style = MaterialTheme.typography.bodyLarge)
                if (!isConflicting) {
                    Text(
                        " " + getText(R.string.import_no_conflict),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (hasChildren) {
                val chevronInteractionSource = remember { MutableInteractionSource() }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) getText(R.string.collapse) else getText(R.string.expand),
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = chevronInteractionSource,
                            indication = LocalIndication.current
                        ) { onToggleExpanded() }
                        .graphicsLayer { rotationZ = chevronRotation }
                        .padding(4.dp)
                )
            }
        }
        if (isConflicting) {
            // Same pill FilterChip style as Create Study Session's Category/Mode rows
            // (TypedChipGrid) — plain label, no checkmark; selection reads from color alone, so
            // toggling never changes either chip's size.
            Box(modifier = Modifier.padding(start = 40.dp, top = 4.dp)) {
                net.ericclark.studiare.screens.UI_Components.TypedChipGrid(
                    items = listOf(true, false),
                    selected = isOverwrite,
                    labelFor = { if (it) getText(R.string.overwrite) else getText(R.string.new_copy) },
                    onSelected = { overwrite -> if (overwrite != isOverwrite) onToggleOverwrite() }
                )
            }
            if (isSelected && isOverwrite) {
                Text(
                    getText(if (isSet) R.string.import_overwrite_warning_set else R.string.import_overwrite_warning),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                )
            }
        }
        if (collapsedSummary != null) {
            Text(
                collapsedSummary,
                style = MaterialTheme.typography.labelMedium,
                color = if (collapsedSummaryIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 40.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun TopSliderDialogSection(
    options: List<String>,
    selectedMode: String,
    onModeChange: (String) -> Unit
) {
    net.ericclark.studiare.screens.UI_Components.ConnectedButtonGroup(
        items = options,
        selected = selectedMode,
        onSelected = onModeChange,
        labelFor = { it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    )
}


@Composable
fun DuplicateWarningDialog(
    result: DuplicateCheckResult,
    onDismiss: () -> Unit,
    onConfirmRemove: () -> Unit,
    onConfirmSaveAnyway: () -> Unit
) {
    val dimensions = net.ericclark.studiare.ui.theme.LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(getText(R.string.duplicates_found), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingSmall))
                Column {
                    Text(stringResource(R.string.duplicates_found_message, result.deckName))
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(result.duplicates) { duplicate ->
                            Text(
                                stringResource(
                                    R.string.duplicate_item_format,
                                    duplicate.text,
                                    duplicate.count
                                ), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(dimensions.spacingLarge))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onConfirmRemove, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.remove_and_save)) }
                    TextButton(onClick = onConfirmSaveAnyway, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.save_anyway)) }
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
fun DeckSortDialog(
    currentSortMode: DeckSortMode,
    showPinnedFirst: Boolean,
    onDismiss: () -> Unit,
    onSortModeSelected: (DeckSortMode) -> Unit,
    onShowPinnedFirstChange: (Boolean) -> Unit
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

    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusDialog), // M3 Expressive Dialog Shape
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = getText(R.string.sort_decks),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(getText(R.string.show_pinned_first), modifier = Modifier.weight(1f))
                    StudiareSwitch(checked = showPinnedFirst, onCheckedChange = onShowPinnedFirstChange)
                }
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
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.cancel))
                    }
                }
            }
        }
    }
}
