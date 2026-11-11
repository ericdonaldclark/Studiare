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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.data.naturalOrderComparator
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.pressSquish

/**
 * Deck/set destination picker for moving one or more cards, reusing
 * [ChangePresetDeckDialog]'s radio-button tree-building approach (same recursive `emitNode`,
 * depth-scaled indentation, expand/collapse chevrons, scroll-position gradient hints) but with a
 * "Create New Deck" entry pinned above the tree instead of that dialog's Pin/Save/Start-session
 * actions — this dialog only ever has one outcome (move), to either an existing destination or a
 * brand-new one.
 */
@Composable
fun MoveCardsDialog(
    decks: List<DeckWithCards>,
    onDismiss: () -> Unit,
    onMoveToExisting: (deckId: String) -> Unit,
    onMoveToNewDeck: (name: String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var selectedDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingNewDeck by rememberSaveable { mutableStateOf(false) }
    var newDeckName by rememberSaveable { mutableStateOf("") }
    val expandedDeckIds = remember { mutableStateSetOf<String>() }
    val listState = rememberLazyListState()

    val childrenByParent = remember(decks) {
        decks.filter { it.deck.parentDeckId != null }
            .groupBy { it.deck.parentDeckId!! }
            .mapValues { (_, children) -> children.sortedWith(compareBy(naturalOrderComparator) { it.deck.name }) }
    }
    val rootDecks = remember(decks) { decks.filter { it.deck.parentDeckId == null }.sortedWith(compareBy(naturalOrderComparator) { it.deck.name }) }
    fun childrenOf(deckId: String): List<DeckWithCards> = childrenByParent[deckId] ?: emptyList()
    fun hasChildren(deckId: String): Boolean = childrenByParent[deckId]?.isNotEmpty() == true

    fun toggleExpanded(deckId: String) {
        if (deckId in expandedDeckIds) expandedDeckIds.remove(deckId) else expandedDeckIds.add(deckId)
    }

    fun selectExisting(deckId: String) {
        creatingNewDeck = false
        selectedDeckId = deckId
    }

    val canScrollUp by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val canScrollDown by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index < listState.layoutInfo.totalItemsCount - 1
        }
    }

    val canMove = if (creatingNewDeck) newDeckName.isNotBlank() else selectedDeckId != null

    AnimatedDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Card(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).heightIn(max = maxDialogHeight),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    getText(R.string.move_cards_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    getText(R.string.move_cards_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = dimensions.spacingSmall).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                val createNewInteractionSource = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressSquish(createNewInteractionSource, 0.98f)
                        .clickable(interactionSource = createNewInteractionSource, indication = LocalIndication.current) {
                            creatingNewDeck = true
                            selectedDeckId = null
                        }
                        .padding(vertical = dimensions.paddingSmall, horizontal = dimensions.paddingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = creatingNewDeck, onClick = { creatingNewDeck = true; selectedDeckId = null })
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(getText(R.string.create_new_deck), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                AnimatedVisibility(visible = creatingNewDeck) {
                    OutlinedTextField(
                        value = newDeckName,
                        onValueChange = { newDeckName = it },
                        singleLine = true,
                        label = { Text(getText(R.string.new_deck_name_hint)) },
                        modifier = Modifier.fillMaxWidth().padding(start = 40.dp, bottom = dimensions.spacingSmall)
                    )
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

                Box {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .heightIn(min = 100.dp, max = 320.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusSmall))
                    ) {
                        fun LazyListScope.emitNode(node: DeckWithCards, depth: Int) {
                            val nodeHasChildren = hasChildren(node.deck.id)
                            item(key = "deck-${node.deck.id}") {
                                MoveCardsDeckTreeItem(
                                    deck = node,
                                    depth = depth,
                                    isSelected = !creatingNewDeck && node.deck.id == selectedDeckId,
                                    onSelect = { selectExisting(node.deck.id) },
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
                                modifier = Modifier.fillMaxWidth().height(24.dp).background(
                                    brush = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainer, Color.Transparent))
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
                                modifier = Modifier.fillMaxWidth().height(24.dp).background(
                                    brush = Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainer))
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = getText(R.string.more_decks_available), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.cancel))
                    }
                    val moveInteractionSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = { if (creatingNewDeck) onMoveToNewDeck(newDeckName.trim()) else selectedDeckId?.let(onMoveToExisting) },
                        enabled = canMove,
                        interactionSource = moveInteractionSource,
                        modifier = Modifier.pressSquish(moveInteractionSource, 0.95f),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.move))
                    }
                }
            }
        }
    }
}

/** [MoveCardsDialog]'s tree row — same radio-button/chevron shape as
 * [ChangePresetDeckTreeItem], duplicated rather than shared since that one is private to its own
 * single-select-preset-location use. */
@Composable
private fun MoveCardsDeckTreeItem(
    deck: DeckWithCards,
    depth: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "moveCardsDeckChevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressSquish(interactionSource, 0.95f)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onSelect() }
            .padding(
                start = (dimensions.paddingSmall + dimensions.paddingSmall * depth),
                end = dimensions.paddingSmall,
                top = 2.dp,
                bottom = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
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
