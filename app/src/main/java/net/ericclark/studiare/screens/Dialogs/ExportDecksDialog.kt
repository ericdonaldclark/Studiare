package net.ericclark.studiare.screens.Dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.screens.UI_Components.TypedChipRow
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.pressSquish


/**
 * A dialog for selecting decks and a format for export.
 * @param decks The list of all available decks.
 * @param onDismiss Callback for when the dialog is dismissed.
 * @param onExport Callback that provides the list of selected decks and the chosen format.
 * @param actionLabel The confirm button's label — "Export" by default, but Settings' "Select
 * Format and Content" reuses this same dialog with "Apply" instead, to persist a choice for
 * Scheduled Export Backup rather than writing a file.
 * @param initialFormat Preselects the format chip row — used by the Settings reuse above so
 * reopening it shows the currently-configured format, not always "JSON".
 * @param initialSelectedDeckIds When non-null, only these decks start selected instead of every
 * deck — used by the Settings reuse above to restore a previously-saved content selection.
 * @param initialIncludeMetadata Preselects the "include metadata" checkbox.
 * @param title The dialog's headline — "Export Decks" by default; Settings' reuse above passes
 * "Select Format and Content" instead, since that flow never actually exports a file.
 */
@Composable
fun ExportDecksDialog(
    decks: List<net.ericclark.studiare.data.DeckWithCards>,
    onDismiss: () -> Unit,
    onExport: (selectedDecks: List<net.ericclark.studiare.data.DeckWithCards>, format: String, includeMetadata: Boolean) -> Unit,
    actionLabel: String? = null,
    initialFormat: String = "JSON",
    initialSelectedDeckIds: Set<String>? = null,
    initialIncludeMetadata: Boolean = true,
    title: String? = null
) {
    val dimensions = LocalStudiareDimensions.current
    val context = LocalContext.current
    var includeSets by rememberSaveable { mutableStateOf(true) }
    var includeMetadata by rememberSaveable { mutableStateOf(initialIncludeMetadata) }
    val selectedDecks = remember { mutableStateListOf<net.ericclark.studiare.data.DeckWithCards>() }
    var format by remember { mutableStateOf(initialFormat) }
    var hasAppliedInitialSelection by remember { mutableStateOf(false) }
    // Starts empty — every node with children begins collapsed, not expanded.
    val expandedDeckIds = remember { mutableStateSetOf<String>() }
    val listState = rememberLazyListState()

    // Sets can themselves have sets, arbitrarily deep — so this is a genuine tree, not just two
    // levels. Group once by parent id; `childrenOf`/`rootDecks` below walk it recursively.
    val childrenByParent = remember(decks) {
        val setPrefix = getText(context, R.string.set_)
        val setComparator = compareBy<net.ericclark.studiare.data.DeckWithCards, Int?>(nullsLast()) {
            it.deck.name.removePrefix(setPrefix).toIntOrNull()
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.deck.name }
        decks.filter { it.deck.parentDeckId != null }
            .groupBy { it.deck.parentDeckId!! }
            .mapValues { (_, children) -> children.sortedWith(setComparator) }
    }
    val rootDecks = remember(decks) { decks.filter { it.deck.parentDeckId == null }.sortedBy { it.deck.name } }
    fun childrenOf(deckId: String): List<net.ericclark.studiare.data.DeckWithCards> = childrenByParent[deckId] ?: emptyList()
    fun hasChildren(deckId: String): Boolean = childrenByParent[deckId]?.isNotEmpty() == true

    val availableItemsToSelect = remember(includeSets, decks, rootDecks) {
        if (includeSets) decks else rootDecks
    }

    LaunchedEffect(availableItemsToSelect) {
        selectedDecks.clear()
        if (initialSelectedDeckIds != null && !hasAppliedInitialSelection) {
            selectedDecks.addAll(availableItemsToSelect.filter { it.deck.id in initialSelectedDeckIds })
            hasAppliedInitialSelection = true
        } else {
            selectedDecks.addAll(availableItemsToSelect)
        }
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

    fun toggleSelected(deck: net.ericclark.studiare.data.DeckWithCards) {
        if (deck in selectedDecks) selectedDecks.remove(deck) else selectedDecks.add(deck)
    }

    fun toggleExpanded(deckId: String) {
        if (deckId in expandedDeckIds) expandedDeckIds.remove(deckId) else expandedDeckIds.add(deckId)
    }

    val labelForFormat: @Composable (String) -> String = { fmt ->
        when (fmt) {
            "CSV" -> getText(R.string.format_csv)
            "ANKI_APKG" -> getText(R.string.format_apkg_long)
            "ANKI_COLPKG" -> getText(R.string.format_colpkg_long)
            else -> getText(R.string.format_json)
        }
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        val maxDialogHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Card(
            modifier = Modifier.heightIn(max = maxDialogHeight),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // Dynamic corner radius
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
              // Everything except the action buttons scrolls, so short windows can still reach every option.
              Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(
                    title ?: getText(R.string.export_decks_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(bottom = dimensions.spacingSmall)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                val selectAllInteractionSource = remember { MutableInteractionSource() }
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
                        .pressSquish(selectAllInteractionSource, 0.95f),
                    contentPadding = PaddingValues(horizontal = dimensions.paddingSmall, vertical = 4.dp),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(if (areAllSelected) getText(R.string.deselect_all_button) else getText(R.string.all_select))
                }
                Text(
                    "${selectedDecks.size} / ${availableItemsToSelect.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(dimensions.spacingSmall))

                Box {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .heightIn(min = 100.dp, max = 240.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(dimensions.cornerRadiusSmall)
                            )
                    ) {
                        // A local recursive fun emitting `item { }` calls — Compose has no issue with
                        // recursion inside a LazyListScope builder lambda, and this is the only way to
                        // interleave each node directly beneath its parent at arbitrary depth (sets
                        // can have their own sets) instead of grouping everything by level.
                        fun LazyListScope.emitNode(node: net.ericclark.studiare.data.DeckWithCards, depth: Int) {
                            val nodeHasChildren = includeSets && hasChildren(node.deck.id)
                            item(key = "deck-${node.deck.id}") {
                                DeckExportTreeItem(
                                    deck = node,
                                    depth = depth,
                                    isSelected = node in selectedDecks,
                                    onToggleSelected = { toggleSelected(node) },
                                    hasChildren = nodeHasChildren,
                                    isExpanded = node.deck.id in expandedDeckIds,
                                    onToggleExpanded = { toggleExpanded(node.deck.id) }
                                )
                            }
                            if (nodeHasChildren && node.deck.id in expandedDeckIds) {
                                childrenOf(node.deck.id).forEach { child -> emitNode(child, depth + 1) }
                            }
                        }
                        rootDecks.forEach { root -> emitNode(root, 0) }
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
                ListItem(
                    headlineContent = { Text(text = getText(R.string.sets_include), color = MaterialTheme.colorScheme.onSurface) },
                    trailingContent = { Checkbox(checked = includeSets, onCheckedChange = { includeSets = it }, enabled = true) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressSquish(includeSetsInteractionSource, 0.95f)
                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                        .clickable(interactionSource = includeSetsInteractionSource, indication = LocalIndication.current) { includeSets = !includeSets }
                )

                // NEW: Include Metadata Checkbox
                val includeMetadataInteractionSource = remember { MutableInteractionSource() }
                ListItem(
                    headlineContent = { Text(text = getText(R.string.include_metadata), color = MaterialTheme.colorScheme.onSurface) },
                    supportingContent = { Text(text = getText(R.string.include_metadata_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { Checkbox(checked = includeMetadata, onCheckedChange = { includeMetadata = it }, enabled = true) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressSquish(includeMetadataInteractionSource, 0.95f)
                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                        .clickable(interactionSource = includeMetadataInteractionSource, indication = LocalIndication.current) { includeMetadata = !includeMetadata }
                )
                Spacer(Modifier.height(dimensions.spacingSmall))

                Text(getText(R.string.export_format), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(dimensions.spacingSmall))

                TypedChipRow(items = listOf("JSON", "CSV"), selected = format, labelFor = labelForFormat, onSelected = { format = it })
                Spacer(Modifier.height(dimensions.spacingSmall))
                TypedChipRow(items = listOf("ANKI_APKG", "ANKI_COLPKG"), selected = format, labelFor = labelForFormat, onSelected = { format = it })
                Spacer(Modifier.height(dimensions.spacingMedium))
              }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }

                    val exportInteractionSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = { onExport(selectedDecks.toList(), format, includeMetadata) },
                        interactionSource = exportInteractionSource,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 56.dp)
                            .pressSquish(exportInteractionSource, 0.95f),
                        enabled = selectedDecks.isNotEmpty(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(actionLabel ?: getText(R.string.export))
                    }
                }
            }
        }
    }
}

/**
 * One row in the deck/set tree, modeled on the visual style of the Decks screen's own tree view
 * (`NavigationDrawerNode.kt`'s `DrawerDeckHierarchyNode`) — depth-scaled indentation, an
 * expand/collapse chevron, bold text only at the root — but with none of that component's
 * navigation/overflow-menu/ViewModel coupling: no `NavController`, no `FlashcardViewModel`, no
 * tapping a row to open the deck, no three-dot menu. Instead it gets a leading checkbox (this
 * dialog's whole purpose is picking decks, not browsing them).
 *
 * Tapping the checkbox or the row body (anything but the chevron) toggles **selection**; tapping
 * the chevron toggles **expand/collapse** only — two independent concerns on one row. Selection is
 * never cascaded parent↔child.
 */
@Composable
private fun DeckExportTreeItem(
    deck: net.ericclark.studiare.data.DeckWithCards,
    depth: Int,
    isSelected: Boolean,
    onToggleSelected: () -> Unit,
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "deckExportTreeChevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressSquish(interactionSource, 0.95f)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onToggleSelected() }
            .padding(
                start = (dimensions.paddingSmall + dimensions.paddingSmall * depth),
                end = dimensions.paddingSmall,
                top = 2.dp, // Tight vertical spacing
                bottom = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelected() }
        )
        Spacer(Modifier.width(8.dp))
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
        } else {
            Spacer(Modifier.size(32.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            deck.deck.name,
            style = if (depth == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Normal
        )
    }
}
