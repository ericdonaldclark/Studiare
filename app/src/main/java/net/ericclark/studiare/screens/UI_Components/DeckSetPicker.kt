package net.ericclark.studiare.screens.UI_Components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.data.naturalOrderComparator
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.pressSquish

/**
 * The common case: the caller just keeps a plain selection list. Thin wrapper around the lambda-
 * based [DeckSetPickerGrid] overload below — see that one for the full doc comment. [selectedDecks]
 * is the caller-owned selection the user can go on to read after this composes (add/removed in
 * place, so the caller sees every change live).
 */
@Composable
fun DeckSetPickerGrid(
    decks: List<DeckWithCards>,
    selectedDecks: SnapshotStateList<DeckWithCards>,
    includeSets: Boolean = true,
    modifier: Modifier = Modifier
) {
    val rootDecks = remember(decks) { decks.filter { it.deck.parentDeckId == null } }
    val availableItemsToSelect = remember(includeSets, decks, rootDecks) {
        if (includeSets) decks else rootDecks
    }
    DeckSetPickerGrid(
        decks = decks,
        isSelected = { it in selectedDecks },
        onToggleSelected = { if (it in selectedDecks) selectedDecks.remove(it) else selectedDecks.add(it) },
        selectedCount = selectedDecks.size,
        totalSelectableCount = availableItemsToSelect.size,
        onToggleSelectAll = {
            if (selectedDecks.size == availableItemsToSelect.size && availableItemsToSelect.isNotEmpty()) {
                selectedDecks.clear()
            } else {
                selectedDecks.clear()
                selectedDecks.addAll(availableItemsToSelect)
            }
        },
        includeSets = includeSets,
        modifier = modifier
    )
}

/**
 * The deck/set multi-select tree — a "Select all"/"Deselect all" button, a live "N / M selected"
 * count, and a bordered, scrollable tree with top/bottom scroll-shadow indicators — originally
 * built for [net.ericclark.studiare.screens.Dialogs.ExportDecksDialog] and promoted here so any
 * other picker (e.g. Settings → Deck Options' "Selected current decks" scope,
 * [net.ericclark.studiare.screens.Dialogs.AdvancedSelectionDialog]'s cross-level picker) can reuse
 * the exact same widget instead of a second, divergent one.
 *
 * Selection itself is read/written through [isSelected]/[onToggleSelected]/[selectedCount]/
 * [totalSelectableCount]/[onToggleSelectAll] rather than a concrete collection type, since callers
 * disagree on where their selection actually lives — a dialog-local list (see the
 * [SnapshotStateList] overload above) versus a shared [net.ericclark.studiare.screens.UI_Components.SelectionController]
 * keyed by id. [decks] is every deck/set available to pick from. [includeSets] matches
 * [net.ericclark.studiare.screens.Dialogs.ExportDecksDialog]'s own "Include sets" checkbox: false
 * restricts the tree to root decks only, still showing nothing below depth 0 — [totalSelectableCount]
 * is the caller's responsibility to compute consistently with whatever it passes here.
 */
@Composable
fun DeckSetPickerGrid(
    decks: List<DeckWithCards>,
    isSelected: (DeckWithCards) -> Boolean,
    onToggleSelected: (DeckWithCards) -> Unit,
    selectedCount: Int,
    totalSelectableCount: Int,
    onToggleSelectAll: () -> Unit,
    includeSets: Boolean = true,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    // Starts empty — every node with children begins collapsed, not expanded.
    val expandedDeckIds = remember { mutableStateSetOf<String>() }
    val listState = rememberLazyListState()

    // Sets can themselves have sets, arbitrarily deep — so this is a genuine tree, not just two
    // levels. Group once by parent id; `childrenOf`/`rootDecks` below walk it recursively.
    // naturalOrderComparator (shared with the decks list's own "A to Z" sort) sorts "Set 10"
    // after "Set 9" instead of right after "Set 1" the way a plain string compare would.
    val childrenByParent = remember(decks) {
        decks.filter { it.deck.parentDeckId != null }
            .groupBy { it.deck.parentDeckId!! }
            .mapValues { (_, children) -> children.sortedWith(compareBy(naturalOrderComparator) { it.deck.name }) }
    }
    val rootDecks = remember(decks) { decks.filter { it.deck.parentDeckId == null }.sortedWith(compareBy(naturalOrderComparator) { it.deck.name }) }
    fun childrenOf(deckId: String): List<DeckWithCards> = childrenByParent[deckId] ?: emptyList()
    fun hasChildren(deckId: String): Boolean = childrenByParent[deckId]?.isNotEmpty() == true

    val areAllSelected = selectedCount == totalSelectableCount && totalSelectableCount > 0

    val canScrollUp by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val canScrollDown by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            // A merely-peeking last item still counts as "visible" to LazyListState, so an index
            // comparison alone misses a barely-clipped final item — also require it to be fully
            // visible (bottom not past the viewport) before saying there's nothing left to scroll to.
            val viewportEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
            lastVisibleItem.index < layoutInfo.totalItemsCount - 1 || lastVisibleItem.offset + lastVisibleItem.size > viewportEnd
        }
    }

    fun toggleExpanded(deckId: String) {
        if (deckId in expandedDeckIds) expandedDeckIds.remove(deckId) else expandedDeckIds.add(deckId)
    }

    Column(modifier) {
        val selectAllInteractionSource = remember { MutableInteractionSource() }
        TextButton(
            onClick = onToggleSelectAll,
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
            "$selectedCount / $totalSelectableCount selected",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(dimensions.spacingSmall))

        Box {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(min = 100.dp, max = 240.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusSmall))
            ) {
                // A local recursive fun emitting `item { }` calls — Compose has no issue with
                // recursion inside a LazyListScope builder lambda, and this is the only way to
                // interleave each node directly beneath its parent at arbitrary depth (sets can
                // have their own sets) instead of grouping everything by level.
                fun LazyListScope.emitNode(node: DeckWithCards, depth: Int) {
                    val nodeHasChildren = includeSets && hasChildren(node.deck.id)
                    item(key = "deck-${node.deck.id}") {
                        DeckSetTreeRow(
                            deck = node,
                            depth = depth,
                            isSelected = isSelected(node),
                            onToggleSelected = { onToggleSelected(node) },
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
                                    colors = listOf(MaterialTheme.colorScheme.surfaceContainer, Color.Transparent)
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
                                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainer)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = getText(R.string.more_decks_available), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
 * widget's whole purpose is picking decks, not browsing them).
 *
 * Tapping the checkbox or the row body (anything but the chevron) toggles **selection**; tapping
 * the chevron toggles **expand/collapse** only — two independent concerns on one row. Selection is
 * never cascaded parent↔child.
 */
@Composable
private fun DeckSetTreeRow(
    deck: DeckWithCards,
    depth: Int,
    isSelected: Boolean,
    onToggleSelected: () -> Unit,
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "deckSetTreeChevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressSquish(interactionSource, 0.95f)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onToggleSelected() }
            .padding(
                start = (dimensions.paddingSmall + dimensions.paddingSmall * depth),
                end = dimensions.paddingSmall,
                top = 2.dp, // Tight vertical spacing
                bottom = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
        Spacer(Modifier.width(8.dp))
        Text(
            deck.deck.name,
            style = if (depth == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (hasChildren) {
            val chevronInteractionSource = remember { MutableInteractionSource() }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) getText(R.string.collapse) else getText(R.string.expand),
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = chevronInteractionSource, indication = LocalIndication.current) { onToggleExpanded() }
                    .graphicsLayer { rotationZ = chevronRotation }
                    .padding(4.dp)
            )
        }
    }
}
