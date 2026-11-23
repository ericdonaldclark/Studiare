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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.Card as CardEntity
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.data.naturalOrderComparator
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.pressSquish

/**
 * Power-user bulk add/remove: lets a single tag or template be applied to, or stripped from, any
 * mix of whole decks and individual cards, without opening each one. Copies [ExportDecksDialog]'s
 * tree-with-checkboxes mechanics (expand/collapse, scroll-edge gradients, Select All) but flat —
 * sets are deliberately excluded here, so this only ever shows top-level decks, each expandable to
 * reveal its own cards (alphanumerical order), individually checkable.
 *
 * A row's checkbox always reflects the *card-level* truth, never a separately-tracked deck flag —
 * a deck's checkbox is fully checked only when every one of its own cards is checked, and tapping
 * it checks/unchecks all of them at once. [initialMatch] seeds the starting checked state per card
 * (so unchecking an already-matching card means "remove on Apply," checking an unmatched one means
 * "add"); a deck whose cards are only partially checked gets a leading interpunct ("·") rather than
 * the usual indeterminate-checkbox styling, per the exact visual called for.
 *
 * Nothing is loaded eagerly per card — a deck's card rows are only ever emitted into the
 * [LazyColumn] while that deck is expanded, so collapsing one both stops composing them and (next
 * recomposition) disposes whatever was composed, the same way [ExportDecksDialog]'s own
 * nested-set rows already work.
 */
@Composable
fun BulkCardSelectionDialog(
    /** e.g. "Add/Remove Tag" — the specific tag/template name is [subtitleContent]'s job, shown
     * on its own row underneath rather than appended to this title. */
    titlePrefix: String,
    /** The tag/template name, on its own row under the title — a colored [TagChip] for tags
     * (so its color is visible here too), plain [Text] for templates. */
    subtitleContent: @Composable () -> Unit,
    decks: List<DeckWithCards>,
    initialMatch: (CardEntity) -> Boolean,
    onDismiss: () -> Unit,
    onApply: (addCardIds: Set<String>, removeCardIds: Set<String>) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current

    // Sets excluded entirely — only top-level decks are shown.
    val rootDecks = remember(decks) { decks.filter { it.deck.parentDeckId == null }.sortedWith(compareBy(naturalOrderComparator) { it.deck.name }) }

    val allCardsById = remember(rootDecks) { rootDecks.flatMap { it.cards }.associateBy { it.id } }
    val initiallyMatchingIds = remember(decks) { allCardsById.values.filter(initialMatch).map { it.id }.toSet() }
    var checkedCardIds by remember(decks) { mutableStateOf(initiallyMatchingIds) }

    // Starts empty — every node with expandable content begins collapsed.
    val expandedDeckIds = remember { mutableStateSetOf<String>() }
    val listState = rememberLazyListState()

    fun toggleExpanded(deckId: String) {
        if (deckId in expandedDeckIds) expandedDeckIds.remove(deckId) else expandedDeckIds.add(deckId)
    }
    fun toggleCard(cardId: String) {
        checkedCardIds = if (cardId in checkedCardIds) checkedCardIds - cardId else checkedCardIds + cardId
    }
    fun deckCheckState(deck: DeckWithCards): Pair<Boolean, Boolean> { // (fullyChecked, partial)
        val ids = deck.cards.map { it.id }
        if (ids.isEmpty()) return false to false
        val checkedCount = ids.count { it in checkedCardIds }
        return (checkedCount == ids.size) to (checkedCount in 1 until ids.size)
    }
    fun toggleDeck(deck: DeckWithCards) {
        val ids = deck.cards.map { it.id }
        val (fullyChecked, _) = deckCheckState(deck)
        checkedCardIds = if (fullyChecked) checkedCardIds - ids else checkedCardIds + ids
    }

    val allCardIds = remember(decks) { allCardsById.keys }
    val areAllSelected = checkedCardIds.size == allCardIds.size && allCardIds.isNotEmpty()

    val canScrollUp by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val canScrollDown by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            val viewportEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
            lastVisibleItem.index < layoutInfo.totalItemsCount - 1 || lastVisibleItem.offset + lastVisibleItem.size > viewportEnd
        }
    }

    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            // Content-driven height (capped, not forced to a fixed fraction of the screen) — a
            // short deck list otherwise left a large dead gap below Cancel/Apply.
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).heightIn(max = 700.dp),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Text(
                        titlePrefix,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = dimensions.spacingSmall), contentAlignment = Alignment.Center) {
                        subtitleContent()
                    }

                    val selectAllInteractionSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { checkedCardIds = if (areAllSelected) emptySet() else allCardIds.toSet() },
                        interactionSource = selectAllInteractionSource,
                        modifier = Modifier.fillMaxWidth().pressSquish(selectAllInteractionSource, 0.95f),
                        contentPadding = PaddingValues(horizontal = dimensions.paddingSmall, vertical = 4.dp),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(if (areAllSelected) getText(R.string.deselect_all_button) else getText(R.string.all_select))
                    }
                    Text(
                        "${checkedCardIds.size} / ${allCardIds.size} selected",
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
                                .heightIn(min = 100.dp, max = 400.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusSmall))
                        ) {
                            // Flat: just this deck's row, and (while expanded) its own cards —
                            // no nested-set recursion.
                            fun LazyListScope.emitDeck(deck: DeckWithCards) {
                                val isExpanded = deck.deck.id in expandedDeckIds
                                val hasCards = deck.cards.isNotEmpty()
                                val (fullyChecked, partial) = deckCheckState(deck)
                                item(key = "deck-${deck.deck.id}") {
                                    DeckBulkSelectTreeItem(
                                        name = deck.deck.name,
                                        isChecked = fullyChecked,
                                        isPartial = partial,
                                        onToggleChecked = { toggleDeck(deck) },
                                        hasExpandableContent = hasCards,
                                        isExpanded = isExpanded,
                                        onToggleExpanded = { toggleExpanded(deck.deck.id) }
                                    )
                                }
                                // Only emitted while expanded — nothing here is composed (or stays
                                // composed) for a collapsed deck.
                                if (isExpanded) {
                                    val sortedCards = deck.cards.sortedWith(compareBy(naturalOrderComparator) { it.front })
                                    sortedCards.forEach { card ->
                                        item(key = "card-${card.id}") {
                                            CardBulkSelectTreeItem(
                                                frontText = card.front,
                                                isChecked = card.id in checkedCardIds,
                                                onToggleChecked = { toggleCard(card.id) }
                                            )
                                        }
                                    }
                                }
                            }
                            rootDecks.forEach { deck -> emitDeck(deck) }
                        }

                        Column(modifier = Modifier.align(Alignment.TopCenter)) {
                            AnimatedVisibility(
                                visible = canScrollUp,
                                enter = slideInVertically() + fadeIn() + expandVertically(),
                                exit = slideOutVertically() + fadeOut() + shrinkVertically()
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(24.dp).background(
                                        brush = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainer, Color.Transparent))
                                    ),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                            AnimatedVisibility(
                                visible = canScrollDown,
                                enter = slideInVertically() + fadeIn() + expandVertically(),
                                exit = slideOutVertically() + fadeOut() + shrinkVertically()
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(24.dp).background(
                                        brush = Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainer))
                                    ),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = getText(R.string.more_decks_available), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(
                        onClick = {
                            val toAdd = checkedCardIds - initiallyMatchingIds
                            val toRemove = initiallyMatchingIds - checkedCardIds
                            onApply(toAdd, toRemove)
                        },
                        enabled = checkedCardIds != initiallyMatchingIds,
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) { Text(getText(R.string.apply)) }
                }
            }
        }
    }
}

/**
 * One deck row — modeled on [ExportDecksDialog]'s `DeckExportTreeItem`, with a leading interpunct
 * when [isPartial] (some but not all of this deck's own cards are checked) instead of an
 * indeterminate-checkbox glyph. Tapping the checkbox or row body toggles every one of this deck's
 * cards at once; tapping the chevron only expands/collapses — independent concerns, same as the
 * export dialog's version.
 */
@Composable
private fun DeckBulkSelectTreeItem(
    name: String,
    isChecked: Boolean,
    isPartial: Boolean,
    onToggleChecked: () -> Unit,
    hasExpandableContent: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "bulkSelectDeckChevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressSquish(interactionSource, 0.95f)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onToggleChecked() }
            .padding(start = dimensions.paddingSmall, end = dimensions.paddingSmall, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Fixed-width always, whether or not the dot is actually drawn — otherwise every row
        // without one shifts left relative to the rows that have it.
        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            if (isPartial) {
                Text(
                    "·",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Checkbox(checked = isChecked, onCheckedChange = { onToggleChecked() })
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (hasExpandableContent) {
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

/** One card leaf row — only ever composed while its owning deck is expanded (see
 * [BulkCardSelectionDialog]'s doc comment on why nothing is loaded eagerly). */
@Composable
private fun CardBulkSelectTreeItem(
    frontText: String,
    isChecked: Boolean,
    onToggleChecked: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressSquish(interactionSource, 0.95f)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onToggleChecked() }
            // Deck row's own start padding, plus its interpunct slot's width — so the dash below
            // starts exactly where the deck row's checkbox starts, not where its interpunct dot
            // does. This card's own checkbox then lands one slot further right than the deck's.
            .padding(start = dimensions.paddingSmall + 16.dp, end = dimensions.paddingSmall, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            Text("-", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = isChecked, onCheckedChange = { onToggleChecked() })
        Spacer(Modifier.width(8.dp))
        Text(
            frontText.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
