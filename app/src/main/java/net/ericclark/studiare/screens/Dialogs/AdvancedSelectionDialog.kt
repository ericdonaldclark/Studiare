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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.data.naturalOrderComparator
import net.ericclark.studiare.screens.UI_Components.SelectionAction
import net.ericclark.studiare.screens.UI_Components.SelectionController
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.pressSquish

/**
 * The "advanced" cross-level selection path flagged in the roadmap plan: a flat, arbitrarily-deep
 * checkbox tree (same shape as [ExportDecksDialog]'s own `DeckExportTreeItem`, but bound straight
 * to the screen's own [SelectionController] instead of a local selection list) for picking items
 * from different parents/levels in one pass — e.g. a set under Deck A and a set under Deck B and
 * Deck C itself, all without drilling in and out of the tile-based Grid/Tree views. Opened from
 * those views' own [SelectionActionBar]; closing it returns to whichever view opened it with the
 * same [selection] now carrying whatever was picked here.
 *
 * [actions] is the same action list the opening screen's own action bar shows — rendered here as
 * a plain button row (icon to the left of its label, not stacked above it like the FAB-style bar)
 * since this dialog has room for text without crowding a tile grid. The "Advanced Selection"
 * action itself is filtered out since re-opening this same dialog from inside it would be a no-op.
 * Any action chosen here dismisses the dialog first — most of them (bulk delete, edit) open or
 * navigate to something else that shouldn't appear stacked behind this one.
 */
@Composable
fun AdvancedSelectionDialog(
    decks: List<DeckWithCards>,
    selection: SelectionController,
    actions: List<SelectionAction>,
    onDismiss: () -> Unit,
    title: String? = null
) {
    val dimensions = LocalStudiareDimensions.current
    // Starts empty — every node with children begins collapsed, matching ExportDecksDialog.
    val expandedIds = remember { mutableStateSetOf<String>() }
    val listState = rememberLazyListState()

    val childrenByParent = remember(decks) {
        decks.filter { it.deck.parentDeckId != null }
            .groupBy { it.deck.parentDeckId!! }
            .mapValues { (_, children) -> children.sortedWith(compareBy(naturalOrderComparator) { it.deck.name }) }
    }
    val rootItems = remember(decks) { decks.filter { it.deck.parentDeckId == null }.sortedWith(compareBy(naturalOrderComparator) { it.deck.name }) }
    fun childrenOf(id: String): List<DeckWithCards> = childrenByParent[id] ?: emptyList()
    fun hasChildren(id: String): Boolean = childrenByParent[id]?.isNotEmpty() == true

    val canScrollUp by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val canScrollDown by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index < listState.layoutInfo.totalItemsCount - 1
        }
    }

    val visibleActions = remember(actions) { actions.filter { it.id != "advancedSelection" } }

    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        val maxDialogHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Card(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).heightIn(max = maxDialogHeight),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                // Everything except the action row scrolls, so short windows can still reach every item.
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Text(
                        title ?: getText(R.string.advanced_selection_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = dimensions.spacingSmall).fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "${selection.selected.size} / ${decks.size} selected",
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
                                .heightIn(min = 200.dp, max = maxDialogHeight * 0.8f)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusSmall))
                        ) {
                            fun LazyListScope.emitNode(node: DeckWithCards, depth: Int) {
                                val nodeHasChildren = hasChildren(node.deck.id)
                                item(key = "advsel-${node.deck.id}") {
                                    AdvancedSelectionTreeItem(
                                        deck = node,
                                        depth = depth,
                                        isSelected = selection.isSelected(node.deck.id),
                                        onToggleSelected = { selection.toggle(node.deck.id) },
                                        hasChildren = nodeHasChildren,
                                        isExpanded = node.deck.id in expandedIds,
                                        onToggleExpanded = {
                                            if (node.deck.id in expandedIds) expandedIds.remove(node.deck.id) else expandedIds.add(node.deck.id)
                                        }
                                    )
                                }
                                if (nodeHasChildren && node.deck.id in expandedIds) {
                                    childrenOf(node.deck.id).forEach { child -> emitNode(child, depth + 1) }
                                }
                            }
                            rootItems.forEach { root -> emitNode(root, 0) }
                        }

                        Column(modifier = Modifier.align(Alignment.TopCenter)) {
                            AnimatedVisibility(
                                visible = canScrollUp,
                                enter = slideInVertically() + fadeIn() + expandVertically(),
                                exit = slideOutVertically() + fadeOut() + shrinkVertically()
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(24.dp).background(
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
                                    modifier = Modifier.fillMaxWidth().height(24.dp).background(
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
                    Spacer(Modifier.height(dimensions.spacingMedium))
                }

                // Non-FAB, icon-left/text-right buttons — see the file doc comment for why this
                // differs from the FAB-style SelectionActionBar the opening screen itself uses.
                if (visibleActions.isNotEmpty()) {
                    AdvancedSelectionActionRow(
                        actions = visibleActions,
                        onActionTaken = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(dimensions.spacingSmall))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.close))
                    }
                }
            }
        }
    }
}

/**
 * A horizontally-scrollable row of plain [Button]s — icon then label side by side, unlike
 * [net.ericclark.studiare.screens.UI_Components.SelectionActionBar]'s stacked FAB style — since
 * this dialog has the width to spare and the request was specifically for text beside the icon
 * here. Each tap dismisses the host dialog via [onActionTaken] before running the action itself.
 */
@Composable
private fun AdvancedSelectionActionRow(
    actions: List<SelectionAction>,
    onActionTaken: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(actions, key = { it.id }) { action ->
            val colors = if (action.isDestructive) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                ButtonDefaults.buttonColors()
            }
            Button(
                onClick = {
                    onActionTaken()
                    action.onClick()
                },
                colors = colors,
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) {
                Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(action.label, maxLines = 1)
            }
        }
    }
}

/**
 * One row in [AdvancedSelectionDialog]'s tree — identical visual shape to [ExportDecksDialog]'s
 * own `DeckExportTreeItem` (depth-scaled indentation, an independent expand/collapse chevron,
 * bold text only at the root), but checked state comes from the screen's shared
 * [SelectionController] via [isSelected]/[onToggleSelected] instead of a dialog-local list.
 */
@Composable
private fun AdvancedSelectionTreeItem(
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
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "advancedSelectionTreeChevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressSquish(interactionSource, 0.95f)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onToggleSelected() }
            .padding(
                start = (dimensions.paddingSmall + dimensions.paddingSmall * depth),
                end = dimensions.paddingSmall,
                top = 2.dp,
                bottom = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
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
