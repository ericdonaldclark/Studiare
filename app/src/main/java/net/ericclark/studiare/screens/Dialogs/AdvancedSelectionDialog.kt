package net.ericclark.studiare.screens.Dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.screens.UI_Components.SelectionAction
import net.ericclark.studiare.screens.UI_Components.SelectionController
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * The "advanced" cross-level selection path flagged in the roadmap plan: the shared
 * [net.ericclark.studiare.screens.UI_Components.DeckSetPickerGrid] (same flat, arbitrarily-deep
 * checkbox tree [ExportDecksDialog] and Settings → Deck Options use), bound to the screen's own
 * [SelectionController] instead of a dialog-local selection list, for picking items from different
 * parents/levels in one pass — e.g. a set under Deck A and a set under Deck B and Deck C itself,
 * all without drilling in and out of the tile-based Grid/Tree views. Opened from those views' own
 * [SelectionActionBar]; closing it returns to whichever view opened it with the same [selection]
 * now carrying whatever was picked here.
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
                        modifier = Modifier.padding(bottom = dimensions.spacingSmall).fillMaxWidth()
                    )
                    net.ericclark.studiare.screens.UI_Components.DeckSetPickerGrid(
                        decks = decks,
                        isSelected = { selection.isSelected(it.deck.id) },
                        onToggleSelected = { selection.toggle(it.deck.id) },
                        selectedCount = selection.selected.size,
                        totalSelectableCount = decks.size,
                        onToggleSelectAll = { selection.toggleAllWithin(decks.map { it.deck.id }) }
                    )
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
