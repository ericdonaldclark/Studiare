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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin as FilledPushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.PushPin
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
import net.ericclark.studiare.screens.UI_Components.PinNamingDialog
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.pressSquish

/**
 * A single-select version of [ExportDecksDialog]'s deck/set tree — same tree-building logic
 * (recursive `emitNode`, depth-scaled indentation, expand/collapse chevrons, the scroll-position
 * gradient hints), but swaps its checkbox multi-select for a radio-button single-select, since
 * this dialog is for picking the one deck/set a [net.ericclark.studiare.data.SessionPreset]
 * should point at, not a set of decks to export. The currently-used deck/set starts selected and
 * pre-expanded down to it.
 *
 * The primary action is [onStartSession] — starts a session with the preset's settings against
 * the *selected* deck/set without touching the preset itself (its own `deckId` is untouched
 * unless the user explicitly commits via the pin/save icons below). Those mirror the
 * session-creation screen's own Pin-vs-Save convention (decision C of the plan file's "Session
 * Preset Card" entry), as icon buttons rather than text: while the selection matches
 * [initialSelectedDeckId] (nothing changed), the pin icon shows filled/"pinned" — tapping it
 * unpins (removes) this preset via [onUnpin]. The moment the selection diverges, the icon swaps
 * to outlined/"unpinned" and a Save icon appears alongside it: tapping Save calls [onSave] to
 * update the existing preset's `deckId` in place; tapping the now-outlined pin icon instead opens
 * [PinNamingDialog] and calls [onPinAsNew] to create a brand-new, separate preset pointed at the
 * new deck/set, leaving the original untouched.
 */
@Composable
fun ChangePresetDeckDialog(
    decks: List<DeckWithCards>,
    initialSelectedDeckId: String,
    defaultPinName: String,
    onDismiss: () -> Unit,
    onStartSession: (newDeckId: String) -> Unit,
    onSave: (newDeckId: String) -> Unit,
    onPinAsNew: (newDeckId: String, name: String?) -> Unit,
    onUnpin: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var selectedDeckId by rememberSaveable { mutableStateOf(initialSelectedDeckId) }
    val isDirty = selectedDeckId != initialSelectedDeckId
    val expandedDeckIds = remember { mutableStateSetOf<String>() }
    var showPinNameDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Sets can themselves have sets, arbitrarily deep — same genuine tree as ExportDecksDialog's.
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

    // Auto-expand every ancestor of the pre-selected node so it's visible without the user having
    // to manually drill down to find where the preset currently points.
    LaunchedEffect(Unit) {
        var parentId = decks.find { it.deck.id == initialSelectedDeckId }?.deck?.parentDeckId
        while (parentId != null) {
            expandedDeckIds.add(parentId)
            parentId = decks.find { it.deck.id == parentId }?.deck?.parentDeckId
        }
    }

    fun toggleExpanded(deckId: String) {
        if (deckId in expandedDeckIds) expandedDeckIds.remove(deckId) else expandedDeckIds.add(deckId)
    }

    val canScrollUp by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val canScrollDown by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index < listState.layoutInfo.totalItemsCount - 1
        }
    }

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
                    getText(R.string.change_preset_deck_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    getText(R.string.change_preset_deck_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = dimensions.spacingSmall).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Box {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .heightIn(min = 100.dp, max = 320.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusSmall))
                    ) {
                        // Same local-recursive-fun approach ExportDecksDialog's tree uses — the
                        // only way to interleave each node directly beneath its parent at
                        // arbitrary depth inside a LazyListScope builder.
                        fun LazyListScope.emitNode(node: DeckWithCards, depth: Int) {
                            val nodeHasChildren = hasChildren(node.deck.id)
                            item(key = "deck-${node.deck.id}") {
                                ChangePresetDeckTreeItem(
                                    deck = node,
                                    depth = depth,
                                    isSelected = node.deck.id == selectedDeckId,
                                    onSelect = { selectedDeckId = node.deck.id },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDirty) {
                            IconButton(onClick = { onSave(selectedDeckId) }) {
                                Icon(Icons.Default.Save, contentDescription = getText(R.string.save_preset))
                            }
                        }
                        IconButton(onClick = { if (isDirty) showPinNameDialog = true else onUnpin() }) {
                            Icon(
                                imageVector = if (isDirty) Icons.Outlined.PushPin else Icons.Default.FilledPushPin,
                                contentDescription = getText(R.string.pin_preset)
                            )
                        }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        val startInteractionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = { onStartSession(selectedDeckId) },
                            interactionSource = startInteractionSource,
                            modifier = Modifier.pressSquish(startInteractionSource, 0.95f),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Text(getText(R.string.session_start))
                        }
                    }
                }
            }
        }
    }

    if (showPinNameDialog) {
        PinNamingDialog(
            defaultName = defaultPinName,
            onDismiss = { showPinNameDialog = false },
            onConfirm = { name -> showPinNameDialog = false; onPinAsNew(selectedDeckId, name.ifBlank { null }) }
        )
    }
}

/**
 * [ChangePresetDeckDialog]'s single-select counterpart to [DeckExportTreeItem] — a radio button
 * instead of a checkbox, and tapping a row always *selects* it rather than toggling, since only
 * one node can ever be selected here.
 */
@Composable
private fun ChangePresetDeckTreeItem(
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
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "changePresetDeckChevron")
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
